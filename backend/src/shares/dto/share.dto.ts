import {
  IsBoolean,
  IsIn,
  IsOptional,
  IsUUID,
  ValidateIf,
} from 'class-validator';
import { ShareScope } from '../share.entity';

const SCOPES: ShareScope[] = ['all', 'flower'];

export class CreateShareDto {
  /** Ami (relation acceptée) avec qui partager. */
  @IsUUID()
  friendId: string;

  @IsIn(SCOPES)
  scope: ShareScope;

  /** Requis si scope='flower'. */
  @ValidateIf((dto: CreateShareDto) => dto.scope === 'flower')
  @IsUUID()
  flowerId?: string;

  /** Inclure les coordonnées GPS (défaut true). */
  @IsOptional()
  @IsBoolean()
  includeGps?: boolean;
}
