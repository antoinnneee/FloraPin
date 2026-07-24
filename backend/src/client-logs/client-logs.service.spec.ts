import { Repository } from 'typeorm';
import { ClientLog } from './client-log.entity';
import { ClientLogsService } from './client-logs.service';

describe('ClientLogsService', () => {
  it('associe le rapport à l’utilisateur authentifié', async () => {
    const create = jest.fn((value) => value);
    const save = jest.fn(async (value) => ({
      ...value,
      id: 'report-1',
      createdAt: new Date('2026-07-24T20:00:00Z'),
    }));
    const repository = { create, save } as unknown as Repository<ClientLog>;
    const service = new ClientLogsService(repository);

    await expect(
      service.create('user-1', {
        appVersion: '1.20.0',
        versionCode: 35,
        deviceModel: 'Google Pixel',
        androidVersion: '15 (API 35)',
        locale: 'fr-FR',
        syncStatus: 'ERROR',
        syncError: 'timeout',
        logs: '07-24 W/FloraPin: timeout',
      }),
    ).resolves.toEqual({
      id: 'report-1',
      createdAt: new Date('2026-07-24T20:00:00Z'),
    });

    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({ userId: 'user-1', syncError: 'timeout' }),
    );
  });
});
