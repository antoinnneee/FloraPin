import { createParamDecorator, ExecutionContext } from '@nestjs/common';
import { AuthenticatedUser } from './jwt.strategy';

/** Récupère l'utilisateur authentifié depuis la requête (rempli par JwtStrategy). */
export const CurrentUser = createParamDecorator(
  (_data: unknown, ctx: ExecutionContext): AuthenticatedUser => {
    // `getRequest()` rend `any` par défaut : on déclare la forme attendue, que
    // JwtStrategy garantit sur toute route protégée par JwtAuthGuard.
    return ctx.switchToHttp().getRequest<{ user: AuthenticatedUser }>().user;
  },
);
