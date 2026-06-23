import { Logger } from '@nestjs/common';
import { MailMessage, MailSender } from './mail.sender';

/**
 * Sous-ensemble de `nodemailer.Transporter` réellement utilisé. Facilite le
 * test (injection d'un faux transport) sans dépendre du SDK complet.
 */
export interface MailTransport {
  sendMail(options: {
    from: string;
    to: string;
    subject: string;
    html: string;
    text?: string;
  }): Promise<unknown>;
}

/** Provider email réel (SMTP via nodemailer). */
export class SmtpMailSender extends MailSender {
  private readonly logger = new Logger(SmtpMailSender.name);

  constructor(
    private readonly transport: MailTransport,
    private readonly from: string,
  ) {
    super();
  }

  async send(message: MailMessage): Promise<void> {
    await this.transport.sendMail({ from: this.from, ...message });
    this.logger.debug(`Email envoyé « ${message.subject} » → ${message.to}`);
  }
}
