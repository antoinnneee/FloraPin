import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Flower } from '../flowers/flower.entity';
import { GroupsService } from '../groups/groups.service';
import { AlbumPermission } from './album-permission.entity';
import { Album } from './album.entity';
import { AlbumsService } from './albums.service';

class FakeAlbumRepo {
  store = new Map<string, Album>();

  create(obj: Partial<Album>): Album {
    return { flowers: [], permissionMode: 'open', ...obj } as Album;
  }

  async save(obj: Album): Promise<Album> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    obj.flowers ??= [];
    obj.permissionMode ??= 'open';
    this.store.set(obj.id, { ...obj, flowers: [...obj.flowers] });
    return obj;
  }

  async findOne(opts: {
    where: {
      id?: string;
      ownerId?: string;
      clientId?: string;
    };
  }): Promise<Album | null> {
    const w = opts.where;
    const found = w.id
      ? this.store.get(w.id)
      : [...this.store.values()].find((a) => a.clientId === w.clientId);
    if (!found) return null;
    if (w.ownerId != null && found.ownerId !== w.ownerId) return null;
    return { ...found, flowers: [...found.flowers] };
  }

  async find(opts: {
    where: { ownerId?: string; groupId?: { _value?: string[] } | string };
  }): Promise<Album[]> {
    const w = opts.where;
    return [...this.store.values()]
      .filter((a) => {
        if (w.ownerId != null) return a.ownerId === w.ownerId;
        if (w.groupId != null) {
          const ids =
            typeof w.groupId === 'object' ? (w.groupId._value ?? []) : [w.groupId];
          return a.groupId != null && ids.includes(a.groupId);
        }
        return true;
      })
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

class FakePermissionRepo {
  rows: AlbumPermission[] = [];

  create(obj: Partial<AlbumPermission>): AlbumPermission {
    return { canEdit: false, ...obj } as AlbumPermission;
  }

  async save(obj: AlbumPermission | AlbumPermission[]): Promise<unknown> {
    const list = Array.isArray(obj) ? obj : [obj];
    this.rows.push(...list);
    return obj;
  }

  async find(opts: {
    where: { albumId: string };
  }): Promise<AlbumPermission[]> {
    return this.rows.filter((r) => r.albumId === opts.where.albumId);
  }

  async findOne(opts: {
    where: { albumId: string; userId: string };
  }): Promise<AlbumPermission | null> {
    return (
      this.rows.find(
        (r) =>
          r.albumId === opts.where.albumId && r.userId === opts.where.userId,
      ) ?? null
    );
  }

  async delete(criteria: { albumId: string; userId?: string }): Promise<void> {
    this.rows = this.rows.filter((r) => {
      if (r.albumId !== criteria.albumId) return true;
      if (criteria.userId != null) return r.userId !== criteria.userId;
      return false;
    });
  }
}

/**
 * Faux GroupsService : appartenances et droits en mémoire, suffisant pour tester
 * la matrice de droits d'AlbumsService sans base.
 */
class FakeGroupsService {
  // groupId -> { ownerId, members: Set<userId accepté> }
  groups = new Map<string, { ownerId: string; accepted: Set<string> }>();

  seedGroup(ownerId: string): string {
    const id = randomUUID();
    this.groups.set(id, { ownerId, accepted: new Set([ownerId]) });
    return id;
  }

  addMember(groupId: string, userId: string): void {
    this.groups.get(groupId)?.accepted.add(userId);
  }

  async create(ownerId: string): Promise<{ id: string }> {
    return { id: this.seedGroup(ownerId) };
  }

  async requireAcceptedMember(userId: string, groupId: string): Promise<void> {
    if (!(await this.isAcceptedMember(userId, groupId))) {
      throw new ForbiddenException();
    }
  }

  async isAcceptedMember(userId: string, groupId: string): Promise<boolean> {
    return this.groups.get(groupId)?.accepted.has(userId) ?? false;
  }

  async acceptedMemberIds(groupId: string): Promise<string[]> {
    return [...(this.groups.get(groupId)?.accepted ?? [])];
  }

  async list(
    userId: string,
  ): Promise<{ id: string; status: string }[]> {
    return [...this.groups.entries()]
      .filter(([, g]) => g.accepted.has(userId))
      .map(([id]) => ({ id, status: 'accepted' }));
  }
}

const OWNER = 'owner-1';

describe('AlbumsService', () => {
  let service: AlbumsService;
  let albums: FakeAlbumRepo;
  let flowers: FakeFlowerRepo;
  let permissions: FakePermissionRepo;
  let groups: FakeGroupsService;

  beforeEach(async () => {
    albums = new FakeAlbumRepo();
    flowers = new FakeFlowerRepo();
    permissions = new FakePermissionRepo();
    groups = new FakeGroupsService();
    const moduleRef = await Test.createTestingModule({
      providers: [
        AlbumsService,
        { provide: getRepositoryToken(Album), useValue: albums },
        { provide: getRepositoryToken(Flower), useValue: flowers },
        { provide: getRepositoryToken(AlbumPermission), useValue: permissions },
        { provide: GroupsService, useValue: groups },
      ],
    }).compile();
    service = moduleRef.get(AlbumsService);
  });

  it('crée puis liste un album', async () => {
    const created = await service.create(OWNER, { name: 'Printemps' });
    expect(created.name).toBe('Printemps');
    expect(created.flowerIds).toEqual([]);
    expect(created.groupId).toBeNull();

    const list = await service.list(OWNER);
    expect(list).toHaveLength(1);
    expect(list[0].id).toBe(created.id);
  });

  it('création idempotente : un même clientId ne crée pas de doublon', async () => {
    const clientId = randomUUID();
    const first = await service.create(OWNER, { name: 'Jardin', clientId });
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

  // --- Albums collaboratifs (TÂCHE 7.1) ---

  it('collaborative:true crée le groupe et rattache l’album', async () => {
    const created = await service.create(OWNER, {
      name: 'Balade',
      collaborative: true,
    });
    expect(created.groupId).not.toBeNull();
    expect(created.permissionMode).toBe('open');
    expect(created.canEdit).toBe(true);
  });

  it('mode ouvert : un membre du groupe voit et édite l’album', async () => {
    const created = await service.create(OWNER, {
      name: 'Balade',
      collaborative: true,
    });
    const member = 'membre-1';
    groups.addMember(created.groupId!, member);

    // Visible dans sa liste.
    const list = await service.list(member);
    expect(list.map((a) => a.id)).toContain(created.id);

    // Peut éditer (ajouter SA fleur).
    const flower = flowers.seed(member);
    const withFlower = await service.addFlower(member, created.id, flower.id);
    expect(withFlower.flowerIds).toEqual([flower.id]);
    expect(withFlower.canEdit).toBe(true);
  });

  it('mode restreint : un membre sans droit voit mais ne peut pas éditer', async () => {
    const created = await service.create(OWNER, {
      name: 'Balade',
      collaborative: true,
    });
    const member = 'membre-1';
    groups.addMember(created.groupId!, member);
    await service.setPermissions(OWNER, created.id, {
      mode: 'restricted',
      entries: [],
    });

    // Voit toujours l'album…
    const seen = await service.getById(member, created.id);
    expect(seen.canEdit).toBe(false);

    // …mais ne peut pas ajouter de fleur.
    const flower = flowers.seed(member);
    await expect(
      service.addFlower(member, created.id, flower.id),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('mode restreint : un membre explicitement autorisé peut éditer', async () => {
    const created = await service.create(OWNER, {
      name: 'Balade',
      collaborative: true,
    });
    const member = 'membre-1';
    groups.addMember(created.groupId!, member);
    await service.setPermissions(OWNER, created.id, {
      mode: 'restricted',
      entries: [{ userId: member, canEdit: true }],
    });

    const flower = flowers.seed(member);
    const withFlower = await service.addFlower(member, created.id, flower.id);
    expect(withFlower.flowerIds).toEqual([flower.id]);
  });

  it('un non-membre ne voit pas l’album de groupe', async () => {
    const created = await service.create(OWNER, {
      name: 'Balade',
      collaborative: true,
    });
    await expect(
      service.getById('etranger', created.id),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('seul le propriétaire de l’album règle les droits', async () => {
    const created = await service.create(OWNER, {
      name: 'Balade',
      collaborative: true,
    });
    const member = 'membre-1';
    groups.addMember(created.groupId!, member);
    await expect(
      service.setPermissions(member, created.id, { mode: 'restricted' }),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });
});
