import { Type } from 'class-transformer';
import {
  IsBoolean,
  IsIn,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsUUID,
  MaxLength,
  ValidateNested,
} from 'class-validator';
import { AlbumPermissionMode } from '../album.entity';

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

  /**
   * TÂCHE 7.1 — rattache l'album à un groupe collaboratif EXISTANT (le créateur
   * doit en être membre accepté). Exclusif avec `collaborative`.
   */
  @IsOptional()
  @IsUUID()
  groupId?: string;

  /**
   * TÂCHE 7.1 — « créer un album crée le groupe » : quand true (et sans groupId),
   * un groupe est créé et l'album y est rattaché.
   */
  @IsOptional()
  @IsBoolean()
  collaborative?: boolean;

  /** Régime de droits initial d'un album de groupe ('open' par défaut). */
  @IsOptional()
  @IsIn(['open', 'restricted'])
  permissionMode?: AlbumPermissionMode;
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

/** Choix de la fleur de couverture d'un album. */
export class SetAlbumCoverDto {
  @IsOptional()
  @IsUUID()
  flowerId?: string | null;
}

/** Rattachement/détachement d'un album à un groupe (TÂCHE 7.1). */
export class SetAlbumGroupDto {
  /** Groupe cible, ou null pour détacher (album redevient solo). */
  @IsOptional()
  @IsUUID()
  groupId?: string | null;

  @IsOptional()
  @IsIn(['open', 'restricted'])
  permissionMode?: AlbumPermissionMode;
}

/** Droit d'édition d'un membre pour un album en mode « au cas par cas ». */
export class AlbumPermissionEntryDto {
  @IsUUID()
  userId: string;

  @IsBoolean()
  canEdit: boolean;
}

/** Configuration des droits d'un album de groupe (TÂCHE 7.1). */
export class SetAlbumPermissionsDto {
  @IsIn(['open', 'restricted'])
  mode: AlbumPermissionMode;

  /**
   * Droits par membre, appliqués uniquement en mode 'restricted'. Absent/vide en
   * mode 'open' (tous les membres éditent).
   */
  @IsOptional()
  @ValidateNested({ each: true })
  @Type(() => AlbumPermissionEntryDto)
  entries?: AlbumPermissionEntryDto[];
}
