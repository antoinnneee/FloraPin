import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { createHash, randomUUID } from 'crypto';
import { IsNull, Repository } from 'typeorm';
import { User } from '../users/user.entity';
import { UsersService } from '../users/users.service';
import { RefreshToken } from './refresh-token.entity';

const BCRYPT_ROUNDS = 10;

export interface PublicUser {
  id: string;
  email: string;
  displayName: string;
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
        expiresIn: this.config.get<string>('JWT_ACCESS_TTL', '900s'),
      },
    );

    const jti = randomUUID();
    const refreshToken = this.jwt.sign(
      { sub: user.id, jti },
      {
        secret: this.config.getOrThrow<string>('JWT_REFRESH_SECRET'),
        expiresIn: this.config.get<string>('JWT_REFRESH_TTL', '30d'),
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
    createdAt: user.createdAt,
  };
}
