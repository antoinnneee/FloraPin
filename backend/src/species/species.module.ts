import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { SpeciesController } from './species.controller';
import { Species } from './species.entity';
import { SpeciesService } from './species.service';

/**
 * Référentiel d'espèces (NODE-124/125). Modèle de données + API encyclopédie
 * (liste paginée, autocomplétion, fiche). Réexporte SpeciesService pour les
 * modules qui résolvent l'espèce d'une fleur (FlowersModule).
 */
@Module({
  imports: [TypeOrmModule.forFeature([Species])],
  controllers: [SpeciesController],
  providers: [SpeciesService],
  exports: [SpeciesService, TypeOrmModule],
})
export class SpeciesModule {}
