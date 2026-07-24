import {
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { FlowersService } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { ProposalResponse, ProposalsService } from '../proposals/proposals.service';
import {
  IdentificationFlowerResponse,
  SharesService,
} from '../shares/shares.service';
import { User } from '../users/user.entity';

/** Demande visible par un ami, enrichie avec le nom de son auteur. */
export interface IdentificationRequestForViewer
  extends IdentificationFlowerResponse {
  ownerName: string;
}

/**
 * Une de mes demandes d'identification (TÂCHE 4.1) : ma fleur en attente et les
 * propositions d'espèce reçues de mes amis (« qui a proposé quoi »).
 */
export interface MyIdentificationRequest {
  flower: IdentificationFlowerResponse;
  proposals: ProposalResponse[];
}

/**
 * Demandes d'identification collaborative (NODE-133).
 *
 * Le propriétaire sollicite ses amis sur une fleur non identifiée ; ceux-ci
 * répondent via les propositions d'espèce existantes (NODE-31).
 */
@Injectable()
export class IdentificationRequestsService {
  /**
   * Délai minimal entre deux sollicitations du réseau pour une même fleur
   * (TÂCHE 4.4) : anti-spam serveur. Une relance manuelle avant ce délai est
   * refusée (409), indépendamment de tout garde-fou UI.
   */
  static readonly REMIND_COOLDOWN_MS = 24 * 60 * 60 * 1000; // 1 jour

  constructor(
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    @InjectRepository(User)
    private readonly users: Repository<User>,
    private readonly friendships: FriendshipsService,
    private readonly notifications: NotificationsService,
    private readonly shares: SharesService,
    private readonly flowersService: FlowersService,
    private readonly proposals: ProposalsService,
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
    // Ancre l'anti-spam des relances (TÂCHE 4.4) sur cette première sollicitation.
    flower.lastRemindedAt = new Date();
    await this.flowers.save(flower);

    await this.notifyFriends(ownerId, flowerId);
  }

  /**
   * Relance manuelle (TÂCHE 4.4) : re-notifie les amis d'une fleur toujours « à
   * identifier ». Anti-spam côté serveur (pas seulement UI) : refuse tant que
   * {@link REMIND_COOLDOWN_MS} n'est pas écoulé depuis la dernière sollicitation
   * (ouverture ou relance), en s'appuyant sur `lastRemindedAt`.
   */
  async remind(ownerId: string, flowerId: string): Promise<void> {
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (!flower.needsIdentification) {
      throw new ConflictException('Cette fleur n’attend pas d’identification.');
    }

    const now = Date.now();
    if (flower.lastRemindedAt) {
      const elapsed = now - flower.lastRemindedAt.getTime();
      if (elapsed < IdentificationRequestsService.REMIND_COOLDOWN_MS) {
        throw new ConflictException(
          'Vous avez déjà relancé vos amis récemment. Réessayez plus tard.',
        );
      }
    }

    flower.lastRemindedAt = new Date(now);
    await this.flowers.save(flower);

    await this.notifyFriends(ownerId, flowerId);
  }

  /**
   * Notifie tous les amis acceptés du propriétaire qu'une identification est
   * demandée (in-app + push best-effort). Un échec unitaire ne casse pas l'action
   * déjà committée (createSafe).
   */
  private async notifyFriends(ownerId: string, flowerId: string): Promise<void> {
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
  async listForViewer(
    viewerId: string,
  ): Promise<IdentificationRequestForViewer[]> {
    const flowers =
      await this.shares.needsIdentificationFromFriends(viewerId);
    if (flowers.length === 0) return [];

    const owners = await this.users.find({
      where: { id: In([...new Set(flowers.map((flower) => flower.ownerId))]) },
    });
    const ownerNames = new Map(
      owners.map((owner) => [owner.id, owner.displayName]),
    );
    return flowers.map((flower) => ({
      ...flower,
      ownerName: ownerNames.get(flower.ownerId) ?? '',
    }));
  }

  /**
   * L'état de MES demandes (TÂCHE 4.1) : mes fleurs `needsIdentification` avec,
   * pour chacune, les propositions d'espèce reçues (« qui a proposé quoi »).
   *
   * Composé côté serveur en une requête : un chargement des fleurs, un batch des
   * réponses (photos/cœurs via toResponseMany) et un batch des propositions +
   * auteurs (listForFlowerIds) — pas de composition N+1 côté client.
   */
  async listMine(ownerId: string): Promise<MyIdentificationRequest[]> {
    const flowers = (
      await this.flowers.find({
        where: { ownerId, needsIdentification: true },
        order: { lastRemindedAt: 'DESC', id: 'DESC' },
      })
    ).sort(compareIdentificationRequestsNewestFirst);
    if (flowers.length === 0) {
      return [];
    }
    const responses = await this.flowersService.toResponseMany(flowers, ownerId);
    const proposalsByFlower = await this.proposals.listForFlowerIds(
      flowers.map((f) => f.id),
    );
    const requestedAtByFlower = new Map(
      flowers.map((flower) => [
        flower.id,
        flower.lastRemindedAt ?? flower.updatedAt,
      ]),
    );
    return responses.map((flower) => ({
      flower: {
        ...flower,
        identificationRequestedAt:
          requestedAtByFlower.get(flower.id) ?? flower.updatedAt,
      },
      proposals: proposalsByFlower.get(flower.id) ?? [],
    }));
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

/** Ordre stable des sollicitations, avec repli pour les demandes historiques. */
function compareIdentificationRequestsNewestFirst(a: Flower, b: Flower): number {
  const aTime = (a.lastRemindedAt ?? a.updatedAt).getTime();
  const bTime = (b.lastRemindedAt ?? b.updatedAt).getTime();
  return bTime - aTime || b.id.localeCompare(a.id);
}
