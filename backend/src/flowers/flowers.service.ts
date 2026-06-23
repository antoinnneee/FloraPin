import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { StorageService, PresignedUpload } from '../storage/storage.service';
import { CreateFlowerDto, UpdateFlowerDto } from './dto/flower.dto';
import { Flower } from './flower.entity';
import { FlowerPhoto } from './flower-photo.entity';

/** Photo d'une fleur, telle qu'exposée par l'API (URL présignée). */
export interface PhotoResponse {
  id: string;
  url: string;
  position: number;
  isCover: boolean;
}

export interface FlowerResponse {
  id: string;
  ownerId: string;
  imageUrl: string;
  latitude: number | null;
  longitude: number | null;
  accuracyM: number | null;
  takenAt: Date;
  notes: string;
  visibility: string;
  species: string | null;
  /** FK vers le référentiel d'espèces (NODE-124), null si non rapprochée. */
  speciesId: string | null;
  /** Espèce résolue depuis le référentiel (NODE-125), null si non rapprochée. */
  speciesRef: { id: string; scientificName: string; commonName: string } | null;
  tags: string[];
  photos: PhotoResponse[];
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
    private readonly storage: StorageService,
  ) {}

  /**
   * Crée la fleur (métadonnées + GPS) et renvoie une URL présignée pour
   * téléverser le binaire image directement sur le stockage objet.
   */
  async create(
    ownerId: string,
    dto: CreateFlowerDto,
  ): Promise<CreateFlowerResult> {
    const hasLat = dto.latitude !== undefined;
    const hasLng = dto.longitude !== undefined;
    if (hasLat !== hasLng) {
      throw new BadRequestException(
        'latitude et longitude doivent être fournies ensemble.',
      );
    }

    const imageKey = this.storage.buildKey(ownerId, 'jpg');
    const entity = this.flowers.create({
      ownerId,
      imageKey,
      location:
        hasLat && hasLng
          ? { type: 'Point', coordinates: [dto.longitude!, dto.latitude!] }
          : null,
      accuracyM: dto.accuracyM ?? null,
      takenAt: new Date(dto.takenAt),
      notes: dto.notes ?? '',
      visibility: dto.visibility ?? 'private',
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
    return { flower: await this.toResponse(saved), upload };
  }

  async getById(ownerId: string, id: string): Promise<FlowerResponse> {
    const flower = await this.flowers.findOne({
      where: { id, ownerId },
      relations: { speciesRef: true },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    return this.toResponse(flower);
  }

  async getImageUrl(ownerId: string, id: string): Promise<{ imageUrl: string }> {
    const flower = await this.flowers.findOne({ where: { id, ownerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    return { imageUrl: await this.storage.presignDownload(flower.imageKey) };
  }

  /** Liste les fleurs de l'utilisateur, filtrables par espèce et/ou étiquette. */
  async search(
    ownerId: string,
    filters: { species?: string; tag?: string } = {},
  ): Promise<FlowerResponse[]> {
    const flowers = await this.flowers.find({
      where: { ownerId },
      order: { takenAt: 'DESC' },
      relations: { speciesRef: true },
    });

    const species = filters.species?.toLowerCase();
    const filtered = flowers.filter((f) => {
      const speciesOk =
        !species || (f.species?.toLowerCase().includes(species) ?? false);
      const tagOk = !filters.tag || (f.tags?.includes(filters.tag) ?? false);
      return speciesOk && tagOk;
    });
    return Promise.all(filtered.map((f) => this.toResponse(f)));
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
      return this.toResponse(reloaded ?? saved);
    }
    return this.toResponse(saved);
  }

  async remove(ownerId: string, id: string): Promise<void> {
    const flower = await this.flowers.findOne({ where: { id, ownerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    await this.flowers.softRemove(flower);
  }

  /** Construit la réponse API d'une fleur (photos + URL couverture présignées). */
  async toResponse(flower: Flower): Promise<FlowerResponse> {
    const [longitude, latitude] = flower.location?.coordinates ?? [null, null];
    const photos = await this.photos.find({
      where: { flowerId: flower.id },
      order: { position: 'ASC' },
    });
    const photoResponses: PhotoResponse[] = await Promise.all(
      photos.map(async (p) => ({
        id: p.id,
        url: await this.storage.presignDownload(p.imageKey),
        position: p.position,
        isCover: p.isCover,
      })),
    );
    const cover = photoResponses.find((p) => p.isCover) ?? photoResponses[0];
    return {
      id: flower.id,
      ownerId: flower.ownerId,
      imageUrl: cover?.url ?? (await this.storage.presignDownload(flower.imageKey)),
      latitude,
      longitude,
      accuracyM: flower.accuracyM,
      takenAt: flower.takenAt,
      notes: flower.notes,
      visibility: flower.visibility,
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
      createdAt: flower.createdAt,
      updatedAt: flower.updatedAt,
    };
  }
}
