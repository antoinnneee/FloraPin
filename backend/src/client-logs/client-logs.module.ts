import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { ClientLog } from './client-log.entity';
import { ClientLogsController } from './client-logs.controller';
import { ClientLogsService } from './client-logs.service';

@Module({
  imports: [TypeOrmModule.forFeature([ClientLog])],
  controllers: [ClientLogsController],
  providers: [ClientLogsService],
})
export class ClientLogsModule {}
