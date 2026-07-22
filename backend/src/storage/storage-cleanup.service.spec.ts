import { ConfigService } from '@nestjs/config';
import { StorageCleanupService } from './storage-cleanup.service';

describe('StorageCleanupService', () => {
  it('supprime seulement les objets anciens sans référence', async () => {
    const deleted: string[] = [];
    const old = new Date(Date.now() - 48 * 60 * 60 * 1000);
    const service = new StorageCleanupService(
      { find: async () => [{ imageKey: 'flowers/u/used.webp', thumbnailKey: null }] } as never,
      { find: async () => [] } as never,
      { find: async () => [] } as never,
      {
        list: async () => [
          { key: 'flowers/u/used.webp', lastModified: old },
          { key: 'flowers/u/orphan.webp', lastModified: old },
          { key: 'flowers/u/recent.webp', lastModified: new Date() },
        ],
        delete: async (key: string) => { deleted.push(key); },
      } as never,
      new ConfigService({ STORAGE_ORPHAN_GRACE_MS: 24 * 60 * 60 * 1000 }),
    );

    expect(await service.cleanup()).toBe(1);
    expect(deleted).toEqual(['flowers/u/orphan.webp']);
  });
});
