import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, IsNull, Repository } from 'typeorm';
import { Album } from '../albums/album.entity';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse, FlowersService } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { CreateShareDto } from './dto/share.dto';
import { Share } from './share.entity';

@Injectable()
export class SharesService {
  constructor(
    @InjectRepository(Share)
    private readonly shares: Repository<Share>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    @InjectRepository(Album)
    private readonly albums: Repository<Album>,
    private readonly friendships: FriendshipsService,
    private readonly flowersService: FlowersService,
    private readonly notifications: NotificationsService,
  ) {}

  /** Crée un partage configurable vers un ami accepté. */
  async create(ownerId: string, dto: CreateShareDto): Promise<Share> {
    const friends = await this.friendships.acceptedFriendIds(ownerId);
    if (!friends.includes(dto.friendId)) {
      throw new ForbiddenException(
        'Le partage est réservé aux amis acceptés.',
      );
    }

    let flowerId: string | null = null;
    let albumId: string | null = null;
    if (dto.scope === 'flower') {
      const flower = await this.flowers.findOne({
        where: { id: dto.flowerId, ownerId },
      });
      if (!flower) {
        throw new NotFoundException('Fleur introuvable.');
      }
      flowerId = flower.id;
    } else if (dto.scope === 'album') {
      const album = await this.albums.findOne({
        where: { id: dto.albumId, ownerId },
      });
      if (!album) {
        throw new NotFoundException('Album introuvable.');
      }
      albumId = album.id;
    }

    const existing = await this.shares.findOne({
      where: {
        ownerId,
        sharedWith: dto.friendId,
        scope: dto.scope,
        flowerId: flowerId ?? IsNull(),
        albumId: albumId ?? IsNull(),
      },
    });
    if (existing) {
      throw new ConflictException('Ce partage existe déjà.');
    }

    const share = await this.shares.save(
      this.shares.create({
        ownerId,
        sharedWith: dto.friendId,
        scope: dto.scope,
        flowerId,
        albumId,
        includeGps: dto.includeGps ?? true,
      }),
    );

    // Notifie le destinataire du partage (NODE-56).
    await this.notifications.create(dto.friendId, 'flower_shared', {
      shareId: share.id,
      fromUserId: ownerId,
      scope: share.scope,
      flowerId: share.flowerId,
      albumId: share.albumId,
    });

    return share;
  }

  listMine(ownerId: string): Promise<Share[]> {
    return this.shares.find({
      where: { ownerId },
      order: { createdAt: 'DESC' },
    });
  }

  async revoke(ownerId: string, id: string): Promise<void> {
    const share = await this.shares.findOne({ where: { id, ownerId } });
    if (!share) {
      throw new NotFoundException('Partage introuvable.');
    }
    await this.shares.remove(share);
  }

  /**
   * Fleurs partagées avec [viewerId], coordonnées masquées si le partage
   * correspondant désactive le GPS. Dédupliquées par id (on conserve la version
   * la plus permissive : GPS visible s'il l'est dans au moins un partage).
   */
  async sharedWithMe(viewerId: string): Promise<FlowerResponse[]> {
    const shares = await this.shares.find({
      where: { sharedWith: viewerId },
    });

    const byId = new Map<string, FlowerResponse>();
    for (const share of shares) {
      const flowers = await this.resolveFlowers(share);

      for (const flower of flowers) {
        const response = await this.flowersService.toResponse(flower, viewerId);
        const presented = share.includeGps ? response : stripGps(response);
        const previous = byId.get(presented.id);
        // Conserve la variante avec GPS si elle existe.
        if (!previous || (previous.latitude === null && share.includeGps)) {
          byId.set(presented.id, presented);
        }
      }
    }
    return [...byId.values()];
  }

  /**
   * Fleurs diffusées à tout le réseau d'amis (NODE-136) : celles de mes amis
   * acceptés marquées `visibility='friends'`, sans partage ciblé. Le GPS est
   * masqué quand `feedIncludeGps` est false (option héritée des partages, NODE-22).
   */
  async broadcastWithMe(viewerId: string): Promise<FlowerResponse[]> {
    const friendIds = await this.friendships.acceptedFriendIds(viewerId);
    if (friendIds.length === 0) return [];

    const flowers = await this.flowers.find({
      where: { ownerId: In(friendIds), visibility: 'friends' },
    });
    return Promise.all(
      flowers.map(async (flower) => {
        const response = await this.flowersService.toResponse(flower, viewerId);
        return flower.feedIncludeGps ? response : stripGps(response);
      }),
    );
  }

  /** Résout les fleurs concrètes couvertes par un partage selon son périmètre. */
  private async resolveFlowers(share: Share): Promise<Flower[]> {
    switch (share.scope) {
      case 'all':
        return this.flowers.find({ where: { ownerId: share.ownerId } });
      case 'album':
        return this.resolveAlbum(share.albumId);
      default:
        return this.resolveSingle(share.flowerId);
    }
  }

  private async resolveSingle(flowerId: string | null): Promise<Flower[]> {
    if (!flowerId) return [];
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    return flower ? [flower] : [];
  }

  private async resolveAlbum(albumId: string | null): Promise<Flower[]> {
    if (!albumId) return [];
    const album = await this.albums.findOne({
      where: { id: albumId },
      relations: { flowers: true },
    });
    return album?.flowers ?? [];
  }
}

function stripGps(flower: FlowerResponse): FlowerResponse {
  return { ...flower, latitude: null, longitude: null, accuracyM: null };
}
