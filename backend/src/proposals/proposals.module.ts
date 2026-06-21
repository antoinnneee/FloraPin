import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsModule } from '../notifications/notifications.module';
import { SharesModule } from '../shares/shares.module';
import { ProposalsController } from './proposals.controller';
import { ProposalsService } from './proposals.service';
import { SpeciesProposal } from './species-proposal.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([SpeciesProposal, Flower]),
    SharesModule,
    NotificationsModule,
  ],
  controllers: [ProposalsController],
  providers: [ProposalsService],
})
export class ProposalsModule {}
