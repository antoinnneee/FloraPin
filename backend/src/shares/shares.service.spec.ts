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

/** Compare deux valeurs (Date comparées par temps) pour tri/opérateurs. */
function cmp(a: unknown, b: unknown): number {
  const av = a instanceof Date ? a.getTime() : a;
  const bv = b instanceof Date ? b.getTime() : b;
  if (av === bv) return 0;
  return (av as number) < (bv as number) ? -1 : 1;
}

/**
 * Teste une valeur de colonne contre une contrainte : opérateur TypeORM
 * (In/LessThan, reconnus via leurs getters `type`/`value`), Date (égalité par
 * temps) ou valeur brute. Couvre les besoins keyset du service (TÂCHE 1.2).
 */
function matchField(actual: unknown, expected: unknown): boolean {
  if (
    expected !== null &&
    typeof expected === 'object' &&
    'type' in (expected as Record<string, unknown>) &&
    'value' in (expected as Record<string, unknown>)
  ) {
    const op = expected as { type: string; value: unknown };
    if (op.type === 'in') return (op.value as unknown[]).includes(actual);
    if (op.type === 'lessThan') return cmp(actual, op.value) < 0;
    return false;
  }
  if (expected instanceof Date || actual instanceof Date) {
    return cmp(actual, expected) === 0;
  }
  return actual === expected;
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
  /**
   * Reproduit `Repository.find` pour les usages du service : `where` objet OU
   * tableau (OR), opérateurs In/LessThan, `order` multi-colonnes et `take`.
   */
  async find(opts: {
    where?: Record<string, unknown> | Record<string, unknown>[];
    order?: Record<string, 'ASC' | 'DESC'>;
    take?: number;
  }): Promise<Flower[]> {
    const clauses = opts.where
      ? Array.isArray(opts.where)
        ? opts.where
        : [opts.where]
      : [{}];
    let rows = [...this.store.values()].filter((f) =>
      clauses.some((clause) =>
        Object.entries(clause).every(([k, v]) =>
          matchField((f as unknown as Record<string, unknown>)[k], v),
        ),
      ),
    );
    if (opts.order) {
      const entries = Object.entries(opts.order);
      rows = rows.sort((a, b) => {
        for (const [k, dir] of entries) {
          const c = cmp(
            (a as unknown as Record<string, unknown>)[k],
            (b as unknown as Record<string, unknown>)[k],
          );
          if (c !== 0) return dir === 'DESC' ? -c : c;
        }
        return 0;
      });
    }
    if (opts.take != null) rows = rows.slice(0, opts.take);
    return rows;
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
        Object.entries(opts.where).every(([k, v]) => {
          const actual = (s as unknown as Record<string, unknown>)[k];
          // IsNull() de TypeORM arrive comme un FindOperator (objet) : on le
          // traite comme un test « colonne IS NULL ».
          if (v !== null && typeof v === 'object') return actual == null;
          return actual === v;
        }),
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
          useValue: { count: async () => 0, find: async () => [] },
        },
        { provide: StorageService, useClass: StubStorageService },
        {
          provide: FriendshipsService,
          useValue: { acceptedFriendIds: async () => acceptedFriends },
        },
        {
          provide: NotificationsService,
          // Le partage notifie via createSafe (best-effort) ; on branche les deux
          // sur le même mock pour conserver les assertions `notify`.
          useValue: { create: notify, createSafe: notify },
        },
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

  it('broadcast : pagine avec curseur + limite (keyset date DESC)', async () => {
    const FRIEND = 'friend';
    acceptedFriends = [FRIEND];
    const mk = (key: string, iso: string) =>
      flowerRepo.seed({
        ownerId: FRIEND,
        imageKey: key,
        takenAt: new Date(iso),
        createdAt: new Date(iso),
        visibility: 'friends',
      });
    const f1 = mk('p1', '2026-06-20T10:00:00Z');
    const f2 = mk('p2', '2026-06-21T10:00:00Z');
    const f3 = mk('p3', '2026-06-22T10:00:00Z');

    // Page 1 : les 2 plus récentes.
    const page1 = await service.broadcastWithMe(VIEWER, undefined, 2);
    expect(page1.map((f) => f.id)).toEqual([f3.id, f2.id]);

    // Page 2 : curseur = dernière de la page 1 → suivantes strictement plus vieilles.
    const cursor = { createdAt: new Date('2026-06-21T10:00:00Z'), id: f2.id };
    const page2 = await service.broadcastWithMe(VIEWER, cursor, 2);
    expect(page2.map((f) => f.id)).toEqual([f1.id]);
  });

  it('sharedWithMe (scope all) : borne la page au curseur + limite', async () => {
    const mk = (key: string, iso: string) =>
      flowerRepo.seed({
        ownerId: OWNER,
        imageKey: key,
        takenAt: new Date(iso),
        createdAt: new Date(iso),
      });
    const f1 = mk('s1', '2026-06-20T10:00:00Z');
    const f2 = mk('s2', '2026-06-21T10:00:00Z');
    const f3 = mk('s3', '2026-06-22T10:00:00Z');
    await service.create(OWNER, { friendId: VIEWER, scope: 'all' });

    const cursor = { createdAt: f3.createdAt, id: f3.id };
    const page = await service.sharedWithMe(VIEWER, cursor, 1);
    // Curseur = f3 (la plus récente) exclue → f2 en tête, limité à 1.
    expect(page.map((f) => f.id)).toEqual([f2.id]);
    // Deuxième page suit f2.
    const page2 = await service.sharedWithMe(
      VIEWER,
      { createdAt: new Date('2026-06-21T10:00:00Z'), id: f2.id },
      1,
    );
    expect(page2.map((f) => f.id)).toEqual([f1.id]);
  });

  describe('isVisibleTo (périmètre commentaires/cœurs)', () => {
    it('le propriétaire voit toujours sa fleur', async () => {
      const flower = flowerRepo.seed({
        ownerId: OWNER,
        imageKey: 'v1',
        takenAt: new Date(),
      });
      expect(await service.isVisibleTo(OWNER, flower)).toBe(true);
    });

    it('un destinataire de partage ciblé voit la fleur', async () => {
      const flower = flowerRepo.seed({
        ownerId: OWNER,
        imageKey: 'v2',
        takenAt: new Date(),
      });
      await service.create(OWNER, {
        friendId: VIEWER,
        scope: 'flower',
        flowerId: flower.id,
      });
      expect(await service.isVisibleTo(VIEWER, flower)).toBe(true);
    });

    it('un ami voit une fleur diffusée au réseau (visibility=friends)', async () => {
      const FRIEND = 'friend';
      acceptedFriends = [FRIEND];
      const flower = flowerRepo.seed({
        ownerId: FRIEND,
        imageKey: 'v3',
        takenAt: new Date(),
        visibility: 'friends',
      });
      expect(await service.isVisibleTo(VIEWER, flower)).toBe(true);
    });

    it('un tiers sans partage ne voit pas la fleur', async () => {
      const flower = flowerRepo.seed({
        ownerId: OWNER,
        imageKey: 'v4',
        takenAt: new Date(),
      });
      expect(await service.isVisibleTo('etranger', flower)).toBe(false);
    });
  });

  it('ré-partage le même périmètre : met à jour le GPS sans conflit', async () => {
    const flower = flowerRepo.seed({
      ownerId: OWNER,
      imageKey: 'k3',
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
    // Re-partage identique en basculant le GPS : pas de 409, on met à jour.
    await service.create(OWNER, {
      friendId: VIEWER,
      scope: 'flower',
      flowerId: flower.id,
      includeGps: false,
    });

    const mine = await service.listMine(OWNER);
    expect(mine).toHaveLength(1);
    expect(mine[0].includeGps).toBe(false);
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
