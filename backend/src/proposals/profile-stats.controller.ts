import { Controller, Get, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ProposalsService } from './proposals.service';

/** Statistiques collaboratives de l'utilisateur courant (page de profil). */
@ApiTags('proposals')
@ApiBearerAuth('access-token')
@Controller('me')
@UseGuards(JwtAuthGuard)
export class ProfileStatsController {
  constructor(private readonly proposals: ProposalsService) {}

  /** Nombre de mes propositions d'espèce qui ont été acceptées par des amis. */
  @Get('proposal-stats')
  async stats(@CurrentUser() user: AuthenticatedUser) {
    return {
      acceptedProposals: await this.proposals.acceptedCountForProposer(
        user.userId,
      ),
    };
  }
}
