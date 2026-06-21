import { BadRequestException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { StorageService } from '../storage/storage.service';
import { StubStorageService } from '../storage/stub-storage.service';
import { Flower } from './flower.entity';
import { FlowersService } from './flowers.service';

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

  async findOne(opts: {
    where: { id: string; ownerId: string };
  }): Promise<Flower | null> {
    const found = this.store.get(opts.where.id);
    if (!found || found.ownerId !== opts.where.ownerId) return null;
    return found;
  }

  async find(opts: { where: { ownerId: string } }): Promise<Flower[]> {
    return [...this.store.values()].filter(
      (f) => f.ownerId === opts.where.ownerId,
    );
  }

  async softRemove(obj: Flower): Promise<Flower> {
    this.store.delete(obj.id);
    return obj;
  }
}

const OWNER = 'owner-1';

describe('FlowersService', () => {
  let service: FlowersService;
  let repo: FakeFlowerRepo;

  beforeEach(async () => {
    repo = new FakeFlowerRepo();
    const moduleRef = await Test.createTestingModule({
      providers: [
        FlowersService,
        { provide: getRepositoryToken(Flower), useValue: repo },
        { provide: StorageService, useClass: StubStorageService },
      ],
    }).compile();
    service = moduleRef.get(FlowersService);
  });

  it('crée une fleur géolocalisée et renvoie une URL d’upload', async () => {
    const result = await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
      latitude: 48.8584,
      longitude: 2.2945,
      accuracyM: 5,
    });

    expect(result.upload.method).toBe('PUT');
    expect(result.upload.url).toContain('upload=stub');
    expect(result.flower.latitude).toBe(48.8584);
    expect(result.flower.longitude).toBe(2.2945);
    expect(result.flower.imageUrl).toContain('download=stub');

    const stored = repo.store.get(result.flower.id)!;
    expect(stored.location).toEqual({
      type: 'Point',
      coordinates: [2.2945, 48.8584],
    });
    expect(stored.ownerId).toBe(OWNER);
  });

  it('crée une fleur sans position', async () => {
    const result = await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
    });
    expect(result.flower.latitude).toBeNull();
    expect(result.flower.longitude).toBeNull();
    expect(repo.store.get(result.flower.id)!.location).toBeNull();
  });

  it('refuse une latitude sans longitude', async () => {
    await expect(
      service.create(OWNER, {
        takenAt: '2026-06-21T09:00:00Z',
        latitude: 48.8584,
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('renvoie 404 pour une fleur d’un autre propriétaire', async () => {
    const result = await service.create(OWNER, {
      takenAt: '2026-06-21T09:00:00Z',
    });
    await expect(
      service.getById('autre', result.flower.id),
    ).rejects.toBeInstanceOf(NotFoundException);
  });
});
