import {
  ExecutionContext,
  ServiceUnavailableException,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { AdminGuard } from './admin.guard';

function context(token?: string): ExecutionContext {
  return {
    switchToHttp: () => ({
      getRequest: () => ({
        header: (name: string) =>
          name === 'x-admin-token' ? token : undefined,
      }),
    }),
  } as ExecutionContext;
}

describe('AdminGuard', () => {
  it('désactive les données du dashboard sans secret configuré', () => {
    const config = { get: () => '' } as unknown as ConfigService;
    expect(() => new AdminGuard(config).canActivate(context('secret'))).toThrow(
      ServiceUnavailableException,
    );
  });

  it('refuse un jeton invalide', () => {
    const config = { get: () => 'bon-secret' } as unknown as ConfigService;
    expect(() => new AdminGuard(config).canActivate(context('mauvais'))).toThrow(
      UnauthorizedException,
    );
  });

  it('accepte exactement le secret configuré', () => {
    const config = { get: () => 'bon-secret' } as unknown as ConfigService;
    expect(new AdminGuard(config).canActivate(context('bon-secret'))).toBe(true);
  });
});
