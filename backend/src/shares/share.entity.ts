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

/**
 * Destinataire d'un partage.
 * - `friend`      : un ami précis (`sharedWith`).
 * - `all_friends` : tout le réseau d'amis acceptés, présents ET futurs. Aucun
 *   `sharedWith` : l'audience est résolue dynamiquement à chaque lecture contre
 *   la liste d'amis courante, si bien qu'un ami ajouté plus tard en hérite.
 */
export type ShareAudience = 'friend' | 'all_friends';

/** Partage paramétrable d'un propriétaire vers un ami (ou tout son réseau). */
@Entity('shares')
export class Share {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'owner_id', type: 'uuid' })
  ownerId: string;

  /** Destinataire précis (audience='friend') ; null si audience='all_friends'. */
  @Index()
  @Column({ name: 'shared_with', type: 'uuid', nullable: true })
  sharedWith: string | null;

  @Column({ type: 'text', default: 'friend' })
  audience: ShareAudience;

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
