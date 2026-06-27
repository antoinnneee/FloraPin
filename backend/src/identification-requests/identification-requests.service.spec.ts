import { NotFoundException } from '@nestjs/common';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { IdentificationRequestsService } from './identification-requests.service';

const OWNER = 'owner-1';
const FLOWER = 'flower-1';

describe('IdentificationRequestsService', () => {
  let flower: Flower | null;
  let flowers: { findOne: jest.Mock; save: jest.Mock };
  let friendships: { acceptedFriendIds: jest.Mock };
  let notifications: { create: jest.Mock };
  let shares: { needsIdentificationFromFriends: jest.Mock };
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
    };
    friendships = { acceptedFriendIds: jest.fn(async () => ['a', 'b']) };
    notifications = { create: jest.fn(async () => undefined) };
    shares = { needsIdentificationFromFriends: jest.fn(async () => []) };
    service = new IdentificationRequestsService(
      flowers as never,
      friendships as never,
      notifications as never,
      shares as never,
    );
  });

  describe('request', () => {
    it('marque la fleur à identifier et notifie chaque ami accepté', async () => {
      await service.request(OWNER, FLOWER);

      expect(flower!.needsIdentification).toBe(true);
      expect(flowers.save).toHaveBeenCalledTimes(1);
      expect(notifications.create).toHaveBeenCalledTimes(2);
      expect(notifications.create).toHaveBeenCalledWith(
        'a',
        'identification_requested',
        { flowerId: FLOWER, byUserId: OWNER },
      );
    });

    it('ne re-sauvegarde pas si déjà demandée, mais re-notifie', async () => {
      flower!.needsIdentification = true;
      await service.request(OWNER, FLOWER);
      expect(flowers.save).not.toHaveBeenCalled();
      expect(notifications.create).toHaveBeenCalledTimes(2);
    });

    it('lève NotFound si la fleur ne m’appartient pas', async () => {
      flower = null;
      await expect(service.request(OWNER, FLOWER)).rejects.toBeInstanceOf(
        NotFoundException,
      );
      expect(notifications.create).not.toHaveBeenCalled();
    });
  });

  describe('listForViewer', () => {
    it('délègue aux fleurs « à identifier » des amis (sans exiger un partage ciblé)', async () => {
      shares.needsIdentificationFromFriends.mockResolvedValue([
        { id: 'f1', needsIdentification: true } as FlowerResponse,
      ]);

      const result = await service.listForViewer('viewer');

      expect(shares.needsIdentificationFromFriends).toHaveBeenCalledWith('viewer');
      expect(result.map((f) => f.id)).toEqual(['f1']);
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
