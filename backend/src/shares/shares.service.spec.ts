import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Album } from '../albums/album.entity';
import { Flower } from '../flowers/flower.entity';
import { FlowerPhoto } from '../flowers/flower-photo.entity';
import { FlowersService } from '../flowers/flowers.service';
import { FlowerLike } from '../likes/flower-like.entity';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { StorageService } from '../storage/storage.service';
import { StubStorageService } from '../storage/stub-storage.service';
import { Share } from './share.entity';
import { SharesService } from './shares.service';

/** Détecte un opérateur TypeORM `In([...])` (porte sa valeur dans `.value`). */
function isInOperator(value: unknown): value is { value: string[] } {
  return (
    typeof value === 'object' &&
    value !== null &&
    Array.isArray((value as { value?: unknown }).value)
  );
}

class FakeFlowerRepo {
  store = new Map<string, Flower>();
  seed(f: Partial<Flower>): Flower {
    const flower = {
      id: randomUUID(),
      notes: '',
      visibility: 'private',
      feedIncludeGps: true,
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
  async find(opts: {
    where: { ownerId?: unknown; visibility?: string };
  }): Promise<Flower[]> {
    const { ownerId, visibility } = opts.where;
    return [...this.store.values()].filter((f) => {
      const ownerOk =
        ownerId === undefined
          ? true
          : isInOperator(ownerId)
            ? ownerId.value.includes(f.ownerId)
            : f.ownerId === ownerId;
      const visOk = visibility === undefined || f.visibility === visibility;
      return ownerOk && visOk;
    });
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

class FakeAlbumRepo {
  store = new Map<string, Album>();
  seed(ownerId: string, flowers: Flower[]): Album {
    const album = { id: randomUUID(), ownerId, name: 'Album', flowers } as Album;
    this.store.set(album.id, album);
    return album;
  }
  async findOne(opts: {
    where: { id?: string; ownerId?: string };
  }): Promise<Album | null> {
    const found = [...this.store.values()].find(
      (a) =>
        (opts.where.id === undefined || a.id === opts.where.id) &&
        (opts.where.ownerId === undefined || a.ownerId === opts.where.ownerId),
    );
    return found ? { ...found, flowers: [...found.flowers] } : null;
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
  let albumRepo: FakeAlbumRepo;
  let acceptedFriends: string[];
  let notify: jest.Mock;

  beforeEach(async () => {
    flowerRepo = new FakeFlowerRepo();
    albumRepo = new FakeAlbumRepo();
    acceptedFriends = [VIEWER];
    notify = jest.fn();

    const moduleRef = await Test.createTestingModule({
      providers: [
        SharesService,
        FlowersService,
        { provide: getRepositoryToken(Share), useClass: FakeShareRepo },
        { provide: getRepositoryToken(Flower), useValue: flowerRepo },
        { provide: getRepositoryToken(Album), useValue: albumRepo },
        {
          provide: getRepositoryToken(FlowerPhoto),
          useValue: { find: async () => [] },
        },
        {
          provide: getRepositoryToken(FlowerLike),
          useValue: { count: async () => 0 },
        },
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

  it("refuse le partage d'un album inexistant", async () => {
    await expect(
      service.create(OWNER, {
        friendId: VIEWER,
        scope: 'album',
        albumId: randomUUID(),
      }),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it("partage les fleurs d'un album", async () => {
    const f1 = flowerRepo.seed({ ownerId: OWNER, imageKey: 'a1', takenAt: new Date() });
    const f2 = flowerRepo.seed({ ownerId: OWNER, imageKey: 'a2', takenAt: new Date() });
    const album = albumRepo.seed(OWNER, [f1, f2]);

    await service.create(OWNER, {
      friendId: VIEWER,
      scope: 'album',
      albumId: album.id,
    });

    const visible = await service.sharedWithMe(VIEWER);
    expect(visible.map((f) => f.id).sort()).toEqual([f1.id, f2.id].sort());
  });

  it('diffuse au feed les fleurs « friends » des amis (broadcast)', async () => {
    const FRIEND = 'friend';
    acceptedFriends = [FRIEND];
    flowerRepo.seed({
      ownerId: FRIEND,
      imageKey: 'b1',
      takenAt: new Date(),
      visibility: 'friends',
      location: { type: 'Point', coordinates: [2.29, 48.85] },
      accuracyM: 5,
    });
    // Une fleur privée du même ami n'est pas diffusée.
    flowerRepo.seed({
      ownerId: FRIEND,
      imageKey: 'b2',
      takenAt: new Date(),
      visibility: 'private',
    });

    const feed = await service.broadcastWithMe(VIEWER);
    expect(feed).toHaveLength(1);
    expect(feed[0].latitude).toBe(48.85);
  });

  it('broadcast : masque le GPS si feedIncludeGps=false', async () => {
    const FRIEND = 'friend';
    acceptedFriends = [FRIEND];
    flowerRepo.seed({
      ownerId: FRIEND,
      imageKey: 'b3',
      takenAt: new Date(),
      visibility: 'friends',
      location: { type: 'Point', coordinates: [2.29, 48.85] },
      accuracyM: 5,
      feedIncludeGps: false,
    });

    const feed = await service.broadcastWithMe(VIEWER);
    expect(feed).toHaveLength(1);
    expect(feed[0].latitude).toBeNull();
    expect(feed[0].longitude).toBeNull();
  });

  it('broadcast vide sans amis acceptés', async () => {
    acceptedFriends = [];
    const feed = await service.broadcastWithMe(VIEWER);
    expect(feed).toEqual([]);
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
