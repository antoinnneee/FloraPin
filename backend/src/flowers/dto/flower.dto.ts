import {
  IsIn,
  IsISO8601,
  IsLatitude,
  IsLongitude,
  IsNumber,
  IsOptional,
  IsString,
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
}
