import { NotFoundException, UnauthorizedException } from '@nestjs/common';
import * as bcrypt from 'bcryptjs';
import { Repository } from 'typeorm';
import { FlowerPhoto } from '../flowers/flower-photo.entity';
import { Flower } from '../flowers/flower.entity';
import { StorageService } from '../storage/storage.service';
import { User } from './user.entity';
import { UsersService } from './users.service';

describe('UsersService.deleteAccount', () => {
  const USER_ID = 'user-1';
  let user: User | null;
  let users: jest.Mocked<Pick<Repository<User>, 'findOne' | 'delete'>>;
  let flowers: jest.Mocked<Pick<Repository<Flower>, 'find'>>;
  let photos: jest.Mocked<Pick<Repository<FlowerPhoto>, 'find'>>;
  let storage: jest.Mocked<Pick<StorageService, 'delete'>>;
  let service: UsersService;

  beforeEach(async () => {
    user = {
      id: USER_ID,
      passwordHash: await bcrypt.hash('correct horse', 10),
    } as User;

    users = {
      findOne: jest.fn(async () => user),
      delete: jest.fn(async () => ({ affected: 1, raw: [] })),
    } as never;
    flowers = {
      find: jest.fn(async () => [
        // f1 avec miniature ; f2 sans (fleur antérieure au réencodage).
        { id: 'f1', imageKey: 'flowers/u/a.jpg', thumbnailKey: 'flowers/u/a-thumb.webp' },
        { id: 'f2', imageKey: 'flowers/u/b.jpg', thumbnailKey: null },
      ]),
    } as never;
    photos = {
      find: jest.fn(async () => [
        { imageKey: 'flowers/u/b.jpg', thumbnailKey: null }, // doublon couverture f2
        { imageKey: 'flowers/u/c.jpg', thumbnailKey: 'flowers/u/c-thumb.webp' },
      ]),
    } as never;
    storage = { delete: jest.fn(async () => undefined) } as never;
    const emailTokens = { delete: jest.fn(async () => undefined) } as never;

    service = new UsersService(
      users as never,
      flowers as never,
      photos as never,
      emailTokens,
      storage as never,
    );
  });

  it('refuse si le mot de passe est incorrect (et ne supprime rien)', async () => {
    await expect(
      service.deleteAccount(USER_ID, 'mauvais'),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(storage.delete).not.toHaveBeenCalled();
    expect(users.delete).not.toHaveBeenCalled();
  });

  it('lève NotFound si l’utilisateur n’existe pas', async () => {
    user = null;
    await expect(
      service.deleteAccount(USER_ID, 'correct horse'),
    ).rejects.toBeInstanceOf(NotFoundException);
  });

  it('purge les objets distincts (images + miniatures) puis supprime l’utilisateur', async () => {
    await service.deleteAccount(USER_ID, 'correct horse');

    const deletedKeys = storage.delete.mock.calls.map((call) => call[0]).sort();
    // Images ET miniatures non-null, sans doublon (b.jpg partagé f2/photo).
    expect(deletedKeys).toEqual([
      'flowers/u/a-thumb.webp',
      'flowers/u/a.jpg',
      'flowers/u/b.jpg',
      'flowers/u/c-thumb.webp',
      'flowers/u/c.jpg',
    ]);
    expect(flowers.find).toHaveBeenCalledWith({
      where: { ownerId: USER_ID },
      withDeleted: true,
    });
    expect(users.delete).toHaveBeenCalledWith({ id: USER_ID });
  });

  it('continue la suppression même si le stockage échoue', async () => {
    storage.delete.mockRejectedValueOnce(new Error('minio down'));
    await service.deleteAccount(USER_ID, 'correct horse');
    expect(users.delete).toHaveBeenCalledWith({ id: USER_ID });
  });
});

describe('UsersService.updateDisplayName', () => {
  const USER_ID = 'user-1';
  let user: User | null;
  let users: jest.Mocked<Pick<Repository<User>, 'findOne' | 'save'>>;
  let service: UsersService;

  beforeEach(() => {
    user = { id: USER_ID, displayName: 'Ancien' } as User;
    users = {
      findOne: jest.fn(async () => user),
      save: jest.fn(async (u: User) => u),
    } as never;
    service = new UsersService(
      users as never,
      {} as never,
      {} as never,
      {} as never,
      {} as never,
    );
  });

  it('applique le nouveau nom et renvoie l’utilisateur', async () => {
    const updated = await service.updateDisplayName(USER_ID, 'Nouveau');
    expect(updated.displayName).toBe('Nouveau');
    expect(users.save).toHaveBeenCalledWith(
      expect.objectContaining({ id: USER_ID, displayName: 'Nouveau' }),
    );
  });

  it('lève NotFound si l’utilisateur n’existe pas', async () => {
    user = null;
    await expect(
      service.updateDisplayName(USER_ID, 'Nouveau'),
    ).rejects.toBeInstanceOf(NotFoundException);
    expect(users.save).not.toHaveBeenCalled();
  });
});
