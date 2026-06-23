import { IsString, MinLength } from 'class-validator';

/**
 * Corps de `DELETE /users/me` (NODE-118) : re-authentification par mot de passe,
 * exigée car l'effacement du compte est définitif et irréversible.
 */
export class DeleteAccountDto {
  @IsString()
  @MinLength(1)
  password: string;
}
