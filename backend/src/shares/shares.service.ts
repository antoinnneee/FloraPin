import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { FindOptionsWhere, In, IsNull, Repository } from 'typeorm';
import { Album } from '../albums/album.entity';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse, FlowersService } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { CreateShareDto, ShareToAllFriendsDto } from './dto/share.dto';
import { Share, ShareScope } from './share.entity';

@Injectable()
export class SharesService {
  constructor(
    @InjectRepository(Share)
    private readonly shares: Repository<Share>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    @InjectRepository(Album)
    private readonly albums: Repository<Album>,
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

    const { flowerId, albumId } = await this.resolveScope(ownerId, dto);
    return this.upsertShare(
      ownerId,
      dto.friendId,
      dto.scope,
      flowerId,
      albumId,
      dto.includeGps ?? true,
    );
  }

  /**
   * Partage le périmètre avec *tout le réseau d'amis* (NODE-71) : un unique
   * partage `audience='all_friends'`, sans destinataire figé. L'audience est
   * résolue dynamiquement à chaque lecture (cf. sharedWithMe/isVisibleTo), si
   * bien qu'un ami ajouté PLUS TARD y accède automatiquement, sans re-partage.
   * Ré-partager le même périmètre met à jour le partage réseau existant.
   */
  async createForAllFriends(
    ownerId: string,
    dto: ShareToAllFriendsDto,
  ): Promise<Share> {
    const { flowerId, albumId } = await this.resolveScope(ownerId, dto);
    const includeGps = dto.includeGps ?? true;

    const existing = await this.shares.findOne({
      where: {
        ownerId,
        audience: 'all_friends',
        scope: dto.scope,
        flowerId: flowerId ?? IsNull(),
        albumId: albumId ?? IsNull(),
      },
    });
    if (existing) {
      existing.includeGps = includeGps;
      return this.shares.save(existing);
    }

    const share = await this.shares.save(
      this.shares.create({
        ownerId,
        sharedWith: null,
        audience: 'all_friends',
        scope: dto.scope,
        flowerId,
        albumId,
        includeGps,
      }),
    );

    // Notifie les amis ACTUELS (best-effort). Les futurs amis, eux, découvriront
    // le partage via leur feed/liste sans notification dédiée à ce stade.
    const friendIds = await this.friendships.acceptedFriendIds(ownerId);
    for (const friendId of friendIds) {
      await this.notifications.createSafe(friendId, 'flower_shared', {
        shareId: share.id,
        fromUserId: ownerId,
        scope: share.scope,
        flowerId: share.flowerId,
        albumId: share.albumId,
      });
    }

    return share;
  }

  /**
   * Résout et valide le périmètre d'un partage : retourne les ids concrets
   * (flowerId/albumId) après avoir vérifié que la fleur/l'album appartient bien
   * à [ownerId]. Pour le périmètre 'all', les deux sont null.
   */
  private async resolveScope(
    ownerId: string,
    dto: { scope: string; flowerId?: string; albumId?: string },
  ): Promise<{ flowerId: string | null; albumId: string | null }> {
    if (dto.scope === 'flower') {
      const flower = await this.flowers.findOne({
        where: { id: dto.flowerId, ownerId },
      });
      if (!flower) {
        throw new NotFoundException('Fleur introuvable.');
      }
      return { flowerId: flower.id, albumId: null };
    }
    if (dto.scope === 'album') {
      const album = await this.albums.findOne({
        where: { id: dto.albumId, ownerId },
      });
      if (!album) {
        throw new NotFoundException('Album introuvable.');
      }
      return { flowerId: null, albumId: album.id };
    }
    return { flowerId: null, albumId: null };
  }

  /**
   * Crée (ou met à jour) le partage d'un périmètre donné vers un ami et le
   * notifie. Ré-partager le même périmètre au même ami n'est pas une erreur : on
   * met à jour le partage existant (typiquement pour basculer le GPS) plutôt que
   * de renvoyer un 409 que l'utilisateur ne peut pas résoudre depuis l'app.
   */
  private async upsertShare(
    ownerId: string,
    friendId: string,
    scope: ShareScope,
    flowerId: string | null,
    albumId: string | null,
    includeGps: boolean,
  ): Promise<Share> {
    const existing = await this.shares.findOne({
      where: {
        ownerId,
        sharedWith: friendId,
        audience: 'friend',
        scope,
        flowerId: flowerId ?? IsNull(),
        albumId: albumId ?? IsNull(),
      },
    });
    if (existing) {
      existing.includeGps = includeGps;
      return this.shares.save(existing);
    }

    const share = await this.shares.save(
      this.shares.create({
        ownerId,
        sharedWith: friendId,
        audience: 'friend',
        scope,
        flowerId,
        albumId,
        includeGps,
      }),
    );

    // Notifie le destinataire du partage (NODE-56) — best-effort : le partage est
    // déjà créé, un échec de notification ne doit pas renvoyer un 500.
    await this.notifications.createSafe(friendId, 'flower_shared', {
      shareId: share.id,
      fromUserId: ownerId,
      scope: share.scope,
      flowerId: share.flowerId,
      albumId: share.albumId,
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
    const shares = await this.sharesVisibleTo(viewerId);

    // Résout les fleurs de chaque partage en mémorisant, par id, la variante GPS
    // la plus permissive (GPS visible s'il l'est dans au moins un partage). On
    // construit ensuite les réponses en une passe batch (toResponseMany) pour
    // éviter le N+1 photos/cœurs.
    const flowerById = new Map<string, Flower>();
    const includeGpsById = new Map<string, boolean>();
    for (const share of shares) {
      const flowers = await this.resolveFlowers(share);
      for (const flower of flowers) {
        flowerById.set(flower.id, flower);
        includeGpsById.set(
          flower.id,
          (includeGpsById.get(flower.id) ?? false) || share.includeGps,
        );
      }
    }

    const responses = await this.flowersService.toResponseMany(
      [...flowerById.values()],
      viewerId,
    );
    return responses.map((response) =>
      includeGpsById.get(response.id) ? response : stripGps(response),
    );
  }

  /**
   * Fleurs diffusées à tout le réseau d'amis (NODE-136) : celles de mes amis
   * acceptés marquées `visibility='friends'`, sans partage ciblé. Le GPS est
   * masqué quand `feedIncludeGps` est false (option héritée des partages, NODE-22).
   */
  async broadcastWithMe(viewerId: string): Promise<FlowerResponse[]> {
    const friendIds = await this.friendships.acceptedFriendIds(viewerId);
    if (friendIds.length === 0) return [];

    const flowers = await this.flowers.find({
      where: { ownerId: In(friendIds), visibility: 'friends' },
    });
    return this.presentFeed(flowers, viewerId);
  }

  /**
   * Vérifie que [viewerId] voit [flower] : propriétaire, partage ciblé reçu,
   * ou fleur diffusée à son réseau (`visibility='friends'`). Même périmètre
   * que le feed (cf. FeedService.getFeed). Utilisé par les commentaires et les
   * cœurs pour contrôler l'accès à une fleur donnée.
   *
   * Test booléen léger : ne construit AUCUNE réponse API (pas d'URL présignée ni
   * de comptage de cœurs) — on résout uniquement les identifiants de fleurs.
   */
  async isVisibleTo(viewerId: string, flower: Flower): Promise<boolean> {
    if (flower.ownerId === viewerId) {
      return true;
    }
    // Diffusion au réseau : fleur 'friends' d'un ami accepté.
    if (flower.visibility === 'friends') {
      const friendIds = await this.friendships.acceptedFriendIds(viewerId);
      if (friendIds.includes(flower.ownerId)) {
        return true;
      }
    }
    // Partages reçus (ciblés + réseau 'all_friends' des amis) : la fleur y figure-t-elle ?
    const shares = await this.sharesVisibleTo(viewerId);
    for (const share of shares) {
      const flowers = await this.resolveFlowers(share);
      if (flowers.some((f) => f.id === flower.id)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Partages dont [viewerId] est destinataire : les partages ciblés reçus
   * (`sharedWith = viewerId`) ET les partages réseau (`audience='all_friends'`)
   * des amis acceptés du viewer. Ces derniers sont résolus dynamiquement, si
   * bien qu'un viewer devenu ami APRÈS la création du partage y accède aussitôt.
   */
  private async sharesVisibleTo(viewerId: string): Promise<Share[]> {
    const friendIds = await this.friendships.acceptedFriendIds(viewerId);
    const where: FindOptionsWhere<Share>[] = [{ sharedWith: viewerId }];
    if (friendIds.length > 0) {
      where.push({ ownerId: In(friendIds), audience: 'all_friends' });
    }
    return this.shares.find({ where });
  }

  /**
   * Fleurs « à identifier » d'amis visibles par [viewerId] (NODE-133/134).
   *
   * La demande d'identification notifie *tous* les amis acceptés du propriétaire
   * (cf. IdentificationRequestsService.request) : l'audience est donc le réseau
   * d'amis, indépendamment d'un partage ciblé ou de la publication au flux. On
   * retourne toutes les fleurs `needsIdentification` des amis acceptés — le clic
   * « Demander une identification » vaut consentement explicite à les montrer.
   * GPS masqué sauf si `feedIncludeGps` (cohérent avec broadcastWithMe).
   */
  async needsIdentificationFromFriends(
    viewerId: string,
  ): Promise<FlowerResponse[]> {
    const friendIds = await this.friendships.acceptedFriendIds(viewerId);
    if (friendIds.length === 0) return [];

    const flowers = await this.flowers.find({
      where: { ownerId: In(friendIds), needsIdentification: true },
    });
    return this.presentFeed(flowers, viewerId);
  }

  /**
   * Contrôle léger « [viewerId] peut-il proposer une espèce pour [flower] ? » :
   * la fleur est ouverte à l'identification et appartient à un ami accepté (même
   * périmètre que `needsIdentificationFromFriends`). Renvoie un booléen SANS
   * construire de réponse API (pas d'URL présignée) — cf. ProposalsService.
   */
  async needsIdentificationVisibleTo(
    viewerId: string,
    flower: Flower,
  ): Promise<boolean> {
    if (!flower.needsIdentification) {
      return false;
    }
    const friendIds = await this.friendships.acceptedFriendIds(viewerId);
    return friendIds.includes(flower.ownerId);
  }

  /**
   * Construit les réponses d'une liste de fleurs « diffusées » (batch, sans
   * N+1) en masquant le GPS de celles dont `feedIncludeGps` est false (NODE-22).
   */
  private async presentFeed(
    flowers: Flower[],
    viewerId: string,
  ): Promise<FlowerResponse[]> {
    const responses = await this.flowersService.toResponseMany(
      flowers,
      viewerId,
    );
    const gpsByFlower = new Map(flowers.map((f) => [f.id, f.feedIncludeGps]));
    return responses.map((response) =>
      gpsByFlower.get(response.id) ? response : stripGps(response),
    );
  }

  /** Résout les fleurs concrètes couvertes par un partage selon son périmètre. */
  private async resolveFlowers(share: Share): Promise<Flower[]> {
    switch (share.scope) {
      case 'all':
        return this.flowers.find({ where: { ownerId: share.ownerId } });
      case 'album':
        return this.resolveAlbum(share.albumId);
      default:
        return this.resolveSingle(share.flowerId);
    }
  }

  private async resolveSingle(flowerId: string | null): Promise<Flower[]> {
    if (!flowerId) return [];
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    return flower ? [flower] : [];
  }

  private async resolveAlbum(albumId: string | null): Promise<Flower[]> {
    if (!albumId) return [];
    const album = await this.albums.findOne({
      where: { id: albumId },
      relations: { flowers: true },
    });
    return album?.flowers ?? [];
  }
}

function stripGps(flower: FlowerResponse): FlowerResponse {
  return { ...flower, latitude: null, longitude: null, accuracyM: null };
}
