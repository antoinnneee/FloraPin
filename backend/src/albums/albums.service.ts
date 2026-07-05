import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { GroupsService } from '../groups/groups.service';
import { AlbumPermission } from './album-permission.entity';
import { Album, AlbumPermissionMode } from './album.entity';
import {
  CreateAlbumDto,
  SetAlbumPermissionsDto,
  UpdateAlbumDto,
} from './dto/album.dto';

export interface AlbumPermissionEntry {
  userId: string;
  canEdit: boolean;
}

export interface AlbumResponse {
  id: string;
  ownerId: string;
  name: string;
  clientId: string | null;
  /** Groupe collaboratif de rattachement (null = album solo/privé). */
  groupId: string | null;
  /** Régime de droits pour un album de groupe. */
  permissionMode: AlbumPermissionMode;
  flowerIds: string[];
  /** Le demandeur peut-il éditer cet album (ajouter/retirer/renommer) ? */
  canEdit: boolean;
  /** Droits « au cas par cas » (présent seulement pour un album de groupe). */
  permissions?: AlbumPermissionEntry[];
  createdAt: Date;
}

@Injectable()
export class AlbumsService {
  constructor(
    @InjectRepository(Album)
    private readonly albums: Repository<Album>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    @InjectRepository(AlbumPermission)
    private readonly albumPermissions: Repository<AlbumPermission>,
    private readonly groups: GroupsService,
  ) {}

  /**
   * Crée un album. Trois cas :
   * - solo (défaut) : album privé du créateur ;
   * - `collaborative: true` sans `groupId` : crée AUSSI le groupe (décision n°1
   *   « créer un album crée le groupe ») et y rattache l'album ;
   * - `groupId` fourni : rattache l'album à un groupe existant dont le créateur
   *   est membre accepté.
   *
   * Idempotent sur (owner, clientId) comme précédemment.
   */
  async create(userId: string, dto: CreateAlbumDto): Promise<AlbumResponse> {
    if (dto.clientId) {
      const existing = await this.albums.findOne({
        where: { ownerId: userId, clientId: dto.clientId },
        relations: { flowers: true },
      });
      if (existing) {
        return this.toResponse(existing, userId);
      }
    }

    let groupId = dto.groupId ?? null;
    const permissionMode: AlbumPermissionMode = dto.permissionMode ?? 'open';
    if (groupId) {
      // Rattachement à un groupe existant : réservé aux membres acceptés.
      await this.groups.requireAcceptedMember(userId, groupId);
    } else if (dto.collaborative) {
      // Créer l'album crée le groupe.
      const group = await this.groups.create(userId, { name: dto.name });
      groupId = group.id;
    }

    const album = this.albums.create({
      ownerId: userId,
      name: dto.name,
      clientId: dto.clientId ?? null,
      groupId,
      permissionMode: groupId ? permissionMode : 'open',
      flowers: [],
    });
    const saved = await this.albums.save(album);
    return this.toResponse(saved, userId);
  }

  /**
   * Albums visibles par l'utilisateur : les siens (solo ou de groupe) ET les
   * albums des groupes dont il est membre accepté (collaboratifs).
   */
  async list(userId: string): Promise<AlbumResponse[]> {
    const own = await this.albums.find({
      where: { ownerId: userId },
      relations: { flowers: true },
      order: { createdAt: 'DESC' },
    });
    const groupResponses = await this.groups.list(userId);
    const acceptedGroupIds = groupResponses
      .filter((g) => g.status === 'accepted')
      .map((g) => g.id);
    let groupAlbums: Album[] = [];
    if (acceptedGroupIds.length > 0) {
      groupAlbums = await this.albums.find({
        where: { groupId: In(acceptedGroupIds) },
        relations: { flowers: true },
        order: { createdAt: 'DESC' },
      });
    }
    // Déduplication : mes propres albums de groupe apparaissent dans les deux.
    const byId = new Map<string, Album>();
    for (const a of [...own, ...groupAlbums]) byId.set(a.id, a);
    const all = [...byId.values()].sort(
      (a, b) => b.createdAt.getTime() - a.createdAt.getTime(),
    );
    return Promise.all(all.map((a) => this.toResponse(a, userId)));
  }

  async getById(userId: string, id: string): Promise<AlbumResponse> {
    const album = await this.requireVisibleAlbum(userId, id);
    return this.toResponse(album, userId);
  }

  async rename(
    userId: string,
    id: string,
    dto: UpdateAlbumDto,
  ): Promise<AlbumResponse> {
    const album = await this.requireEditableAlbum(userId, id);
    album.name = dto.name;
    const saved = await this.albums.save(album);
    return this.toResponse(saved, userId);
  }

  async remove(userId: string, id: string): Promise<void> {
    const album = await this.requireAlbum(id);
    // La suppression d'un album reste réservée à son propriétaire, même en groupe
    // (un membre ne supprime pas l'album d'un autre — il peut quitter le groupe).
    if (album.ownerId !== userId) {
      throw new ForbiddenException('Seul le propriétaire supprime l’album.');
    }
    await this.albumPermissions.delete({ albumId: album.id });
    await this.albums.remove(album);
  }

  /**
   * Rattache l'album à un groupe (ou le détache si `groupId` null). Réservé au
   * propriétaire de l'album ; pour un rattachement, il doit être membre accepté
   * du groupe cible.
   */
  async setGroup(
    userId: string,
    albumId: string,
    groupId: string | null,
    permissionMode?: AlbumPermissionMode,
  ): Promise<AlbumResponse> {
    const album = await this.requireAlbum(albumId);
    if (album.ownerId !== userId) {
      throw new ForbiddenException('Seul le propriétaire rattache l’album.');
    }
    if (groupId) {
      await this.groups.requireAcceptedMember(userId, groupId);
      album.groupId = groupId;
      album.permissionMode = permissionMode ?? album.permissionMode ?? 'open';
    } else {
      album.groupId = null;
      album.permissionMode = 'open';
      await this.albumPermissions.delete({ albumId: album.id });
    }
    const saved = await this.albums.save(album);
    return this.toResponse(saved, userId);
  }

  /**
   * Configure les droits d'un album de groupe (mode + droits par membre).
   * Réservé au propriétaire de l'album. En mode 'open', purge les droits
   * individuels (inutiles). En mode 'restricted', remplace la table des droits.
   */
  async setPermissions(
    userId: string,
    albumId: string,
    dto: SetAlbumPermissionsDto,
  ): Promise<AlbumResponse> {
    const album = await this.requireAlbum(albumId);
    if (album.ownerId !== userId) {
      throw new ForbiddenException('Seul le propriétaire règle les droits.');
    }
    if (!album.groupId) {
      throw new ForbiddenException('Album non collaboratif (aucun groupe).');
    }
    album.permissionMode = dto.mode;
    await this.albums.save(album);
    await this.albumPermissions.delete({ albumId: album.id });
    if (dto.mode === 'restricted' && dto.entries?.length) {
      const acceptedIds = new Set(
        await this.groups.acceptedMemberIds(album.groupId),
      );
      const rows = dto.entries
        // On n'accorde des droits qu'à des membres acceptés du groupe.
        .filter((e) => acceptedIds.has(e.userId) && e.userId !== album.ownerId)
        .map((e) =>
          this.albumPermissions.create({
            albumId: album.id,
            userId: e.userId,
            canEdit: e.canEdit,
          }),
        );
      if (rows.length) await this.albumPermissions.save(rows);
    }
    const reloaded = await this.requireAlbum(albumId);
    return this.toResponse(reloaded, userId);
  }

  /**
   * Ajoute une fleur à l'album. La fleur doit appartenir à l'utilisateur qui
   * l'ajoute (chacun apporte ses propres fleurs dans un album collaboratif).
   * Idempotent.
   */
  async addFlower(
    userId: string,
    albumId: string,
    flowerId: string,
  ): Promise<AlbumResponse> {
    const album = await this.requireEditableAlbum(userId, albumId);
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId: userId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (!album.flowers.some((f) => f.id === flowerId)) {
      album.flowers.push(flower);
      await this.albums.save(album);
    }
    return this.toResponse(album, userId);
  }

  /** Retire une fleur de l'album. Idempotent. */
  async removeFlower(
    userId: string,
    albumId: string,
    flowerId: string,
  ): Promise<AlbumResponse> {
    const album = await this.requireEditableAlbum(userId, albumId);
    album.flowers = album.flowers.filter((f) => f.id !== flowerId);
    await this.albums.save(album);
    return this.toResponse(album, userId);
  }

  // --- Résolution d'accès ---

  private async requireAlbum(id: string): Promise<Album> {
    const album = await this.albums.findOne({
      where: { id },
      relations: { flowers: true },
    });
    if (!album) {
      throw new NotFoundException('Album introuvable.');
    }
    return album;
  }

  /** Album visible par l'utilisateur (propriétaire ou membre accepté du groupe). */
  private async requireVisibleAlbum(
    userId: string,
    id: string,
  ): Promise<Album> {
    const album = await this.requireAlbum(id);
    if (album.ownerId === userId) return album;
    if (album.groupId && (await this.groups.isAcceptedMember(userId, album.groupId))) {
      return album;
    }
    // On ne révèle pas l'existence d'un album inaccessible.
    throw new NotFoundException('Album introuvable.');
  }

  /** Album éditable par l'utilisateur (selon le régime de droits). */
  private async requireEditableAlbum(
    userId: string,
    id: string,
  ): Promise<Album> {
    const album = await this.requireVisibleAlbum(userId, id);
    if (await this.canEdit(userId, album)) return album;
    throw new ForbiddenException('Droits d’édition insuffisants sur cet album.');
  }

  private async canEdit(userId: string, album: Album): Promise<boolean> {
    if (album.ownerId === userId) return true;
    if (!album.groupId) return false;
    if (!(await this.groups.isAcceptedMember(userId, album.groupId))) {
      return false;
    }
    if (album.permissionMode === 'open') return true;
    const grant = await this.albumPermissions.findOne({
      where: { albumId: album.id, userId },
    });
    return grant?.canEdit === true;
  }

  private async toResponse(
    album: Album,
    viewerId: string,
  ): Promise<AlbumResponse> {
    const response: AlbumResponse = {
      id: album.id,
      ownerId: album.ownerId,
      name: album.name,
      clientId: album.clientId ?? null,
      groupId: album.groupId ?? null,
      permissionMode: album.permissionMode ?? 'open',
      flowerIds: (album.flowers ?? []).map((f) => f.id),
      canEdit: await this.canEdit(viewerId, album),
      createdAt: album.createdAt,
    };
    if (album.groupId) {
      const perms = await this.albumPermissions.find({
        where: { albumId: album.id },
      });
      response.permissions = perms.map((p) => ({
        userId: p.userId,
        canEdit: p.canEdit,
      }));
    }
    return response;
  }
}
