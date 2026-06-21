import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { FlowersModule } from '../flowers/flowers.module';
import { FriendshipsModule } from '../friendships/friendships.module';
import { Share } from './share.entity';
import { SharesController } from './shares.controller';
import { SharesService } from './shares.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([Share, Flower]),
    FlowersModule,
    FriendshipsModule,
  ],
  controllers: [SharesController],
  providers: [SharesService],
  exports: [SharesService],
})
export class SharesModule {}
