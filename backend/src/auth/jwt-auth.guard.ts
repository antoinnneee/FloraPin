import { Injectable } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';

/** Garde protégeant les routes nécessitant un access token valide. */
@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {}
