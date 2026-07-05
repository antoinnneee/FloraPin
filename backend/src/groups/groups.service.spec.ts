import {
  BadRequestException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { AlbumPermission } from '../albums/album-permission.entity';
import { Album } from '../albums/album.entity';
import { FriendshipsService } from '../friendships/friendships.service';
import { NotificationsService } from '../notifications/notifications.service';
import { UsersService } from '../users/users.service';
import { Group } from './group.entity';
import { GroupMember } from './group-member.entity';
import { GroupsService } from './groups.service';

let seq = 0;

class FakeRepo<T extends object> {
  store = new Map<string, T>();

  create(obj: Partial<T>): T {
    return { ...obj } as T;
  }

  async save(obj: T): Promise<T> {
    const rec = obj as Record<string, unknown>;
    // Clé de stockage : `id` si présent (groupes/membres), sinon clé synthétique
    // pour les entités à clé composite (album_permissions).
    if (rec.id == null && rec.albumId == null) rec.id = randomUUID();
    rec.createdAt ??= new Date();
    rec.updatedAt ??= new Date();
    const key = (rec.id as string) ?? `k${seq++}`;
    this.store.set(key, { ...obj });
    return obj;
  }

  async findOne(opts: { where: Partial<T> }): Promise<T | null> {
    const entries = Object.entries(opts.where);
    const found = [...this.store.values()].find((row) =>
      entries.every(
        ([k, v]) => (row as Record<string, unknown>)[k] === v,
      ),
    );
    return found ? { ...found } : null;
  }

  async find(opts?: {
    where?: Partial<T>;
    order?: unknown;
  }): Promise<T[]> {
    const where = opts?.where ?? {};
    const entries = Object.entries(where);
    return [...this.store.values()]
      .filter((row) =>
        entries.every(
          ([k, v]) => (row as Record<string, unknown>)[k] === v,
        ),
      )
      .map((row) => ({ ...row }));
  }

  async remove(obj: T): Promise<T> {
    const id = (obj as Record<string, unknown>).id as string | undefined;
    if (id) this.store.delete(id);
    return obj;
  }

  async delete(criteria: Partial<T>): Promise<void> {
    const entries = Object.entries(criteria);
    for (const [id, row] of [...this.store.entries()]) {
      if (
        entries.every(([k, v]) => (row as Record<string, unknown>)[k] === v)
      ) {
        this.store.delete(id);
      }
    }
  }
}

describe('GroupsService', () => {
  let service: GroupsService;
  let groups: FakeRepo<Group>;
  let members: FakeRepo<GroupMember>;
  let albums: FakeRepo<Album>;
  let permissions: FakeRepo<AlbumPermission>;
  let friendsBetween: boolean;
  const notified: { userId: string; type: string }[] = [];

  const OWNER = 'owner-1';
  const FRIEND = 'friend-1';

  beforeEach(async () => {
    groups = new FakeRepo<Group>();
    members = new FakeRepo<GroupMember>();
    albums = new FakeRepo<Album>();
    permissions = new FakeRepo<AlbumPermission>();
    friendsBetween = true;
    notified.length = 0;

    const users = {
      findById: async (id: string) => ({ id, displayName: id }),
      findByIds: async (ids: string[]) =>
        ids.map((id) => ({ id, displayName: id })),
    };
    const friendships = {
      acceptedBetween: async () => (friendsBetween ? { id: 'f' } : null),
    };
    const notifications = {
      createSafe: async (userId: string, type: string) => {
        notified.push({ userId, type });
      },
    };

    const moduleRef = await Test.createTestingModule({
      providers: [
        GroupsService,
        { provide: getRepositoryToken(Group), useValue: groups },
        { provide: getRepositoryToken(GroupMember), useValue: members },
        { provide: getRepositoryToken(Album), useValue: albums },
        { provide: getRepositoryToken(AlbumPermission), useValue: permissions },
        { provide: UsersService, useValue: users },
        { provide: FriendshipsService, useValue: friendships },
        { provide: NotificationsService, useValue: notifications },
      ],
    }).compile();
    service = moduleRef.get(GroupsService);
  });

  it('crée un groupe avec le créateur en owner accepté', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    expect(group.ownerId).toBe(OWNER);
    expect(group.role).toBe('owner');
    expect(group.status).toBe('accepted');
    expect(group.members).toHaveLength(1);
  });

  it('création idempotente sur clientId', async () => {
    const clientId = randomUUID();
    const a = await service.create(OWNER, { name: 'B', clientId });
    const b = await service.create(OWNER, { name: 'B', clientId });
    expect(b.id).toBe(a.id);
    expect([...groups.store.values()]).toHaveLength(1);
  });

  it('invite un ami (pending) puis il accepte (accepted)', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    await service.invite(OWNER, group.id, { userId: FRIEND });
    expect(notified.some((n) => n.type === 'group_invited')).toBe(true);

    const afterInvite = await service.getById(FRIEND, group.id);
    expect(afterInvite.status).toBe('pending');

    await service.accept(FRIEND, group.id);
    expect(notified.some((n) => n.type === 'group_member_joined')).toBe(true);
    expect(await service.isAcceptedMember(FRIEND, group.id)).toBe(true);
  });

  it('refuse d’inviter un non-ami', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    friendsBetween = false;
    await expect(
      service.invite(OWNER, group.id, { userId: FRIEND }),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('refuse l’invitation par un non-membre', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    await expect(
      service.invite('etranger', group.id, { userId: FRIEND }),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('le propriétaire retire un membre', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    await service.invite(OWNER, group.id, { userId: FRIEND });
    await service.accept(FRIEND, group.id);
    await service.removeMember(OWNER, group.id, FRIEND);
    expect(await service.isAcceptedMember(FRIEND, group.id)).toBe(false);
  });

  it('un membre peut quitter le groupe (self-remove)', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    await service.invite(OWNER, group.id, { userId: FRIEND });
    await service.accept(FRIEND, group.id);
    await service.removeMember(FRIEND, group.id, FRIEND);
    expect(await service.isAcceptedMember(FRIEND, group.id)).toBe(false);
  });

  it('le propriétaire ne peut pas se retirer (doit supprimer le groupe)', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    await expect(
      service.removeMember(OWNER, group.id, OWNER),
    ).rejects.toBeInstanceOf(BadRequestException);
  });

  it('supprimer le groupe détache ses albums (groupId null)', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    const album = await albums.save({
      id: randomUUID(),
      ownerId: OWNER,
      groupId: group.id,
      permissionMode: 'restricted',
    } as Album);
    await service.remove(OWNER, group.id);
    const reloaded = await albums.findOne({ where: { id: album.id } as never });
    expect(reloaded?.groupId ?? null).toBeNull();
    expect([...groups.store.values()]).toHaveLength(0);
  });

  it('seul le propriétaire supprime le groupe', async () => {
    const group = await service.create(OWNER, { name: 'Balade' });
    await service.invite(OWNER, group.id, { userId: FRIEND });
    await service.accept(FRIEND, group.id);
    await expect(service.remove(FRIEND, group.id)).rejects.toBeInstanceOf(
      ForbiddenException,
    );
  });

  it('renvoie 404 sur un groupe inexistant', async () => {
    await expect(
      service.getById(OWNER, randomUUID()),
    ).rejects.toBeInstanceOf(NotFoundException);
  });
});
