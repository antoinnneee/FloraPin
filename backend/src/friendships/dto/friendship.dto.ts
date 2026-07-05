import { IsEmail, IsUUID } from 'class-validator';

export class CreateFriendshipDto {
  /** Email de l'utilisateur à inviter. */
  @IsEmail()
  email: string;
}

export class AddFriendByIdDto {
  /**
   * Identifiant (UUID) de l'utilisateur à inviter — encodé dans son QR code
   * (TÂCHE 4.5). On encode l'id, jamais l'email (vie privée).
   */
  @IsUUID()
  userId: string;
}
