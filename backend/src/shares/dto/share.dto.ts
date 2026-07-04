import {
  IsBoolean,
  IsIn,
  IsOptional,
  IsUUID,
  ValidateIf,
} from 'class-validator';
import { ShareScope } from '../share.entity';

const SCOPES: ShareScope[] = ['all', 'flower', 'album'];

/** Périmètre + options d'un partage, sans le destinataire. */
export class ShareToAllFriendsDto {
  @IsIn(SCOPES)
  scope: ShareScope;

  /** Requis si scope='flower'. */
  @ValidateIf((dto: ShareToAllFriendsDto) => dto.scope === 'flower')
  @IsUUID()
  flowerId?: string;

  /** Requis si scope='album'. */
  @ValidateIf((dto: ShareToAllFriendsDto) => dto.scope === 'album')
  @IsUUID()
  albumId?: string;

  /** Inclure les coordonnées GPS (défaut true). */
  @IsOptional()
  @IsBoolean()
  includeGps?: boolean;
}

export class CreateShareDto extends ShareToAllFriendsDto {
  /** Ami (relation acceptée) avec qui partager. */
  @IsUUID()
  friendId: string;
}
