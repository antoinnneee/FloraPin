import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsModule } from '../notifications/notifications.module';
import { FlowerLike } from './flower-like.entity';
import { LikesController } from './likes.controller';
import { LikesService } from './likes.service';

/** Cœurs sur les fleurs (NODE-139). */
@Module({
  imports: [TypeOrmModule.forFeature([FlowerLike, Flower]), NotificationsModule],
  controllers: [LikesController],
  providers: [LikesService],
})
export class LikesModule {}
