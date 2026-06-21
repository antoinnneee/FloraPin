import { Injectable, Logger } from '@nestjs/common';
import { DeviceTokensService } from './device-tokens.service';
import { PushMessage, PushSender } from './push.sender';

/** Réponse multicast minimale (sous-ensemble de l'API firebase-admin). */
export interface FcmMulticastResponse {
  responses: { success: boolean; error?: { code: string } }[];
}

/** Client de messagerie FCM minimal — facilite le test (mock du SDK). */
export interface FcmMessaging {
  sendEachForMulticast(message: {
    tokens: string[];
    data: Record<string, string>;
  }): Promise<FcmMulticastResponse>;
}

/** Codes d'erreur FCM signalant un jeton à supprimer. */
const STALE_TOKEN_CODES = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
  'messaging/invalid-argument',
]);

/**
 * Provider push FCM (firebase-admin). Envoie en multicast et purge les jetons
 * devenus invalides via [DeviceTokensService].
 */
@Injectable()
export class FcmPushSender extends PushSender {
  private readonly logger = new Logger(FcmPushSender.name);

  constructor(
    private readonly messaging: FcmMessaging,
    private readonly devices: DeviceTokensService,
  ) {
    super();
  }

  async send(tokens: string[], message: PushMessage): Promise<void> {
    if (tokens.length === 0) return;

    const result = await this.messaging.sendEachForMulticast({
      tokens,
      data: toStringData(message),
    });

    // Supprime les jetons rejetés comme invalides/expirés.
    const stale = result.responses
      .map((r, i) => ({ r, token: tokens[i] }))
      .filter(({ r }) => !r.success && r.error && STALE_TOKEN_CODES.has(r.error.code))
      .map(({ token }) => token);

    for (const token of stale) {
      await this.devices.unregister(token);
    }
    if (stale.length > 0) {
      this.logger.log(`${stale.length} jeton(s) FCM invalide(s) purgé(s).`);
    }
  }
}

/** FCM n'accepte que des données string : on sérialise le type + le payload. */
function toStringData(message: PushMessage): Record<string, string> {
  const data: Record<string, string> = { type: message.type };
  for (const [key, value] of Object.entries(message.data)) {
    data[key] =
      value === null || value === undefined
        ? ''
        : typeof value === 'string'
          ? value
          : JSON.stringify(value);
  }
  return data;
}
