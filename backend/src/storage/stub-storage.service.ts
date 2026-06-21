import { Injectable } from '@nestjs/common';
import { randomUUID } from 'crypto';
import { PresignedUpload, StorageService } from './storage.service';

/**
 * Implémentation de remplacement (placeholder) du stockage objet.
 *
 * Génère des clés réalistes et des URLs factices afin que le module flowers soit
 * fonctionnel et testable. À REMPLACER par l'implémentation MinIO/S3 présignée
 * dans NODE-28.
 */
@Injectable()
export class StubStorageService extends StorageService {
  private static readonly EXPIRES_IN = 600;

  buildKey(ownerId: string, extension: string): string {
    return `flowers/${ownerId}/${randomUUID()}.${extension}`;
  }

  async presignUpload(key: string): Promise<PresignedUpload> {
    return {
      url: `https://storage.invalid/${key}?upload=stub`,
      method: 'PUT',
      expiresIn: StubStorageService.EXPIRES_IN,
    };
  }

  async presignDownload(key: string): Promise<string> {
    return `https://storage.invalid/${key}?download=stub`;
  }
}
