import { Column, Entity, Index, PrimaryColumn } from 'typeorm';

/**
 * Droit d'édition d'un membre sur un album collaboratif « au cas par cas »
 * (TÂCHE 7.1). N'a de sens que pour un album dont `permissionMode = 'restricted'`
 * : seuls les membres avec `canEdit = true` peuvent modifier l'album ; les autres
 * membres du groupe le voient en lecture seule.
 *
 * En mode `permissionMode = 'open'` (tout ouvert), cette table est ignorée : tout
 * membre accepté du groupe peut éditer.
 */
@Entity('album_permissions')
export class AlbumPermission {
  @PrimaryColumn({ name: 'album_id', type: 'uuid' })
  albumId: string;

  @Index()
  @PrimaryColumn({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'can_edit', type: 'boolean', default: false })
  canEdit: boolean;
}
