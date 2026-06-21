import { Body, Controller, Get, Post, Query, UseGuards } from '@nestjs/common';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { SyncPullQueryDto, SyncPushDto } from './dto/sync.dto';
import { SyncService } from './sync.service';

@Controller('sync')
@UseGuards(JwtAuthGuard)
export class SyncController {
  constructor(private readonly sync: SyncService) {}

  @Get()
  pull(
    @CurrentUser() user: AuthenticatedUser,
    @Query() query: SyncPullQueryDto,
  ) {
    const since = query.since ? new Date(query.since) : undefined;
    return this.sync.pull(user.userId, since);
  }

  @Post('flowers')
  push(@CurrentUser() user: AuthenticatedUser, @Body() dto: SyncPushDto) {
    return this.sync.push(user.userId, dto.items);
  }
}
