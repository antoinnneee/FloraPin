import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';
import { UsersService } from '../users/users.service';
import { FlowerComment } from './flower-comment.entity';

/**
 * Encodage d'une mention dans le corps d'un commentaire : `@[userId]`. On stocke
 * l'IDENTIFIANT (jamais le nom d'affichage) afin qu'un renommage (TÂCHE 1.7) ne
 * casse pas la mention — le nom est résolu au moment de l'affichage (`mentions`).
 * Le motif ne matche que des UUID pour ne pas confondre un texte quelconque
 * (`@[note]`) avec une mention.
 */
const MENTION_PATTERN =
  /@\[([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\]/gi;

/** Ids mentionnés dans un corps, dédupliqués et dans l'ordre d'apparition. */
export function parseMentionIds(body: string): string[] {
  const ids: string[] = [];
  for (const match of body.matchAll(MENTION_PATTERN)) {
    const id = match[1].toLowerCase();
    if (!ids.includes(id)) ids.push(id);
  }
  return ids;
}

/** Mention résolue : id encodé dans le corps + nom d'affichage COURANT. */
export interface CommentMention {
  userId: string;
  displayName: string;
}

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
  /**
   * Personnes mentionnées (`@[userId]`) dans `body`, avec leur nom d'affichage
   * courant — pour rendre `@Nom` côté client sans figer le nom (cf. 1.7).
   */
  mentions: CommentMention[];
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
    private readonly friendships: FriendshipsService,
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

    // Mentions @ami : notifie les amis mentionnés (best-effort, comme ci-dessus).
    const mentionIds = parseMentionIds(body);
    await this.notifyMentions(authorId, flower, saved.id, mentionIds, []);

    const authorName = (await this.users.findById(authorId))?.displayName ?? '';
    const replyToAuthorName = parent
      ? ((await this.users.findById(parent.authoredBy))?.displayName ?? '')
      : null;
    const mentions = await this.resolveMentions(mentionIds);
    return this.buildResponse(
      saved,
      authorId,
      flower.ownerId,
      authorName,
      {
        authorName: replyToAuthorName,
        body: parent?.body ?? null,
      },
      mentions,
    );
  }

  /**
   * Notifie les amis mentionnés (`@[userId]`) d'un commentaire. On ne notifie que
   * les mentions NOUVELLES ([previousIds] évite de re-pinguer à chaque édition),
   * en excluant l'auteur et le propriétaire de la fleur (déjà averti par
   * `flower_commented`). On restreint au réseau d'amis acceptés de l'auteur :
   * une mention est « @ami », ce qui écarte aussi un id arbitraire injecté.
   * Best-effort (createSafe) : le commentaire est déjà persisté.
   */
  private async notifyMentions(
    authorId: string,
    flower: Flower,
    commentId: string,
    mentionIds: string[],
    previousIds: string[],
  ): Promise<void> {
    const fresh = mentionIds.filter(
      (id) =>
        !previousIds.includes(id) &&
        id !== authorId &&
        id !== flower.ownerId,
    );
    if (fresh.length === 0) return;
    const friendIds = await this.friendships.acceptedFriendIds(authorId);
    for (const id of fresh) {
      if (!friendIds.includes(id)) continue;
      await this.notifications.createSafe(id, 'comment_mention', {
        flowerId: flower.id,
        commentId,
        byUserId: authorId,
      });
    }
  }

  /** Résout les noms d'affichage COURANTS des ids mentionnés (batch, sans N+1). */
  private async resolveMentions(ids: string[]): Promise<CommentMention[]> {
    if (ids.length === 0) return [];
    const users = await this.users.findByIds(ids);
    const nameById = new Map(users.map((u) => [u.id, u.displayName]));
    return ids.map((id) => ({
      userId: id,
      displayName: nameById.get(id) ?? '',
    }));
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
    // Batch des utilisateurs : auteurs ET personnes mentionnées, en une requête
    // (au lieu d'un users.findById par commentaire / mention — N+1).
    const mentionIdsByComment = new Map(
      comments.map((c) => [c.id, parseMentionIds(c.body)]),
    );
    const userIds = [
      ...new Set([
        ...comments.map((c) => c.authoredBy),
        ...[...mentionIdsByComment.values()].flat(),
      ]),
    ];
    const users = await this.users.findByIds(userIds);
    const nameById = new Map(users.map((u) => [u.id, u.displayName]));
    // Résolution de la citation : le parent est forcément un commentaire de la
    // même fleur, donc déjà dans `comments` (aplatissement à un seul niveau).
    const byId = new Map(comments.map((c) => [c.id, c]));
    return comments.map((c) => {
      const parent = c.replyToId ? byId.get(c.replyToId) : undefined;
      const mentions = (mentionIdsByComment.get(c.id) ?? []).map((id) => ({
        userId: id,
        displayName: nameById.get(id) ?? '',
      }));
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
        mentions,
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
    // Mentions déjà présentes AVANT l'édition : on ne re-pingue pas les amis
    // déjà mentionnés, seulement ceux ajoutés par cette modification.
    const previousMentionIds = parseMentionIds(comment.body);
    comment.body = body;
    comment.editedAt = new Date();
    const saved = await this.comments.save(comment);

    const mentionIds = parseMentionIds(body);
    await this.notifyMentions(
      viewerId,
      flower,
      comment.id,
      mentionIds,
      previousMentionIds,
    );

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
    const mentions = await this.resolveMentions(mentionIds);
    return this.buildResponse(
      saved,
      viewerId,
      flower.ownerId,
      authorName,
      replyTo,
      mentions,
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
    mentions: CommentMention[] = [],
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
      mentions,
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
