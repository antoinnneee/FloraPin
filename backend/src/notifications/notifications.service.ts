import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { DeviceTokensService } from '../push/device-tokens.service';
import { PushSender } from '../push/push.sender';
import { StorageService } from '../storage/storage.service';
import { UsersService } from '../users/users.service';
import { Notification, NotificationType } from './notification.entity';

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  constructor(
    @InjectRepository(Notification)
    private readonly notifications: Repository<Notification>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly push: PushSender,
    private readonly devices: DeviceTokensService,
    private readonly users: UsersService,
    private readonly storage: StorageService,
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
      const enriched = await this.enrichForPush(data);
      await this.push.send(tokens, { type, data: enriched });
    } catch (error) {
      // Le push ne doit jamais faire échouer la notification in-app.
      this.logger.warn(`Échec d'envoi du push « ${type} » : ${String(error)}`);
    }
  }

  /**
   * Enrichit le `data` du PUSH avec des libellés « incarnés » (TÂCHE 2.1) —
   * « Marie a partagé Coquelicot » plutôt qu'un texte générique. Résolution
   * FAITE À L'ENVOI (jamais figée) : le nom d'affichage de l'émetteur suit ses
   * mises à jour ultérieures (cohérent avec 1.7).
   *
   * N'enrichit QUE la copie destinée au push : la notification in-app persistée
   * conserve ses identifiants bruts. Reste data-only et léger (payload ≤ 4 Ko) :
   * un nom, une espèce et une URL de miniature — dont l'URL présignée de lecture
   * est à longue durée (7 jours par défaut), pas à courte durée de vie.
   *
   * Best-effort : une résolution partielle (émetteur/fleur supprimé entre-temps)
   * n'empêche pas l'envoi — on renvoie ce qui a pu être résolu.
   */
  private async enrichForPush(
    data: Record<string, unknown>,
  ): Promise<Record<string, unknown>> {
    const enriched: Record<string, unknown> = { ...data };

    // Nom de l'émetteur : selon le type, l'identifiant est `byUserId`
    // (cœur, commentaire, proposition…) ou `fromUserId` (partage, demande d'ami).
    const actorId = data.byUserId ?? data.fromUserId;
    if (typeof actorId === 'string' && actorId) {
      const actor = await this.users.findById(actorId);
      if (actor?.displayName) {
        enriched.byUserName = actor.displayName;
      }
    }

    // Espèce + miniature de la fleur concernée, quand une fleur est référencée
    // (partage ciblé, cœur, commentaire, identification…). Le partage 'all' /
    // 'album' n'a pas de `flowerId` : on n'ajoute alors rien.
    const flowerId = data.flowerId;
    if (typeof flowerId === 'string' && flowerId) {
      const flower = await this.flowers.findOne({ where: { id: flowerId } });
      if (flower) {
        // N'écrase pas une espèce déjà transmise (ex. proposition : le texte
        // proposé prime sur l'espèce courante de la fleur).
        if (enriched.species == null && flower.species) {
          enriched.species = flower.species;
        }
        if (flower.thumbnailKey) {
          enriched.thumbnailUrl = await this.storage.presignDownload(
            flower.thumbnailKey,
          );
        }
      }
    }

    return enriched;
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

  /** Marque en une seule requête toutes les notifications de l'utilisateur. */
  async markAllRead(userId: string): Promise<number> {
    const result = await this.notifications.update(
      { userId, readAt: IsNull() },
      { readAt: new Date() },
    );
    return result.affected ?? 0;
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
