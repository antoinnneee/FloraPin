import { ConflictException, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Test } from '@nestjs/testing';
import { getRepositoryToken } from '@nestjs/typeorm';
import { randomUUID } from 'crypto';
import { MailMessage, MailSender } from '../mail/mail.sender';
import { User } from '../users/user.entity';
import { UsersService } from '../users/users.service';
import { AuthService } from './auth.service';
import { EmailVerificationToken } from './email-verification-token.entity';
import { PasswordResetToken } from './password-reset-token.entity';
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

  async setPasswordHash(userId: string, passwordHash: string): Promise<void> {
    const user = this.byId.get(userId);
    if (user) user.passwordHash = passwordHash;
  }

  async setEmailVerified(userId: string): Promise<void> {
    const user = this.byId.get(userId);
    if (user) {
      user.emailVerified = true;
      user.emailVerifiedAt = new Date();
    }
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
      emailVerified: false,
      emailVerifiedAt: null,
      createdAt: new Date(),
      updatedAt: new Date(),
    };
    this.byId.set(user.id, user);
    return user;
  }
}

/** Repository PasswordResetToken factice. */
class FakeResetRepo {
  store: PasswordResetToken[] = [];

  create(obj: Partial<PasswordResetToken>): PasswordResetToken {
    return { ...obj } as PasswordResetToken;
  }

  async save(obj: PasswordResetToken): Promise<PasswordResetToken> {
    const existing = this.store.find((t) => t === obj);
    if (!existing) this.store.push(obj);
    return obj;
  }

  async findOne(opts: {
    where: { tokenHash: string; usedAt: unknown };
  }): Promise<PasswordResetToken | null> {
    return (
      this.store.find(
        (t) => t.tokenHash === opts.where.tokenHash && t.usedAt == null,
      ) ?? null
    );
  }
}

/** Repository EmailVerificationToken factice. */
class FakeEmailRepo {
  store: EmailVerificationToken[] = [];

  create(obj: Partial<EmailVerificationToken>): EmailVerificationToken {
    return { ...obj } as EmailVerificationToken;
  }

  async save(obj: EmailVerificationToken): Promise<EmailVerificationToken> {
    if (!this.store.includes(obj)) this.store.push(obj);
    return obj;
  }

  async findOne(opts: {
    where: { tokenHash: string; usedAt: unknown };
  }): Promise<EmailVerificationToken | null> {
    return (
      this.store.find(
        (t) => t.tokenHash === opts.where.tokenHash && t.usedAt == null,
      ) ?? null
    );
  }
}

/** MailSender factice : mémorise les emails envoyés. */
class FakeMailSender extends MailSender {
  sent: MailMessage[] = [];
  async send(message: MailMessage): Promise<void> {
    this.sent.push(message);
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
    criteria: { id?: string; userId?: string; revokedAt: unknown },
    partial: Partial<RefreshToken>,
  ): Promise<void> {
    // Révocation par id (logout) ou par userId (reset : déconnexion globale),
    // limitée aux tokens encore actifs (revokedAt null).
    for (const [key, token] of this.store) {
      const matches =
        token.revokedAt === null &&
        (criteria.id !== undefined
          ? token.id === criteria.id
          : token.userId === criteria.userId);
      if (matches) this.store.set(key, { ...token, ...partial });
    }
  }
}

const CONFIG: Record<string, string> = {
  JWT_ACCESS_SECRET: 'test-access-secret',
  JWT_ACCESS_TTL: '900s',
  JWT_REFRESH_SECRET: 'test-refresh-secret',
  JWT_REFRESH_TTL: '30d',
  PASSWORD_RESET_TTL_MIN: '60',
  EMAIL_VERIFY_TTL_MIN: '1440',
  APP_BASE_URL: 'https://florapin.fr',
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
  let resetRepo: FakeResetRepo;
  let emailRepo: FakeEmailRepo;
  let mail: FakeMailSender;

  beforeEach(async () => {
    repo = new FakeRefreshRepo();
    resetRepo = new FakeResetRepo();
    emailRepo = new FakeEmailRepo();
    mail = new FakeMailSender();
    const moduleRef = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: UsersService, useClass: FakeUsersService },
        { provide: JwtService, useValue: new JwtService({}) },
        { provide: ConfigService, useClass: FakeConfigService },
        { provide: getRepositoryToken(RefreshToken), useValue: repo },
        { provide: getRepositoryToken(PasswordResetToken), useValue: resetRepo },
        { provide: getRepositoryToken(EmailVerificationToken), useValue: emailRepo },
        { provide: MailSender, useValue: mail },
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

  it('changePassword : applique le nouveau mdp, garde la session courante et coupe les autres', async () => {
    const reg = await auth.register('a@example.com', 'password123', 'Alice');
    // Deuxième appareil : une seconde session (refresh) pour le même compte.
    const other = await auth.login('a@example.com', 'password123');

    const pair = await auth.changePassword(
      reg.user.id,
      'password123',
      'nouveauPass1',
    );
    // Une paire fraîche est renvoyée pour l'appareil courant.
    expect(pair.accessToken).toBeTruthy();
    expect(pair.refreshToken).toBeTruthy();
    expect(pair.refreshToken).not.toBe(reg.refreshToken);

    // La session courante réémise fonctionne...
    expect((await auth.refresh(pair.refreshToken)).accessToken).toBeTruthy();
    // ...mais les anciennes sessions (courante d'origine ET autre appareil) sont révoquées.
    await expect(auth.refresh(reg.refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    await expect(auth.refresh(other.refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );

    // Le nouveau mot de passe est bien pris en compte, l'ancien ne marche plus.
    await expect(auth.login('a@example.com', 'password123')).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    expect(
      (await auth.login('a@example.com', 'nouveauPass1')).accessToken,
    ).toBeTruthy();
  });

  it('changePassword : ancien mot de passe erroné est rejeté (401) sans rien changer', async () => {
    const reg = await auth.register('a@example.com', 'password123', 'Alice');

    await expect(
      auth.changePassword(reg.user.id, 'mauvais', 'nouveauPass1'),
    ).rejects.toBeInstanceOf(UnauthorizedException);

    // Le mot de passe n'a pas changé et la session d'origine reste valide.
    expect((await auth.login('a@example.com', 'password123')).accessToken).toBeTruthy();
    expect((await auth.refresh(reg.refreshToken)).accessToken).toBeTruthy();
  });

  it('forgotPassword : compte inconnu → aucun token, aucun email (anti-énumération)', async () => {
    await auth.forgotPassword('inconnu@example.com');
    expect(resetRepo.store).toHaveLength(0);
    expect(mail.sent).toHaveLength(0);
  });

  it('forgotPassword : génère un token et envoie le lien par email', async () => {
    await auth.register('a@example.com', 'password123', 'Alice');
    await auth.forgotPassword('A@Example.com');

    expect(resetRepo.store).toHaveLength(1);
    expect(mail.sent).toHaveLength(1);
    expect(mail.sent[0].to).toBe('a@example.com');
    // Le lien porte le token en clair ; seul le hash est persisté.
    const match = mail.sent[0].text?.match(/token=([0-9a-f]+)/);
    expect(match).toBeTruthy();
    expect(resetRepo.store[0].tokenHash).not.toBe(match?.[1]);
  });

  it('resetPassword : applique le nouveau mot de passe et révoque les sessions', async () => {
    const reg = await auth.register('a@example.com', 'password123', 'Alice');
    await auth.forgotPassword('a@example.com');
    const token = mail.sent[0].text?.match(/token=([0-9a-f]+)/)?.[1] as string;

    await auth.resetPassword(token, 'nouveauPass1');

    // Connexion avec le nouveau mot de passe, l'ancien ne marche plus.
    await expect(auth.login('a@example.com', 'password123')).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    expect((await auth.login('a@example.com', 'nouveauPass1')).accessToken).toBeTruthy();
    // L'ancien refresh est révoqué (déconnexion globale).
    await expect(auth.refresh(reg.refreshToken)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('resetPassword : token déjà utilisé est rejeté', async () => {
    await auth.register('a@example.com', 'password123', 'Alice');
    await auth.forgotPassword('a@example.com');
    const token = mail.sent[0].text?.match(/token=([0-9a-f]+)/)?.[1] as string;

    await auth.resetPassword(token, 'nouveauPass1');
    await expect(auth.resetPassword(token, 'encoreAutre1')).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('resetPassword : token inconnu est rejeté', async () => {
    await expect(auth.resetPassword('deadbeef', 'nouveauPass1')).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });

  it('requestEmailVerification : envoie un lien de vérification', async () => {
    const reg = await auth.register('a@example.com', 'password123', 'Alice');
    await auth.requestEmailVerification(reg.user.id);

    expect(emailRepo.store).toHaveLength(1);
    expect(mail.sent).toHaveLength(1);
    expect(mail.sent[0].text).toContain('/verify?token=');
  });

  it('verifyEmailToken : marque l’adresse vérifiée puis rejette la réutilisation', async () => {
    const reg = await auth.register('a@example.com', 'password123', 'Alice');
    expect(reg.user.emailVerified).toBe(false);

    await auth.requestEmailVerification(reg.user.id);
    const token = mail.sent[0].text?.match(/token=([0-9a-f]+)/)?.[1] as string;
    await auth.verifyEmailToken(token);

    // Idempotence : déjà vérifié → plus d'envoi.
    await auth.requestEmailVerification(reg.user.id);
    expect(mail.sent).toHaveLength(1);

    // Token déjà utilisé.
    await expect(auth.verifyEmailToken(token)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
  });
});
