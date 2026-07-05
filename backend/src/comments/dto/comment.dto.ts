import { IsString, MaxLength, MinLength } from 'class-validator';

/** Corps de POST flowers/{id}/comments : un commentaire libre. */
export class CreateCommentDto {
  @IsString()
  @MinLength(1)
  @MaxLength(1000)
  body: string;
}

/** Corps de PATCH flowers/{id}/comments/{commentId} : le nouveau texte. */
export class UpdateCommentDto {
  @IsString()
  @MinLength(1)
  @MaxLength(1000)
  body: string;
}
