import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { FlowerComment } from '../comments/flower-comment.entity';
import { Flower } from '../flowers/flower.entity';
import { Friendship } from '../friendships/friendship.entity';
import { FlowerLike } from '../likes/flower-like.entity';
import { SpeciesProposal } from '../proposals/species-proposal.entity';
import { BadgesController } from './badges.controller';
import { BadgesService } from './badges.service';

/**
 * Badges « entraide » (TÂCHE 5.4) : agrégation en lecture des compteurs
 * collaboratifs (amis, propositions, demandes, commentaires, réactions). Aucune
 * table dédiée — recalcul à la volée sur les entités existantes.
 */
@Module({
  imports: [
    TypeOrmModule.forFeature([
      Friendship,
      SpeciesProposal,
      FlowerComment,
      FlowerLike,
      Flower,
    ]),
  ],
  controllers: [BadgesController],
  providers: [BadgesService],
})
export class BadgesModule {}
