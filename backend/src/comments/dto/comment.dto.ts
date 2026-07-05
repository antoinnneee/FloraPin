import { IsOptional, IsString, IsUUID, MaxLength, MinLength } from 'class-validator';

/** Corps de POST flowers/{id}/comments : un commentaire libre. */
export class CreateCommentDto {
  @IsString()
  @MinLength(1)
  @MaxLength(1000)
  body: string;

  /**
   * Réponse citée : id du commentaire auquel on répond (optionnel). Le serveur
   * aplatit vers la racine si la cible est elle-même une réponse (fil à un seul
   * niveau).
   */
  @IsOptional()
  @IsUUID()
  replyToId?: string;
}

/** Corps de PATCH flowers/{id}/comments/{commentId} : le nouveau texte. */
export class UpdateCommentDto {
  @IsString()
  @MinLength(1)
  @MaxLength(1000)
  body: string;
}
