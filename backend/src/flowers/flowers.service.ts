import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import {
  DEFAULT_REACTION,
  FlowerLike,
  Reaction,
} from '../likes/flower-like.entity';
import { FlowerComment } from '../comments/flower-comment.entity';
import { StorageService, PresignedUpload } from '../storage/storage.service';
import { encodeWebp } from '../storage/image-processing';
import { CreateFlowerDto, UpdateFlowerDto } from './dto/flower.dto';
import { Flower } from './flower.entity';
import { FlowerPhoto } from './flower-photo.entity';

/** Photo d'une fleur, telle qu'exposée par l'API (URL présignée). */
export interface PhotoResponse {
  id: string;
  url: string;
  /** URL présignée de la miniature WebP, ou null si non encore réencodée. */
  thumbnailUrl: string | null;
  position: number;
  isCover: boolean;
}

export interface FlowerResponse {
  id: string;
  ownerId: string;
  imageUrl: string;
  /** URL présignée de la miniature de couverture (preview), ou null. */
  thumbnailUrl: string | null;
  latitude: number | null;
  longitude: number | null;
  accuracyM: number | null;
  takenAt: Date;
  notes: string;
  visibility: string;
  /** Diffusion du GPS au feed des amis quand visibility='friends' (NODE-136). */
  feedIncludeGps: boolean;
  /** Demande d'identification collaborative en cours (NODE-133). */
  needsIdentification: boolean;
  species: string | null;
  /** FK vers le référentiel d'espèces (NODE-124), null si non rapprochée. */
  speciesId: string | null;
  /** Espèce résolue depuis le référentiel (NODE-125), null si non rapprochée. */
  speciesRef: { id: string; scientificName: string; commonName: string } | null;
  tags: string[];
  photos: PhotoResponse[];
  /** Nombre total de réactions reçues, tous types confondus (NODE-139). */
  likeCount: number;
  /** Le spectateur courant a-t-il réagi à cette fleur (NODE-139). */
  likedByMe: boolean;
  /** Décompte par type de réaction, types absents omis (TÂCHE 3.5). */
  reactionCounts: Partial<Record<Reaction, number>>;
  /** Réaction posée par le spectateur courant, ou null (TÂCHE 3.5). */
  myReaction: Reaction | null;
  /** Nombre de commentaires reçus (TÂCHE 3.3). */
  commentCount: number;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateFlowerResult {
  flower: FlowerResponse;
  upload: PresignedUpload;
}

@Injectable()
export class FlowersService {
  constructor(
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    @InjectRepository(FlowerPhoto)
    private readonly photos: Repository<FlowerPhoto>,
    @InjectRepository(FlowerLike)
    private readonly likes: Repository<FlowerLike>,
    @InjectRepository(FlowerComment)
    private readonly comments: Repository<FlowerComment>,
    private readonly storage: StorageService,
  ) {}

  /**
   * Crée la fleur (métadonnées + GPS) et renvoie une URL présignée pour
   * téléverser le binaire image directement sur le stockage objet.
   *
   * [clientId] : identifiant local stable fourni par le client (sync push,
   * NODE-19). Rend la création idempotente sur le modèle des albums : un re-push
   * (réponse perdue / retry) retombe sur la fleur existante au lieu d'en créer
   * un doublon. Renvoie alors une nouvelle URL d'upload pointant sur son objet.
   */
  async create(
    ownerId: string,
    dto: CreateFlowerDto,
    clientId?: string,
  ): Promise<CreateFlowerResult> {
    const hasLat = dto.latitude !== undefined;
    const hasLng = dto.longitude !== undefined;
    if (hasLat !== hasLng) {
      throw new BadRequestException(
        'latitude et longitude doivent être fournies ensemble.',
      );
    }

    if (clientId) {
      const existing = await this.flowers.findOne({
        where: { ownerId, clientId },
        relations: { speciesRef: true },
      });
      if (existing) {
        const upload = await this.storage.presignUpload(existing.imageKey);
        return { flower: await this.toResponse(existing, ownerId), upload };
      }
    }

    const imageKey = this.storage.buildKey(ownerId, 'jpg');
    const entity = this.flowers.create({
      ownerId,
      clientId: clientId ?? null,
      imageKey,
      location:
        hasLat && hasLng
          ? { type: 'Point', coordinates: [dto.longitude!, dto.latitude!] }
          : null,
      accuracyM: dto.accuracyM ?? null,
      takenAt: new Date(dto.takenAt),
      notes: dto.notes ?? '',
      visibility: dto.visibility ?? 'private',
      feedIncludeGps: dto.feedIncludeGps ?? true,
      species: dto.species ?? null,
      tags: dto.tags ?? [],
    });

    const saved = await this.flowers.save(entity);
    // Photo de couverture initiale (NODE-104) : même clé que l'image principale.
    await this.photos.save(
      this.photos.create({
        flowerId: saved.id,
        imageKey,
        position: 0,
        isCover: true,
      }),
    );
    const upload = await this.storage.presignUpload(imageKey);
    return { flower: await this.toResponse(saved, ownerId), upload };
  }

  async getById(ownerId: string, id: string): Promise<FlowerResponse> {
    const flower = await this.flowers.findOne({
      where: { id, ownerId },
      relations: { speciesRef: true },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    return this.toResponse(flower, ownerId);
  }

  /**
   * Réencode l'image reçue en WebP (pleine résolution + miniature), la stocke
   * sur MinIO et met à jour la fleur + sa photo de couverture. Remplace l'upload
   * présigné direct : l'octet transite par l'API pour être réencodé.
   */
  async uploadImage(
    ownerId: string,
    id: string,
    input: Buffer,
  ): Promise<FlowerResponse> {
    const flower = await this.flowers.findOne({
      where: { id, ownerId },
      relations: { speciesRef: true },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }

    const { full, thumbnail } = await encodeWebp(input);
    const imageKey = this.storage.buildKey(ownerId, 'webp');
    const thumbnailKey = this.storage.buildKey(ownerId, 'webp');
    await this.storage.putObject(imageKey, full, 'image/webp');
    await this.storage.putObject(thumbnailKey, thumbnail, 'image/webp');

    // Inclut l'ancienne miniature : sinon un ré-upload laissait un thumbnail
    // orphelin sur MinIO à chaque remplacement d'image.
    const oldKeys = [flower.imageKey, flower.thumbnailKey];
    flower.imageKey = imageKey;
    flower.thumbnailKey = thumbnailKey;
    const saved = await this.flowers.save(flower);

    // La couverture suit l'image principale (NODE-104).
    await this.photos.update(
      { flowerId: id, isCover: true },
      { imageKey, thumbnailKey },
    );

    // Best-effort : retire les anciens objets (image + miniature, souvent le
    // placeholder .jpg initial).
    await Promise.all(
      oldKeys
        .filter(
          (k): k is string => !!k && k !== imageKey && k !== thumbnailKey,
        )
        .map((k) => this.storage.delete(k).catch(() => undefined)),
    );

    return this.toResponse(saved, ownerId);
  }

  async getImageUrl(ownerId: string, id: string): Promise<{ imageUrl: string }> {
    const flower = await this.flowers.findOne({ where: { id, ownerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    return { imageUrl: await this.storage.presignDownload(flower.imageKey) };
  }

  /**
   * Liste les fleurs de l'utilisateur, filtrables par espèce et/ou étiquette.
   * Le filtrage est poussé en SQL (au lieu de charger toutes les fleurs puis de
   * filtrer en mémoire) pour exploiter les index `idx_flowers_species` /
   * `idx_flowers_tags`. `species` reste une correspondance insensible à la casse
   * par sous-chaîne (ILIKE) ; `tag` teste l'appartenance au tableau (`= ANY`).
   */
  async search(
    ownerId: string,
    filters: { species?: string; tag?: string } = {},
  ): Promise<FlowerResponse[]> {
    const qb = this.flowers
      .createQueryBuilder('flower')
      .leftJoinAndSelect('flower.speciesRef', 'speciesRef')
      .where('flower.ownerId = :ownerId', { ownerId })
      .orderBy('flower.takenAt', 'DESC');
    if (filters.species) {
      qb.andWhere('flower.species ILIKE :species', {
        species: `%${filters.species}%`,
      });
    }
    if (filters.tag) {
      qb.andWhere(':tag = ANY(flower.tags)', { tag: filters.tag });
    }
    const flowers = await qb.getMany();
    return this.toResponseMany(flowers, ownerId);
  }

  async update(
    ownerId: string,
    id: string,
    dto: UpdateFlowerDto,
  ): Promise<FlowerResponse> {
    const flower = await this.flowers.findOne({
      where: { id, ownerId },
      relations: { speciesRef: true },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (dto.notes !== undefined) flower.notes = dto.notes;
    if (dto.visibility !== undefined) flower.visibility = dto.visibility;
    if (dto.feedIncludeGps !== undefined) {
      flower.feedIncludeGps = dto.feedIncludeGps;
    }
    if (dto.takenAt !== undefined) flower.takenAt = new Date(dto.takenAt);
    if (dto.species !== undefined) flower.species = dto.species;
    // Rattachement au référentiel (NODE-128) : le propriétaire pose species_id
    // via le sélecteur. On recharge la relation pour exposer l'espèce résolue.
    if (dto.speciesId !== undefined) {
      flower.speciesId = dto.speciesId;
      flower.speciesRef = null;
    }
    if (dto.tags !== undefined) flower.tags = dto.tags;
    const saved = await this.flowers.save(flower);
    if (dto.speciesId !== undefined) {
      const reloaded = await this.flowers.findOne({
        where: { id: saved.id, ownerId },
        relations: { speciesRef: true },
      });
      return this.toResponse(reloaded ?? saved, ownerId);
    }
    return this.toResponse(saved, ownerId);
  }

  async remove(ownerId: string, id: string): Promise<void> {
    const flower = await this.flowers.findOne({ where: { id, ownerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    await this.flowers.softRemove(flower);
  }

  /**
   * Construit la réponse API d'une fleur (photos + URL couverture présignées).
   * [viewerId] sert à calculer `likedByMe` (NODE-139) ; omis pour les contextes
   * sans spectateur identifié. Pour une LISTE de fleurs, préférer
   * `toResponseMany` qui regroupe les requêtes photos/cœurs (évite le N+1).
   */
  async toResponse(
    flower: Flower,
    viewerId?: string,
  ): Promise<FlowerResponse> {
    const photos = await this.photos.find({
      where: { flowerId: flower.id },
      order: { position: 'ASC' },
    });
    // Toutes les réactions de la fleur en une requête : total, décompte par type
    // et « ma réaction » se déduisent en mémoire (TÂCHE 3.5).
    const likeRows = await this.likes.find({
      where: { flowerId: flower.id },
    });
    const { likeCount, reactionCounts, myReaction } = aggregateReactions(
      likeRows,
      viewerId,
    );
    const commentCount = await this.comments.count({
      where: { flowerId: flower.id },
    });
    return this.buildResponse(
      flower,
      photos,
      likeCount,
      reactionCounts,
      myReaction,
      commentCount,
    );
  }

  /**
   * Variante batch de `toResponse` pour une liste de fleurs (feed, listing,
   * partages…). Charge les photos et les cœurs de TOUTES les fleurs en deux
   * requêtes groupées (`IN`) au lieu d'une par fleur, supprimant le N+1 sur les
   * requêtes SQL. Les URLs présignées restent calculées par photo (inhérent au
   * stockage objet). Renvoie les réponses dans l'ordre des fleurs fournies.
   */
  async toResponseMany(
    flowers: Flower[],
    viewerId?: string,
  ): Promise<FlowerResponse[]> {
    if (flowers.length === 0) return [];
    const flowerIds = flowers.map((f) => f.id);

    const allPhotos = await this.photos.find({
      where: { flowerId: In(flowerIds) },
      order: { position: 'ASC' },
    });
    const photosByFlower = new Map<string, FlowerPhoto[]>();
    for (const p of allPhotos) {
      const list = photosByFlower.get(p.flowerId);
      if (list) list.push(p);
      else photosByFlower.set(p.flowerId, [p]);
    }

    // Un seul chargement des réactions de la page : total, décompte par type et
    // « ma réaction » calculés en mémoire, groupés par fleur (TÂCHE 3.5).
    const allLikes = await this.likes.find({
      where: { flowerId: In(flowerIds) },
    });
    const likesByFlower = new Map<string, FlowerLike[]>();
    for (const like of allLikes) {
      const list = likesByFlower.get(like.flowerId);
      if (list) list.push(like);
      else likesByFlower.set(like.flowerId, [like]);
    }

    // Comptage groupé des commentaires (TÂCHE 3.3) : un seul COUNT ... GROUP BY
    // sur la page, sans charger les corps de commentaires (évite le N+1).
    const commentCountByFlower = new Map<string, number>();
    const commentRows = await this.comments
      .createQueryBuilder('c')
      .select('c.flower_id', 'flowerId')
      .addSelect('COUNT(*)', 'count')
      .where('c.flower_id IN (:...flowerIds)', { flowerIds })
      .groupBy('c.flower_id')
      .getRawMany<{ flowerId: string; count: string }>();
    for (const row of commentRows) {
      commentCountByFlower.set(row.flowerId, Number(row.count));
    }

    return Promise.all(
      flowers.map((flower) => {
        const { likeCount, reactionCounts, myReaction } = aggregateReactions(
          likesByFlower.get(flower.id) ?? [],
          viewerId,
        );
        return this.buildResponse(
          flower,
          photosByFlower.get(flower.id) ?? [],
          likeCount,
          reactionCounts,
          myReaction,
          commentCountByFlower.get(flower.id) ?? 0,
        );
      }),
    );
  }

  /** Assemble la réponse API à partir des données déjà chargées (photos/réactions). */
  private async buildResponse(
    flower: Flower,
    photos: FlowerPhoto[],
    likeCount: number,
    reactionCounts: Partial<Record<Reaction, number>>,
    myReaction: Reaction | null,
    commentCount: number,
  ): Promise<FlowerResponse> {
    const [longitude, latitude] = flower.location?.coordinates ?? [null, null];
    const photoResponses: PhotoResponse[] = await Promise.all(
      photos.map(async (p) => ({
        id: p.id,
        url: await this.storage.presignDownload(p.imageKey),
        thumbnailUrl: p.thumbnailKey
          ? await this.storage.presignDownload(p.thumbnailKey)
          : null,
        position: p.position,
        isCover: p.isCover,
      })),
    );
    const cover = photoResponses.find((p) => p.isCover) ?? photoResponses[0];
    return {
      id: flower.id,
      ownerId: flower.ownerId,
      imageUrl: cover?.url ?? (await this.storage.presignDownload(flower.imageKey)),
      thumbnailUrl: cover?.thumbnailUrl ?? null,
      latitude,
      longitude,
      accuracyM: flower.accuracyM,
      takenAt: flower.takenAt,
      notes: flower.notes,
      visibility: flower.visibility,
      feedIncludeGps: flower.feedIncludeGps ?? true,
      needsIdentification: flower.needsIdentification ?? false,
      species: flower.species ?? null,
      speciesId: flower.speciesId ?? null,
      speciesRef: flower.speciesRef
        ? {
            id: flower.speciesRef.id,
            scientificName: flower.speciesRef.scientificName,
            commonName: flower.speciesRef.commonName,
          }
        : null,
      tags: flower.tags ?? [],
      photos: photoResponses,
      likeCount,
      likedByMe: myReaction !== null,
      reactionCounts,
      myReaction,
      commentCount,
      createdAt: flower.createdAt,
      updatedAt: flower.updatedAt,
    };
  }
}

/**
 * Agrège un jeu de réactions d'UNE fleur (TÂCHE 3.5) : total, décompte par type
 * (types absents omis) et réaction du spectateur ([viewerId]). Robuste aux
 * lignes historiques sans colonne `reaction` (repli sur le cœur par défaut).
 */
function aggregateReactions(
  likeRows: FlowerLike[],
  viewerId?: string,
): {
  likeCount: number;
  reactionCounts: Partial<Record<Reaction, number>>;
  myReaction: Reaction | null;
} {
  const reactionCounts: Partial<Record<Reaction, number>> = {};
  let myReaction: Reaction | null = null;
  for (const like of likeRows) {
    const reaction = like.reaction ?? DEFAULT_REACTION;
    reactionCounts[reaction] = (reactionCounts[reaction] ?? 0) + 1;
    if (viewerId && like.userId === viewerId) myReaction = reaction;
  }
  return { likeCount: likeRows.length, reactionCounts, myReaction };
}
