import { Type } from 'class-transformer';
import {
  IsInt,
  IsNotEmpty,
  IsString,
  IsOptional,
  Max,
  MaxLength,
  Min,
} from 'class-validator';

/** Pagination de la liste de l'encyclopédie (NODE-125). */
export class ListSpeciesQueryDto {
  /** Page (1-based). */
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page?: number;

  /** Taille de page (max 200). */
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  limit?: number;
}

/** Requête d'autocomplétion par nom scientifique ou commun (NODE-125). */
export class SearchSpeciesQueryDto {
  /** Terme recherché (préfixe/sous-chaîne, insensible à la casse). */
  @IsString()
  @IsNotEmpty()
  @MaxLength(200)
  q: string;

  /** Nombre maximum de suggestions (max 50). */
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit?: number;
}

/** Espèce telle qu'exposée par l'API. */
export interface SpeciesResponse {
  id: string;
  scientificName: string;
  commonName: string;
  family: string;
  description: string;
  emoji: string | null;
}

/** Page de résultats de l'encyclopédie. */
export interface PaginatedSpeciesResponse {
  items: SpeciesResponse[];
  total: number;
  page: number;
  limit: number;
}

// --- Herbier / stats de collection (TÂCHE 5.6) ---

/** Une espèce présente dans l'herbier de l'utilisateur. */
export interface HerbierSpecies {
  /** Id du référentiel, ou `null` pour une espèce en texte libre non rapprochée. */
  id: string | null;
  scientificName: string;
  commonName: string;
  emoji: string | null;
  /** Nombre de mes fleurs (actives) de cette espèce. */
  flowerCount: number;
}

/** Regroupement de l'herbier par famille botanique. */
export interface HerbierFamily {
  /**
   * Nom canonique de la famille (normalisé), ou le libellé « Non classées » pour
   * les espèces sans famille connue (texte libre non rapproché).
   */
  family: string;
  /** Nombre d'espèces distinctes dans cette famille. */
  speciesCount: number;
  /** Nombre total de fleurs (actives) dans cette famille. */
  flowerCount: number;
  species: HerbierSpecies[];
}

/** Herbier de l'utilisateur : espèces distinctes regroupées par famille. */
export interface HerbierResponse {
  /** Nombre d'espèces distinctes (toutes familles confondues). */
  distinctSpecies: number;
  /** Nombre total de fleurs (actives) rattachées à une espèce. */
  totalFlowers: number;
  /** Nombre de familles réellement classées (hors « Non classées »). */
  familyCount: number;
  families: HerbierFamily[];
}
