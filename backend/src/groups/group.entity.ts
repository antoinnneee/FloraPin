import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * Groupe collaboratif (TÂCHE 7.1) : unité de collaboration autour d'un ou
 * plusieurs albums. Créer un album collaboratif crée le groupe ; d'autres albums
 * peuvent être rattachés au même groupe. Découplé du partage réseau (`shares`) :
 * l'appartenance à un groupe ne dépend pas des amitiés/partages, elle vit dans
 * `group_members`.
 */
@Entity('groups')
export class Group {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  /** Créateur du groupe (membre `owner`, droits pleins). */
  @Index()
  @Column({ name: 'owner_id', type: 'uuid' })
  ownerId: string;

  @Column({ type: 'text' })
  name: string;

  /**
   * Identifiant stable fourni par le client à la création (UUID). Rend la
   * création idempotente sur (owner, clientId) — anti-doublon de sync, sur le
   * modèle des albums. Null pour les groupes créés sans clientId.
   */
  @Column({ name: 'client_id', type: 'uuid', nullable: true })
  clientId: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
