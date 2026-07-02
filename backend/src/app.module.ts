import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { APP_GUARD } from '@nestjs/core';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { TypeOrmModule } from '@nestjs/typeorm';
import { AlbumsModule } from './albums/albums.module';
import { AuthModule } from './auth/auth.module';
import { CommentsModule } from './comments/comments.module';
import { FeedModule } from './feed/feed.module';
import { FlowersModule } from './flowers/flowers.module';
import { FriendshipsModule } from './friendships/friendships.module';
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
import { UsersModule } from './users/users.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    // Rate limiting global (C2) : 100 req/min par IP. Les endpoints sensibles
    // (login, register, forgot-password…) portent des limites plus strictes via
    // @Throttle() dans AuthController.
    ThrottlerModule.forRoot({
      throttlers: [{ ttl: 60_000, limit: 100 }],
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
    AuthModule,
    UsersModule,
    FlowersModule,
    SpeciesModule,
    AlbumsModule,
    FriendshipsModule,
    NotificationsModule,
    SharesModule,
    FeedModule,
    IdentificationModule,
    IdentificationRequestsModule,
    LikesModule,
    ProposalsModule,
    CommentsModule,
    PushModule,
    SyncModule,
  ],
  providers: [
    // Applique le rate limiting à toutes les routes HTTP.
    { provide: APP_GUARD, useClass: ThrottlerGuard },
  ],
})
export class AppModule {}
