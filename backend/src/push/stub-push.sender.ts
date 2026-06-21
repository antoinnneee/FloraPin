import { Injectable, Logger } from '@nestjs/common';
import { PushMessage, PushSender } from './push.sender';

/** Provider push de repli : journalise sans rien envoyer (dev / FCM non configuré). */
@Injectable()
export class StubPushSender extends PushSender {
  private readonly logger = new Logger(StubPushSender.name);

  async send(tokens: string[], message: PushMessage): Promise<void> {
    this.logger.debug(
      `Push (stub) « ${message.type} » → ${tokens.length} appareil(s).`,
    );
  }
}
