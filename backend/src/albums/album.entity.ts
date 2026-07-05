import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  JoinTable,
  ManyToMany,
  PrimaryGeneratedColumn,
} from 'typeorm';
import { Flower } from '../flowers/flower.entity';

/**
 * Mode de droits d'un album collaboratif (TÂCHE 7.1) :
 * - 'open'       : tout membre accepté du groupe peut éditer l'album ;
 * - 'restricted' : seuls les membres explicitement autorisés (table
 *   `album_permissions`, `can_edit = true`) peuvent éditer ; les autres membres
 *   le voient en lecture seule.
 * Sans effet pour un album solo (`groupId` null) : seul le propriétaire y accède.
 */
export type AlbumPermissionMode = 'open' | 'restricted';

/**
 * Album : regroupement nommé de fleurs (cf. backend/db/schema.sql). Les fleurs
 * sont rattachées via la table de jointure `flower_albums`.
 *
 * Deux régimes coexistent :
 * - album SOLO   (`groupId` null) : privé au propriétaire, comportement historique ;
 * - album de GROUPE (`groupId` défini) : collaboratif — les membres du groupe le
 *   voient, l'éditent selon `permissionMode`. Découplé du partage réseau.
 */
@Entity('albums')
export class Album {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'owner_id', type: 'uuid' })
  ownerId: string;

  @Column({ type: 'text' })
  name: string;

  /**
   * Groupe collaboratif de rattachement (TÂCHE 7.1). Null = album solo (privé).
   * Plusieurs albums peuvent partager le même groupe.
   */
  @Index()
  @Column({ name: 'group_id', type: 'uuid', nullable: true })
  groupId: string | null;

  /** Régime de droits quand l'album est rattaché à un groupe. */
  @Column({ name: 'permission_mode', type: 'text', default: 'open' })
  permissionMode: AlbumPermissionMode;

  /**
   * Identifiant stable fourni par le client à la création (UUID). Rend la
   * création idempotente : voir AlbumsService.create. Null pour les albums
   * créés avant l'introduction du champ.
   */
  @Column({ name: 'client_id', type: 'uuid', nullable: true })
  clientId: string | null;

  @ManyToMany(() => Flower)
  @JoinTable({
    name: 'flower_albums',
    joinColumn: { name: 'album_id', referencedColumnName: 'id' },
    inverseJoinColumn: { name: 'flower_id', referencedColumnName: 'id' },
  })
  flowers: Flower[];

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
