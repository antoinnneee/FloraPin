import {
  ArrayMaxSize,
  IsArray,
  IsIn,
  IsISO8601,
  IsLatitude,
  IsLongitude,
  IsNumber,
  IsOptional,
  IsString,
  IsUUID,
  Max,
  MaxLength,
  Min,
} from 'class-validator';
import { FlowerVisibility } from '../flower.entity';

const VISIBILITIES: FlowerVisibility[] = ['private', 'friends'];

export class CreateFlowerDto {
  @IsISO8601()
  takenAt: string;

  @IsOptional()
  @IsLatitude()
  latitude?: number;

  @IsOptional()
  @IsLongitude()
  longitude?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  @Max(100000)
  accuracyM?: number;

  @IsOptional()
  @IsString()
  @MaxLength(2000)
  notes?: string;

  @IsOptional()
  @IsIn(VISIBILITIES)
  visibility?: FlowerVisibility;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  species?: string;

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(20)
  @IsString({ each: true })
  @MaxLength(60, { each: true })
  tags?: string[];
}

export class UpdateFlowerDto {
  @IsOptional()
  @IsString()
  @MaxLength(2000)
  notes?: string;

  @IsOptional()
  @IsIn(VISIBILITIES)
  visibility?: FlowerVisibility;

  @IsOptional()
  @IsISO8601()
  takenAt?: string;

  @IsOptional()
  @IsString()
  @MaxLength(200)
  species?: string;

  /** Rattachement au référentiel d'espèces (NODE-128). */
  @IsOptional()
  @IsUUID()
  speciesId?: string;

  @IsOptional()
  @IsArray()
  @ArrayMaxSize(20)
  @IsString({ each: true })
  @MaxLength(60, { each: true })
  tags?: string[];
}

/** Filtres de recherche des fleurs (par espèce / étiquette). */
export class SearchFlowersQueryDto {
  @IsOptional()
  @IsString()
  @MaxLength(200)
  species?: string;

  @IsOptional()
  @IsString()
  @MaxLength(60)
  tag?: string;
}
