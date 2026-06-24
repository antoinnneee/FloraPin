import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { FriendshipsModule } from '../friendships/friendships.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { SharesModule } from '../shares/shares.module';
import { IdentificationRequestsController } from './identification-requests.controller';
import { IdentificationRequestsService } from './identification-requests.service';

/** Demandes d'identification collaborative (NODE-133). */
@Module({
  imports: [
    TypeOrmModule.forFeature([Flower]),
    FriendshipsModule,
    NotificationsModule,
    SharesModule,
  ],
  controllers: [IdentificationRequestsController],
  providers: [IdentificationRequestsService],
})
export class IdentificationRequestsModule {}
