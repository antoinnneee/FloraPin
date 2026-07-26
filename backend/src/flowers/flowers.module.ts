import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { FlowerLike } from '../likes/flower-like.entity';
import { FlowerComment } from '../comments/flower-comment.entity';
import { SpeciesModule } from '../species/species.module';
import { StorageModule } from '../storage/storage.module';
import { FlowerPhoto } from './flower-photo.entity';
import { FlowerPhotosController } from './flower-photos.controller';
import { FlowerPhotosService } from './flower-photos.service';
import { Flower } from './flower.entity';
import { FlowersController } from './flowers.controller';
import { FlowersService } from './flowers.service';

@Module({
  imports: [
    TypeOrmModule.forFeature([Flower, FlowerPhoto, FlowerLike, FlowerComment]),
    StorageModule,
    // Rattachement de l'espèce au référentiel à l'écriture (cf. FlowersService).
    SpeciesModule,
  ],
  controllers: [FlowersController, FlowerPhotosController],
  providers: [FlowersService, FlowerPhotosService],
  exports: [FlowersService],
})
export class FlowersModule {}
