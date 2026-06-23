import { Injectable, Logger } from '@nestjs/common';
import { MailMessage, MailSender } from './mail.sender';

/**
 * Provider email de repli : journalise sans rien envoyer (dev / SMTP non
 * configuré). Le lien éventuel apparaît dans les logs pour tester les flux.
 */
@Injectable()
export class StubMailSender extends MailSender {
  private readonly logger = new Logger(StubMailSender.name);

  async send(message: MailMessage): Promise<void> {
    this.logger.debug(
      `Email (stub) « ${message.subject} » → ${message.to}\n${message.text ?? message.html}`,
    );
  }
}
