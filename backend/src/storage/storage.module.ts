import { Module } from '@nestjs/common';
import { StorageService } from './storage.service';
import { StubStorageService } from './stub-storage.service';

/**
 * Fournit l'implémentation de [StorageService]. NODE-28 substituera
 * [StubStorageService] par l'implémentation MinIO/S3.
 */
@Module({
  providers: [{ provide: StorageService, useClass: StubStorageService }],
  exports: [StorageService],
})
export class StorageModule {}
