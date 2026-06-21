import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AuthModule } from './auth/auth.module';
import { FeedModule } from './feed/feed.module';
import { FlowersModule } from './flowers/flowers.module';
import { FriendshipsModule } from './friendships/friendships.module';
import { IdentificationModule } from './identification/identification.module';
import { NotificationsModule } from './notifications/notifications.module';
import { ProposalsModule } from './proposals/proposals.module';
import { SharesModule } from './shares/shares.module';
import { SyncModule } from './sync/sync.module';
import { UsersModule } from './users/users.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    TypeOrmModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres',
        host: config.get<string>('DATABASE_HOST', 'localhost'),
        port: config.get<number>('DATABASE_PORT', 5432),
        username: config.get<string>('DATABASE_USER', 'florapin'),
        password: config.get<string>('DATABASE_PASSWORD', ''),
        database: config.get<string>('DATABASE_NAME', 'florapin'),
        autoLoadEntities: true,
        // Le schéma est géré par db/schema.sql et les migrations (pas de sync auto).
        synchronize: false,
      }),
    }),
    AuthModule,
    UsersModule,
    FlowersModule,
    FriendshipsModule,
    NotificationsModule,
    SharesModule,
    FeedModule,
    IdentificationModule,
    ProposalsModule,
    SyncModule,
  ],
})
export class AppModule {}
