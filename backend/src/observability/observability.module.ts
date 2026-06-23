import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { APP_FILTER } from '@nestjs/core';
import { AllExceptionsFilter } from './all-exceptions.filter';
import { ErrorReporter } from './error-reporter';
import { LoggingErrorReporter } from './logging-error-reporter';

/**
 * Observabilité (NODE-122) : enregistre le filtre d'exceptions global et
 * fournit le [ErrorReporter].
 *
 * - Par défaut → [LoggingErrorReporter] (journalisation structurée).
 * - Si `SENTRY_DSN` est défini : le driver Sentry réel n'est pas encore câblé
 *   (dépend du DSN/compte — cf. nœud « infos requises ») ; on prévient et on
 *   reste sur le reporter de logs.
 */
@Module({
  providers: [
    {
      provide: ErrorReporter,
      inject: [ConfigService],
      useFactory: (config: ConfigService): ErrorReporter => {
        const dsn = config.get<string>('SENTRY_DSN');
        if (dsn) {
          new Logger('ObservabilityModule').warn(
            "SENTRY_DSN défini mais le driver Sentry n'est pas encore câblé : " +
              'repli sur le reporter de logs.',
          );
        }
        return new LoggingErrorReporter();
      },
    },
    { provide: APP_FILTER, useClass: AllExceptionsFilter },
  ],
  exports: [ErrorReporter],
})
export class ObservabilityModule {}
