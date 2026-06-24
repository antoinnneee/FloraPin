import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { FlowerPhoto } from '../flowers/flower-photo.entity';
import { FlowersService } from '../flowers/flowers.service';
import { FlowerLike } from '../likes/flower-like.entity';
import { StorageService } from '../storage/storage.service';
import { StubStorageService } from '../storage/stub-storage.service';
import { SyncService } from './sync.service';

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

  // Filtre par owner ; ignore les opérateurs de date (suffisant pour le test).
  async find(opts: {
    where: { ownerId: string };
    withDeleted?: boolean;
  }): Promise<Flower[]> {
    if (opts.withDeleted) return [];
    return [...this.store.values()].filter(
      (f) => f.ownerId === opts.where.ownerId,
    );
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
  async find(opts: { where: { flowerId: string } }): Promise<FlowerPhoto[]> {
    return [...this.store.values()].filter(
      (p) => p.flowerId === opts.where.flowerId,
    );
  }
}

const OWNER = 'owner-1';

describe('SyncService', () => {
  let sync: SyncService;
  let repo: FakeFlowerRepo;

  beforeEach(async () => {
    repo = new FakeFlowerRepo();
    const moduleRef = await Test.createTestingModule({
      providers: [
        SyncService,
        FlowersService,
        { provide: getRepositoryToken(Flower), useValue: repo },
        { provide: getRepositoryToken(FlowerPhoto), useClass: FakePhotoRepo },
        {
          provide: getRepositoryToken(FlowerLike),
          useValue: { count: async () => 0 },
        },
        { provide: StorageService, useClass: StubStorageService },
      ],
    }).compile();
    sync = moduleRef.get(SyncService);
  });

  it('pousse un lot de captures et renvoie le mapping localId', async () => {
    const results = await sync.push(OWNER, [
      { localId: 'L1', takenAt: '2026-06-21T09:00:00Z' },
      {
        localId: 'L2',
        takenAt: '2026-06-21T09:05:00Z',
        latitude: 48.85,
        longitude: 2.29,
      },
    ]);

    expect(results.map((r) => r.localId)).toEqual(['L1', 'L2']);
    expect(results[0].upload.method).toBe('PUT');
    expect(results[1].flower.latitude).toBe(48.85);
    expect(repo.store.size).toBe(2);
  });

  it('tire toutes les fleurs au premier pull (sans since)', async () => {
    await sync.push(OWNER, [{ localId: 'L1', takenAt: '2026-06-21T09:00:00Z' }]);

    const result = await sync.pull(OWNER);
    expect(result.flowers).toHaveLength(1);
    expect(result.deletedIds).toEqual([]);
    expect(typeof result.serverTime).toBe('string');
  });
});
