import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { Notification, NotificationType } from './notification.entity';

@Injectable()
export class NotificationsService {
  constructor(
    @InjectRepository(Notification)
    private readonly notifications: Repository<Notification>,
  ) {}

  create(
    userId: string,
    type: NotificationType,
    data: Record<string, unknown> = {},
  ): Promise<Notification> {
    return this.notifications.save(
      this.notifications.create({ userId, type, data, readAt: null }),
    );
  }

  list(userId: string): Promise<Notification[]> {
    return this.notifications.find({
      where: { userId },
      order: { createdAt: 'DESC' },
      take: 100,
    });
  }

  unreadCount(userId: string): Promise<number> {
    return this.notifications.count({
      where: { userId, readAt: IsNull() },
    });
  }

  async markRead(userId: string, id: string): Promise<Notification> {
    const notification = await this.notifications.findOne({
      where: { id, userId },
    });
    if (!notification) {
      throw new NotFoundException('Notification introuvable.');
    }
    notification.readAt ??= new Date();
    return this.notifications.save(notification);
  }
}
