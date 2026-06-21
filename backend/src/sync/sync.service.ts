import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { MoreThan, Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import {
  CreateFlowerResult,
  FlowerResponse,
  FlowersService,
} from '../flowers/flowers.service';
import { PushItemDto } from './dto/sync.dto';

export interface SyncPullResult {
  serverTime: string;
  flowers: FlowerResponse[];
  deletedIds: string[];
}

export interface SyncPushItemResult extends CreateFlowerResult {
  localId: string;
}

/**
 * Synchronisation hors-ligne (NODE-19) :
 * - pull : delta des fleurs créées/maj + ids supprimés depuis `since` ;
 * - push : envoi par lot des captures locales (création), renvoie les URLs
 *   d'upload et le mapping localId → fleur serveur.
 *
 * Stratégie de conflit : last-write-wins côté client (il applique le delta).
 */
@Injectable()
export class SyncService {
  constructor(
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly flowersService: FlowersService,
  ) {}

  async pull(ownerId: string, since?: Date): Promise<SyncPullResult> {
    const changed = await this.flowers.find({
      where: since
        ? { ownerId, updatedAt: MoreThan(since) }
        : { ownerId },
      order: { updatedAt: 'ASC' },
    });

    const deleted = since
      ? await this.flowers.find({
          where: { ownerId, deletedAt: MoreThan(since) },
          withDeleted: true,
        })
      : [];

    return {
      serverTime: new Date().toISOString(),
      flowers: await Promise.all(
        changed.map((f) => this.flowersService.toResponse(f)),
      ),
      deletedIds: deleted.map((f) => f.id),
    };
  }

  async push(
    ownerId: string,
    items: PushItemDto[],
  ): Promise<SyncPushItemResult[]> {
    const results: SyncPushItemResult[] = [];
    for (const item of items) {
      const created = await this.flowersService.create(ownerId, item);
      results.push({ localId: item.localId, ...created });
    }
    return results;
  }
}
