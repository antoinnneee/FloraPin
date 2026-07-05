import { BadgesService } from './badges.service';

const USER = 'user-1';

/**
 * Fabrique un query builder factice chaînable dont `getCount()` résout [count].
 * Sert à simuler les jointures (propositions acceptées sur mes fleurs, réactions
 * reçues) sans base réelle.
 */
function fakeQueryBuilder(count: number) {
  const chain: Record<string, unknown> = {};
  const self = () => chain;
  chain.innerJoin = self;
  chain.where = self;
  chain.andWhere = self;
  chain.getCount = async () => count;
  return chain;
}

describe('BadgesService', () => {
  it('agrège tous les compteurs d’entraide en un seul objet', async () => {
    // count() renvoie une valeur distincte selon l'entité/where interrogé, pour
    // vérifier que chaque compteur est bien câblé sur la bonne source.
    const friendships = {
      count: jest.fn(async () => 4), // 🤝 amis acceptés
    };
    const proposals = {
      count: jest.fn(async ({ where }: { where: Record<string, unknown> }) =>
        where.status === 'accepted' ? 7 : 12,
      ),
      // ✅ propositions acceptées sur mes fleurs (jointure).
      createQueryBuilder: jest.fn(() => fakeQueryBuilder(3)),
    };
    const comments = { count: jest.fn(async () => 9) };
    const likes = {
      count: jest.fn(async () => 20), // 👍 réactions données
      // ❤️ réactions reçues (jointure).
      createQueryBuilder: jest.fn(() => fakeQueryBuilder(15)),
    };
    const flowers = { count: jest.fn(async () => 2) }; // ❓ demandes ouvertes

    const service = new BadgesService(
      friendships as never,
      proposals as never,
      comments as never,
      likes as never,
      flowers as never,
    );

    const counts = await service.countsFor(USER);

    expect(counts).toEqual({
      friends: 4,
      proposalsMade: 12,
      proposalsAccepted: 7,
      identificationRequests: 2,
      proposalsAcceptedAsOwner: 3,
      comments: 9,
      reactionsGiven: 20,
      reactionsReceived: 15,
    });

    // 🤝 Amis : accepté, en demandeur OU destinataire (where en tableau = OR).
    expect(friendships.count).toHaveBeenCalledWith({
      where: [
        { requesterId: USER, status: 'accepted' },
        { addresseeId: USER, status: 'accepted' },
      ],
    });
    // 🔍 Proposer = toutes mes propositions ; 🎓 Acceptées = statut accepté.
    expect(proposals.count).toHaveBeenCalledWith({
      where: { proposedBy: USER },
    });
    expect(proposals.count).toHaveBeenCalledWith({
      where: { proposedBy: USER, status: 'accepted' },
    });
    // ❓ Demander = fleurs à moi en attente d'identification.
    expect(flowers.count).toHaveBeenCalledWith({
      where: { ownerId: USER, needsIdentification: true },
    });
    // 👍 Réactions données = les cœurs que j'ai posés.
    expect(likes.count).toHaveBeenCalledWith({ where: { userId: USER } });
  });

  it('renvoie des compteurs nuls pour un utilisateur sans activité', async () => {
    const zero = { count: jest.fn(async () => 0) };
    const proposals = {
      count: jest.fn(async () => 0),
      createQueryBuilder: jest.fn(() => fakeQueryBuilder(0)),
    };
    const likes = {
      count: jest.fn(async () => 0),
      createQueryBuilder: jest.fn(() => fakeQueryBuilder(0)),
    };
    const service = new BadgesService(
      zero as never,
      proposals as never,
      zero as never,
      likes as never,
      zero as never,
    );

    const counts = await service.countsFor(USER);

    expect(counts).toEqual({
      friends: 0,
      proposalsMade: 0,
      proposalsAccepted: 0,
      identificationRequests: 0,
      proposalsAcceptedAsOwner: 0,
      comments: 0,
      reactionsGiven: 0,
      reactionsReceived: 0,
    });
  });
});
