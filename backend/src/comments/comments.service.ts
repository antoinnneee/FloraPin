import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';
import { UsersService } from '../users/users.service';
import { FlowerComment } from './flower-comment.entity';

/** Commentaire enrichi du nom d'affichage de son auteur et des droits du lecteur. */
export interface CommentResponse {
  id: string;
  flowerId: string;
  authoredBy: string;
  /** Nom d'affichage de l'auteur (vide si introuvable). */
  authorName: string;
  body: string;
  /** Le lecteur courant peut-il supprimer ce commentaire ? (auteur ou propriétaire). */
  canDelete: boolean;
  createdAt: Date;
}

/**
 * Fil de discussion sur une fleur : toute personne qui voit la fleur peut
 * commenter et lire les commentaires. L'auteur supprime les siens ; le
 * propriétaire de la fleur peut supprimer n'importe quel commentaire.
 */
@Injectable()
export class CommentsService {
  constructor(
    @InjectRepository(FlowerComment)
    private readonly comments: Repository<FlowerComment>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly shares: SharesService,
    private readonly notifications: NotificationsService,
    private readonly users: UsersService,
  ) {}

  /** Poste un commentaire sur une fleur visible par l'auteur. Notifie le propriétaire. */
  async post(
    authorId: string,
    flowerId: string,
    body: string,
  ): Promise<CommentResponse> {
    const flower = await this.visibleFlowerOrThrow(authorId, flowerId);

    const saved = await this.comments.save(
      this.comments.create({ flowerId, authoredBy: authorId, body }),
    );

    // Notifie le propriétaire (sauf auto-commentaire).
    if (flower.ownerId !== authorId) {
      await this.notifications.create(flower.ownerId, 'flower_commented', {
        flowerId,
        commentId: saved.id,
        byUserId: authorId,
      });
    }

    const authorName = (await this.users.findById(authorId))?.displayName ?? '';
    return this.buildResponse(saved, authorId, flower.ownerId, authorName);
  }

  /** Liste les commentaires d'une fleur visible, du plus ancien au plus récent. */
  async listForFlower(
    viewerId: string,
    flowerId: string,
  ): Promise<CommentResponse[]> {
    const flower = await this.visibleFlowerOrThrow(viewerId, flowerId);
    const comments = await this.comments.find({
      where: { flowerId },
      order: { createdAt: 'ASC' },
    });
    // Batch des auteurs : un seul chargement au lieu d'un users.findById par
    // commentaire (N+1).
    const authorIds = [...new Set(comments.map((c) => c.authoredBy))];
    const authors = await this.users.findByIds(authorIds);
    const nameById = new Map(authors.map((u) => [u.id, u.displayName]));
    return comments.map((c) =>
      this.buildResponse(
        c,
        viewerId,
        flower.ownerId,
        nameById.get(c.authoredBy) ?? '',
      ),
    );
  }

  /** Supprime un commentaire : autorisé pour son auteur ou le propriétaire de la fleur. */
  async delete(
    viewerId: string,
    flowerId: string,
    commentId: string,
  ): Promise<void> {
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    const comment = await this.comments.findOne({
      where: { id: commentId, flowerId },
    });
    if (!comment) {
      throw new NotFoundException('Commentaire introuvable.');
    }
    if (comment.authoredBy !== viewerId && flower.ownerId !== viewerId) {
      throw new ForbiddenException('Suppression non autorisée.');
    }
    await this.comments.delete(comment.id);
  }

  /** Assemble la réponse d'un commentaire à partir d'un nom d'auteur déjà résolu. */
  private buildResponse(
    c: FlowerComment,
    viewerId: string,
    ownerId: string,
    authorName: string,
  ): CommentResponse {
    return {
      id: c.id,
      flowerId: c.flowerId,
      authoredBy: c.authoredBy,
      authorName,
      body: c.body,
      canDelete: c.authoredBy === viewerId || ownerId === viewerId,
      createdAt: c.createdAt,
    };
  }

  /**
   * Vérifie que [viewerId] voit la fleur : propriétaire, partage ciblé reçu, ou
   * fleur diffusée à son réseau (`visibility='friends'`). Même périmètre que le
   * feed — délégué à SharesService.isVisibleTo (partagé avec les cœurs).
   */
  private async visibleFlowerOrThrow(
    viewerId: string,
    flowerId: string,
  ): Promise<Flower> {
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (!(await this.shares.isVisibleTo(viewerId, flower))) {
      throw new ForbiddenException('Fleur non accessible.');
    }
    return flower;
  }
}
