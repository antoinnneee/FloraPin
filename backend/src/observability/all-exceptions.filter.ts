import {
  ArgumentsHost,
  Catch,
  ExceptionFilter,
  HttpException,
  HttpStatus,
} from '@nestjs/common';
import type { Request, Response } from 'express';
import { ErrorReporter } from './error-reporter';

/**
 * Filtre d'exceptions global (NODE-122) : capture toute exception non gérée,
 * remonte les erreurs SERVEUR (5xx) au [ErrorReporter] avec un contexte sans
 * PII, et renvoie une réponse JSON cohérente. Les erreurs CLIENT (4xx :
 * validation, auth, not-found...) sont attendues : on préserve leur réponse
 * d'origine sans les reporter.
 */
@Catch()
export class AllExceptionsFilter implements ExceptionFilter {
  constructor(private readonly reporter: ErrorReporter) {}

  catch(exception: unknown, host: ArgumentsHost): void {
    const ctx = host.switchToHttp();
    const response = ctx.getResponse<Response>();
    const request = ctx.getRequest<Request>();

    const status =
      exception instanceof HttpException
        ? exception.getStatus()
        : HttpStatus.INTERNAL_SERVER_ERROR;

    if (status >= HttpStatus.INTERNAL_SERVER_ERROR) {
      this.reporter.captureException(exception, {
        method: request.method,
        // request.path = pathname sans query string → pas de PII.
        path: request.path,
        statusCode: status,
      });
    }

    const payload =
      exception instanceof HttpException
        ? exception.getResponse()
        : { statusCode: status, message: 'Internal server error' };

    response
      .status(status)
      .json(
        typeof payload === 'string'
          ? { statusCode: status, message: payload }
          : payload,
      );
  }
}
