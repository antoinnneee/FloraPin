import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AlbumsModule } from './albums/albums.module';
import { AdminModule } from './admin/admin.module';
import { AuthModule } from './auth/auth.module';
import { BadgesModule } from './badges/badges.module';
import { CommentsModule } from './comments/comments.module';
import { FeedModule } from './feed/feed.module';
import { FlowersModule } from './flowers/flowers.module';
import { FriendProfileModule } from './friend-profile/friend-profile.module';
import { FriendshipsModule } from './friendships/friendships.module';
import { GroupsModule } from './groups/groups.module';
import { IdentificationModule } from './identification/identification.module';
import { IdentificationRequestsModule } from './identification-requests/identification-requests.module';
import { LikesModule } from './likes/likes.module';
import { MailModule } from './mail/mail.module';
import { NotificationsModule } from './notifications/notifications.module';
import { ObservabilityModule } from './observability/observability.module';
import { ProposalsModule } from './proposals/proposals.module';
import { PushModule } from './push/push.module';
import { SharesModule } from './shares/shares.module';
import { SpeciesModule } from './species/species.module';
import { SyncModule } from './sync/sync.module';
import { StorageCleanupModule } from './storage/storage-cleanup.module';
import { UsersModule } from './users/users.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    // Rate limiting global (C2) : 100 req/min par IP. Les endpoints sensibles
    // (login, register, forgot-password…) portent des limites plus strictes via
    // @Throttle() dans AuthController.
    ThrottlerModule.forRoot({
      throttlers: [{ ttl: 60_000, limit: 100 }],
      // Permet de désactiver entièrement le rate limiting (y compris les limites
      // @Throttle strictes) dans les tests e2e qui enchaînent des inscriptions
      // depuis la même IP. Reste actif en prod.
      skipIf: () => process.env.DISABLE_THROTTLE === 'true',
    }),
    ObservabilityModule,
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
    MailModule,
    AdminModule,
    AuthModule,
    UsersModule,
    FlowersModule,
    SpeciesModule,
    AlbumsModule,
    GroupsModule,
    FriendshipsModule,
    NotificationsModule,
    SharesModule,
    FriendProfileModule,
    FeedModule,
    IdentificationModule,
    IdentificationRequestsModule,
    LikesModule,
    ProposalsModule,
    CommentsModule,
    BadgesModule,
    PushModule,
    SyncModule,
    StorageCleanupModule,
  ],
  providers: [
    // Applique le rate limiting à toutes les routes HTTP.
    { provide: APP_GUARD, useClass: ThrottlerGuard },
  ],
})
export class AppModule {}
