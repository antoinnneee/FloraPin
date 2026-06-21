import { PlantIdentifier, SpeciesSuggestion } from './plant-identifier';

/**
 * Identificateur de remplacement quand aucune clé Pl@ntNet n'est configurée.
 * Renvoie une liste vide (l'identification est optionnelle — NODE-24).
 */
export class StubPlantIdentifier extends PlantIdentifier {
  async identify(_imageUrl: string): Promise<SpeciesSuggestion[]> {
    return [];
  }
}
