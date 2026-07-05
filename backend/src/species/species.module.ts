import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Flower } from '../flowers/flower.entity';
import { SpeciesController } from './species.controller';
import { Species } from './species.entity';
import { SpeciesService } from './species.service';

/**
 * Référentiel d'espèces (NODE-124/125). Modèle de données + API encyclopédie
 * (liste paginée, autocomplétion, fiche) + herbier de l'utilisateur (TÂCHE 5.6 :
 * espèces distinctes regroupées par famille, d'où l'accès en lecture à `Flower`).
 * Réexporte SpeciesService pour les modules qui résolvent l'espèce d'une fleur
 * (FlowersModule).
 */
@Module({
  imports: [TypeOrmModule.forFeature([Species, Flower])],
  controllers: [SpeciesController],
  providers: [SpeciesService],
  exports: [SpeciesService, TypeOrmModule],
})
export class SpeciesModule {}
