import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';
import { UsersService } from '../users/users.service';
import { DEFAULT_REACTION, FlowerLike, Reaction } from './flower-like.entity';

/** Un « liker » d'une fleur : identifiant, nom d'affichage et réaction posée. */
export interface LikerResponse {
  userId: string;
  /** Nom d'affichage du liker (vide si introuvable). */
  displayName: string;
  /** Type de réaction posé par ce liker (TÂCHE 3.5). */
  reaction: Reaction;
}

/**
 * Cœurs sur les fleurs (NODE-139) : pose/retrait idempotents et notification
 * best-effort au propriétaire. Réservé aux fleurs visibles par le viewer
 * (même périmètre que les commentaires — SharesService.isVisibleTo).
 */
@Injectable()
export class LikesService {
  constructor(
    @InjectRepository(FlowerLike)
    private readonly likes: Repository<FlowerLike>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly shares: SharesService,
    private readonly notifications: NotificationsService,
    private readonly users: UsersService,
  ) {}

  /**
   * Pose une réaction (idempotent), ou met à jour le type si l'utilisateur en
   * avait déjà une (changer d'emoji = update, pas insert — la clé composite
   * (flower, user) garantit une seule réaction). [reaction] par défaut = cœur
   * (compat ascendante : POST sans corps). Notifie le propriétaire uniquement à
   * la PREMIÈRE réaction (pas au simple changement d'emoji), et jamais en
   * auto-réaction.
   */
  async like(
    userId: string,
    flowerId: string,
    reaction: Reaction = DEFAULT_REACTION,
  ): Promise<void> {
    const flower = await this.visibleFlowerOrThrow(userId, flowerId);

    const existing = await this.likes.findOne({ where: { flowerId, userId } });
    if (existing) {
      if (existing.reaction !== reaction) {
        existing.reaction = reaction;
        await this.likes.save(existing);
      }
      return;
    }

    await this.likes.save(this.likes.create({ flowerId, userId, reaction }));

    if (flower.ownerId !== userId) {
      await this.notifications.create(flower.ownerId, 'flower_liked', {
        flowerId,
        byUserId: userId,
      });
    }
  }

  /** Retire la réaction (idempotent : sans effet si absente). */
  async unlike(userId: string, flowerId: string): Promise<void> {
    await this.visibleFlowerOrThrow(userId, flowerId);
    await this.likes.delete({ flowerId, userId });
  }

  /**
   * Liste les utilisateurs ayant posé un cœur sur la fleur, du plus ancien au
   * plus récent. Même contrôle d'accès que pose/retrait : le viewer doit voir
   * la fleur (sinon 404, on ne révèle pas l'existence d'une fleur privée).
   */
  async listLikers(
    viewerId: string,
    flowerId: string,
  ): Promise<LikerResponse[]> {
    await this.visibleFlowerOrThrow(viewerId, flowerId);
    const rows = await this.likes.find({
      where: { flowerId },
      order: { createdAt: 'ASC' },
    });
    // Résolution en lot des noms d'affichage (évite un findById par liker).
    const userIds = [...new Set(rows.map((r) => r.userId))];
    const users = await this.users.findByIds(userIds);
    const nameById = new Map(users.map((u) => [u.id, u.displayName]));
    return rows.map((r) => ({
      userId: r.userId,
      displayName: nameById.get(r.userId) ?? '',
      reaction: r.reaction ?? DEFAULT_REACTION,
    }));
  }

  /**
   * Renvoie la fleur si [viewerId] la voit, sinon 404 (on ne révèle pas
   * l'existence d'une fleur non accessible).
   */
  private async visibleFlowerOrThrow(
    viewerId: string,
    flowerId: string,
  ): Promise<Flower> {
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    if (!flower || !(await this.shares.isVisibleTo(viewerId, flower))) {
      throw new NotFoundException('Fleur introuvable.');
    }
    return flower;
  }
}
