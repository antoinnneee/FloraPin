import { Injectable } from '@nestjs/common';
import { FlowerResponse } from '../flowers/flowers.service';
import { SharesService } from '../shares/shares.service';
import { FeedSort } from './dto/feed.dto';

const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 200;

/**
 * Feed des amis (NODE-23/136) : les fleurs visibles par l'utilisateur, des plus
 * récentes aux plus anciennes. Réunit les partages ciblés (NODE-22) et les
 * fleurs diffusées à tout le réseau (`visibility='friends'`, NODE-136). `since`
 * permet de ne récupérer que les nouvelles.
 */
@Injectable()
export class FeedService {
  constructor(private readonly shares: SharesService) {}

  async getFeed(
    viewerId: string,
    since?: Date,
    limit = DEFAULT_LIMIT,
    sort: FeedSort = 'date',
  ): Promise<FlowerResponse[]> {
    const [targeted, broadcast] = await Promise.all([
      this.shares.sharedWithMe(viewerId),
      this.shares.broadcastWithMe(viewerId),
    ]);

    // Une fleur peut être à la fois diffusée et partagée ciblé : on déduplique
    // par id en conservant la variante la plus permissive (GPS visible).
    const byId = new Map<string, FlowerResponse>();
    for (const flower of [...targeted, ...broadcast]) {
      const previous = byId.get(flower.id);
      if (
        !previous ||
        (previous.latitude === null && flower.latitude !== null)
      ) {
        byId.set(flower.id, flower);
      }
    }

    const effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
    const byDate = (a: FlowerResponse, b: FlowerResponse) =>
      b.createdAt.getTime() - a.createdAt.getTime();
    // Tri par cœurs (NODE-139), la date départageant les ex æquo.
    const byLikes = (a: FlowerResponse, b: FlowerResponse) =>
      b.likeCount - a.likeCount || byDate(a, b);
    return [...byId.values()]
      .filter((f) => !since || f.createdAt.getTime() > since.getTime())
      .sort(sort === 'likes' ? byLikes : byDate)
      .slice(0, effectiveLimit);
  }
}
