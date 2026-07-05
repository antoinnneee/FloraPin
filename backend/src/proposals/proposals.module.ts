import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsModule } from '../notifications/notifications.module';
import { SharesModule } from '../shares/shares.module';
import { SpeciesModule } from '../species/species.module';
import { UsersModule } from '../users/users.module';
import { ProfileStatsController } from './profile-stats.controller';
import { ProposalsController } from './proposals.controller';
import { ProposalsService } from './proposals.service';
import { SpeciesProposal } from './species-proposal.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([SpeciesProposal, Flower]),
    SharesModule,
    NotificationsModule,
    SpeciesModule,
    UsersModule,
  ],
  controllers: [ProposalsController, ProfileStatsController],
  providers: [ProposalsService],
  exports: [ProposalsService],
})
export class ProposalsModule {}
