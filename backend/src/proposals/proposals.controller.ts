import {
  Body,
  Controller,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  UseGuards,
} from '@nestjs/common';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ProposeSpeciesDto } from './dto/proposal.dto';
import { ProposalsService } from './proposals.service';

@Controller('flowers/:id/proposals')
@UseGuards(JwtAuthGuard)
export class ProposalsController {
  constructor(private readonly proposals: ProposalsService) {}

  /** Un ami propose une espèce. */
  @Post()
  propose(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Body() dto: ProposeSpeciesDto,
  ) {
    return this.proposals.propose(user.userId, flowerId, dto.species);
  }

  /** Le propriétaire liste les propositions reçues. */
  @Get()
  list(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ) {
    return this.proposals.listForFlower(user.userId, flowerId);
  }

  /** Le propriétaire accepte une proposition. */
  @Post(':proposalId/accept')
  @HttpCode(HttpStatus.OK)
  accept(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
    @Param('proposalId', ParseUUIDPipe) proposalId: string,
  ) {
    return this.proposals.accept(user.userId, flowerId, proposalId);
  }
}
