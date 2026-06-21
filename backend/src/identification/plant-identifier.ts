/** Suggestion d'espèce renvoyée par un service d'identification. */
export interface SpeciesSuggestion {
  scientificName: string;
  commonName: string | null;
  /** Score de confiance 0..1. */
  score: number;
}

/**
 * Abstraction d'un identificateur de plantes à partir d'une image.
 * Implémentations : Pl@ntNet (réelle) ou stub (sans clé API).
 */
export abstract class PlantIdentifier {
  abstract identify(imageUrl: string): Promise<SpeciesSuggestion[]>;
}
