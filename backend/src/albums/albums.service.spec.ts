import { NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { Album } from './album.entity';
import { AlbumsService } from './albums.service';

class FakeAlbumRepo {
  store = new Map<string, Album>();

  create(obj: Partial<Album>): Album {
    return { flowers: [], ...obj } as Album;
  }

  async save(obj: Album): Promise<Album> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    obj.flowers ??= [];
    this.store.set(obj.id, { ...obj, flowers: [...obj.flowers] });
    return obj;
  }

  async findOne(opts: {
    where: { id?: string; ownerId: string; clientId?: string };
  }): Promise<Album | null> {
    // Recherche par id (cas courant) ou par clientId (idempotence de create).
    const found = opts.where.id
      ? this.store.get(opts.where.id)
      : [...this.store.values()].find(
          (a) => a.clientId === opts.where.clientId,
        );
    if (!found || found.ownerId !== opts.where.ownerId) return null;
    return { ...found, flowers: [...found.flowers] };
  }

  async find(opts: { where: { ownerId: string } }): Promise<Album[]> {
    return [...this.store.values()]
      .filter((a) => a.ownerId === opts.where.ownerId)
      .map((a) => ({ ...a, flowers: [...a.flowers] }));
  }

  async remove(obj: Album): Promise<Album> {
    this.store.delete(obj.id);
    return obj;
  }
}

class FakeFlowerRepo {
  store = new Map<string, Flower>();

  seed(ownerId: string): Flower {
    const flower = { id: randomUUID(), ownerId } as Flower;
    this.store.set(flower.id, flower);
    return flower;
  }

  async findOne(opts: {
    where: { id: string; ownerId: string };
  }): Promise<Flower | null> {
    const found = this.store.get(opts.where.id);
    if (!found || found.ownerId !== opts.where.ownerId) return null;
    return found;
  }
}

const OWNER = 'owner-1';

describe('AlbumsService', () => {
  let service: AlbumsService;
  let albums: FakeAlbumRepo;
  let flowers: FakeFlowerRepo;

  beforeEach(async () => {
    albums = new FakeAlbumRepo();
    flowers = new FakeFlowerRepo();
    const moduleRef = await Test.createTestingModule({
      providers: [
        AlbumsService,
        { provide: getRepositoryToken(Album), useValue: albums },
        { provide: getRepositoryToken(Flower), useValue: flowers },
      ],
    }).compile();
    service = moduleRef.get(AlbumsService);
  });

  it('crée puis liste un album', async () => {
    const created = await service.create(OWNER, { name: 'Printemps' });
    expect(created.name).toBe('Printemps');
    expect(created.flowerIds).toEqual([]);

    const list = await service.list(OWNER);
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe(created.id);
  });

  it('création idempotente : un même clientId ne crée pas de doublon', async () => {
    const clientId = randomUUID();
    const first = await service.create(OWNER, { name: 'Jardin', clientId });
    // Re-push (réponse perdue) : même clientId → renvoie l'album existant.
    const second = await service.create(OWNER, { name: 'Jardin', clientId });

    expect(second.id).toBe(first.id);
    expect(second.clientId).toBe(clientId);
    const list = await service.list(OWNER);
    expect(list).toHaveLength(1);
  });

  it('renomme un album', async () => {
    const created = await service.create(OWNER, { name: 'Ancien' });
    const renamed = await service.rename(OWNER, created.id, { name: 'Nouveau' });
    expect(renamed.name).toBe('Nouveau');
  });

  it('supprime un album', async () => {
    const created = await service.create(OWNER, { name: 'A' });
    await service.remove(OWNER, created.id);
    await expect(service.getById(OWNER, created.id)).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it("rattache et détache une fleur de l'album (idempotent)", async () => {
    const created = await service.create(OWNER, { name: 'Roses' });
    const flower = flowers.seed(OWNER);

    const withFlower = await service.addFlower(OWNER, created.id, flower.id);
    expect(withFlower.flowerIds).toEqual([flower.id]);

    // idempotent : un second ajout ne duplique pas
    const again = await service.addFlower(OWNER, created.id, flower.id);
    expect(again.flowerIds).toEqual([flower.id]);

    const without = await service.removeFlower(OWNER, created.id, flower.id);
    expect(without.flowerIds).toEqual([]);
  });

  it("refuse de rattacher la fleur d'un autre propriétaire", async () => {
    const created = await service.create(OWNER, { name: 'Roses' });
    const other = flowers.seed('autre');
    await expect(
      service.addFlower(OWNER, created.id, other.id),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it("renvoie 404 sur l'album d'un autre propriétaire", async () => {
    const created = await service.create(OWNER, { name: 'Roses' });
    await expect(service.getById('autre', created.id)).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});
