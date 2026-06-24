import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { ApiBearerAuth, ApiTags } from '@nestjs/swagger';
import { CurrentUser } from '../auth/current-user.decorator';
import { AuthenticatedUser } from '../auth/jwt.strategy';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { FeedQueryDto } from './dto/feed.dto';
import { FeedService } from './feed.service';

@ApiTags('feed')
@ApiBearerAuth('access-token')
@Controller('feed')
@UseGuards(JwtAuthGuard)
export class FeedController {
  constructor(private readonly feed: FeedService) {}

  @Get()
  get(@CurrentUser() user: AuthenticatedUser, @Query() query: FeedQueryDto) {
    const since = query.since ? new Date(query.since) : undefined;
    return this.feed.getFeed(user.userId, since, query.limit, query.sort);
  }
}
