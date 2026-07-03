import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';

/**
 * Demandes d'identification collaborative (NODE-133).
 *
 * Le propriétaire sollicite ses amis sur une fleur non identifiée ; ceux-ci
 * répondent via les propositions d'espèce existantes (NODE-31).
 */
@Injectable()
export class IdentificationRequestsService {
  constructor(
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly friendships: FriendshipsService,
    private readonly notifications: NotificationsService,
    private readonly shares: SharesService,
  ) {}

  /**
   * Marque la fleur « à identifier » et notifie les amis acceptés du
   * propriétaire (notification in-app + push best-effort).
   *
   * Les amis ne sont notifiés QUE lors de la transition « pas encore ouverte » →
   * « ouverte » : un re-POST sur une fleur déjà à identifier est idempotent et ne
   * re-spamme pas le réseau (la demande reste visible côté ami tant que le flag
   * est posé).
   */
  async request(ownerId: string, flowerId: string): Promise<void> {
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }

    if (flower.needsIdentification) {
      return; // déjà ouverte : rien à faire, pas de nouvelle notification.
    }

    flower.needsIdentification = true;
    await this.flowers.save(flower);

    const friendIds = await this.friendships.acceptedFriendIds(ownerId);
    await Promise.all(
      friendIds.map((friendId) =>
        this.notifications.createSafe(friendId, 'identification_requested', {
          flowerId,
          byUserId: ownerId,
        }),
      ),
    );
  }

  /**
   * Fleurs « à identifier » d'amis (vue côté ami) : je peux y répondre par une
   * proposition d'espèce. La demande sollicite *tous* les amis acceptés, donc on
   * liste les fleurs `needsIdentification` de mes amis, sans exiger un partage
   * ciblé ni une publication au flux (cf. needsIdentificationFromFriends).
   */
  async listForViewer(viewerId: string): Promise<FlowerResponse[]> {
    return this.shares.needsIdentificationFromFriends(viewerId);
  }

  /** Lève la demande d'identification (propriétaire). */
  async cancel(ownerId: string, flowerId: string): Promise<void> {
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (flower.needsIdentification) {
      flower.needsIdentification = false;
      await this.flowers.save(flower);
    }
  }
}
