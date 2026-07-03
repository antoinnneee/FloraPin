import { Injectable, UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { UsersService } from '../users/users.service';

export interface AccessTokenPayload {
  sub: string;
  email: string;
}

/** Identité injectée dans la requête après validation du JWT d'accès. */
export interface AuthenticatedUser {
  userId: string;
  email: string;
}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(
    config: ConfigService,
    private readonly users: UsersService,
  ) {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: config.getOrThrow<string>('JWT_ACCESS_SECRET'),
    });
  }

  /**
   * Le compte peut avoir été supprimé alors qu'un JWT d'accès (durée ~15 min)
   * est toujours en circulation : sans ce contrôle, la première requête tapait
   * une FK inexistante et renvoyait un 500. On vérifie l'existence et on renvoie
   * un 401 propre le cas échéant.
   */
  async validate(payload: AccessTokenPayload): Promise<AuthenticatedUser> {
    const user = await this.users.findById(payload.sub);
    if (!user) {
      throw new UnauthorizedException('Compte introuvable.');
    }
    return { userId: user.id, email: user.email };
  }
}
