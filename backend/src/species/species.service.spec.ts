import { NotFoundException } from '@nestjs/common';
import { Repository } from 'typeorm';
import { Species } from './species.entity';
import { SpeciesService } from './species.service';

function makeSpecies(over: Partial<Species> = {}): Species {
  return {
    id: 'sp-1',
    scientificName: 'Rosa canina',
    commonName: 'Églantier',
    family: 'Rosaceae',
    description: '',
    emoji: '🌹',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...over,
  } as Species;
}

describe('SpeciesService', () => {
  let repo: jest.Mocked<
    Pick<Repository<Species>, 'find' | 'findAndCount' | 'findOne'>
  >;
  let service: SpeciesService;

  beforeEach(() => {
    repo = {
      find: jest.fn(),
      findAndCount: jest.fn(),
      findOne: jest.fn(),
    } as never;
    service = new SpeciesService(repo as never);
  });

  describe('list', () => {
    it('renvoie une page (skip/take) et le total, triée par nom scientifique', async () => {
      repo.findAndCount.mockResolvedValue([[makeSpecies()], 7]);

      const res = await service.list(2, 3);

      expect(repo.findAndCount).toHaveBeenCalledWith({
        order: { scientificName: 'ASC' },
        skip: 3,
        take: 3,
      });
      expect(res).toEqual({
        items: [
          {
            id: 'sp-1',
            scientificName: 'Rosa canina',
            commonName: 'Églantier',
            family: 'Rosaceae',
            description: '',
            emoji: '🌹',
          },
        ],
        total: 7,
        page: 2,
        limit: 3,
      });
    });

    it('borne la taille de page au maximum et la page au minimum', async () => {
      repo.findAndCount.mockResolvedValue([[], 0]);
      await service.list(0, 9999);
      expect(repo.findAndCount).toHaveBeenCalledWith({
        order: { scientificName: 'ASC' },
        skip: 0, // page ramenée à 1
        take: 200, // limit bornée à 200
      });
    });
  });

  describe('search', () => {
    it('renvoie [] sans interroger la base pour un terme vide', async () => {
      const res = await service.search('   ');
      expect(res).toEqual([]);
      expect(repo.find).not.toHaveBeenCalled();
    });

    it('cherche sur nom scientifique OU commun, insensible à la casse, limité', async () => {
      repo.find.mockResolvedValue([makeSpecies()]);

      const res = await service.search('ros', 5);

      const arg = repo.find.mock.calls[0][0]!;
      expect(arg.take).toBe(5);
      expect(arg.order).toEqual({ scientificName: 'ASC' });
      // Deux clauses OR : nom scientifique et nom commun.
      expect(Array.isArray(arg.where)).toBe(true);
      expect((arg.where as unknown[]).length).toBe(2);
      expect(res).toHaveLength(1);
      expect(res[0].scientificName).toBe('Rosa canina');
    });

    it('borne le nombre de suggestions au maximum', async () => {
      repo.find.mockResolvedValue([]);
      await service.search('ros', 9999);
      expect(repo.find.mock.calls[0][0]!.take).toBe(50);
    });
  });

  describe('getById', () => {
    it('renvoie la fiche si trouvée', async () => {
      repo.findOne.mockResolvedValue(makeSpecies({ id: 'sp-9' }));
      const res = await service.getById('sp-9');
      expect(res.id).toBe('sp-9');
      expect(repo.findOne).toHaveBeenCalledWith({ where: { id: 'sp-9' } });
    });

    it('lève NotFound si absente', async () => {
      repo.findOne.mockResolvedValue(null);
      await expect(service.getById('nope')).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });
  });
});
