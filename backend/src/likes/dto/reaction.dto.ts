import { IsIn, IsOptional } from 'class-validator';
import { REACTIONS, Reaction } from '../flower-like.entity';

/**
 * Corps (optionnel) de POST flowers/{id}/like : le type de réaction. Absent
 * (anciennes apps, POST sans corps) → réaction par défaut (cœur) côté service.
 */
export class ReactionDto {
  @IsOptional()
  @IsIn(REACTIONS)
  reaction?: Reaction;
}
