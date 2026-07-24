import { Body, Controller, Post, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { Throttle } from '@nestjs/throttler';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { ClientLogsService } from './client-logs.service';
import { CreateClientLogDto } from './dto/create-client-log.dto';

@ApiTags('diagnostics')
@ApiBearerAuth('access-token')
@Controller('diagnostics/logs')
@UseGuards(JwtAuthGuard)
export class ClientLogsController {
  constructor(private readonly clientLogs: ClientLogsService) {}

  /** Enregistre un rapport explicitement envoyé par l'utilisateur connecté. */
  @Post()
  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  create(
    @CurrentUser() user: AuthenticatedUser,
    @Body() dto: CreateClientLogDto,
  ) {
    return this.clientLogs.create(user.userId, dto);
  }
}
