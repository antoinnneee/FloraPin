import { MailMessage } from './mail.sender';

/** Coque HTML minimale et sobre, cohérente avec le branding FloraPin. */
function wrap(title: string, bodyHtml: string): string {
  return `<!doctype html><html lang="fr"><body style="font-family:sans-serif;color:#1b2a22;">
  <h2>🌸 FloraPin</h2>
  <h3>${title}</h3>
  ${bodyHtml}
  <hr><p style="font-size:12px;color:#6b7c72;">Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.</p>
  </body></html>`;
}

/**
 * Email de réinitialisation de mot de passe (NODE-116). [resetUrl] porte le
 * token en clair (jamais stocké en clair côté serveur) ; [expiresInMinutes]
 * rappelle la durée de validité.
 */
export function resetPasswordEmail(
  to: string,
  resetUrl: string,
  expiresInMinutes: number,
): MailMessage {
  const subject = 'Réinitialisation de votre mot de passe FloraPin';
  const html = wrap(
    'Réinitialisation du mot de passe',
    `<p>Pour choisir un nouveau mot de passe, cliquez sur le lien ci-dessous (valable ${expiresInMinutes} minutes) :</p>
     <p><a href="${resetUrl}">${resetUrl}</a></p>`,
  );
  const text =
    `Réinitialisation du mot de passe FloraPin\n\n` +
    `Ouvrez ce lien (valable ${expiresInMinutes} minutes) :\n${resetUrl}\n\n` +
    `Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.`;
  return { to, subject, html, text };
}

/**
 * Email de vérification d'adresse (NODE-117). [verifyUrl] porte le token en
 * clair.
 */
export function verifyEmail(to: string, verifyUrl: string): MailMessage {
  const subject = 'Confirmez votre adresse email FloraPin';
  const html = wrap(
    'Confirmation de l’adresse email',
    `<p>Confirmez votre adresse en cliquant sur le lien ci-dessous :</p>
     <p><a href="${verifyUrl}">${verifyUrl}</a></p>`,
  );
  const text =
    `Confirmez votre adresse email FloraPin\n\n` +
    `Ouvrez ce lien :\n${verifyUrl}\n\n` +
    `Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.`;
  return { to, subject, html, text };
}
