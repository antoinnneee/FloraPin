import { NotFoundException } from '@nestjs/common';
import { FindOperator, Repository } from 'typeorm';
import { Species } from './species.entity';
import { SpeciesService } from './species.service';

/** Lit la valeur (pattern) d'un opérateur ILike posé sur un champ. */
function likeValue(clause: unknown, field: string): string {
  return (clause as Record<string, FindOperator<string>>)[field].value;
}

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
    Pick<
      Repository<Species>,
      'find' | 'findAndCount' | 'findOne' | 'create' | 'save'
    >
  >;
  let service: SpeciesService;

  beforeEach(() => {
    repo = {
      find: jest.fn(),
      findAndCount: jest.fn(),
      findOne: jest.fn(),
      create: jest.fn((o) => o),
      save: jest.fn(),
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

    it('échappe les jokers ILIKE (%, _, \\) du terme recherché', async () => {
      repo.find.mockResolvedValue([]);
      await service.search('R_s%');
      const where = repo.find.mock.calls[0][0]!.where as unknown[];
      // Les jokers du terme sont échappés ; les % englobants restent des jokers.
      expect(likeValue(where[0], 'scientificName')).toBe('%R\\_s\\%%');
      expect(likeValue(where[1], 'commonName')).toBe('%R\\_s\\%%');
    });
  });

  describe('resolveOrCreateByName', () => {
    it('renvoie null pour un texte vide (sans toucher la base)', async () => {
      expect(await service.resolveOrCreateByName('  ')).toBeNull();
      expect(repo.findOne).not.toHaveBeenCalled();
    });

    it('rapproche par nom scientifique si trouvé', async () => {
      const sp = makeSpecies({ id: 'sp-sci' });
      repo.findOne.mockResolvedValueOnce(sp);
      const res = await service.resolveOrCreateByName('rosa canina');
      expect(res).toBe(sp);
      // Une seule lecture : le rapprochement scientifique a suffi.
      expect(repo.findOne).toHaveBeenCalledTimes(1);
    });

    it('échappe les jokers pour le rapprochement exact (« R_sa » ≠ « Rosa »)', async () => {
      repo.findOne.mockResolvedValue(null);
      repo.save.mockResolvedValue(makeSpecies());
      await service.resolveOrCreateByName('R_sa');
      expect(likeValue(repo.findOne.mock.calls[0][0]!.where, 'scientificName')).toBe(
        'R\\_sa',
      );
    });

    it('rapproche par nom commun si le nom scientifique ne matche pas', async () => {
      const sp = makeSpecies({ id: 'sp-com' });
      repo.findOne.mockResolvedValueOnce(null).mockResolvedValueOnce(sp);
      const res = await service.resolveOrCreateByName('Églantier');
      expect(res).toBe(sp);
      expect(repo.findOne).toHaveBeenCalledTimes(2);
    });

    it('crée une fiche minimale si aucun rapprochement', async () => {
      repo.findOne.mockResolvedValue(null);
      const created = makeSpecies({ id: 'sp-new', scientificName: 'Inconnue x' });
      repo.save.mockResolvedValue(created);

      const res = await service.resolveOrCreateByName('Inconnue x');
      expect(res).toBe(created);
      expect(repo.save).toHaveBeenCalledWith(
        expect.objectContaining({
          scientificName: 'Inconnue x',
          commonName: '',
          family: '',
        }),
      );
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
