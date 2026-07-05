import {
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
} from 'class-validator';

export class CreateGroupDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name: string;

  /**
   * Identifiant stable généré par le client (UUID). Optionnel ; quand il est
   * fourni la création devient idempotente sur (owner, clientId).
   */
  @IsOptional()
  @IsUUID()
  clientId?: string;
}

export class UpdateGroupDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(120)
  name: string;
}

/** Invitation d'un membre au groupe (par UUID utilisateur, jamais par email). */
export class InviteMemberDto {
  @IsUUID()
  userId: string;
}
