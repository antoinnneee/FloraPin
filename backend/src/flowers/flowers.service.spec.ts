import { BadRequestException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { FlowerLike } from '../likes/flower-like.entity';
import { FlowerComment } from '../comments/flower-comment.entity';
import { SpeciesService } from '../species/species.service';
import { StorageService } from '../storage/storage.service';
import { StubStorageService } from '../storage/stub-storage.service';
import { Flower } from './flower.entity';
import { FlowerPhoto } from './flower-photo.entity';
import { FlowersService } from './flowers.service';

class FakeFlowerRepo {
  store = new Map<string, Flower>();

  create(obj: Partial<Flower>): Flower {
    return { ...obj } as Flower;
  }

  async save(obj: Flower): Promise<Flower> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    obj.updatedAt = new Date();
    this.store.set(obj.id, { ...obj });
    return obj;
  }

  async findOne(opts: {
    where: { id: string; ownerId: string };
  }): Promise<Flower | null> {
    const found = this.store.get(opts.where.id);
    if (!found || found.ownerId !== opts.where.ownerId) return null;
    return found;
  }

  async find(opts: { where: { ownerId: string } }): Promise<Flower[]> {
    return [...this.store.values()].filter(
      (f) => f.ownerId === opts.where.ownerId,
    );
  }

  async softRemove(obj: Flower): Promise<Flower> {
    this.store.delete(obj.id);
    return obj;
  }

  /**
   * Fake QueryBuilder minimal reproduisant le filtrage SQL de `search` :
   * ownerId (=), species (ILIKE sous-chaîne, insensible à la casse) et tag
   * (appartenance au tableau). Suffisant pour les tests unitaires.
   */
  createQueryBuilder(_alias: string) {
    const store = this.store;
    const filters: {
      ownerId?: string;
      species?: string;
      tag?: string;
    } = {};
    const builder = {
      leftJoinAndSelect() {
        return builder;
      },
      where(_clause: string, params: { ownerId: string }) {
        filters.ownerId = params.ownerId;
        return builder;
      },
      orderBy() {
        return builder;
      },
      andWhere(clause: string, params: Record<string, string>) {
        if (clause.includes('species')) {
          filters.species = params.species; // '%rosa%'
        } else if (clause.includes('ANY')) {
          filters.tag = params.tag;
        }
        return builder;
      },
      async getMany(): Promise<Flower[]> {
        const needle = filters.species
          ? filters.species.replace(/%/g, '').toLowerCase()
          : undefined;
        return [...store.values()].filter((f) => {
          const ownerOk = !filters.ownerId || f.ownerId === filters.ownerId;
          const speciesOk =
            !needle || (f.species?.toLowerCase().includes(needle) ?? false);
          const tagOk = !filters.tag || (f.tags?.includes(filters.tag) ?? false);
          return ownerOk && speciesOk && tagOk;
        });
      },
    };
    return builder;
  }
}

class FakePhotoRepo {
  store = new Map<string, FlowerPhoto>();
  create(obj: Partial<FlowerPhoto>): FlowerPhoto {
    return { ...obj } as FlowerPhoto;
  }
  async save(obj: FlowerPhoto): Promise<FlowerPhoto> {
    if (!obj.id) obj.id = randomUUID();
    this.store.set(obj.id, { ...obj });
    return obj;
  }
  async find(opts: {
    where: { flowerId: string | { value: string[] } };
  }): Promise<FlowerPhoto[]> {
    const { flowerId } = opts.where;
    // Supporte l'opérateur TypeORM In([...]) (batch toResponseMany).
    const matches = (id: string) =>
      typeof flowerId === 'object' && Array.isArray(flowerId.value)
        ? flowerId.value.includes(id)
        : id === flowerId;
    return [...this.store.values()]
      .filter((p) => matches(p.flowerId))
      .sort((a, b) => a.position - b.position);
  }
}

const OWNER = 'owner-1';

/**
 * Référentiel d'espèces en mémoire : reproduit `resolveOrCreateByName`
 * (rapprochement insensible à la casse, création à défaut) pour vérifier le
 * rattachement automatique du texte libre sans monter le vrai service.
 */
class FakeSpeciesService {
  readonly store = new Map<string, { id: string; scientificName: string }>();
  /** Noms passés à la résolution, dans l'ordre (assertions d'appel). */
  readonly resolved: string[] = [];

  seed(scientificName: string): { id: string; scientificName: string } {
    const entry = { id: randomUUID(), scientificName };
    this.store.set(entry.id, entry);
    return entry;
  }

  async resolveOrCreateByName(
    name: string,
  ): Promise<{ id: string; scientificName: string } | null> {
    const term = name.trim();
    this.resolved.push(term);
    if (!term) return null;
    const existing = [...this.store.values()].find(
      (s) => s.scientificName.toLowerCase() === term.toLowerCase(),
    );
    return existing ?? this.seed(term);
  }
}

describe('FlowersService', () => {
  let service: FlowersService;
  let repo: FakeFlowerRepo;
  let species: FakeSpeciesService;

  beforeEach(async () => {
    repo = new FakeFlowerRepo();
    species = new FakeSpeciesService();
    const moduleRef = await Test.createTestingModule({
      providers: [
        FlowersService,
        { provide: getRepositoryToken(Flower), useValue: repo },
        { provide: getRepositoryToken(FlowerPhoto), useClass: FakePhotoRepo },
        {
          provide: getRepositoryToken(FlowerLike),
          useValue: { count: async () => 0, find: async () => [] },
        },
        {
          provide: getRepositoryToken(FlowerComment),
          useValue: {
            count: async () => 0,
            createQueryBuilder: () => ({
              select: () => ({
                addSelect: () => ({
                  where: () => ({
                    groupBy: () => ({ getRawMany: async () => [] }),
                  }),
                }),
              }),
            }),
          },
        },
        { provide: StorageService, useClass: StubStorageService },
        { provide: SpeciesService, useValue: species },
      ],
    }).compile();
    service = moduleRef.get(FlowersService);
  });

  it('crée une fleur géolocalisée et renvoie une URL d’upload', async () => {
    const result = await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
      latitude: 48.8584,
      longitude: 2.2945,
      accuracyM: 5,
    });

    expect(result.upload.method).toBe('PUT');
    expect(result.upload.url).toContain('upload=stub');
    expect(result.flower.latitude).toBe(48.8584);
    expect(result.flower.longitude).toBe(2.2945);
    expect(result.flower.imageUrl).toContain('download=stub');

    const stored = repo.store.get(result.flower.id)!;
    expect(stored.location).toEqual({
      type: 'Point',
      coordinates: [2.2945, 48.8584],
    });
    expect(stored.ownerId).toBe(OWNER);
  });

  it('crée une fleur sans position', async () => {
    const result = await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
    });
    expect(result.flower.latitude).toBeNull();
    expect(result.flower.longitude).toBeNull();
    expect(repo.store.get(result.flower.id)!.location).toBeNull();
  });

  it('refuse une latitude sans longitude', async () => {
    await expect(
      service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
        latitude: 48.8584,
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('renvoie 404 pour une fleur d’un autre propriétaire', async () => {
    const result = await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
    });
    await expect(
      service.getById('autre', result.flower.id),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('recherche par espèce', async () => {
    await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
      species: 'Rosa canina',
    });
    await service.create(OWNER, {
      takenAt: '2026-06-21T09:01:00Z',
      species: 'Bellis perennis',
    });

    const result = await service.search(OWNER, { species: 'rosa' });
    expect(result).toHaveLength(1);
    expect(result[0].species).toBe('Rosa canina');
  });

  it('rattache la fleur au référentiel via species_id (NODE-128)', async () => {
    const created = await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
    });
    const id = created.flower.id;

    const speciesId = '11111111-1111-1111-1111-111111111111';
    const updated = await service.update(OWNER, id, { speciesId });

    expect(updated.speciesId).toBe(speciesId);
    expect(repo.store.get(id)!.speciesId).toBe(speciesId);
  });

  it('toResponseMany renvoie une réponse par fleur, dans l’ordre fourni', async () => {
    const a = await service.create(OWNER, { takenAt: '2026-06-21T09:00:00Z' });
    const b = await service.create(OWNER, { takenAt: '2026-06-21T09:05:00Z' });
    const flowerA = repo.store.get(a.flower.id)!;
    const flowerB = repo.store.get(b.flower.id)!;

    const responses = await service.toResponseMany([flowerB, flowerA], OWNER);

    expect(responses.map((r) => r.id)).toEqual([flowerB.id, flowerA.id]);
    // Chaque réponse porte sa photo de couverture (présignée) et un décompte de
    // cœurs (0 ici) : forme identique à toResponse.
    expect(responses[0].photos).toHaveLength(1);
    expect(responses[0].imageUrl).toContain('download=stub');
    expect(responses[0].likeCount).toBe(0);
    expect(responses[0].likedByMe).toBe(false);
  });

  it('toResponseMany sur une liste vide ne fait aucune requête', async () => {
    expect(await service.toResponseMany([], OWNER)).toEqual([]);
  });

  it('recherche par étiquette', async () => {
    await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
      tags: ['jardin', 'rouge'],
    });
    await service.create(OWNER, {
      takenAt: '2026-06-21T09:01:00Z',
      tags: ['foret'],
    });

    const result = await service.search(OWNER, { tag: 'jardin' });
    expect(result).toHaveLength(1);
    expect(result[0].tags).toContain('jardin');
  });

  // --- Rattachement de l'espèce au référentiel à l'écriture ---
  //
  // Le texte libre ne posait pas `species_id` : la fleur n'apparaissait dans
  // l'herbier (qui joint sur cette colonne) qu'après le rattrapage SQL rejoué au
  // déploiement suivant. Ces cas verrouillent le rattachement immédiat.

  describe('rattachement de l’espèce au référentiel', () => {
    it('rattache une espèce saisie en texte libre dès la création', async () => {
      const coquelicot = species.seed('Papaver rhoeas');

      const { flower } = await service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
        species: 'papaver rhoeas',
      });

      // Rapprochement insensible à la casse : pas de doublon au référentiel.
      expect(flower.speciesId).toBe(coquelicot.id);
      expect(species.store.size).toBe(1);
    });

    it('crée l’entrée au référentiel si l’espèce y est inconnue', async () => {
      const { flower } = await service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
        species: 'Espèce inédite',
      });

      expect(flower.speciesId).not.toBeNull();
      expect(species.store.size).toBe(1);
    });

    it('laisse speciesId nul quand aucune espèce n’est fournie', async () => {
      const { flower } = await service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
      });

      expect(flower.speciesId).toBeNull();
      // Aucun appel inutile au référentiel.
      expect(species.resolved).toHaveLength(0);
    });

    it('rattache aussi une espèce saisie à la modification', async () => {
      const rosa = species.seed('Rosa canina');
      const { flower } = await service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
      });

      const updated = await service.update(OWNER, flower.id, {
        species: 'Rosa canina',
      });

      expect(updated.speciesId).toBe(rosa.id);
    });

    it('recalcule speciesId quand le nom est corrigé', async () => {
      const rosa = species.seed('Rosa canina');
      const tulipa = species.seed('Tulipa gesneriana');
      const { flower } = await service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
        species: 'Rosa canina',
      });
      expect(flower.speciesId).toBe(rosa.id);

      // Sans recalcul, species_id serait resté sur Rosa : l'herbier aurait
      // affiché la mauvaise espèce.
      const updated = await service.update(OWNER, flower.id, {
        species: 'Tulipa gesneriana',
      });

      expect(updated.speciesId).toBe(tulipa.id);
    });

    it('détache la fleur quand l’espèce est effacée', async () => {
      species.seed('Rosa canina');
      const { flower } = await service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
        species: 'Rosa canina',
      });

      const updated = await service.update(OWNER, flower.id, { species: '' });

      expect(updated.speciesId).toBeNull();
    });

    it('respecte un speciesId explicite sans re-résoudre le texte', async () => {
      const choisi = species.seed('Rosa canina');
      const { flower } = await service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
      });
      species.resolved.length = 0;

      // Sélection via l'autocomplétion : le client fait autorité, le texte
      // affiché peut différer du nom scientifique (nom commun, langue).
      const updated = await service.update(OWNER, flower.id, {
        species: 'Églantier',
        speciesId: choisi.id,
      });

      expect(updated.speciesId).toBe(choisi.id);
      expect(species.resolved).toHaveLength(0);
    });
  });
});
