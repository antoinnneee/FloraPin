import {
  CanActivate,
  ExecutionContext,
  Injectable,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { timingSafeEqual } from 'crypto';
import { Request } from 'express';

/** Protège les données d'administration avec un secret dédié, hors JWT utilisateur. */
@Injectable()
export class AdminGuard implements CanActivate {
  constructor(private readonly config: ConfigService) {}

  canActivate(context: ExecutionContext): boolean {
    const expected = this.config.get<string>('ADMIN_DASHBOARD_TOKEN', '');
    if (!expected) {
      throw new ServiceUnavailableException(
        'Dashboard désactivé : ADMIN_DASHBOARD_TOKEN absent.',
      );
    }

    const request = context.switchToHttp().getRequest<Request>();
    const supplied = request.header('x-admin-token') ?? '';
    const left = Buffer.from(supplied);
    const right = Buffer.from(expected);
    if (left.length !== right.length || !timingSafeEqual(left, right)) {
      throw new UnauthorizedException('Jeton administrateur invalide.');
    }
    return true;
  }
}
