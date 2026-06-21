import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { FlowersModule } from '../flowers/flowers.module';
import { SyncController } from './sync.controller';
import { SyncService } from './sync.service';

@Module({
  imports: [TypeOrmModule.forFeature([Flower]), FlowersModule],
  controllers: [SyncController],
  providers: [SyncService],
})
export class SyncModule {}
