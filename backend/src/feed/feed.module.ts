import { Module } from '@nestjs/common';
import { SharesModule } from '../shares/shares.module';
import { FeedController } from './feed.controller';
import { FeedService } from './feed.service';

@Module({
  imports: [SharesModule],
  controllers: [FeedController],
  providers: [FeedService],
})
export class FeedModule {}
