import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import {
  FindOptionsWhere,
  In,
  IsNull,
  LessThan,
  Repository,
} from 'typeorm';
import { Album } from '../albums/album.entity';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse, FlowersService } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { CreateShareDto, ShareToAllFriendsDto } from './dto/share.dto';
import { Share, ShareScope } from './share.entity';

/**
 * Curseur de pagination keyset du feed (TÂCHE 1.2) : repère stable (createdAt,
 * id) appliqué en tri descendant. Une fleur est « avant » le curseur si elle est
 * plus ancienne, ou de même date mais d'id inférieur (départage déterministe).
 */
export interface FeedCursor {
  createdAt: Date;
  id: string;
}

/** Fleur sollicitant une identification, accompagnée de la date de sollicitation. */
export interface IdentificationFlowerResponse extends FlowerResponse {
  identificationRequestedAt: Date;
}

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
   *
   * Pagination keyset (TÂCHE 1.2) : `cursor`/`limit` bornent la page (fleurs
   * strictement plus anciennes que le curseur, au plus `limit`). Le périmètre
   * 'all' (potentiellement large) est borné dès le SQL ; l'ensemble fusionné est
   * ensuite ré-ordonné et tranché à `limit` AVANT de construire les réponses,
   * pour ne présigner/compter les cœurs que sur la page effective.
   */
  async sharedWithMe(
    viewerId: string,
    cursor?: FeedCursor,
    limit?: number,
  ): Promise<FlowerResponse[]> {
    const shares = await this.sharesVisibleTo(viewerId);

    // Résout les fleurs de chaque partage en mémorisant, par id, la variante GPS
    // la plus permissive (GPS visible s'il l'est dans au moins un partage) et le
    // partage de rattachement (le PLUS RÉCENT quand la fleur figure dans
    // plusieurs partages) pour un regroupement en lot fiable côté client (3.6).
    const flowerById = new Map<string, Flower>();
    const includeGpsById = new Map<string, boolean>();
    const shareInfoById = new Map<string, { shareId: string; sharedAt: Date }>();
    for (const share of shares) {
      const flowers = await this.resolveFlowers(share, cursor, limit);
      for (const flower of flowers) {
        flowerById.set(flower.id, flower);
        includeGpsById.set(
          flower.id,
          (includeGpsById.get(flower.id) ?? false) || share.includeGps,
        );
        const prev = shareInfoById.get(flower.id);
        if (!prev || share.createdAt.getTime() > prev.sharedAt.getTime()) {
          shareInfoById.set(flower.id, {
            shareId: share.id,
            sharedAt: share.createdAt,
          });
        }
      }
    }

    // Applique le curseur keyset (défense en profondeur pour les scopes non
    // bornés au SQL : album/single) puis tranche à `limit` avant le batch.
    const page = orderAndSlice([...flowerById.values()], cursor, limit);
    const responses = await this.flowersService.toResponseMany(page, viewerId);
    return responses.map((response) => {
      const info = shareInfoById.get(response.id);
      const tagged: FlowerResponse = info
        ? { ...response, shareId: info.shareId, sharedAt: info.sharedAt }
        : response;
      return includeGpsById.get(response.id) ? tagged : stripGps(tagged);
    });
  }

  /**
   * Fleurs diffusées à tout le réseau d'amis (NODE-136) : celles de mes amis
   * acceptés marquées `visibility='friends'`, sans partage ciblé. Le GPS est
   * masqué quand `feedIncludeGps` est false (option héritée des partages, NODE-22).
   */
  async broadcastWithMe(
    viewerId: string,
    cursor?: FeedCursor,
    limit?: number,
  ): Promise<FlowerResponse[]> {
    const friendIds = await this.friendships.acceptedFriendIds(viewerId);
    if (friendIds.length === 0) return [];

    // Keyset SQL (TÂCHE 1.2) : filtre + tri (createdAt, id) DESC + limite côté
    // base, plutôt qu'un chargement complet suivi d'un slice mémoire.
    const flowers = await this.flowers.find({
      where: keysetWhere(
        { ownerId: In(friendIds), visibility: 'friends' },
        cursor,
      ),
      order: { createdAt: 'DESC', id: 'DESC' },
      ...(limit ? { take: limit } : {}),
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
  ): Promise<IdentificationFlowerResponse[]> {
    const friendIds = await this.friendships.acceptedFriendIds(viewerId);
    if (friendIds.length === 0) return [];

    const flowers = (
      await this.flowers.find({
        where: { ownerId: In(friendIds), needsIdentification: true },
        order: { lastRemindedAt: 'DESC', id: 'DESC' },
      })
    ).sort(compareIdentificationRequestsNewestFirst);
    const responses = await this.presentFeed(flowers, viewerId);
    const requestedAtByFlower = new Map(
      flowers.map((flower) => [
        flower.id,
        flower.lastRemindedAt ?? flower.updatedAt,
      ]),
    );
    return responses.map((flower) => ({
      ...flower,
      identificationRequestedAt:
        requestedAtByFlower.get(flower.id) ?? flower.updatedAt,
    }));
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

  /**
   * Résout les fleurs concrètes couvertes par un partage selon son périmètre.
   * Pour le périmètre 'all' (non borné), le curseur/limite du feed sont poussés
   * au SQL (keyset). Album/single sont naturellement bornés : ils sont filtrés
   * par le curseur en aval (orderAndSlice). Sans curseur/limite (contrôle
   * d'accès `isVisibleTo`), on résout l'intégralité du périmètre.
   */
  private async resolveFlowers(
    share: Share,
    cursor?: FeedCursor,
    limit?: number,
  ): Promise<Flower[]> {
    switch (share.scope) {
      case 'all':
        return this.resolveAllOwnerFlowers(share.ownerId, cursor, limit);
      case 'album':
        return this.resolveAlbum(share.albumId);
      default:
        return this.resolveSingle(share.flowerId);
    }
  }

  /** Fleurs d'un propriétaire, keyset (createdAt, id) DESC borné à `limit`. */
  private resolveAllOwnerFlowers(
    ownerId: string,
    cursor?: FeedCursor,
    limit?: number,
  ): Promise<Flower[]> {
    return this.flowers.find({
      where: keysetWhere({ ownerId }, cursor),
      order: { createdAt: 'DESC', id: 'DESC' },
      ...(limit ? { take: limit } : {}),
    });
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

/** Ordre stable des sollicitations, y compris pour les anciennes lignes sans date. */
function compareIdentificationRequestsNewestFirst(a: Flower, b: Flower): number {
  const aTime = (a.lastRemindedAt ?? a.updatedAt).getTime();
  const bTime = (b.lastRemindedAt ?? b.updatedAt).getTime();
  return bTime - aTime || b.id.localeCompare(a.id);
}

function stripGps(flower: FlowerResponse): FlowerResponse {
  return { ...flower, latitude: null, longitude: null, accuracyM: null };
}

/**
 * Traduit le curseur keyset (createdAt, id) descendant en clause `where` TypeORM.
 * Sans curseur : la clause de base (première page / delta). Avec curseur : deux
 * branches OR — plus ancien, OU même date mais id inférieur — chacune conservant
 * les filtres de base (owner/visibilité).
 */
function keysetWhere(
  base: FindOptionsWhere<Flower>,
  cursor?: FeedCursor,
): FindOptionsWhere<Flower> | FindOptionsWhere<Flower>[] {
  if (!cursor) return base;
  return [
    { ...base, createdAt: LessThan(cursor.createdAt) },
    { ...base, createdAt: cursor.createdAt, id: LessThan(cursor.id) },
  ];
}

/** Vrai si (createdAt, id) est strictement « avant » le curseur (ordre DESC). */
function isBeforeCursor(flower: Flower, cursor: FeedCursor): boolean {
  const delta = flower.createdAt.getTime() - cursor.createdAt.getTime();
  if (delta !== 0) return delta < 0;
  return flower.id < cursor.id;
}

/** Compare deux fleurs pour un tri (createdAt, id) descendant. */
function byCreatedThenId(a: Flower, b: Flower): number {
  const delta = b.createdAt.getTime() - a.createdAt.getTime();
  if (delta !== 0) return delta;
  return a.id < b.id ? 1 : a.id > b.id ? -1 : 0;
}

/**
 * Ordonne des fleurs en (createdAt, id) DESC, écarte celles hors curseur, puis
 * tranche à `limit`. Utilisé pour fusionner des périmètres bornés au SQL (scope
 * 'all') avec ceux filtrés en mémoire (album/single).
 */
function orderAndSlice(
  flowers: Flower[],
  cursor?: FeedCursor,
  limit?: number,
): Flower[] {
  const filtered = cursor
    ? flowers.filter((f) => isBeforeCursor(f, cursor))
    : flowers;
  filtered.sort(byCreatedThenId);
  return limit ? filtered.slice(0, limit) : filtered;
}
