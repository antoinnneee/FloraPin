import { IsEmail } from 'class-validator';

export class CreateFriendshipDto {
  /** Email de l'utilisateur à inviter. */
  @IsEmail()
  email: string;
}
