import { NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { Notification } from './notification.entity';
import { NotificationsService } from './notifications.service';

class FakeNotifRepo {
  store = new Map<string, Notification>();

  create(obj: Partial<Notification>): Notification {
    return { ...obj } as Notification;
  }
  async save(obj: Notification): Promise<Notification> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date(Object.keys(this.store).length);
    this.store.set(obj.id, { ...obj });
    return obj;
  }
  async find(opts: { where: { userId: string } }): Promise<Notification[]> {
    return [...this.store.values()].filter(
      (n) => n.userId === opts.where.userId,
    );
  }
  async count(opts: { where: { userId: string } }): Promise<number> {
    return [...this.store.values()].filter(
      (n) => n.userId === opts.where.userId && n.readAt === null,
    ).length;
  }
  async findOne(opts: {
    where: { id: string; userId: string };
  }): Promise<Notification | null> {
    const found = this.store.get(opts.where.id);
    return found && found.userId === opts.where.userId ? found : null;
  }
}

const USER = 'user-1';

describe('NotificationsService', () => {
  let service: NotificationsService;
  let repo: FakeNotifRepo;

  beforeEach(async () => {
    repo = new FakeNotifRepo();
    const moduleRef = await Test.createTestingModule({
      providers: [
        NotificationsService,
        { provide: getRepositoryToken(Notification), useValue: repo },
      ],
    }).compile();
    service = moduleRef.get(NotificationsService);
  });

  it('crée une notification non lue et la compte', async () => {
    await service.create(USER, 'friend_request', { fromUserId: 'x' });
    expect(await service.unreadCount(USER)).toBe(1);
    const list = await service.list(USER);
    expect(list).toHaveLength(1);
    expect(list[0].type).toBe('friend_request');
  });

  it('marque comme lue et décrémente le compteur', async () => {
    const n = await service.create(USER, 'friend_accepted');
    await service.markRead(USER, n.id);
    expect(await service.unreadCount(USER)).toBe(0);
  });

  it('refuse de marquer la notification d’un autre', async () => {
    const n = await service.create(USER, 'friend_request');
    await expect(service.markRead('autre', n.id)).rejects.toBeInstanceOf(
      NotFoundException,
    );
  });
});
