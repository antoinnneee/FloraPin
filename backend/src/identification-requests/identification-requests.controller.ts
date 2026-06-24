import {
  Controller,
  Delete,
  Get,
  HttpCode,
  HttpStatus,
  Param,
  ParseUUIDPipe,
  Post,
  UseGuards,
} from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { IdentificationRequestsService } from './identification-requests.service';

/** Demandes d'identification collaborative (NODE-133). */
@ApiTags('identification-requests')
@ApiBearerAuth('access-token')
@UseGuards(JwtAuthGuard)
@Controller()
export class IdentificationRequestsController {
  constructor(private readonly service: IdentificationRequestsService) {}

  /** Le propriétaire demande à ses amis d'identifier une fleur. */
  @Post('flowers/:id/identification-requests')
  @HttpCode(HttpStatus.NO_CONTENT)
  async request(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ): Promise<void> {
    await this.service.request(user.userId, flowerId);
  }

  /** Le propriétaire annule la demande. */
  @Delete('flowers/:id/identification-requests')
  @HttpCode(HttpStatus.NO_CONTENT)
  async cancel(
    @CurrentUser() user: AuthenticatedUser,
    @Param('id', ParseUUIDPipe) flowerId: string,
  ): Promise<void> {
    await this.service.cancel(user.userId, flowerId);
  }

  /** Les fleurs « à identifier » qui me sont partagées (vue côté ami). */
  @Get('identification-requests')
  listForMe(@CurrentUser() user: AuthenticatedUser) {
    return this.service.listForViewer(user.userId);
  }
}
