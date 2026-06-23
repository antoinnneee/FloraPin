import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';
import { MailSender } from './mail.sender';
import { SmtpMailSender } from './smtp-mail.sender';
import { StubMailSender } from './stub-mail.sender';

/**
 * Fournit l'envoi d'emails (NODE-130).
 *
 * - `MAIL_DRIVER=smtp` + credentials SMTP présents → [SmtpMailSender] (nodemailer).
 * - sinon → [StubMailSender] (journalise, défaut dev/tests).
 */
@Module({
  providers: [
    {
      provide: MailSender,
      inject: [ConfigService],
      useFactory: (config: ConfigService): MailSender => {
        const logger = new Logger('MailModule');
        const from = config.get<string>(
          'MAIL_FROM',
          'FloraPin <no-reply@florapin.fr>',
        );

        if (config.get<string>('MAIL_DRIVER', 'stub') !== 'smtp') {
          logger.warn('Email : StubMailSender (MAIL_DRIVER absent ou != smtp).');
          return new StubMailSender();
        }

        const host = config.get<string>('SMTP_HOST');
        const port = config.get<number>('SMTP_PORT', 587);
        const user = config.get<string>('SMTP_USER');
        const pass = config.get<string>('SMTP_PASS');

        if (!host || !user || !pass) {
          logger.warn(
            'MAIL_DRIVER=smtp mais credentials SMTP incomplets : repli StubMailSender.',
          );
          return new StubMailSender();
        }

        const transport = nodemailer.createTransport({
          host,
          port,
          secure: port === 465, // SMTPS implicite sur 465, STARTTLS sinon
          auth: { user, pass },
        });
        logger.log('Email : SmtpMailSender (nodemailer) actif.');
        return new SmtpMailSender(transport, from);
      },
    },
  ],
  exports: [MailSender],
})
export class MailModule {}
