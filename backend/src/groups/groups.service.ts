import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Album } from '../albums/album.entity';
import { AlbumPermission } from '../albums/album-permission.entity';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { UsersService } from '../users/users.service';
import {
  CreateGroupDto,
  InviteMemberDto,
  UpdateGroupDto,
} from './dto/group.dto';
import { Group } from './group.entity';
import {
  GroupMember,
  GroupMemberRole,
  GroupMemberStatus,
} from './group-member.entity';

export interface GroupMemberResponse {
  userId: string;
  displayName: string;
  role: GroupMemberRole;
  status: GroupMemberStatus;
}

export interface GroupResponse {
  id: string;
  ownerId: string;
  name: string;
  clientId: string | null;
  /** Rôle du demandeur dans ce groupe. */
  role: GroupMemberRole;
  /** Statut d'appartenance du demandeur ('pending' pour une invitation reçue). */
  status: GroupMemberStatus;
  members: GroupMemberResponse[];
  createdAt: Date;
}

@Injectable()
export class GroupsService {
  constructor(
    @InjectRepository(Group)
    private readonly groups: Repository<Group>,
    @InjectRepository(GroupMember)
    private readonly members: Repository<GroupMember>,
    @InjectRepository(Album)
    private readonly albums: Repository<Album>,
    @InjectRepository(AlbumPermission)
    private readonly albumPermissions: Repository<AlbumPermission>,
    private readonly users: UsersService,
    private readonly friendships: FriendshipsService,
    private readonly notifications: NotificationsService,
  ) {}

  /**
   * Crée un groupe et inscrit le créateur comme membre `owner`/`accepted`.
   * Idempotent sur (owner, clientId) : un re-push retombe sur le groupe existant.
   */
  async create(ownerId: string, dto: CreateGroupDto): Promise<GroupResponse> {
    if (dto.clientId) {
      const existing = await this.groups.findOne({
        where: { ownerId, clientId: dto.clientId },
      });
      if (existing) {
        return this.toResponse(existing, ownerId);
      }
    }
    const group = await this.groups.save(
      this.groups.create({
        ownerId,
        name: dto.name,
        clientId: dto.clientId ?? null,
      }),
    );
    await this.members.save(
      this.members.create({
        groupId: group.id,
        userId: ownerId,
        role: 'owner',
        status: 'accepted',
        invitedBy: null,
      }),
    );
    return this.toResponse(group, ownerId);
  }

  /** Groupes où l'utilisateur est membre (accepté ou invitation en attente). */
  async list(userId: string): Promise<GroupResponse[]> {
    const memberships = await this.members.find({
      where: { userId },
      order: { createdAt: 'DESC' },
    });
    const groups = await Promise.all(
      memberships.map((m) => this.groups.findOne({ where: { id: m.groupId } })),
    );
    const result: GroupResponse[] = [];
    for (const group of groups) {
      if (group) result.push(await this.toResponse(group, userId));
    }
    return result;
  }

  async getById(userId: string, id: string): Promise<GroupResponse> {
    const group = await this.requireGroup(id);
    // Un membre en attente peut voir le groupe (pour décider d'accepter).
    await this.requireMembership(userId, id);
    return this.toResponse(group, userId);
  }

  async rename(
    userId: string,
    id: string,
    dto: UpdateGroupDto,
  ): Promise<GroupResponse> {
    const group = await this.requireGroup(id);
    await this.requireOwner(userId, group);
    group.name = dto.name;
    const saved = await this.groups.save(group);
    return this.toResponse(saved, userId);
  }

  /**
   * Invite un ami dans le groupe. L'invitant doit être membre accepté et l'invité
   * doit être un de ses amis (découplé du partage réseau mais réservé au cercle
   * de confiance). Idempotent : une invitation/appartenance déjà présente est
   * renvoyée telle quelle.
   */
  async invite(
    inviterId: string,
    groupId: string,
    dto: InviteMemberDto,
  ): Promise<GroupResponse> {
    const group = await this.requireGroup(groupId);
    await this.requireAcceptedMember(inviterId, groupId);
    if (dto.userId === inviterId) {
      throw new BadRequestException('Impossible de s’inviter soi-même.');
    }
    const invitee = await this.users.findById(dto.userId);
    if (!invitee) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    const friendship = await this.friendships.acceptedBetween(
      inviterId,
      dto.userId,
    );
    if (!friendship) {
      throw new ForbiddenException('On ne peut inviter que ses amis.');
    }
    const existing = await this.members.findOne({
      where: { groupId, userId: dto.userId },
    });
    if (!existing) {
      await this.members.save(
        this.members.create({
          groupId,
          userId: dto.userId,
          role: 'member',
          status: 'pending',
          invitedBy: inviterId,
        }),
      );
      await this.notifications.createSafe(dto.userId, 'group_invited', {
        groupId,
        groupName: group.name,
        fromUserId: inviterId,
      });
    }
    return this.toResponse(group, inviterId);
  }

  /** Le destinataire accepte son invitation. Notifie le propriétaire du groupe. */
  async accept(userId: string, groupId: string): Promise<GroupResponse> {
    const group = await this.requireGroup(groupId);
    const membership = await this.members.findOne({
      where: { groupId, userId },
    });
    if (!membership) {
      throw new NotFoundException('Invitation introuvable.');
    }
    if (membership.status !== 'accepted') {
      membership.status = 'accepted';
      await this.members.save(membership);
      await this.notifications.createSafe(group.ownerId, 'group_member_joined', {
        groupId,
        groupName: group.name,
        byUserId: userId,
      });
    }
    return this.toResponse(group, userId);
  }

  /**
   * Retrait d'un membre :
   * - le propriétaire peut retirer n'importe quel membre (sauf lui-même — il doit
   *   supprimer le groupe) ;
   * - un membre peut se retirer lui-même (quitter le groupe).
   */
  async removeMember(
    actorId: string,
    groupId: string,
    targetUserId: string,
  ): Promise<void> {
    const group = await this.requireGroup(groupId);
    const isOwner = group.ownerId === actorId;
    const isSelf = targetUserId === actorId;
    if (!isOwner && !isSelf) {
      throw new ForbiddenException(
        'Seul le propriétaire retire un autre membre.',
      );
    }
    if (targetUserId === group.ownerId) {
      throw new BadRequestException(
        'Le propriétaire ne peut pas quitter son groupe ; supprimez-le.',
      );
    }
    const membership = await this.members.findOne({
      where: { groupId, userId: targetUserId },
    });
    if (!membership) {
      throw new NotFoundException('Membre introuvable.');
    }
    await this.members.remove(membership);
    // Purge les droits « au cas par cas » de ce membre sur les albums du groupe.
    const albums = await this.albums.find({ where: { groupId } });
    for (const album of albums) {
      await this.albumPermissions.delete({
        albumId: album.id,
        userId: targetUserId,
      });
    }
  }

  /**
   * Supprime le groupe (propriétaire uniquement). Les albums rattachés
   * redeviennent solos (groupId null) chez leur propriétaire — on ne détruit pas
   * les fleurs. Purge les membres et les droits d'album.
   */
  async remove(userId: string, groupId: string): Promise<void> {
    const group = await this.requireGroup(groupId);
    await this.requireOwner(userId, group);
    const albums = await this.albums.find({ where: { groupId } });
    for (const album of albums) {
      album.groupId = null;
      album.permissionMode = 'open';
      await this.albums.save(album);
      await this.albumPermissions.delete({ albumId: album.id });
    }
    await this.members.delete({ groupId });
    await this.groups.remove(group);
  }

  // --- Helpers réutilisés par AlbumsService ---

  /** Membre accepté du groupe ? */
  async isAcceptedMember(userId: string, groupId: string): Promise<boolean> {
    const membership = await this.members.findOne({
      where: { groupId, userId, status: 'accepted' },
    });
    return membership != null;
  }

  async requireAcceptedMember(userId: string, groupId: string): Promise<void> {
    if (!(await this.isAcceptedMember(userId, groupId))) {
      throw new ForbiddenException('Accès réservé aux membres du groupe.');
    }
  }

  /** Propriétaire du groupe, ou null. */
  async ownerOf(groupId: string): Promise<string | null> {
    const group = await this.groups.findOne({ where: { id: groupId } });
    return group?.ownerId ?? null;
  }

  /** Ids des membres acceptés (pour notifier tout le groupe). */
  async acceptedMemberIds(groupId: string): Promise<string[]> {
    const rows = await this.members.find({
      where: { groupId, status: 'accepted' },
    });
    return rows.map((r) => r.userId);
  }

  private async requireGroup(id: string): Promise<Group> {
    const group = await this.groups.findOne({ where: { id } });
    if (!group) {
      throw new NotFoundException('Groupe introuvable.');
    }
    return group;
  }

  private async requireMembership(
    userId: string,
    groupId: string,
  ): Promise<GroupMember> {
    const membership = await this.members.findOne({
      where: { groupId, userId },
    });
    if (!membership) {
      throw new ForbiddenException('Accès réservé aux membres du groupe.');
    }
    return membership;
  }

  private async requireOwner(userId: string, group: Group): Promise<void> {
    if (group.ownerId !== userId) {
      throw new ForbiddenException('Réservé au propriétaire du groupe.');
    }
  }

  private async toResponse(
    group: Group,
    viewerId: string,
  ): Promise<GroupResponse> {
    const rows = await this.members.find({
      where: { groupId: group.id },
      order: { createdAt: 'ASC' },
    });
    const users = await this.users.findByIds(rows.map((r) => r.userId));
    const nameById = new Map(users.map((u) => [u.id, u.displayName]));
    const members: GroupMemberResponse[] = rows.map((r) => ({
      userId: r.userId,
      displayName: nameById.get(r.userId) ?? '',
      role: r.role,
      status: r.status,
    }));
    const mine = rows.find((r) => r.userId === viewerId);
    return {
      id: group.id,
      ownerId: group.ownerId,
      name: group.name,
      clientId: group.clientId ?? null,
      role: mine?.role ?? 'member',
      status: mine?.status ?? 'pending',
      members,
      createdAt: group.createdAt,
    };
  }
}
