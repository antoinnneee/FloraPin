import { IsUUID } from 'class-validator';

export class CreateFriendshipDto {
  /** Utilisateur à inviter. */
  @IsUUID()
  addresseeId: string;
}
