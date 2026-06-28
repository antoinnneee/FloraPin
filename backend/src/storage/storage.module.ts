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

        const accessKey = config.getOrThrow<string>('MINIO_ACCESS_KEY');
        const secretKey = config.getOrThrow<string>('MINIO_SECRET_KEY');
        const region = config.get<string>('MINIO_REGION', 'us-east-1');

        const client = new Client({
          endPoint: endpoint,
          port: Number(config.get<string>('MINIO_PORT', '9000')),
          useSSL: config.get<string>('MINIO_USE_SSL', 'false') === 'true',
          accessKey,
          secretKey,
          region,
        });

        // Client de SIGNATURE avec endpoint PUBLIC : les URLs présignées doivent
        // pointer vers un hôte joignable par l'appareil (ex. le domaine public),
        // pas vers l'hôte Docker interne `minio:9000`. Sans MINIO_PUBLIC_ENDPOINT,
        // on retombe sur le client interne (dev local où l'endpoint est déjà
        // joignable, ex. localhost). Le reverse-proxy doit router /{bucket}/* vers
        // MinIO en préservant l'en-tête Host (signature SigV4).
        const publicEndpoint = config.get<string>('MINIO_PUBLIC_ENDPOINT');
        const presignClient = publicEndpoint
          ? new Client({
              // Number(...) IMPÉRATIF : ConfigService renvoie une chaîne. Avec un
              // port "443" (string), minio-js teste `port !== 443` (strict) → vrai
              // → il ajoute `:443` au Host SIGNÉ, alors que le client envoie un
              // Host sans port → SignatureDoesNotMatch (403).
              endPoint: publicEndpoint,
              port: Number(config.get<string>('MINIO_PUBLIC_PORT', '443')),
              useSSL:
                config.get<string>('MINIO_PUBLIC_USE_SSL', 'true') === 'true',
              accessKey,
              secretKey,
              region,
            })
          : client;

        return new MinioStorageService(
          client,
          config.get<string>('MINIO_BUCKET', 'florapin'),
          config.get<number>('STORAGE_PRESIGN_EXPIRES', 600),
          region,
          presignClient,
          // Lecture : URLs persistées en local par l'app (device-first), donc
          // longue durée (défaut 7 jours = maximum SigV4) pour qu'une fleur
          // synchronisée reste visible entre deux synchros.
          config.get<number>('STORAGE_DOWNLOAD_PRESIGN_EXPIRES', 604800),
        );
      },
    },
  ],
  exports: [StorageService],
})
export class StorageModule {}
