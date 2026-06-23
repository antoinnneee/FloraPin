import { Injectable, Logger } from '@nestjs/common';
import { ErrorContext, ErrorReporter } from './error-reporter';

/**
 * Reporter par défaut : journalise les erreurs serveur via le Logger Nest, avec
 * le contexte requête (méthode, route, statut) mais SANS PII (ni corps, ni
 * en-têtes, ni query : pas d'email/token/coordonnées GPS dans les logs).
 */
@Injectable()
export class LoggingErrorReporter extends ErrorReporter {
  private readonly logger = new Logger('ErrorReporter');

  captureException(error: unknown, context: ErrorContext = {}): void {
    const { method, path, statusCode } = context;
    const where = method && path ? `${method} ${path}` : 'unknown';
    const status = statusCode ?? 500;
    const message = error instanceof Error ? error.message : String(error);
    const stack = error instanceof Error ? error.stack : undefined;
    this.logger.error(`[${status}] ${where} — ${message}`, stack);
  }
}
