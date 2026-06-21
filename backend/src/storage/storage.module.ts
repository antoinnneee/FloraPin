import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Client } from 'minio';
import { MinioStorageService } from './minio-storage.service';
import { StorageService } from './storage.service';
import { StubStorageService } from './stub-storage.service';

/**
 * Fournit l'implémentation de [StorageService].
 *
 * - `STORAGE_DRIVER=stub` (ou MinIO non configuré en dev) → [StubStorageService].
 * - sinon → [MinioStorageService] (S3/MinIO, URLs présignées).
 */
@Module({
  providers: [
    {
      provide: StorageService,
      inject: [ConfigService],
      useFactory: (config: ConfigService): StorageService => {
        const driver = config.get<string>('STORAGE_DRIVER', 'minio');
        const endpoint = config.get<string>('MINIO_ENDPOINT');

        if (driver === 'stub' || !endpoint) {
          new Logger('StorageModule').warn(
            'Stockage objet : StubStorageService (MinIO non configuré).',
          );
          return new StubStorageService();
        }

        const client = new Client({
          endPoint: endpoint,
          port: config.get<number>('MINIO_PORT', 9000),
          useSSL: config.get<string>('MINIO_USE_SSL', 'false') === 'true',
          accessKey: config.getOrThrow<string>('MINIO_ACCESS_KEY'),
          secretKey: config.getOrThrow<string>('MINIO_SECRET_KEY'),
        });

        return new MinioStorageService(
          client,
          config.get<string>('MINIO_BUCKET', 'florapin'),
          config.get<number>('STORAGE_PRESIGN_EXPIRES', 600),
        );
      },
    },
  ],
  exports: [StorageService],
})
export class StorageModule {}
