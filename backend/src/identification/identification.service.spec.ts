import { NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { StorageService } from '../storage/storage.service';
import { StubStorageService } from '../storage/stub-storage.service';
import { IdentificationService } from './identification.service';
import { PlantIdentifier, SpeciesSuggestion } from './plant-identifier';

class FakeFlowerRepo {
  store = new Map<string, Flower>();
  seed(f: Partial<Flower>): Flower {
    const flower = { id: 'f1', ownerId: 'o', imageKey: 'k', ...f } as Flower;
    this.store.set(flower.id, flower);
    return flower;
  }
  async findOne(opts: {
    where: { id: string; ownerId: string };
  }): Promise<Flower | null> {
    const found = this.store.get(opts.where.id);
    return found && found.ownerId === opts.where.ownerId ? found : null;
  }
}

class FakeIdentifier extends PlantIdentifier {
  lastUrl?: string;
  async identify(imageUrl: string): Promise<SpeciesSuggestion[]> {
    this.lastUrl = imageUrl;
    return [{ scientificName: 'Rosa canina', commonName: 'Églantier', score: 0.92 }];
  }
}

describe('IdentificationService', () => {
  let service: IdentificationService;
  let repo: FakeFlowerRepo;
  let identifier: FakeIdentifier;

  beforeEach(async () => {
    repo = new FakeFlowerRepo();
    identifier = new FakeIdentifier();
    const moduleRef = await Test.createTestingModule({
      providers: [
        IdentificationService,
        { provide: getRepositoryToken(Flower), useValue: repo },
        { provide: StorageService, useClass: StubStorageService },
        { provide: PlantIdentifier, useValue: identifier },
      ],
    }).compile();
    service = moduleRef.get(IdentificationService);
  });

  it('identifie une fleur et renvoie des suggestions', async () => {
    repo.seed({ id: 'f1', ownerId: 'o', imageKey: 'k' });
    const result = await service.identify('o', 'f1');
    expect(result.flowerId).toBe('f1');
    expect(result.suggestions[0].scientificName).toBe('Rosa canina');
    expect(identifier.lastUrl).toContain('download=stub');
  });

  it('renvoie 404 si la fleur n’appartient pas à l’utilisateur', async () => {
    repo.seed({ id: 'f1', ownerId: 'o', imageKey: 'k' });
    await expect(service.identify('autre', 'f1')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});
