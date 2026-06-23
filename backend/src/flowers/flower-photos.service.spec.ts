import { NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { StorageService } from '../storage/storage.service';
import { StubStorageService } from '../storage/stub-storage.service';
import { FlowerPhoto } from './flower-photo.entity';
import { FlowerPhotosService } from './flower-photos.service';
import { Flower } from './flower.entity';

class FakeFlowerRepo {
  store = new Map<string, Flower>();
  seed(ownerId: string): Flower {
    const f = { id: randomUUID(), ownerId, imageKey: 'cover' } as Flower;
    this.store.set(f.id, f);
    return f;
  }
  async findOne(opts: { where: { id: string; ownerId: string } }) {
    const f = this.store.get(opts.where.id);
    return f && f.ownerId === opts.where.ownerId ? f : null;
  }
  async update(id: string, patch: Partial<Flower>) {
    const f = this.store.get(id);
    if (f) this.store.set(id, { ...f, ...patch });
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
  async find(opts: {
    where: { flowerId: string };
    order?: { position: 'ASC' };
  }): Promise<FlowerPhoto[]> {
    const list = [...this.store.values()].filter(
      (p) => p.flowerId === opts.where.flowerId,
    );
    return opts.order ? list.sort((a, b) => a.position - b.position) : list;
  }
  async findOne(opts: { where: { id: string; flowerId: string } }) {
    const p = this.store.get(opts.where.id);
    return p && p.flowerId === opts.where.flowerId ? p : null;
  }
  async remove(obj: FlowerPhoto) {
    this.store.delete(obj.id);
    return obj;
  }
  async update(
    criteria: string | { flowerId: string },
    patch: Partial<FlowerPhoto>,
  ) {
    for (const [id, p] of this.store) {
      const match =
        typeof criteria === 'string'
          ? id === criteria
          : p.flowerId === criteria.flowerId;
      if (match) this.store.set(id, { ...p, ...patch });
    }
  }
}

const OWNER = 'owner-1';

describe('FlowerPhotosService', () => {
  let service: FlowerPhotosService;
  let flowers: FakeFlowerRepo;
  let photos: FakePhotoRepo;

  beforeEach(async () => {
    flowers = new FakeFlowerRepo();
    photos = new FakePhotoRepo();
    const moduleRef = await Test.createTestingModule({
      providers: [
        FlowerPhotosService,
        { provide: getRepositoryToken(Flower), useValue: flowers },
        { provide: getRepositoryToken(FlowerPhoto), useValue: photos },
        { provide: StorageService, useClass: StubStorageService },
      ],
    }).compile();
    service = moduleRef.get(FlowerPhotosService);
  });

  it('ajoute une première photo (couverture) avec URL d’upload', async () => {
    const flower = flowers.seed(OWNER);
    const result = await service.add(OWNER, flower.id);

    expect(result.upload.method).toBe('PUT');
    expect(result.photo.isCover).toBe(true);
    expect(result.photo.position).toBe(0);
  });

  it('ajoute une seconde photo non-couverture, position incrémentée', async () => {
    const flower = flowers.seed(OWNER);
    await service.add(OWNER, flower.id);
    const second = await service.add(OWNER, flower.id);

    expect(second.photo.isCover).toBe(false);
    expect(second.photo.position).toBe(1);
  });

  it('change la couverture et met à jour image_key de la fleur', async () => {
    const flower = flowers.seed(OWNER);
    await service.add(OWNER, flower.id);
    const second = await service.add(OWNER, flower.id);

    const list = await service.setCover(OWNER, flower.id, second.photo.id);

    expect(list.find((p) => p.id === second.photo.id)?.isCover).toBe(true);
    expect(list.filter((p) => p.isCover)).toHaveLength(1);
    expect(flowers.store.get(flower.id)!.imageKey).toBe(
      photos.store.get(second.photo.id)!.imageKey,
    );
  });

  it('promeut une nouvelle couverture quand on supprime la couverture', async () => {
    const flower = flowers.seed(OWNER);
    const first = await service.add(OWNER, flower.id);
    const second = await service.add(OWNER, flower.id);

    await service.remove(OWNER, flower.id, first.photo.id);

    const list = await service.list(flower.id);
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe(second.photo.id);
    expect(list[0].isCover).toBe(true);
  });

  it('réordonne les photos', async () => {
    const flower = flowers.seed(OWNER);
    const a = await service.add(OWNER, flower.id);
    const b = await service.add(OWNER, flower.id);

    const list = await service.reorder(OWNER, flower.id, [b.photo.id, a.photo.id]);

    expect(list.map((p) => p.id)).toEqual([b.photo.id, a.photo.id]);
    expect(list.map((p) => p.position)).toEqual([0, 1]);
  });

  it("refuse d'agir sur la fleur d'un autre propriétaire", async () => {
    const flower = flowers.seed(OWNER);
    await expect(service.add('autre', flower.id)).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});
