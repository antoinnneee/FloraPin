import { BadRequestException, Injectable } from '@nestjs/common';
import { FlowerResponse } from '../flowers/flowers.service';
import { FeedCursor, SharesService } from '../shares/shares.service';
import { FeedSort } from './dto/feed.dto';

const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 200;

/**
 * Feed des amis (NODE-23/136) : les fleurs visibles par l'utilisateur, des plus
 * récentes aux plus anciennes. Réunit les partages ciblés (NODE-22) et les
 * fleurs diffusées à tout le réseau (`visibility='friends'`, NODE-136). `since`
 * permet de ne récupérer que les nouvelles (delta) ; `before` pagine en keyset
 * descendant (TÂCHE 1.2, débloque 3.6).
 */
@Injectable()
export class FeedService {
  constructor(private readonly shares: SharesService) {}

  async getFeed(
    viewerId: string,
    since?: Date,
    limit = DEFAULT_LIMIT,
    sort: FeedSort = 'date',
    before?: string,
  ): Promise<FlowerResponse[]> {
    const effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    const cursor = before ? parseCursor(before) : undefined;
    // Le tri par cœurs n'est pas temporel : un curseur (createdAt, id) n'y a
    // aucun sens. On refuse la combinaison plutôt que de renvoyer une page fausse.
    if (cursor && sort === 'likes') {
      throw new BadRequestException(
        'Le curseur `before` est réservé au tri par date (sort=date).',
      );
    }

    // Tri par date : on pousse curseur + limite jusqu'au SQL (keyset) pour ne
    // charger qu'une page par source. Tri par cœurs : il faut tout charger pour
    // classer avant de trancher (comportement historique préservé).
    const paged = sort === 'date';
    const pageCursor = paged ? cursor : undefined;
    const pageLimit = paged ? effectiveLimit : undefined;

    const [targeted, broadcast] = await Promise.all([
      this.shares.sharedWithMe(viewerId, pageCursor, pageLimit),
      this.shares.broadcastWithMe(viewerId, pageCursor, pageLimit),
    ]);

    // Une fleur peut être à la fois diffusée et partagée ciblé : on déduplique
    // par id en conservant la variante la plus permissive (GPS visible). Le
    // `shareId` (TÂCHE 3.6) ne vient que du partage ciblé : on le reporte sur la
    // variante retenue même si le GPS fait pencher pour la variante diffusée,
    // afin de garder une clé de regroupement en lot fiable.
    const byId = new Map<string, FlowerResponse>();
    for (const flower of [...targeted, ...broadcast]) {
      const previous = byId.get(flower.id);
      if (!previous) {
        byId.set(flower.id, flower);
        continue;
      }
      const kept =
        previous.latitude === null && flower.latitude !== null
          ? flower
          : previous;
      const shareId = previous.shareId ?? flower.shareId;
      const sharedAt = previous.sharedAt ?? flower.sharedAt;
      byId.set(flower.id, { ...kept, shareId, sharedAt });
    }

    // Tri (createdAt, id) DESC : l'id départage les ex æquo pour un ordre stable,
    // cohérent avec le curseur keyset (frontière de page déterministe).
    const byDate = (a: FlowerResponse, b: FlowerResponse) =>
      b.createdAt.getTime() - a.createdAt.getTime() ||
      (a.id < b.id ? 1 : a.id > b.id ? -1 : 0);
    // Tri par cœurs (NODE-139), la date (puis l'id) départageant les ex æquo.
    const byLikes = (a: FlowerResponse, b: FlowerResponse) =>
      b.likeCount - a.likeCount || byDate(a, b);
    return [...byId.values()]
      .filter((f) => !since || f.createdAt.getTime() > since.getTime())
      // Curseur keyset : garde-fou au niveau feed (les sources l'ont déjà appliqué
      // au SQL) pour une frontière de page déterministe, id compris.
      .filter((f) => !cursor || isBeforeCursor(f, cursor))
      .sort(sort === 'likes' ? byLikes : byDate)
      .slice(0, effectiveLimit);
  }
}

/** Vrai si (createdAt, id) est strictement « avant » le curseur (ordre DESC). */
function isBeforeCursor(flower: FlowerResponse, cursor: FeedCursor): boolean {
  const delta = flower.createdAt.getTime() - cursor.createdAt.getTime();
  if (delta !== 0) return delta < 0;
  return flower.id < cursor.id;
}

/**
 * Décode un curseur `before` (`<ISO8601>_<id>`) en repère (createdAt, id).
 * L'ISO8601 ne contient pas d'underscore : le premier `_` sépare la date de l'id.
 */
function parseCursor(before: string): FeedCursor {
  const idx = before.indexOf('_');
  const iso = idx > 0 ? before.slice(0, idx) : '';
  const id = idx > 0 ? before.slice(idx + 1) : '';
  const createdAt = new Date(iso);
  if (!id || Number.isNaN(createdAt.getTime())) {
    throw new BadRequestException(
      'Curseur `before` invalide (attendu `<ISO8601>_<id>`).',
    );
  }
  return { createdAt, id };
}
