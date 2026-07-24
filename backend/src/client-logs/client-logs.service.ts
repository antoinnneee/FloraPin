import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { ClientLog } from './client-log.entity';
import { CreateClientLogDto } from './dto/create-client-log.dto';

@Injectable()
export class ClientLogsService {
  constructor(
    @InjectRepository(ClientLog)
    private readonly logs: Repository<ClientLog>,
  ) {}

  async create(userId: string, dto: CreateClientLogDto) {
    const report = await this.logs.save(
      this.logs.create({
        ...dto,
        userId,
        syncError: dto.syncError ?? null,
      }),
    );
    return { id: report.id, createdAt: report.createdAt };
  }
}
