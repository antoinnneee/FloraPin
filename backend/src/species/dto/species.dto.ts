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
