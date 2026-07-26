import {
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

/** Charge utile bornée afin qu'un rapport ne puisse pas saturer l'API ou la base. */
export class CreateClientLogDto {
  @IsString()
  @MinLength(1)
  @MaxLength(40)
  appVersion: string;

  @IsInt()
  @Min(1)
  @Max(2_147_483_647)
  versionCode: number;

  @IsString()
  @MinLength(1)
  @MaxLength(160)
  deviceModel: string;

  @IsString()
  @MinLength(1)
  @MaxLength(80)
  androidVersion: string;

  @IsString()
  @MinLength(1)
  @MaxLength(40)
  locale: string;

  @IsString()
  @MinLength(1)
  @MaxLength(40)
  syncStatus: string;

  @IsOptional()
  @IsString()
  @MaxLength(1_000)
  syncError?: string | null;

  @IsOptional()
  @IsString()
  @MaxLength(2_000)
  message?: string | null;

  @IsString()
  @MinLength(1)
  @MaxLength(25_000)
  logs: string;
}
