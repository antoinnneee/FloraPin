import { ConflictException, NotFoundException } from '@nestjs/common';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { IdentificationRequestsService } from './identification-requests.service';

const OWNER = 'owner-1';
const FLOWER = 'flower-1';

describe('IdentificationRequestsService', () => {
  let flower: Flower | null;
  let flowers: { findOne: jest.Mock; save: jest.Mock; find: jest.Mock };
  let users: { find: jest.Mock };
  let friendships: { acceptedFriendIds: jest.Mock };
  let notifications: { create: jest.Mock; createSafe: jest.Mock };
  let shares: { needsIdentificationFromFriends: jest.Mock };
  let flowersService: { toResponseMany: jest.Mock };
  let proposals: { listForFlowerIds: jest.Mock };
  let service: IdentificationRequestsService;

  beforeEach(() => {
    flower = {
      id: FLOWER,
      ownerId: OWNER,
      needsIdentification: false,
    } as Flower;
    flowers = {
      findOne: jest.fn(async () => flower),
      save: jest.fn(async (f: Flower) => f),
      find: jest.fn(async () => []),
    };
    users = { find: jest.fn(async () => []) };
    friendships = { acceptedFriendIds: jest.fn(async () => ['a', 'b']) };
    notifications = {
      create: jest.fn(async () => undefined),
      createSafe: jest.fn(async () => undefined),
    };
    shares = { needsIdentificationFromFriends: jest.fn(async () => []) };
    flowersService = { toResponseMany: jest.fn(async () => []) };
    proposals = { listForFlowerIds: jest.fn(async () => new Map()) };
    service = new IdentificationRequestsService(
      flowers as never,
      users as never,
      friendships as never,
      notifications as never,
      shares as never,
      flowersService as never,
      proposals as never,
    );
  });

  describe('request', () => {
    it('marque la fleur à identifier et notifie chaque ami accepté', async () => {
      await service.request(OWNER, FLOWER);

      expect(flower!.needsIdentification).toBe(true);
      expect(flowers.save).toHaveBeenCalledTimes(1);
      // Notifications best-effort (createSafe) : un échec ne casse pas la demande.
      expect(notifications.createSafe).toHaveBeenCalledTimes(2);
      expect(notifications.createSafe).toHaveBeenCalledWith(
        'a',
        'identification_requested',
        { flowerId: FLOWER, byUserId: OWNER },
      );
    });

    it('est idempotente si déjà demandée : ni save ni nouvelle notification', async () => {
      // Anti-spam : un re-POST sur une fleur déjà « à identifier » ne re-notifie
      // pas le réseau (la demande reste visible tant que le drapeau est posé).
      flower!.needsIdentification = true;
      await service.request(OWNER, FLOWER);
      expect(flowers.save).not.toHaveBeenCalled();
      expect(notifications.createSafe).not.toHaveBeenCalled();
    });

    it('lève NotFound si la fleur ne m’appartient pas', async () => {
      flower = null;
      await expect(service.request(OWNER, FLOWER)).rejects.toBeInstanceOf(
        NotFoundException,
      );
      expect(notifications.createSafe).not.toHaveBeenCalled();
    });
  });

  describe('listForViewer', () => {
    it('délègue aux fleurs « à identifier » des amis (sans exiger un partage ciblé)', async () => {
      shares.needsIdentificationFromFriends.mockResolvedValue([
        {
          id: 'f1',
          ownerId: OWNER,
          needsIdentification: true,
          identificationRequestedAt: new Date('2026-07-24T12:00:00Z'),
        } as never,
      ]);
      users.find.mockResolvedValue([{ id: OWNER, displayName: 'Alice' }]);

      const result = await service.listForViewer('viewer');

      expect(shares.needsIdentificationFromFriends).toHaveBeenCalledWith('viewer');
      expect(result.map((f) => f.id)).toEqual(['f1']);
      expect(result[0].ownerName).toBe('Alice');
    });
  });

  describe('listMine', () => {
    it('compose mes fleurs en attente avec leurs propositions (sans N+1)', async () => {
      const requestedAt = new Date('2026-07-24T12:00:00Z');
      flowers.find.mockResolvedValue([
        { id: FLOWER, lastRemindedAt: requestedAt } as Flower,
      ]);
      flowersService.toResponseMany.mockResolvedValue([
        {
          id: FLOWER,
          needsIdentification: true,
          updatedAt: new Date('2026-07-23T12:00:00Z'),
        } as FlowerResponse,
      ]);
      proposals.listForFlowerIds.mockResolvedValue(
        new Map([[FLOWER, [{ id: 'p1', species: 'Coquelicot' }]]]),
      );

      const result = await service.listMine(OWNER);

      expect(flowers.find).toHaveBeenCalledWith({
        where: { ownerId: OWNER, needsIdentification: true },
        order: { lastRemindedAt: 'DESC', id: 'DESC' },
      });
      // Un seul batch des propositions pour tout le lot (pas d'appel par fleur).
      expect(proposals.listForFlowerIds).toHaveBeenCalledTimes(1);
      expect(proposals.listForFlowerIds).toHaveBeenCalledWith([FLOWER]);
      expect(result).toEqual([
        {
          flower: {
            id: FLOWER,
            needsIdentification: true,
            updatedAt: new Date('2026-07-23T12:00:00Z'),
            identificationRequestedAt: requestedAt,
          },
          proposals: [{ id: 'p1', species: 'Coquelicot' }],
        },
      ]);
    });

    it('retourne une liste vide sans fleur en attente (aucun batch)', async () => {
      flowers.find.mockResolvedValue([]);
      const result = await service.listMine(OWNER);
      expect(result).toEqual([]);
      expect(flowersService.toResponseMany).not.toHaveBeenCalled();
      expect(proposals.listForFlowerIds).not.toHaveBeenCalled();
    });
  });

  describe('remind', () => {
    it('re-notifie les amis et met à jour lastRemindedAt', async () => {
      // Demande ouverte, jamais relancée depuis assez longtemps.
      flower!.needsIdentification = true;
      flower!.lastRemindedAt = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000);

      await service.remind(OWNER, FLOWER);

      expect(flowers.save).toHaveBeenCalledTimes(1);
      expect(flower!.lastRemindedAt.getTime()).toBeGreaterThan(
        Date.now() - 5000,
      );
      expect(notifications.createSafe).toHaveBeenCalledTimes(2);
      expect(notifications.createSafe).toHaveBeenCalledWith(
        'a',
        'identification_requested',
        { flowerId: FLOWER, byUserId: OWNER },
      );
    });

    it('relance possible si jamais sollicitée (lastRemindedAt null)', async () => {
      flower!.needsIdentification = true;
      flower!.lastRemindedAt = null;

      await service.remind(OWNER, FLOWER);

      expect(flowers.save).toHaveBeenCalledTimes(1);
      expect(notifications.createSafe).toHaveBeenCalledTimes(2);
    });

    it('refuse (anti-spam) une relance trop rapprochée : ni save ni notification', async () => {
      flower!.needsIdentification = true;
      flower!.lastRemindedAt = new Date(Date.now() - 60 * 1000); // il y a 1 min

      await expect(service.remind(OWNER, FLOWER)).rejects.toBeInstanceOf(
        ConflictException,
      );
      expect(flowers.save).not.toHaveBeenCalled();
      expect(notifications.createSafe).not.toHaveBeenCalled();
    });

    it('refuse si la fleur n’attend pas d’identification', async () => {
      flower!.needsIdentification = false;
      await expect(service.remind(OWNER, FLOWER)).rejects.toBeInstanceOf(
        ConflictException,
      );
      expect(notifications.createSafe).not.toHaveBeenCalled();
    });

    it('lève NotFound si la fleur ne m’appartient pas', async () => {
      flower = null;
      await expect(service.remind(OWNER, FLOWER)).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });
  });

  describe('cancel', () => {
    it('lève le drapeau de demande', async () => {
      flower!.needsIdentification = true;
      await service.cancel(OWNER, FLOWER);
      expect(flower!.needsIdentification).toBe(false);
      expect(flowers.save).toHaveBeenCalledTimes(1);
    });
  });
});
