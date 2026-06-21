import { Injectable } from '@nestjs/common';
import { FlowerResponse } from '../flowers/flowers.service';
import { SharesService } from '../shares/shares.service';

const DEFAULT_LIMIT = 50;
const MAX_LIMIT = 200;

/**
 * Feed des amis (NODE-23) : les fleurs partagées avec l'utilisateur, des plus
 * récentes aux plus anciennes. `since` permet de ne récupérer que les nouvelles.
 */
@Injectable()
export class FeedService {
  constructor(private readonly shares: SharesService) {}

  async getFeed(
    viewerId: string,
    since?: Date,
    limit = DEFAULT_LIMIT,
  ): Promise<FlowerResponse[]> {
    const flowers = await this.shares.sharedWithMe(viewerId);
    const effectiveLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);

    return flowers
      .filter((f) => !since || f.createdAt.getTime() > since.getTime())
      .sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime())
      .slice(0, effectiveLimit);
  }
}
