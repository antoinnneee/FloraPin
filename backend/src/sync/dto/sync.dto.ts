import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  IsArray,
  IsISO8601,
  IsOptional,
  IsString,
  MaxLength,
  ValidateNested,
} from 'class-validator';
import { CreateFlowerDto } from '../../flowers/dto/flower.dto';

/** Élément poussé : une capture locale + un identifiant client pour le mapping. */
export class PushItemDto extends CreateFlowerDto {
  @IsString()
  @MaxLength(120)
  localId: string;
}

export class SyncPushDto {
  @IsArray()
  @ArrayMaxSize(200)
  @ValidateNested({ each: true })
  @Type(() => PushItemDto)
  items: PushItemDto[];
}

export class SyncPullQueryDto {
  @IsOptional()
  @IsISO8601()
  since?: string;
}
