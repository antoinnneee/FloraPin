import { UnauthorizedException } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { UsersService } from '../users/users.service';
import { JwtStrategy } from './jwt.strategy';

describe('JwtStrategy', () => {
  const config = {
    getOrThrow: () => 'test-secret',
  } as unknown as ConfigService;

  function makeStrategy(findById: jest.Mock): JwtStrategy {
    const users = { findById } as unknown as UsersService;
    return new JwtStrategy(config, users);
  }

  it('renvoie l’identité si le compte existe encore', async () => {
    const findById = jest.fn(async () => ({
      id: 'u-1',
      email: 'a@flora.pin',
    }));
    const strategy = makeStrategy(findById);

    const result = await strategy.validate({
      sub: 'u-1',
      email: 'stale@flora.pin',
    });

    expect(findById).toHaveBeenCalledWith('u-1');
    // L'email provient de la base (source de vérité), pas du payload.
    expect(result).toEqual({ userId: 'u-1', email: 'a@flora.pin' });
  });

  it('lève 401 (au lieu de 500) si le compte a été supprimé', async () => {
    const strategy = makeStrategy(jest.fn(async () => null));

    await expect(
      strategy.validate({ sub: 'gone', email: 'gone@flora.pin' }),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });
});
