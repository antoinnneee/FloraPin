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
          useValue: { create: async () => undefined },
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

  it('liste les relations avec direction', async () => {
    await service.request(ALICE, BOB);
    const aliceList = await service.list(ALICE);
    const bobList = await service.list(BOB);
    expect(aliceList[0].direction).toBe('outgoing');
    expect(bobList[0].direction).toBe('incoming');
  });
});
