import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { NotificationsService } from '../notifications/notifications.service';
import { UsersService } from '../users/users.service';
import { Friendship } from './friendship.entity';

export interface FriendshipResponse {
  id: string;
  status: string;
  direction: 'incoming' | 'outgoing';
  user: { id: string; displayName: string; email: string };
  createdAt: Date;
}

@Injectable()
export class FriendshipsService {
  constructor(
    @InjectRepository(Friendship)
    private readonly friendships: Repository<Friendship>,
    private readonly users: UsersService,
    private readonly notifications: NotificationsService,
  ) {}

  /** Invite par email : résout l'utilisateur puis délègue à request(). */
  async requestByEmail(
    requesterId: string,
    email: string,
  ): Promise<FriendshipResponse> {
    const addressee = await this.users.findByEmail(email);
    if (!addressee) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    return this.request(requesterId, addressee.id);
  }

  /** Crée une demande d'amitié (statut pending). */
  async request(
    requesterId: string,
    addresseeId: string,
  ): Promise<FriendshipResponse> {
    if (requesterId === addresseeId) {
      throw new BadRequestException('Impossible de s’ajouter soi-même.');
    }
    const addressee = await this.users.findById(addresseeId);
    if (!addressee) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    const existing = await this.findBetween(requesterId, addresseeId);
    if (existing) {
      throw new ConflictException('Une relation existe déjà.');
    }

    const saved = await this.friendships.save(
      this.friendships.create({ requesterId, addresseeId, status: 'pending' }),
    );
    await this.notifications.create(addresseeId, 'friend_request', {
      friendshipId: saved.id,
      fromUserId: requesterId,
    });
    return this.toResponse(saved, requesterId);
  }

  /** Le destinataire accepte une demande reçue. */
  async accept(userId: string, id: string): Promise<FriendshipResponse> {
    const friendship = await this.friendships.findOne({ where: { id } });
    if (!friendship) {
      throw new NotFoundException('Demande introuvable.');
    }
    if (friendship.addresseeId !== userId) {
      throw new ForbiddenException('Seul le destinataire peut accepter.');
    }
    if (friendship.status !== 'pending') {
      throw new ConflictException('La demande n’est pas en attente.');
    }
    friendship.status = 'accepted';
    const saved = await this.friendships.save(friendship);
    await this.notifications.create(friendship.requesterId, 'friend_accepted', {
      friendshipId: saved.id,
      byUserId: userId,
    });
    return this.toResponse(saved, userId);
  }

  /** Refuse / annule / supprime une relation impliquant l'utilisateur. */
  async remove(userId: string, id: string): Promise<void> {
    const friendship = await this.friendships.findOne({ where: { id } });
    if (!friendship) {
      throw new NotFoundException('Relation introuvable.');
    }
    if (
      friendship.requesterId !== userId &&
      friendship.addresseeId !== userId
    ) {
      throw new ForbiddenException('Relation non autorisée.');
    }
    await this.friendships.remove(friendship);
  }

  /** Liste les relations de l'utilisateur (entrantes et sortantes). */
  async list(userId: string): Promise<FriendshipResponse[]> {
    const relations = await this.friendships.find({
      where: [{ requesterId: userId }, { addresseeId: userId }],
      order: { createdAt: 'DESC' },
    });
    return Promise.all(relations.map((r) => this.toResponse(r, userId)));
  }

  /** Ids des amis acceptés de l'utilisateur (pour le partage — NODE-20). */
  async acceptedFriendIds(userId: string): Promise<string[]> {
    const relations = await this.friendships.find({
      where: [
        { requesterId: userId, status: 'accepted' },
        { addresseeId: userId, status: 'accepted' },
      ],
    });
    return relations.map((r) =>
      r.requesterId === userId ? r.addresseeId : r.requesterId,
    );
  }

  private findBetween(a: string, b: string): Promise<Friendship | null> {
    return this.friendships.findOne({
      where: [
        { requesterId: a, addresseeId: b },
        { requesterId: b, addresseeId: a },
      ],
    });
  }

  private async toResponse(
    friendship: Friendship,
    viewerId: string,
  ): Promise<FriendshipResponse> {
    const isOutgoing = friendship.requesterId === viewerId;
    const otherId = isOutgoing
      ? friendship.addresseeId
      : friendship.requesterId;
    const other = await this.users.findById(otherId);
    return {
      id: friendship.id,
      status: friendship.status,
      direction: isOutgoing ? 'outgoing' : 'incoming',
      user: {
        id: otherId,
        displayName: other?.displayName ?? '',
        email: other?.email ?? '',
      },
      createdAt: friendship.createdAt,
    };
  }
}
