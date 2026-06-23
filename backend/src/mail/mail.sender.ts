/** Un email à envoyer. */
export interface MailMessage {
  to: string;
  subject: string;
  html: string;
  text?: string;
}

/**
 * Abstraction d'envoi d'emails (NODE-130). Même pattern que [PushSender] :
 * une interface, un stub par défaut, un provider SMTP réel config-gated.
 *
 * Requise par la réinitialisation de mot de passe (NODE-116) et la vérification
 * d'email (NODE-117).
 */
export abstract class MailSender {
  abstract send(message: MailMessage): Promise<void>;
}
