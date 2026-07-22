import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { NotificationsService } from '../notifications/notifications.service';
import { UsersService } from '../users/users.service';
import { Friendship } from './friendship.entity';
import { FriendshipsService } from './friendships.service';

type Cond = Partial<Friendship>;

class FakeFriendshipRepo {
  store = new Map<string, Friendship>();

  create(obj: Partial<Friendship>): Friendship {
    return { ...obj } as Friendship;
  }

  async save(obj: Friendship): Promise<Friendship> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    obj.updatedAt = new Date();
    this.store.set(obj.id, { ...obj });
    return obj;
  }

  async findOne(opts: { where: Cond | Cond[] }): Promise<Friendship | null> {
    const conds = Array.isArray(opts.where) ? opts.where : [opts.where];
    return (
      [...this.store.values()].find((e) =>
        conds.some((c) => matches(e, c)),
      ) ?? null
    );
  }

  async find(opts: { where: Cond | Cond[] }): Promise<Friendship[]> {
    const conds = Array.isArray(opts.where) ? opts.where : [opts.where];
    return [...this.store.values()].filter((e) =>
      conds.some((c) => matches(e, c)),
    );
  }

  async remove(obj: Friendship): Promise<Friendship> {
    this.store.delete(obj.id);
    return obj;
  }
}

function matches(entity: Friendship, cond: Cond): boolean {
  return Object.entries(cond).every(
    ([k, v]) => (entity as unknown as Record<string, unknown>)[k] === v,
  );
}

class FakeUsersService {
  known = new Set<string>();
  async findById(id: string) {
    if (!this.known.has(id)) return null;
    return { id, displayName: `User ${id}`, email: `${id}@test` };
  }
  async findByEmail(email: string) {
    const id = [...this.known].find(
      (k) => `${k}@test` === email.trim().toLowerCase(),
    );
    return id ? this.findById(id) : null;
  }
  async avatarUrl(user: { id: string }) {
    return `https://avatars.test/${user.id}`;
  }
}

const ALICE = 'alice';
const BOB = 'bob';

describe('FriendshipsService', () => {
  let service: FriendshipsService;
  let repo: FakeFriendshipRepo;
  let users: FakeUsersService;

  beforeEach(async () => {
    repo = new FakeFriendshipRepo();
    users = new FakeUsersService();
    users.known.add(ALICE);
    users.known.add(BOB);

    const moduleRef = await Test.createTestingModule({
      providers: [
        FriendshipsService,
        { provide: getRepositoryToken(Friendship), useValue: repo },
        { provide: UsersService, useValue: users },
        {
          provide: NotificationsService,
          useValue: {
            create: async () => undefined,
            createSafe: async () => undefined,
          },
        },
      ],
    }).compile();
    service = moduleRef.get(FriendshipsService);
  });

  it('crée une demande en attente', async () => {
    const res = await service.request(ALICE, BOB);
    expect(res.status).toBe('pending');
    expect(res.direction).toBe('outgoing');
    expect(res.user.id).toBe(BOB);
    expect(res.user.avatarUrl).toBe(`https://avatars.test/${BOB}`);
  });

  it('invite par email (résout l’utilisateur)', async () => {
    const res = await service.requestByEmail(ALICE, 'BOB@test');
    expect(res.status).toBe('pending');
    expect(res.user.id).toBe(BOB);
  });

  it('invite par email : email inconnu → réponse générique sans ligne (anti-énumération)', async () => {
    const res = await service.requestByEmail(ALICE, 'ghost@test');
    // Même forme que le cas nominal : on ne révèle pas l'inexistence du compte.
    expect(res.status).toBe('pending');
    expect(res.direction).toBe('outgoing');
    expect(res.user.email).toBe('ghost@test');
    expect(res.user.displayName).toBe('');
    // Aucune relation créée en base.
    expect(repo.store.size).toBe(0);
    expect(await service.list(ALICE)).toEqual([]);
  });

  it('refuse l’auto-ajout', async () => {
    await expect(service.request(ALICE, ALICE)).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('refuse un destinataire inconnu', async () => {
    await expect(service.request(ALICE, 'ghost')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it('refuse une relation en double (sens inverse)', async () => {
    await service.request(ALICE, BOB);
    await expect(service.request(BOB, ALICE)).rejects.toBeInstanceOf(
      ConflictException,
    );
  });

  it('le destinataire accepte, le demandeur ne peut pas', async () => {
    const req = await service.request(ALICE, BOB);
    await expect(service.accept(ALICE, req.id)).rejects.toBeInstanceOf(
      ForbiddenException,
    );
    const accepted = await service.accept(BOB, req.id);
    expect(accepted.status).toBe('accepted');
    expect(await service.acceptedFriendIds(ALICE)).toEqual([BOB]);
    expect(await service.acceptedFriendIds(BOB)).toEqual([ALICE]);
  });

  it('ajout par id : crée une demande en attente', async () => {
    const res = await service.requestById(ALICE, BOB);
    expect(res.status).toBe('pending');
    expect(res.direction).toBe('outgoing');
    expect(res.user.id).toBe(BOB);
  });

  it('ajout par id : refuse l’auto-ajout', async () => {
    await expect(service.requestById(ALICE, ALICE)).rejects.toBeInstanceOf(
      BadRequestException,
    );
  });

  it('ajout par id : refuse un utilisateur inconnu', async () => {
    await expect(service.requestById(ALICE, 'ghost')).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });

  it('ajout par id : acceptation croisée si chacun scanne l’autre', async () => {
    // Bob a scanné le QR d'Alice → demande Bob → Alice.
    const req = await service.requestById(BOB, ALICE);
    expect(req.status).toBe('pending');
    // Alice scanne à son tour le QR de Bob : consentement mutuel → accepté.
    const res = await service.requestById(ALICE, BOB);
    expect(res.status).toBe('accepted');
    // Aucune relation en double : la même ligne a basculé en 'accepted'.
    expect(repo.store.size).toBe(1);
    expect(await service.acceptedFriendIds(ALICE)).toEqual([BOB]);
    expect(await service.acceptedFriendIds(BOB)).toEqual([ALICE]);
  });

  it('ajout par id : re-scan de ma propre demande sortante est sans effet', async () => {
    const first = await service.requestById(ALICE, BOB);
    const again = await service.requestById(ALICE, BOB);
    expect(again.id).toBe(first.id);
    expect(again.status).toBe('pending');
    expect(repo.store.size).toBe(1);
  });

  it('ajout par id : re-scan quand déjà amis est sans effet (pas de 409)', async () => {
    const req = await service.requestById(ALICE, BOB);
    await service.accept(BOB, req.id);
    const res = await service.requestById(ALICE, BOB);
    expect(res.status).toBe('accepted');
    expect(repo.store.size).toBe(1);
  });

  it('liste les relations avec direction', async () => {
    await service.request(ALICE, BOB);
    const aliceList = await service.list(ALICE);
    const bobList = await service.list(BOB);
    expect(aliceList[0].direction).toBe('outgoing');
    expect(bobList[0].direction).toBe('incoming');
  });
});
