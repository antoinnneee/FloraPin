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
  /** Le lecteur courant peut-il éditer ce commentaire ? (auteur uniquement). */
  canEdit: boolean;
  createdAt: Date;
  /** Dernière édition par l'auteur, `null` si jamais modifié. */
  editedAt: Date | null;
  /** Réponse citée : id du commentaire racine visé, `null` au premier niveau. */
  replyToId: string | null;
  /** Nom d'affichage de l'auteur du commentaire cité (`null` si sans réponse). */
  replyToAuthorName: string | null;
  /** Texte du commentaire cité (`null` si sans réponse). */
  replyToBody: string | null;
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

  /**
   * Poste un commentaire sur une fleur visible par l'auteur. Notifie le
   * propriétaire. Si [replyToId] est fourni, le commentaire répond à un autre :
   * le fil est volontairement à un seul niveau, donc une réponse à une réponse
   * est aplatie pour pointer la racine.
   */
  async post(
    authorId: string,
    flowerId: string,
    body: string,
    replyToId?: string,
  ): Promise<CommentResponse> {
    const flower = await this.visibleFlowerOrThrow(authorId, flowerId);

    // Réponse citée : la cible doit exister sur la MÊME fleur. On aplatit vers
    // la racine (fil à un seul niveau) et on garde le parent pour la citation.
    let parent: FlowerComment | null = null;
    if (replyToId) {
      parent = await this.rootParentOrThrow(flowerId, replyToId);
    }

    const saved = await this.comments.save(
      this.comments.create({
        flowerId,
        authoredBy: authorId,
        body,
        replyToId: parent?.id ?? null,
      }),
    );

    // Notifie le propriétaire (sauf auto-commentaire) — best-effort : le
    // commentaire est déjà persisté, un échec de notification ne doit pas 500.
    if (flower.ownerId !== authorId) {
      await this.notifications.createSafe(flower.ownerId, 'flower_commented', {
        flowerId,
        commentId: saved.id,
        byUserId: authorId,
      });
    }

    const authorName = (await this.users.findById(authorId))?.displayName ?? '';
    const replyToAuthorName = parent
      ? ((await this.users.findById(parent.authoredBy))?.displayName ?? '')
      : null;
    return this.buildResponse(saved, authorId, flower.ownerId, authorName, {
      authorName: replyToAuthorName,
      body: parent?.body ?? null,
    });
  }

  /**
   * Charge le commentaire racine visé par une réponse. La cible doit appartenir
   * à la même fleur ; si elle est elle-même une réponse, on remonte à sa racine
   * (aplatissement, fil à un seul niveau).
   */
  private async rootParentOrThrow(
    flowerId: string,
    targetId: string,
  ): Promise<FlowerComment> {
    const target = await this.comments.findOne({
      where: { id: targetId, flowerId },
    });
    if (!target) {
      throw new NotFoundException('Commentaire cité introuvable.');
    }
    if (!target.replyToId) {
      return target;
    }
    const root = await this.comments.findOne({
      where: { id: target.replyToId, flowerId },
    });
    // La racine devrait toujours exister (cascade FK) ; repli défensif sur la
    // cible directe si l'intégrité était rompue.
    return root ?? target;
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
    // Résolution de la citation : le parent est forcément un commentaire de la
    // même fleur, donc déjà dans `comments` (aplatissement à un seul niveau).
    const byId = new Map(comments.map((c) => [c.id, c]));
    return comments.map((c) => {
      const parent = c.replyToId ? byId.get(c.replyToId) : undefined;
      return this.buildResponse(
        c,
        viewerId,
        flower.ownerId,
        nameById.get(c.authoredBy) ?? '',
        parent
          ? {
              authorName: nameById.get(parent.authoredBy) ?? '',
              body: parent.body,
            }
          : null,
      );
    });
  }

  /** Édite un commentaire : réservé à son auteur. Marque `editedAt`. */
  async update(
    viewerId: string,
    flowerId: string,
    commentId: string,
    body: string,
  ): Promise<CommentResponse> {
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
    if (comment.authoredBy !== viewerId) {
      throw new ForbiddenException('Édition non autorisée.');
    }
    comment.body = body;
    comment.editedAt = new Date();
    const saved = await this.comments.save(comment);

    const authorName =
      (await this.users.findById(comment.authoredBy))?.displayName ?? '';
    // Préserve la citation existante : l'édition ne change pas le parent.
    const parent = saved.replyToId
      ? await this.comments.findOne({
          where: { id: saved.replyToId, flowerId },
        })
      : null;
    const replyTo = parent
      ? {
          authorName:
            (await this.users.findById(parent.authoredBy))?.displayName ?? '',
          body: parent.body,
        }
      : null;
    return this.buildResponse(
      saved,
      viewerId,
      flower.ownerId,
      authorName,
      replyTo,
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

  /**
   * Assemble la réponse d'un commentaire à partir d'un nom d'auteur déjà résolu.
   * [replyTo] porte la citation du commentaire parent (nom + texte), `null` pour
   * un commentaire de premier niveau.
   */
  private buildResponse(
    c: FlowerComment,
    viewerId: string,
    ownerId: string,
    authorName: string,
    replyTo: { authorName: string | null; body: string | null } | null = null,
  ): CommentResponse {
    return {
      id: c.id,
      flowerId: c.flowerId,
      authoredBy: c.authoredBy,
      authorName,
      body: c.body,
      canDelete: c.authoredBy === viewerId || ownerId === viewerId,
      canEdit: c.authoredBy === viewerId,
      createdAt: c.createdAt,
      editedAt: c.editedAt ?? null,
      replyToId: c.replyToId ?? null,
      replyToAuthorName: replyTo?.authorName ?? null,
      replyToBody: replyTo?.body ?? null,
    };
  }

  /**
   * Vérifie que [viewerId] peut participer au fil de la fleur. Deux périmètres se
   * cumulent :
   * - il voit la fleur (propriétaire, partage ciblé, ou diffusion réseau) —
   *   `SharesService.isVisibleTo`, partagé avec les cœurs ;
   * - OU la fleur est ouverte à l'identification et il fait partie du réseau
   *   d'amis sollicités — `SharesService.needsIdentificationVisibleTo`. On peut
   *   ainsi discuter d'une demande d'identification (environnement, demander une
   *   photo supplémentaire…) même sans partage ciblé ni publication au flux.
   */
  private async visibleFlowerOrThrow(
    viewerId: string,
    flowerId: string,
  ): Promise<Flower> {
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    const canParticipate =
      (await this.shares.isVisibleTo(viewerId, flower)) ||
      (await this.shares.needsIdentificationVisibleTo(viewerId, flower));
    if (!canParticipate) {
      throw new ForbiddenException('Fleur non accessible.');
    }
    return flower;
  }
}
