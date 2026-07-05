import { Transform } from 'class-transformer';
import { IsString, MaxLength, MinLength } from 'class-validator';

/**
 * Corps de PATCH /users/me (TÂCHE 1.7) : modification du nom d'affichage.
 * Mêmes règles qu'à l'inscription (trim + 1..80 caractères). Le trim est
 * appliqué avant validation pour rejeter les noms composés uniquement
 * d'espaces.
 */
export class UpdateProfileDto {
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  displayName: string;
}
