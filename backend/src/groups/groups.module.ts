import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AlbumPermission } from '../albums/album-permission.entity';
import { Album } from '../albums/album.entity';
import { FriendshipsModule } from '../friendships/friendships.module';
import { NotificationsModule } from '../notifications/notifications.module';
import { UsersModule } from '../users/users.module';
import { GroupsController } from './groups.controller';
import { GroupsService } from './groups.service';
import { Group } from './group.entity';
import { GroupMember } from './group-member.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([Group, GroupMember, Album, AlbumPermission]),
    UsersModule,
    FriendshipsModule,
    NotificationsModule,
  ],
  controllers: [GroupsController],
  providers: [GroupsService],
  exports: [GroupsService],
})
export class GroupsModule {}
