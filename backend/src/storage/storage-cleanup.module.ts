import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { FlowerPhoto } from '../flowers/flower-photo.entity';
import { Flower } from '../flowers/flower.entity';
import { User } from '../users/user.entity';
import { StorageCleanupService } from './storage-cleanup.service';
import { StorageModule } from './storage.module';

@Module({
  imports: [TypeOrmModule.forFeature([Flower, FlowerPhoto, User]), StorageModule],
  providers: [StorageCleanupService],
})
export class StorageCleanupModule {}
