import {
  Injectable,
  Logger,
  OnApplicationBootstrap,
  OnModuleDestroy,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { FlowerPhoto } from '../flowers/flower-photo.entity';
import { Flower } from '../flowers/flower.entity';
import { User } from '../users/user.entity';
import { StorageService } from './storage.service';

/** Nettoie périodiquement les objets assez anciens qui ne sont plus référencés. */
@Injectable()
export class StorageCleanupService
  implements OnApplicationBootstrap, OnModuleDestroy
{
  private readonly logger = new Logger(StorageCleanupService.name);
  private timer?: NodeJS.Timeout;

  constructor(
    @InjectRepository(Flower) private readonly flowers: Repository<Flower>,
    @InjectRepository(FlowerPhoto) private readonly photos: Repository<FlowerPhoto>,
    @InjectRepository(User) private readonly users: Repository<User>,
    private readonly storage: StorageService,
    private readonly config: ConfigService,
  ) {}

  onApplicationBootstrap(): void {
    if (this.config.get<string>('STORAGE_ORPHAN_CLEANUP_ENABLED', 'true') === 'false') return;
    const interval = Number(
      this.config.get<string>(
        'STORAGE_ORPHAN_CLEANUP_INTERVAL_MS',
        String(24 * 60 * 60 * 1000),
      ),
    );
    this.timer = setInterval(() => {
      void this.cleanup().catch((error) =>
        this.logger.warn(`Nettoyage MinIO échoué : ${String(error)}`),
      );
    }, interval);
    this.timer.unref();
  }

  onModuleDestroy(): void {
    if (this.timer) clearInterval(this.timer);
  }

  async cleanup(): Promise<number> {
    const graceMs = Number(
      this.config.get<string>(
        'STORAGE_ORPHAN_GRACE_MS',
        String(24 * 60 * 60 * 1000),
      ),
    );
    const cutoff = Date.now() - graceMs;
    const [flowers, photos, users, objects] = await Promise.all([
      this.flowers.find({ withDeleted: true }),
      this.photos.find(),
      this.users.find(),
      this.storage.list('flowers/'),
    ]);
    const referenced = new Set<string>();
    for (const flower of flowers) {
      referenced.add(flower.imageKey);
      if (flower.thumbnailKey) referenced.add(flower.thumbnailKey);
    }
    for (const photo of photos) {
      referenced.add(photo.imageKey);
      if (photo.thumbnailKey) referenced.add(photo.thumbnailKey);
    }
    for (const user of users) {
      if (user.avatarKey) referenced.add(user.avatarKey);
    }

    const orphaned = objects.filter(
      (object) =>
        object.lastModified.getTime() < cutoff && !referenced.has(object.key),
    );
    await Promise.all(orphaned.map((object) => this.storage.delete(object.key)));
    if (orphaned.length > 0) {
      this.logger.log(`${orphaned.length} objet(s) MinIO orphelin(s) supprimé(s).`);
    }
    return orphaned.length;
  }
}
