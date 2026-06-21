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
  tags: string[];
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
    const upload = await this.storage.presignUpload(imageKey);
    return { flower: await this.toResponse(saved), upload };
  }

  async getById(ownerId: string, id: string): Promise<FlowerResponse> {
    const flower = await this.flowers.findOne({ where: { id, ownerId } });
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
    const flower = await this.flowers.findOne({ where: { id, ownerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (dto.notes !== undefined) flower.notes = dto.notes;
    if (dto.visibility !== undefined) flower.visibility = dto.visibility;
    if (dto.takenAt !== undefined) flower.takenAt = new Date(dto.takenAt);
    if (dto.species !== undefined) flower.species = dto.species;
    if (dto.tags !== undefined) flower.tags = dto.tags;
    const saved = await this.flowers.save(flower);
    return this.toResponse(saved);
  }

  async remove(ownerId: string, id: string): Promise<void> {
    const flower = await this.flowers.findOne({ where: { id, ownerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    await this.flowers.softRemove(flower);
  }

  /** Construit la réponse API d'une fleur (URL image présignée incluse). */
  async toResponse(flower: Flower): Promise<FlowerResponse> {
    const [longitude, latitude] = flower.location?.coordinates ?? [null, null];
    return {
      id: flower.id,
      ownerId: flower.ownerId,
      imageUrl: await this.storage.presignDownload(flower.imageKey),
      latitude,
      longitude,
      accuracyM: flower.accuracyM,
      takenAt: flower.takenAt,
      notes: flower.notes,
      visibility: flower.visibility,
      species: flower.species ?? null,
      tags: flower.tags ?? [],
      createdAt: flower.createdAt,
      updatedAt: flower.updatedAt,
    };
  }
}
