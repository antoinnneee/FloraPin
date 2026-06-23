import {
  ArgumentsHost,
  BadRequestException,
  InternalServerErrorException,
} from '@nestjs/common';
import { AllExceptionsFilter } from './all-exceptions.filter';
import { ErrorReporter } from './error-reporter';

function makeHost(): {
  host: ArgumentsHost;
  status: jest.Mock;
  json: jest.Mock;
} {
  const json = jest.fn();
  const status = jest.fn(() => ({ json }));
  const response = { status };
  const request = { method: 'GET', path: '/api/v1/flowers' };
  const host = {
    switchToHttp: () => ({
      getResponse: () => response,
      getRequest: () => request,
    }),
  } as unknown as ArgumentsHost;
  return { host, status, json };
}

describe('AllExceptionsFilter', () => {
  let reporter: jest.Mocked<ErrorReporter>;
  let filter: AllExceptionsFilter;

  beforeEach(() => {
    reporter = { captureException: jest.fn() } as never;
    filter = new AllExceptionsFilter(reporter);
  });

  it('reporte les erreurs 5xx avec contexte sans PII', () => {
    const { host, status } = makeHost();
    filter.catch(new InternalServerErrorException('boom'), host);

    expect(status).toHaveBeenCalledWith(500);
    expect(reporter.captureException).toHaveBeenCalledTimes(1);
    expect(reporter.captureException.mock.calls[0][1]).toEqual({
      method: 'GET',
      path: '/api/v1/flowers',
      statusCode: 500,
    });
  });

  it('ne reporte pas les erreurs client 4xx mais préserve leur réponse', () => {
    const { host, status, json } = makeHost();
    filter.catch(new BadRequestException('email invalide'), host);

    expect(status).toHaveBeenCalledWith(400);
    expect(reporter.captureException).not.toHaveBeenCalled();
    expect(json).toHaveBeenCalledWith(
      expect.objectContaining({ statusCode: 400, message: 'email invalide' }),
    );
  });

  it('mappe une exception inconnue en 500 générique et la reporte', () => {
    const { host, status, json } = makeHost();
    filter.catch(new Error('non gérée'), host);

    expect(status).toHaveBeenCalledWith(500);
    expect(reporter.captureException).toHaveBeenCalledTimes(1);
    expect(json).toHaveBeenCalledWith({
      statusCode: 500,
      message: 'Internal server error',
    });
  });
});
