import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
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
      // Anti-énumération (I3) : on ne révèle pas l'inexistence du compte via un
      // 404. Réponse synthétique de même forme que le cas nominal (le client
      // Android parse le corps puis rafraîchit la liste : la « demande » fantôme
      // n'y apparaît jamais). Aucune ligne créée, aucune notification.
      return {
        id: randomUUID(),
        status: 'pending',
        direction: 'outgoing',
        user: { id: randomUUID(), displayName: '', email: email.trim() },
        createdAt: new Date(),
      };
    }
    return this.request(requesterId, addressee.id);
  }

  /**
   * Ajout d'ami par QR code (TÂCHE 4.5) : le demandeur a scanné le QR de
   * l'`addresseeId` (son UUID, jamais son email — vie privée).
   *
   * Sémantique idempotente + acceptation croisée, pour survivre au cas où
   * chacun scanne le QR de l'autre (la contrainte unique `(requesterId,
   * addresseeId)` interdit de créer un second `pending` symétrique) :
   *  - déjà amis → renvoie la relation telle quelle (pas de 409) ;
   *  - demande déjà envoyée par moi → renvoie la mienne (re-scan sans effet) ;
   *  - demande en attente reçue de l'autre (il m'a déjà demandé) → mon scan vaut
   *    consentement : on accepte automatiquement au lieu d'échouer ;
   *  - sinon → nouvelle demande `pending`.
   */
  async requestById(
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
      // Déjà amis, ou demande déjà émise par moi : idempotent, aucun changement.
      if (
        existing.status === 'accepted' ||
        existing.requesterId === requesterId
      ) {
        return this.toResponse(existing, requesterId);
      }
      // L'autre m'avait déjà envoyé une demande et je scanne son QR :
      // acceptation croisée (consentement mutuel).
      existing.status = 'accepted';
      const saved = await this.friendships.save(existing);
      await this.notifications.createSafe(
        existing.requesterId,
        'friend_accepted',
        { friendshipId: saved.id, byUserId: requesterId },
      );
      return this.toResponse(saved, requesterId);
    }

    const saved = await this.friendships.save(
      this.friendships.create({ requesterId, addresseeId, status: 'pending' }),
    );
    await this.notifications.createSafe(addresseeId, 'friend_request', {
      friendshipId: saved.id,
      fromUserId: requesterId,
    });
    return this.toResponse(saved, requesterId);
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
    // Best-effort : la demande est créée, un échec de notification ne doit pas 500.
    await this.notifications.createSafe(addresseeId, 'friend_request', {
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
    // Best-effort : l'amitié est acceptée, un échec de notification ne doit pas 500.
    await this.notifications.createSafe(
      friendship.requesterId,
      'friend_accepted',
      { friendshipId: saved.id, byUserId: userId },
    );
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

  /**
   * Amitié ACCEPTÉE entre [a] et [b] (dans un sens ou l'autre), ou null. Sert au
   * profil d'ami (TÂCHE 5.7) : `createdAt` fournit l'ancienneté « Amis depuis… ».
   */
  acceptedBetween(a: string, b: string): Promise<Friendship | null> {
    return this.friendships.findOne({
      where: [
        { requesterId: a, addresseeId: b, status: 'accepted' },
        { requesterId: b, addresseeId: a, status: 'accepted' },
      ],
    });
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
