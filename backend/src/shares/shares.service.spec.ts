import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { FlowersService } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { StorageService } from '../storage/storage.service';
import { StubStorageService } from '../storage/stub-storage.service';
import { Share } from './share.entity';
import { SharesService } from './shares.service';

class FakeFlowerRepo {
  store = new Map<string, Flower>();
  seed(f: Partial<Flower>): Flower {
    const flower = {
      id: randomUUID(),
      notes: '',
      visibility: 'private',
      accuracyM: null,
      location: null,
      createdAt: new Date(),
      updatedAt: new Date(),
      deletedAt: null,
      ...f,
    } as Flower;
    this.store.set(flower.id, flower);
    return flower;
  }
  async find(opts: { where: { ownerId: string } }): Promise<Flower[]> {
    return [...this.store.values()].filter(
      (f) => f.ownerId === opts.where.ownerId,
    );
  }
  async findOne(opts: {
    where: { id?: string; ownerId?: string };
  }): Promise<Flower | null> {
    return (
      [...this.store.values()].find(
        (f) =>
          (opts.where.id === undefined || f.id === opts.where.id) &&
          (opts.where.ownerId === undefined ||
            f.ownerId === opts.where.ownerId),
      ) ?? null
    );
  }
}

class FakeShareRepo {
  store = new Map<string, Share>();
  create(obj: Partial<Share>): Share {
    return { ...obj } as Share;
  }
  async save(obj: Share): Promise<Share> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    this.store.set(obj.id, { ...obj });
    return obj;
  }
  async find(opts: {
    where: { sharedWith?: string; ownerId?: string };
  }): Promise<Share[]> {
    return [...this.store.values()].filter(
      (s) =>
        (opts.where.sharedWith === undefined ||
          s.sharedWith === opts.where.sharedWith) &&
        (opts.where.ownerId === undefined ||
          s.ownerId === opts.where.ownerId),
    );
  }
  async findOne(opts: { where: Partial<Share> }): Promise<Share | null> {
    return (
      [...this.store.values()].find((s) =>
        Object.entries(opts.where).every(
          ([k, v]) => (s as unknown as Record<string, unknown>)[k] === v,
        ),
      ) ?? null
    );
  }
  async remove(obj: Share): Promise<Share> {
    this.store.delete(obj.id);
    return obj;
  }
}

const OWNER = 'owner';
const VIEWER = 'viewer';

describe('SharesService', () => {
  let service: SharesService;
  let flowerRepo: FakeFlowerRepo;
  let acceptedFriends: string[];
  let notify: jest.Mock;

  beforeEach(async () => {
    flowerRepo = new FakeFlowerRepo();
    acceptedFriends = [VIEWER];
    notify = jest.fn();

    const moduleRef = await Test.createTestingModule({
      providers: [
        SharesService,
        FlowersService,
        { provide: getRepositoryToken(Share), useClass: FakeShareRepo },
        { provide: getRepositoryToken(Flower), useValue: flowerRepo },
        { provide: StorageService, useClass: StubStorageService },
        {
          provide: FriendshipsService,
          useValue: { acceptedFriendIds: async () => acceptedFriends },
        },
        { provide: NotificationsService, useValue: { create: notify } },
      ],
    }).compile();
    service = moduleRef.get(SharesService);
  });

  it('refuse le partage avec un non-ami', async () => {
    acceptedFriends = [];
    await expect(
      service.create(OWNER, { friendId: VIEWER, scope: 'all' }),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('refuse le partage d’une fleur inexistante', async () => {
    await expect(
      service.create(OWNER, {
        friendId: VIEWER,
        scope: 'flower',
        flowerId: randomUUID(),
      }),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('partage toutes les fleurs en masquant le GPS', async () => {
    flowerRepo.seed({
      ownerId: OWNER,
      imageKey: 'k1',
      takenAt: new Date(),
      location: { type: 'Point', coordinates: [2.29, 48.85] },
      accuracyM: 5,
    });
    await service.create(OWNER, {
      friendId: VIEWER,
      scope: 'all',
      includeGps: false,
    });

    const visible = await service.sharedWithMe(VIEWER);
    expect(visible).toHaveLength(1);
    expect(visible[0].latitude).toBeNull();
    expect(visible[0].longitude).toBeNull();
  });

  it('notifie le destinataire à la création du partage', async () => {
    await service.create(OWNER, {
      friendId: VIEWER,
      scope: 'all',
    });

    expect(notify).toHaveBeenCalledTimes(1);
    expect(notify).toHaveBeenCalledWith(
      VIEWER,
      'flower_shared',
      expect.objectContaining({ fromUserId: OWNER, scope: 'all' }),
    );
  });

  it('partage une fleur avec GPS', async () => {
    const flower = flowerRepo.seed({
      ownerId: OWNER,
      imageKey: 'k2',
      takenAt: new Date(),
      location: { type: 'Point', coordinates: [2.29, 48.85] },
      accuracyM: 5,
    });
    await service.create(OWNER, {
      friendId: VIEWER,
      scope: 'flower',
      flowerId: flower.id,
      includeGps: true,
    });

    const visible = await service.sharedWithMe(VIEWER);
    expect(visible).toHaveLength(1);
    expect(visible[0].latitude).toBe(48.85);
    expect(visible[0].longitude).toBe(2.29);
  });
});
