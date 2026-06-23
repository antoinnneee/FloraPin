import { Module } from '@nestjs/common';
import { TypeOrmModule } from '@nestjs/typeorm';
import { Species } from './species.entity';

/**
 * Référentiel d'espèces (NODE-124). Pour l'instant limité au modèle de données :
 * enregistre l'entité Species (auto-chargée par TypeORM) et la réexporte pour
 * les modules qui en dépendront (recherche/autocomplétion, seed).
 */
@Module({
  imports: [TypeOrmModule.forFeature([Species])],
  exports: [TypeOrmModule],
})
export class SpeciesModule {}
