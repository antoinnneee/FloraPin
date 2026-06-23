import { resetPasswordEmail, verifyEmail } from './mail-templates';

describe('mail-templates', () => {
  it('email de reset : porte le lien, l’expiration et le destinataire', () => {
    const mail = resetPasswordEmail(
      'alice@flora.pin',
      'https://florapin.fr/reset?token=abc',
      60,
    );
    expect(mail.to).toBe('alice@flora.pin');
    expect(mail.subject).toMatch(/mot de passe/i);
    expect(mail.html).toContain('https://florapin.fr/reset?token=abc');
    expect(mail.text).toContain('https://florapin.fr/reset?token=abc');
    expect(mail.text).toContain('60');
  });

  it('email de vérification : porte le lien de confirmation', () => {
    const mail = verifyEmail(
      'bob@flora.pin',
      'https://florapin.fr/verify?token=xyz',
    );
    expect(mail.to).toBe('bob@flora.pin');
    expect(mail.html).toContain('https://florapin.fr/verify?token=xyz');
    expect(mail.text).toContain('https://florapin.fr/verify?token=xyz');
  });
});
