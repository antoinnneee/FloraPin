import { ConflictException, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { User } from '../users/user.entity';
import { UsersService } from '../users/users.service';
import { AuthService } from './auth.service';
import { RefreshToken } from './refresh-token.entity';

/** UsersService factice en mémoire. */
class FakeUsersService {
  private byId = new Map<string, User>();

  async findByEmail(email: string): Promise<User | null> {
    const normalized = UsersService.normalizeEmail(email);
    return (
      [...this.byId.values()].find((u) => u.email === normalized) ?? null
    );
  }

  async findById(id: string): Promise<User | null> {
    return this.byId.get(id) ?? null;
  }

  async create(params: {
    email: string;
    passwordHash: string;
    displayName: string;
  }): Promise<User> {
    const user: User = {
      id: randomUUID(),
      email: UsersService.normalizeEmail(params.email),
      passwordHash: params.passwordHash,
      displayName: params.displayName,
      createdAt: new Date(),
      updatedAt: new Date(),
    };
    this.byId.set(user.id, user);
    return user;
  }
}

/** Repository RefreshToken factice. */
class FakeRefreshRepo {
  store = new Map<string, RefreshToken>();

  create(obj: Partial<RefreshToken>): RefreshToken {
    return { ...obj } as RefreshToken;
  }

  async save(obj: RefreshToken): Promise<RefreshToken> {
    this.store.set(obj.id, { ...obj });
    return obj;
  }

  async findOne(opts: { where: { id: string } }): Promise<RefreshToken | null> {
    return this.store.get(opts.where.id) ?? null;
  }

  async update(
    criteria: { id: string; revokedAt: unknown },
    partial: Partial<RefreshToken>,
  ): Promise<void> {
    const found = this.store.get(criteria.id);
    if (found && found.revokedAt === null) {
      this.store.set(criteria.id, { ...found, ...partial });
    }
  }
}

const CONFIG: Record<string, string> = {
  JWT_ACCESS_SECRET: 'test-access-secret',
  JWT_ACCESS_TTL: '900s',
  JWT_REFRESH_SECRET: 'test-refresh-secret',
  JWT_REFRESH_TTL: '30d',
};

class FakeConfigService {
  get<T>(key: string, def?: T): T {
    return (CONFIG[key] as unknown as T) ?? (def as T);
  }
  getOrThrow<T>(key: string): T {
    if (!CONFIG[key]) throw new Error(`missing ${key}`);
    return CONFIG[key] as unknown as T;
  }
}

describe('AuthService', () => {
  let auth: AuthService;
  let repo: FakeRefreshRepo;

  beforeEach(async () => {
    repo = new FakeRefreshRepo();
    const moduleRef = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: UsersService, useClass: FakeUsersService },
        { provide: JwtService, useValue: new JwtService({}) },
        { provide: ConfigService, useClass: FakeConfigService },
        { provide: getRepositoryToken(RefreshToken), useValue: repo },
      ],
    }).compile();

    auth = moduleRef.get(AuthService);
  });

  it('inscrit un utilisateur et renvoie des tokens', async () => {
    const result = await auth.register('A@Example.com', 'password123', 'Alice');
    expect(result.user.email).toBe('a@example.com');
    expect(result.accessToken).toBeTruthy();
    expect(result.refreshToken).toBeTruthy();
    expect(repo.store.size).toBe(1);
  });

  it('refuse une double inscription du même email', async () => {
    await auth.register('a@example.com', 'password123', 'Alice');
    await expect(
      auth.register('a@example.com', 'password123', 'Alice2'),
    ).rejects.toBeInstanceOf(ConflictException);
  });

  it('connecte avec le bon mot de passe et rejette le mauvais', async () => {
    await auth.register('a@example.com', 'password123', 'Alice');
    await expect(auth.login('a@example.com', 'wrong')).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    const ok = await auth.login('a@example.com', 'password123');
    expect(ok.accessToken).toBeTruthy();
  });

  it('effectue la rotation du refresh et invalide l’ancien', async () => {
    const reg = await auth.register('a@example.com', 'password123', 'Alice');

    const rotated = await auth.refresh(reg.refreshToken);
    expect(rotated.refreshToken).not.toBe(reg.refreshToken);

    // L'ancien refresh est désormais révoqué.
    await expect(auth.refresh(reg.refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    // Le nouveau fonctionne.
    const again = await auth.refresh(rotated.refreshToken);
    expect(again.accessToken).toBeTruthy();
  });

  it('révoque le refresh au logout', async () => {
    const reg = await auth.register('a@example.com', 'password123', 'Alice');
    await auth.logout(reg.refreshToken);
    await expect(auth.refresh(reg.refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });
});
