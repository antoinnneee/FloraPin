import { Logger, ServiceUnavailableException } from '@nestjs/common';
import { PlantIdentifier, SpeciesSuggestion } from './plant-identifier';

interface PlantNetResult {
  score: number;
  species?: {
    scientificNameWithoutAuthor?: string;
    commonNames?: string[];
  };
}

/**
 * Identification via l'API Pl@ntNet : on télécharge l'image (URL présignée) puis
 * on la POST en multipart à l'endpoint d'identification.
 */
export class PlantNetIdentifier extends PlantIdentifier {
  private readonly logger = new Logger(PlantNetIdentifier.name);

  constructor(
    private readonly apiKey: string,
    private readonly baseUrl = 'https://my-api.plantnet.org',
    private readonly maxResults = 5,
  ) {
    super();
  }

  async identify(imageUrl: string): Promise<SpeciesSuggestion[]> {
    try {
      const imageResponse = await fetch(imageUrl);
      if (!imageResponse.ok) {
        throw new Error(`image inaccessible (${imageResponse.status})`);
      }
      const blob = await imageResponse.blob();

      const form = new FormData();
      form.append('images', blob, 'flower.jpg');
      form.append('organs', 'auto');

      const url = `${this.baseUrl}/v2/identify/all?api-key=${this.apiKey}`;
      const response = await fetch(url, { method: 'POST', body: form });
      if (!response.ok) {
        throw new Error(`Pl@ntNet a répondu ${response.status}`);
      }

      const payload = (await response.json()) as { results?: PlantNetResult[] };
      return (payload.results ?? [])
        .slice(0, this.maxResults)
        .map((result) => ({
          scientificName: result.species?.scientificNameWithoutAuthor ?? '',
          commonName: result.species?.commonNames?.[0] ?? null,
          score: result.score,
        }));
    } catch (error) {
      this.logger.error(`Échec de l'identification : ${String(error)}`);
      throw new ServiceUnavailableException(
        'Service d’identification indisponible.',
      );
    }
  }
}
