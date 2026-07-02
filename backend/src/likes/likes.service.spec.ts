import { NotFoundException } from '@nestjs/common';
import { Flower } from '../flowers/flower.entity';
import { FlowerLike } from './flower-like.entity';
import { LikesService } from './likes.service';

const OWNER = 'owner-1';
const LIKER = 'liker-1';
const STRANGER = 'stranger-1';
const FLOWER = 'flower-1';

describe('LikesService', () => {
  let flower: Flower | null;
  /** Viewers (hors propriétaire) qui voient la fleur via partage/diffusion. */
  let visibleTo: Set<string>;
  let likeRows: FlowerLike[];
  let likes: {
    findOne: jest.Mock;
    create: jest.Mock;
    save: jest.Mock;
    delete: jest.Mock;
  };
  let flowers: { findOne: jest.Mock };
  let shares: { isVisibleTo: jest.Mock };
  let notifications: { create: jest.Mock };
  let service: LikesService;

  beforeEach(() => {
    flower = { id: FLOWER, ownerId: OWNER } as Flower;
    visibleTo = new Set([LIKER]);
    likeRows = [];
    likes = {
      findOne: jest.fn(
        async ({ where }: { where: { flowerId: string; userId: string } }) =>
          likeRows.find(
            (r) => r.flowerId === where.flowerId && r.userId === where.userId,
          ) ?? null,
      ),
      create: jest.fn((o: Partial<FlowerLike>) => o as FlowerLike),
      save: jest.fn(async (o: FlowerLike) => {
        likeRows.push(o);
        return o;
      }),
      delete: jest.fn(
        async (where: { flowerId: string; userId: string }) => {
          likeRows = likeRows.filter(
            (r) => !(r.flowerId === where.flowerId && r.userId === where.userId),
          );
          return { affected: 1 };
        },
      ),
    };
    flowers = { findOne: jest.fn(async () => flower) };
    shares = {
      // Même périmètre que CommentsService : propriétaire ou partage reçu.
      isVisibleTo: jest.fn(
        async (viewerId: string, f: Flower) =>
          f.ownerId === viewerId || visibleTo.has(viewerId),
      ),
    };
    notifications = { create: jest.fn(async () => undefined) };
    service = new LikesService(
      likes as never,
      flowers as never,
      shares as never,
      notifications as never,
    );
  });

  describe('like', () => {
    it('pose un cœur et notifie le propriétaire', async () => {
      await service.like(LIKER, FLOWER);
      expect(likes.save).toHaveBeenCalledTimes(1);
      expect(notifications.create).toHaveBeenCalledWith(OWNER, 'flower_liked', {
        flowerId: FLOWER,
        byUserId: LIKER,
      });
    });

    it('est idempotent : un second cœur ne crée rien', async () => {
      await service.like(LIKER, FLOWER);
      await service.like(LIKER, FLOWER);
      expect(likes.save).toHaveBeenCalledTimes(1);
      expect(notifications.create).toHaveBeenCalledTimes(1);
    });

    it('ne notifie pas lors d’un auto-cœur', async () => {
      await service.like(OWNER, FLOWER);
      expect(likes.save).toHaveBeenCalledTimes(1);
      expect(notifications.create).not.toHaveBeenCalled();
    });

    it('lève NotFound si la fleur n’existe pas', async () => {
      flower = null;
      await expect(service.like(LIKER, FLOWER)).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });

    it('lève NotFound si la fleur n’est pas visible par le viewer (I1)', async () => {
      await expect(service.like(STRANGER, FLOWER)).rejects.toBeInstanceOf(
        NotFoundException,
      );
      expect(likes.save).not.toHaveBeenCalled();
      expect(notifications.create).not.toHaveBeenCalled();
    });
  });

  describe('unlike', () => {
    it('retire le cœur (idempotent)', async () => {
      await service.like(LIKER, FLOWER);
      await service.unlike(LIKER, FLOWER);
      expect(likes.delete).toHaveBeenCalledWith({
        flowerId: FLOWER,
        userId: LIKER,
      });
      expect(likeRows).toHaveLength(0);
    });

    it('lève NotFound si la fleur n’est pas visible par le viewer (I1)', async () => {
      await expect(service.unlike(STRANGER, FLOWER)).rejects.toBeInstanceOf(
        NotFoundException,
      );
      expect(likes.delete).not.toHaveBeenCalled();
    });

    it('lève NotFound si la fleur n’existe pas', async () => {
      flower = null;
      await expect(service.unlike(LIKER, FLOWER)).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });
  });
});
