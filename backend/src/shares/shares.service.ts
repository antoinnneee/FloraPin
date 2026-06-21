import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse, FlowersService } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { CreateShareDto } from './dto/share.dto';
import { Share } from './share.entity';

@Injectable()
export class SharesService {
  constructor(
    @InjectRepository(Share)
    private readonly shares: Repository<Share>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly friendships: FriendshipsService,
    private readonly flowersService: FlowersService,
    private readonly notifications: NotificationsService,
  ) {}

  /** Crée un partage configurable vers un ami accepté. */
  async create(ownerId: string, dto: CreateShareDto): Promise<Share> {
    const friends = await this.friendships.acceptedFriendIds(ownerId);
    if (!friends.includes(dto.friendId)) {
      throw new ForbiddenException(
        'Le partage est réservé aux amis acceptés.',
      );
    }

    let flowerId: string | null = null;
    if (dto.scope === 'flower') {
      const flower = await this.flowers.findOne({
        where: { id: dto.flowerId, ownerId },
      });
      if (!flower) {
        throw new NotFoundException('Fleur introuvable.');
      }
      flowerId = flower.id;
    }

    const existing = await this.shares.findOne({
      where: {
        ownerId,
        sharedWith: dto.friendId,
        scope: dto.scope,
        flowerId: flowerId ?? IsNull(),
      },
    });
    if (existing) {
      throw new ConflictException('Ce partage existe déjà.');
    }

    const share = await this.shares.save(
      this.shares.create({
        ownerId,
        sharedWith: dto.friendId,
        scope: dto.scope,
        flowerId,
        includeGps: dto.includeGps ?? true,
      }),
    );

    // Notifie le destinataire du partage (NODE-56).
    await this.notifications.create(dto.friendId, 'flower_shared', {
      shareId: share.id,
      fromUserId: ownerId,
      scope: share.scope,
      flowerId: share.flowerId,
    });

    return share;
  }

  listMine(ownerId: string): Promise<Share[]> {
    return this.shares.find({
      where: { ownerId },
      order: { createdAt: 'DESC' },
    });
  }

  async revoke(ownerId: string, id: string): Promise<void> {
    const share = await this.shares.findOne({ where: { id, ownerId } });
    if (!share) {
      throw new NotFoundException('Partage introuvable.');
    }
    await this.shares.remove(share);
  }

  /**
   * Fleurs partagées avec [viewerId], coordonnées masquées si le partage
   * correspondant désactive le GPS. Dédupliquées par id (on conserve la version
   * la plus permissive : GPS visible s'il l'est dans au moins un partage).
   */
  async sharedWithMe(viewerId: string): Promise<FlowerResponse[]> {
    const shares = await this.shares.find({
      where: { sharedWith: viewerId },
    });

    const byId = new Map<string, FlowerResponse>();
    for (const share of shares) {
      const flowers =
        share.scope === 'all'
          ? await this.flowers.find({ where: { ownerId: share.ownerId } })
          : await this.resolveSingle(share.flowerId);

      for (const flower of flowers) {
        const response = await this.flowersService.toResponse(flower);
        const presented = share.includeGps ? response : stripGps(response);
        const previous = byId.get(presented.id);
        // Conserve la variante avec GPS si elle existe.
        if (!previous || (previous.latitude === null && share.includeGps)) {
          byId.set(presented.id, presented);
        }
      }
    }
    return [...byId.values()];
  }

  private async resolveSingle(flowerId: string | null): Promise<Flower[]> {
    if (!flowerId) return [];
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    return flower ? [flower] : [];
  }
}

function stripGps(flower: FlowerResponse): FlowerResponse {
  return { ...flower, latitude: null, longitude: null, accuracyM: null };
}
