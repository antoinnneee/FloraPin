import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { BadgeCounts, BadgesService } from './badges.service';

/**
 * Badges « entraide » calculés côté serveur (TÂCHE 5.4) : le seul point d'entrée
 * expose les compteurs bruts de l'utilisateur courant. Le mapping vers les
 * paliers et la fusion avec les badges « collection » locaux se font côté app.
 */
@ApiTags('badges')
@ApiBearerAuth('access-token')
@UseGuards(JwtAuthGuard)
@Controller('me')
export class BadgesController {
  constructor(private readonly badges: BadgesService) {}

  /** Compteurs d'entraide de l'utilisateur courant. */
  @Get('badges')
  counts(@CurrentUser() user: AuthenticatedUser): Promise<BadgeCounts> {
    return this.badges.countsFor(user.userId);
  }
}
