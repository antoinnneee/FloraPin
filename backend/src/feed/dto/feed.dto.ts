import { Type } from 'class-transformer';
import { IsIn, IsInt, IsISO8601, IsOptional, Max, Min } from 'class-validator';

export type FeedSort = 'date' | 'likes';

export class FeedQueryDto {
  @IsOptional()
  @IsISO8601()
  since?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  limit?: number;

  /** Ordre du feed : 'date' (défaut) ou 'likes' (par cœurs, NODE-139). */
  @IsOptional()
  @IsIn(['date', 'likes'])
  sort?: FeedSort;
}
