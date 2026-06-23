import { ArrayNotEmpty, IsArray, IsUUID } from 'class-validator';

/** Nouvel ordre des photos d'une fleur (liste complète des ids, ordonnée). */
export class ReorderPhotosDto {
  @IsArray()
  @ArrayNotEmpty()
  @IsUUID('4', { each: true })
  photoIds: string[];
}
