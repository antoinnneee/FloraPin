import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { FlowerLike } from './flower-like.entity';

/**
 * Cœurs sur les fleurs (NODE-139) : pose/retrait idempotents et notification
 * best-effort au propriétaire.
 */
@Injectable()
export class LikesService {
  constructor(
    @InjectRepository(FlowerLike)
    private readonly likes: Repository<FlowerLike>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly notifications: NotificationsService,
  ) {}

  /** Pose un cœur (idempotent). Notifie le propriétaire (sauf auto-cœur). */
  async like(userId: string, flowerId: string): Promise<void> {
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }

    const existing = await this.likes.findOne({ where: { flowerId, userId } });
    if (existing) return;

    await this.likes.save(this.likes.create({ flowerId, userId }));

    if (flower.ownerId !== userId) {
      await this.notifications.create(flower.ownerId, 'flower_liked', {
        flowerId,
        byUserId: userId,
      });
    }
  }

  /** Retire le cœur (idempotent : sans effet si absent). */
  async unlike(userId: string, flowerId: string): Promise<void> {
    await this.likes.delete({ flowerId, userId });
  }
}
