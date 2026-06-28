import {
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
} from 'class-validator';

export class CreateAlbumDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name: string;

  /**
   * Identifiant stable généré par le client (UUID). Optionnel pour rester
   * compatible avec d'anciens clients, mais quand il est fourni la création
   * devient idempotente sur (owner, clientId) — anti-doublon de synchronisation.
   */
  @IsOptional()
  @IsUUID()
  clientId?: string;
}

export class UpdateAlbumDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name: string;
}

/** Rattachement d'une fleur à un album. */
export class AddFlowerToAlbumDto {
  @IsUUID()
  flowerId: string;
}
