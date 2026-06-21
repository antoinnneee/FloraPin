import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { cert, getApp, getApps, initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { DeviceToken } from './device-token.entity';
import { DeviceTokensService } from './device-tokens.service';
import { FcmMessaging, FcmPushSender } from './fcm-push.sender';
import { PushController } from './push.controller';
import { PushSender } from './push.sender';
import { StubPushSender } from './stub-push.sender';

/**
 * Fournit l'envoi de push et la gestion des jetons d'appareil.
 *
 * - `PUSH_DRIVER=fcm` + credentials Firebase présents → [FcmPushSender].
 * - sinon → [StubPushSender] (no-op journalisé).
 */
@Module({
  imports: [TypeOrmModule.forFeature([DeviceToken])],
  controllers: [PushController],
  providers: [
    DeviceTokensService,
    {
      provide: PushSender,
      inject: [ConfigService, DeviceTokensService],
      useFactory: (
        config: ConfigService,
        devices: DeviceTokensService,
      ): PushSender => {
        const logger = new Logger('PushModule');
        if (config.get<string>('PUSH_DRIVER', 'stub') !== 'fcm') {
          logger.warn('Push : StubPushSender (PUSH_DRIVER absent ou != fcm).');
          return new StubPushSender();
        }

        const projectId = config.get<string>('FCM_PROJECT_ID');
        const clientEmail = config.get<string>('FCM_CLIENT_EMAIL');
        // La clé privée contient des \n littéraux dans le .env : les restaurer.
        const privateKey = config
          .get<string>('FCM_PRIVATE_KEY')
          ?.replace(/\\n/g, '\n');

        if (!projectId || !clientEmail || !privateKey) {
          logger.warn(
            'PUSH_DRIVER=fcm mais credentials Firebase incomplets : repli StubPushSender.',
          );
          return new StubPushSender();
        }

        const app = getApps().length
          ? getApp()
          : initializeApp({
              credential: cert({ projectId, clientEmail, privateKey }),
            });

        const messaging = getMessaging(app);
        const adapter: FcmMessaging = {
          sendEachForMulticast: (message) =>
            messaging.sendEachForMulticast(message),
        };
        logger.log('Push : FcmPushSender (Firebase) actif.');
        return new FcmPushSender(adapter, devices);
      },
    },
  ],
  exports: [PushSender, DeviceTokensService],
})
export class PushModule {}
