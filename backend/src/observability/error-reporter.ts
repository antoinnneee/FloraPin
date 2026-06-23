/** Contexte (sans PII) accompagnant une erreur reportée. */
export interface ErrorContext {
  method?: string;
  /** Chemin de la route (sans query string ni valeurs sensibles). */
  path?: string;
  statusCode?: number;
}

/**
 * Abstraction de remontée d'erreurs (NODE-122). Implémentation par défaut :
 * journalisation structurée. Un driver Sentry réel se branchera ici quand le
 * DSN sera fourni (cf. nœud « infos requises »), sans changer les appelants.
 */
export abstract class ErrorReporter {
  abstract captureException(error: unknown, context?: ErrorContext): void;
}
