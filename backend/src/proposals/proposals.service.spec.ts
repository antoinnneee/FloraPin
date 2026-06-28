import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
} from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';
import { Species } from '../species/species.entity';
import { SpeciesService } from '../species/species.service';
import { SpeciesProposal } from './species-proposal.entity';
import { ProposalsService } from './proposals.service';

class FakeFlowerRepo {
  store = new Map<string, Flower>();
  seed(f: Partial<Flower>): Flower {
    const flower = { species: null, ...f } as Flower;
    this.store.set(flower.id, flower);
    return flower;
  }
  async findOne(opts: {
    where: { id: string; ownerId?: string };
  }): Promise<Flower | null> {
    const found = this.store.get(opts.where.id);
    if (!found) return null;
    if (opts.where.ownerId && found.ownerId !== opts.where.ownerId) return null;
    return found;
  }
  async save(f: Flower): Promise<Flower> {
    this.store.set(f.id, f);
    return f;
  }
}

class FakeProposalRepo {
  store = new Map<string, SpeciesProposal>();
  create(obj: Partial<SpeciesProposal>): SpeciesProposal {
    return { ...obj } as SpeciesProposal;
  }
  async save(obj: SpeciesProposal): Promise<SpeciesProposal> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    this.store.set(obj.id, { ...obj });
    return obj;
  }
  async find(opts: { where: { flowerId: string } }): Promise<SpeciesProposal[]> {
    return [...this.store.values()].filter(
      (p) => p.flowerId === opts.where.flowerId,
    );
  }
  async findOne(opts: {
    where: { id: string; flowerId: string };
  }): Promise<SpeciesProposal | null> {
    const found = this.store.get(opts.where.id);
    return found && found.flowerId === opts.where.flowerId ? found : null;
  }
}

const OWNER = 'owner';
const FRIEND = 'friend';
const FLOWER = 'flower-1';

describe('ProposalsService', () => {
  let service: ProposalsService;
  let flowerRepo: FakeFlowerRepo;
  let proposalRepo: FakeProposalRepo;
  let visibleToFriend: FlowerResponse[];
  let notified: Array<{ userId: string; type: string }>;

  beforeEach(async () => {
    flowerRepo = new FakeFlowerRepo();
    proposalRepo = new FakeProposalRepo();
    visibleToFriend = [{ id: FLOWER } as FlowerResponse];
    notified = [];

    const moduleRef = await Test.createTestingModule({
      providers: [
        ProposalsService,
        { provide: getRepositoryToken(SpeciesProposal), useValue: proposalRepo },
        { provide: getRepositoryToken(Flower), useValue: flowerRepo },
        {
          provide: SharesService,
          useValue: { needsIdentificationFromFriends: async () => visibleToFriend },
        },
        {
          provide: NotificationsService,
          useValue: {
            create: async (userId: string, type: string) => {
              notified.push({ userId, type });
            },
          },
        },
        {
          provide: SpeciesService,
          useValue: {
            // Rattachement simulé : le texte devient une fiche au nom
            // scientifique identique (NODE-127).
            resolveOrCreateByName: async (name: string): Promise<Species> =>
              ({
                id: `sp-${name}`,
                scientificName: name,
                commonName: '',
                family: '',
                description: '',
                emoji: null,
              }) as Species,
          },
        },
      ],
    }).compile();
    service = moduleRef.get(ProposalsService);
  });

  it('refuse une proposition du propriétaire', async () => {
    flowerRepo.seed({ id: FLOWER, ownerId: OWNER, species: null });
    await expect(
      service.propose(OWNER, FLOWER, 'Rosa canina'),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('refuse si la fleur n’attend pas d’identification', async () => {
    flowerRepo.seed({
      id: FLOWER,
      ownerId: OWNER,
      species: 'Bellis',
      needsIdentification: false,
    });
    await expect(
      service.propose(FRIEND, FLOWER, 'Rosa canina'),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('autorise une proposition après suppression puis nouvelle demande d’identification', async () => {
    // Régression : une ancienne espèce subsiste (suppression non propagée) mais
    // la fleur a été ré-ouverte (needsIdentification=true) → propose() doit
    // réussir au lieu de renvoyer un 409.
    flowerRepo.seed({
      id: FLOWER,
      ownerId: OWNER,
      species: 'Bellis',
      needsIdentification: true,
    });
    const proposal = await service.propose(FRIEND, FLOWER, 'Rosa canina');
    expect(proposal.status).toBe('pending');
  });

  it('refuse si la fleur n’est pas partagée avec l’ami', async () => {
    flowerRepo.seed({
      id: FLOWER,
      ownerId: OWNER,
      species: null,
      needsIdentification: true,
    });
    visibleToFriend = [];
    await expect(
      service.propose(FRIEND, FLOWER, 'Rosa canina'),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('crée une proposition et notifie le propriétaire', async () => {
    flowerRepo.seed({
      id: FLOWER,
      ownerId: OWNER,
      species: null,
      needsIdentification: true,
    });
    const proposal = await service.propose(FRIEND, FLOWER, 'Rosa canina');
    expect(proposal.status).toBe('pending');
    expect(notified).toContainEqual({
      userId: OWNER,
      type: 'species_proposed',
    });
  });

  it('le propriétaire accepte : l’espèce est appliquée et l’ami notifié', async () => {
    flowerRepo.seed({
      id: FLOWER,
      ownerId: OWNER,
      species: null,
      needsIdentification: true,
    });
    const proposal = await service.propose(FRIEND, FLOWER, 'Rosa canina');

    await service.accept(OWNER, FLOWER, proposal.id);

    const updated = flowerRepo.store.get(FLOWER)!;
    expect(updated.species).toBe('Rosa canina');
    // Normalisation vers le référentiel (NODE-127) : species_id renseigné.
    expect(updated.speciesId).toBe('sp-Rosa canina');
    expect(notified).toContainEqual({
      userId: FRIEND,
      type: 'species_confirmed',
    });
  });
});
