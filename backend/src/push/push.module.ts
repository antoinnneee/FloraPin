import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { DeviceToken } from './device-token.entity';
import { DeviceTokensService } from './device-tokens.service';
import { PushController } from './push.controller';
import { PushSender } from './push.sender';
import { StubPushSender } from './stub-push.sender';

/**
 * Fournit l'envoi de push et la gestion des jetons d'appareil.
 *
 * - `PUSH_DRIVER=fcm` → provider FCM (à implémenter ; nécessite un projet
 *   Firebase + credentials). En attendant, repli sur [StubPushSender].
 * - sinon → [StubPushSender] (no-op journalisé).
 */
@Module({
  imports: [TypeOrmModule.forFeature([DeviceToken])],
  controllers: [PushController],
  providers: [
    DeviceTokensService,
    {
      provide: PushSender,
      inject: [ConfigService],
      useFactory: (config: ConfigService): PushSender => {
        const driver = config.get<string>('PUSH_DRIVER', 'stub');
        const logger = new Logger('PushModule');
        if (driver === 'fcm') {
          logger.warn(
            'PUSH_DRIVER=fcm mais provider FCM non implémenté : repli StubPushSender.',
          );
        } else {
          logger.warn('Push : StubPushSender (PUSH_DRIVER absent ou != fcm).');
        }
        return new StubPushSender();
      },
    },
  ],
  exports: [PushSender, DeviceTokensService],
})
export class PushModule {}
