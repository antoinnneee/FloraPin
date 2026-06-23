import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * Périmètre d'un partage.
 * - `all`    : toutes les fleurs du propriétaire.
 * - `flower` : une fleur précise (`flowerId`).
 * - `album`  : les fleurs d'un album précis (`albumId`) — NODE-101.
 */
export type ShareScope = 'all' | 'flower' | 'album';

/** Partage paramétrable d'un propriétaire vers un ami. */
@Entity('shares')
export class Share {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'owner_id', type: 'uuid' })
  ownerId: string;

  @Index()
  @Column({ name: 'shared_with', type: 'uuid' })
  sharedWith: string;

  @Column({ type: 'text', default: 'all' })
  scope: ShareScope;

  /** Renseigné uniquement pour scope='flower'. */
  @Column({ name: 'flower_id', type: 'uuid', nullable: true })
  flowerId: string | null;

  /** Renseigné uniquement pour scope='album'. */
  @Column({ name: 'album_id', type: 'uuid', nullable: true })
  albumId: string | null;

  /** Si false, les coordonnées GPS sont masquées (protection des spots). */
  @Column({ name: 'include_gps', type: 'boolean', default: true })
  includeGps: boolean;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
