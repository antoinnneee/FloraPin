import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { DeviceToken } from './device-token.entity';
import { DeviceTokensService } from './device-tokens.service';

class FakeDeviceRepo {
  store = new Map<string, DeviceToken>();

  create(obj: Partial<DeviceToken>): DeviceToken {
    return { ...obj } as DeviceToken;
  }
  async save(obj: DeviceToken): Promise<DeviceToken> {
    if (!obj.id) obj.id = randomUUID();
    obj.createdAt ??= new Date();
    this.store.set(obj.id, { ...obj });
    return obj;
  }
  async findOne(opts: { where: { token: string } }): Promise<DeviceToken | null> {
    return (
      [...this.store.values()].find((d) => d.token === opts.where.token) ?? null
    );
  }
  async find(opts: { where: { userId: string } }): Promise<DeviceToken[]> {
    return [...this.store.values()].filter(
      (d) => d.userId === opts.where.userId,
    );
  }
  async delete(criteria: { token: string; userId?: string }): Promise<void> {
    for (const [id, d] of this.store) {
      const tokenOk = d.token === criteria.token;
      const userOk =
        criteria.userId === undefined || d.userId === criteria.userId;
      if (tokenOk && userOk) this.store.delete(id);
    }
  }
}

const USER = 'user-1';

describe('DeviceTokensService', () => {
  let service: DeviceTokensService;

  beforeEach(async () => {
    const moduleRef = await Test.createTestingModule({
      providers: [
        DeviceTokensService,
        { provide: getRepositoryToken(DeviceToken), useClass: FakeDeviceRepo },
      ],
    }).compile();
    service = moduleRef.get(DeviceTokensService);
  });

  it('enregistre puis liste les jetons d’un utilisateur', async () => {
    await service.register(USER, 'tok-a', 'android');
    await service.register(USER, 'tok-b', 'ios');
    expect((await service.tokensFor(USER)).sort()).toEqual(['tok-a', 'tok-b']);
  });

  it('ré-attribue un jeton existant au nouvel utilisateur', async () => {
    await service.register('ancien', 'tok-a', 'android');
    await service.register(USER, 'tok-a', 'android');
    expect(await service.tokensFor('ancien')).toEqual([]);
    expect(await service.tokensFor(USER)).toEqual(['tok-a']);
  });

  it('désenregistre un jeton', async () => {
    await service.register(USER, 'tok-a', 'android');
    await service.unregister(USER, 'tok-a');
    expect(await service.tokensFor(USER)).toEqual([]);
  });

  it('ne désenregistre pas le jeton d’un autre utilisateur (I2)', async () => {
    await service.register(USER, 'tok-a', 'android');
    await service.unregister('intrus', 'tok-a');
    expect(await service.tokensFor(USER)).toEqual(['tok-a']);
  });

  it('purge un jeton invalide quel que soit son propriétaire (FCM)', async () => {
    await service.register(USER, 'tok-a', 'android');
    await service.purgeToken('tok-a');
    expect(await service.tokensFor(USER)).toEqual([]);
  });
});
