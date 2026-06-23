import { SmtpMailSender, MailTransport } from './smtp-mail.sender';

describe('SmtpMailSender', () => {
  it('relaie le message au transport avec l’expéditeur configuré', async () => {
    const sent: unknown[] = [];
    const transport: MailTransport = {
      sendMail: async (options) => {
        sent.push(options);
        return { messageId: 'x' };
      },
    };
    const sender = new SmtpMailSender(transport, 'FloraPin <no-reply@florapin.fr>');

    await sender.send({
      to: 'alice@flora.pin',
      subject: 'Sujet',
      html: '<p>Bonjour</p>',
      text: 'Bonjour',
    });

    expect(sent).toEqual([
      {
        from: 'FloraPin <no-reply@florapin.fr>',
        to: 'alice@flora.pin',
        subject: 'Sujet',
        html: '<p>Bonjour</p>',
        text: 'Bonjour',
      },
    ]);
  });
});
