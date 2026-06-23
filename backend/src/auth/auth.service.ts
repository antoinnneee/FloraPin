import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { createHash, randomBytes, randomUUID } from 'crypto';
import type { SignOptions } from 'jsonwebtoken';
import { IsNull, Repository } from 'typeorm';

/** Durée de validité d'un JWT, typée comme l'attend jsonwebtoken (ms StringValue). */
type ExpiresIn = SignOptions['expiresIn'];
import { MailSender } from '../mail/mail.sender';
import { resetPasswordEmail, verifyEmail } from '../mail/mail-templates';
import { User } from '../users/user.entity';
import { UsersService } from '../users/users.service';
import { EmailVerificationToken } from './email-verification-token.entity';
import { PasswordResetToken } from './password-reset-token.entity';
import { RefreshToken } from './refresh-token.entity';

const BCRYPT_ROUNDS = 10;

export interface PublicUser {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
  createdAt: Date;
}

export interface AuthResult {
  user: PublicUser;
  accessToken: string;
  refreshToken: string;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

interface RefreshPayload {
  sub: string;
  jti: string;
}

@Injectable()
export class AuthService {
  constructor(
    private readonly users: UsersService,
    private readonly jwt: JwtService,
    private readonly config: ConfigService,
    @InjectRepository(RefreshToken)
    private readonly refreshTokens: Repository<RefreshToken>,
    @InjectRepository(PasswordResetToken)
    private readonly resetTokens: Repository<PasswordResetToken>,
    @InjectRepository(EmailVerificationToken)
    private readonly emailTokens: Repository<EmailVerificationToken>,
    private readonly mail: MailSender,
  ) {}

  async register(
    email: string,
    password: string,
    displayName: string,
  ): Promise<AuthResult> {
    const existing = await this.users.findByEmail(email);
    if (existing) {
      throw new ConflictException('Un compte existe déjà avec cet email.');
    }
    const passwordHash = await bcrypt.hash(password, BCRYPT_ROUNDS);
    const user = await this.users.create({ email, passwordHash, displayName });
    return this.buildAuthResult(user);
  }

  async login(email: string, password: string): Promise<AuthResult> {
    const user = await this.users.findByEmail(email);
    if (!user || !(await bcrypt.compare(password, user.passwordHash))) {
      throw new UnauthorizedException('Identifiants invalides.');
    }
    return this.buildAuthResult(user);
  }

  /** Échange un refresh valide contre une nouvelle paire (rotation). */
  async refresh(refreshToken: string): Promise<TokenPair> {
    const payload = this.verifyRefresh(refreshToken);
    const record = await this.refreshTokens.findOne({
      where: { id: payload.jti },
    });

    if (
      !record ||
      record.revokedAt ||
      record.expiresAt.getTime() < Date.now() ||
      record.tokenHash !== hashToken(refreshToken)
    ) {
      throw new UnauthorizedException('Refresh token invalide.');
    }

    const user = await this.users.findById(payload.sub);
    if (!user) {
      throw new UnauthorizedException('Utilisateur introuvable.');
    }

    record.revokedAt = new Date();
    await this.refreshTokens.save(record);

    return this.issueTokens(user);
  }

  /**
   * Démarre un « mot de passe oublié » (NODE-116) : si un compte correspond à
   * [email], génère un token à usage unique et durée limitée, le persiste
   * (hashé) et envoie le lien par email. Ne révèle JAMAIS si l'email existe
   * (anti-énumération) : l'appelant répond 200 dans tous les cas.
   */
  async forgotPassword(email: string): Promise<void> {
    const user = await this.users.findByEmail(email);
    if (!user) return;

    const ttlMinutes = Number(
      this.config.get<string>('PASSWORD_RESET_TTL_MIN', '60'),
    );
    const rawToken = randomBytes(32).toString('hex');
    await this.resetTokens.save(
      this.resetTokens.create({
        userId: user.id,
        tokenHash: hashToken(rawToken),
        expiresAt: new Date(Date.now() + ttlMinutes * 60_000),
        usedAt: null,
      }),
    );

    const baseUrl = this.config.get<string>('APP_BASE_URL', 'https://florapin.fr');
    const resetUrl = `${baseUrl}/reset?token=${rawToken}`;
    await this.mail.send(resetPasswordEmail(user.email, resetUrl, ttlMinutes));
  }

  /**
   * Termine un reset (NODE-116) : valide le token (présent, non expiré, non
   * utilisé), re-hash le nouveau mot de passe, marque le token consommé et
   * révoque tous les refresh tokens actifs de l'utilisateur (déconnexion
   * globale). Token invalide/expiré ⇒ 401.
   */
  async resetPassword(token: string, newPassword: string): Promise<void> {
    const record = await this.resetTokens.findOne({
      where: { tokenHash: hashToken(token), usedAt: IsNull() },
    });
    if (!record || record.expiresAt.getTime() < Date.now()) {
      throw new UnauthorizedException(
        'Lien de réinitialisation invalide ou expiré.',
      );
    }

    const passwordHash = await bcrypt.hash(newPassword, BCRYPT_ROUNDS);
    await this.users.setPasswordHash(record.userId, passwordHash);

    record.usedAt = new Date();
    await this.resetTokens.save(record);

    await this.refreshTokens.update(
      { userId: record.userId, revokedAt: IsNull() },
      { revokedAt: new Date() },
    );
  }

  /**
   * Envoie (ou renvoie) un email de vérification d'adresse (NODE-117). Opt-in :
   * sans effet si l'email est déjà vérifié (idempotent). Protégé par JWT côté
   * contrôleur, donc [userId] est connu.
   */
  async requestEmailVerification(userId: string): Promise<void> {
    const user = await this.users.findById(userId);
    if (!user || user.emailVerified) return;

    const ttlMinutes = Number(
      this.config.get<string>('EMAIL_VERIFY_TTL_MIN', '1440'),
    );
    const rawToken = randomBytes(32).toString('hex');
    await this.emailTokens.save(
      this.emailTokens.create({
        userId: user.id,
        tokenHash: hashToken(rawToken),
        expiresAt: new Date(Date.now() + ttlMinutes * 60_000),
        usedAt: null,
      }),
    );

    const baseUrl = this.config.get<string>('APP_BASE_URL', 'https://florapin.fr');
    const verifyUrl = `${baseUrl}/verify?token=${rawToken}`;
    await this.mail.send(verifyEmail(user.email, verifyUrl));
  }

  /**
   * Valide un token de vérification d'email (NODE-117) → marque l'adresse
   * vérifiée. Token invalide/expiré/déjà utilisé ⇒ 401.
   */
  async verifyEmailToken(token: string): Promise<void> {
    const record = await this.emailTokens.findOne({
      where: { tokenHash: hashToken(token), usedAt: IsNull() },
    });
    if (!record || record.expiresAt.getTime() < Date.now()) {
      throw new UnauthorizedException('Lien de vérification invalide ou expiré.');
    }
    await this.users.setEmailVerified(record.userId);
    record.usedAt = new Date();
    await this.emailTokens.save(record);
  }

  /** Révoque le refresh fourni (déconnexion). */
  async logout(refreshToken: string): Promise<void> {
    let payload: RefreshPayload;
    try {
      payload = this.verifyRefresh(refreshToken);
    } catch {
      return; // déjà invalide : rien à faire
    }
    await this.refreshTokens.update(
      { id: payload.jti, revokedAt: IsNull() },
      { revokedAt: new Date() },
    );
  }

  private verifyRefresh(token: string): RefreshPayload {
    try {
      return this.jwt.verify<RefreshPayload>(token, {
        secret: this.config.getOrThrow<string>('JWT_REFRESH_SECRET'),
      });
    } catch {
      throw new UnauthorizedException('Refresh token invalide.');
    }
  }

  private async buildAuthResult(user: User): Promise<AuthResult> {
    const tokens = await this.issueTokens(user);
    return { user: toPublicUser(user), ...tokens };
  }

  private async issueTokens(user: User): Promise<TokenPair> {
    const accessToken = this.jwt.sign(
      { sub: user.id, email: user.email },
      {
        secret: this.config.getOrThrow<string>('JWT_ACCESS_SECRET'),
        expiresIn: this.config.get<string>('JWT_ACCESS_TTL', '900s') as ExpiresIn,
      },
    );

    const jti = randomUUID();
    const refreshToken = this.jwt.sign(
      { sub: user.id, jti },
      {
        secret: this.config.getOrThrow<string>('JWT_REFRESH_SECRET'),
        expiresIn: this.config.get<string>('JWT_REFRESH_TTL', '30d') as ExpiresIn,
      },
    );

    const decoded = this.jwt.decode(refreshToken) as { exp: number };
    await this.refreshTokens.save(
      this.refreshTokens.create({
        id: jti,
        userId: user.id,
        tokenHash: hashToken(refreshToken),
        expiresAt: new Date(decoded.exp * 1000),
        revokedAt: null,
      }),
    );

    return { accessToken, refreshToken };
  }
}

function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

function toPublicUser(user: User): PublicUser {
  return {
    id: user.id,
    email: user.email,
    displayName: user.displayName,
    emailVerified: user.emailVerified ?? false,
    createdAt: user.createdAt,
  };
}
