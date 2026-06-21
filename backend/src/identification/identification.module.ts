import { Logger, Module } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { StorageModule } from '../storage/storage.module';
import { IdentificationController } from './identification.controller';
import { IdentificationService } from './identification.service';
import { PlantIdentifier } from './plant-identifier';
import { PlantNetIdentifier } from './plantnet.identifier';
import { StubPlantIdentifier } from './stub.identifier';

@Module({
  imports: [TypeOrmModule.forFeature([Flower]), StorageModule],
  controllers: [IdentificationController],
  providers: [
    IdentificationService,
    {
      provide: PlantIdentifier,
      inject: [ConfigService],
      useFactory: (config: ConfigService): PlantIdentifier => {
        const apiKey = config.get<string>('PLANTNET_API_KEY');
        if (!apiKey) {
          new Logger('IdentificationModule').warn(
            'Identification : StubPlantIdentifier (PLANTNET_API_KEY absent).',
          );
          return new StubPlantIdentifier();
        }
        return new PlantNetIdentifier(
          apiKey,
          config.get<string>('PLANTNET_BASE_URL', 'https://my-api.plantnet.org'),
        );
      },
    },
  ],
})
export class IdentificationModule {}
