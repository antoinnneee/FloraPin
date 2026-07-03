import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { DeviceTokensService } from '../push/device-tokens.service';
import { PushSender } from '../push/push.sender';
import { Notification, NotificationType } from './notification.entity';

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    @InjectRepository(Notification)
    private readonly notifications: Repository<Notification>,
    private readonly push: PushSender,
    private readonly devices: DeviceTokensService,
  ) {}

  async create(
    userId: string,
    type: NotificationType,
    data: Record<string, unknown> = {},
  ): Promise<Notification> {
    const saved = await this.notifications.save(
      this.notifications.create({ userId, type, data, readAt: null }),
    );
    await this.dispatchPush(userId, type, data);
    return saved;
  }

  /**
   * Variante best-effort de {@link create} : une notification est un effet de
   * bord d'une action DÉJÀ réussie et committée (partage, commentaire, amitié,
   * demande d'identification). Un échec d'insertion (destinataire supprimé
   * entre-temps, erreur transitoire) ne doit pas renvoyer un 500 alors que
   * l'action est faite — on journalise et on continue.
   */
  async createSafe(
    userId: string,
    type: NotificationType,
    data: Record<string, unknown> = {},
  ): Promise<void> {
    try {
      await this.create(userId, type, data);
    } catch (error) {
      this.logger.warn(
        `Échec de la notification « ${type} » pour ${userId} : ${String(error)}`,
      );
    }
  }

  /** Envoie un push best-effort vers les appareils de l'utilisateur. */
  private async dispatchPush(
    userId: string,
    type: NotificationType,
    data: Record<string, unknown>,
  ): Promise<void> {
    const tokens = await this.devices.tokensFor(userId);
    if (tokens.length === 0) return;
    try {
      await this.push.send(tokens, { type, data });
    } catch (error) {
      // Le push ne doit jamais faire échouer la notification in-app.
      this.logger.warn(`Échec d'envoi du push « ${type} » : ${String(error)}`);
    }
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
