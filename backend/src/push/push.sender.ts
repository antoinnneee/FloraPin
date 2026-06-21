/** Charge utile d'un push, dérivée d'une notification in-app. */
export interface PushMessage {
  type: string;
  data: Record<string, unknown>;
}

/**
 * Abstraction d'envoi de notifications push (FCM/APNs).
 *
 * Le provider concret (FCM) nécessite un projet Firebase et des credentials :
 * il est config-gated comme le stockage objet (cf. StorageModule). En l'absence
 * de configuration, [StubPushSender] est utilisé (no-op journalisé).
 */
export abstract class PushSender {
  abstract send(tokens: string[], message: PushMessage): Promise<void>;
}
