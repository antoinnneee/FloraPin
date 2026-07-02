import { Logger, OnModuleInit } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { PresignedUpload, StorageService } from './storage.service';

/**
 * Sous-ensemble du client MinIO/S3 utilisé par le service. Facilite le test
 * (injection d'un faux client) sans dépendre du SDK complet.
 */
export interface ObjectStorageClient {
  presignedPutObject(bucket: string, key: string, expiry: number): Promise<string>;
  presignedGetObject(bucket: string, key: string, expiry: number): Promise<string>;
  putObject(
    bucket: string,
    key: string,
    body: Buffer,
    size?: number,
    metaData?: Record<string, string>,
  ): Promise<unknown>;
  bucketExists(bucket: string): Promise<boolean>;
  makeBucket(bucket: string, region?: string): Promise<void>;
  removeObject(bucket: string, key: string): Promise<void>;
}

/**
 * Stockage objet S3-compatible (MinIO auto-hébergé). Les images ne sont jamais
 * servies par l'API : le client téléverse/télécharge directement via des URLs
 * présignées à durée limitée.
 */
export class MinioStorageService
  extends StorageService
  implements OnModuleInit
{
  private readonly logger = new Logger(MinioStorageService.name);

  constructor(
    private readonly client: ObjectStorageClient,
    private readonly bucket: string,
    private readonly expiresIn: number,
    private readonly region = 'us-east-1',
    /**
     * Client dédié à la SIGNATURE des URLs présignées, configuré avec un endpoint
     * PUBLIC (joignable depuis l'appareil/le navigateur). Par défaut = client
     * interne : sans endpoint public, les URLs pointeraient vers l'hôte Docker
     * interne (ex. `minio:9000`), injoignable par un autre appareil au pull.
     */
    private readonly presignClient: ObjectStorageClient = client,
    /**
     * Expiration des URLs de LECTURE (GET), distincte de l'upload. L'app est
     * device-first : elle PERSISTE ces URLs en base locale lors de la synchro et
     * les réaffiche bien plus tard. Une expiration courte (celle de l'upload)
     * rendait toutes les images d'une fleur synchronisée illisibles (403 « Request
     * has expired ») dès 10 min après le pull. On vise donc le maximum SigV4
     * (7 jours), rafraîchi à chaque synchro.
     */
    private readonly downloadExpiresIn: number = expiresIn,
  ) {
    super();
  }

  /** Crée le bucket au démarrage s'il n'existe pas (tolérant en POC). */
  async onModuleInit(): Promise<void> {
    try {
      const exists = await this.client.bucketExists(this.bucket);
      if (!exists) {
        await this.client.makeBucket(this.bucket, this.region);
        this.logger.log(`Bucket "${this.bucket}" créé.`);
      }
    } catch (error) {
      this.logger.warn(
        `Impossible de vérifier/créer le bucket "${this.bucket}" : ${String(
          error,
        )}`,
      );
    }
  }

  buildKey(ownerId: string, extension: string): string {
    return `flowers/${ownerId}/${randomUUID()}.${extension}`;
  }

  /**
   * Durée de vie maximale d'une URL d'upload présignée (secondes). Volontairement
   * courte : un PUT présigné SigV4 ne peut imposer NI taille maximale NI type de
   * contenu, et aucun point de confirmation serveur ne re-vérifie l'objet après
   * coup (le client Android actuel n'utilise d'ailleurs plus ce flux : il passe
   * par les endpoints multipart `POST /flowers/:id/image` et
   * `POST /flowers/:id/photos/:photoId/image`, validés et réencodés par l'API).
   * Réduire la fenêtre limite l'abus d'une URL fuitée ; ne pas l'allonger sans
   * ajouter une validation `statObject` post-upload.
   */
  private static readonly MAX_UPLOAD_EXPIRY_SECONDS = 300;

  async presignUpload(key: string): Promise<PresignedUpload> {
    const expiresIn = Math.min(
      this.expiresIn,
      MinioStorageService.MAX_UPLOAD_EXPIRY_SECONDS,
    );
    const url = await this.presignClient.presignedPutObject(
      this.bucket,
      key,
      expiresIn,
    );
    return { url, method: 'PUT', expiresIn };
  }

  async putObject(
    key: string,
    body: Buffer,
    contentType: string,
  ): Promise<void> {
    await this.client.putObject(this.bucket, key, body, body.length, {
      'Content-Type': contentType,
    });
  }

  async presignDownload(key: string): Promise<string> {
    return this.presignClient.presignedGetObject(
      this.bucket,
      key,
      this.downloadExpiresIn,
    );
  }

  async delete(key: string): Promise<void> {
    await this.client.removeObject(this.bucket, key);
  }
}
