import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { StorageService } from '../storage/storage.service';
import { PlantIdentifier, SpeciesSuggestion } from './plant-identifier';

export interface IdentificationResult {
  flowerId: string;
  suggestions: SpeciesSuggestion[];
}

@Injectable()
export class IdentificationService {
  constructor(
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly storage: StorageService,
    private readonly identifier: PlantIdentifier,
  ) {}

  /** Identifie l'espèce d'une fleur (de l'utilisateur) via son image. */
  async identify(ownerId: string, flowerId: string): Promise<IdentificationResult> {
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    const imageUrl = await this.storage.presignDownload(flower.imageKey);
    const suggestions = await this.identifier.identify(imageUrl);
    return { flowerId, suggestions };
  }
}
