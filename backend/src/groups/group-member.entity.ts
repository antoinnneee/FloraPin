import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
  UpdateDateColumn,
} from 'typeorm';

export type GroupMemberRole = 'owner' | 'member';
export type GroupMemberStatus = 'pending' | 'accepted';

/**
 * Appartenance d'un utilisateur à un groupe collaboratif (TÂCHE 7.1).
 *
 * - `role`   : 'owner' (créateur) ou 'member'.
 * - `status` : 'pending' (invitation en attente) ou 'accepted'.
 *
 * Une seule ligne par (groupe, utilisateur) — contrainte unique. Le créateur est
 * inséré directement en 'owner'/'accepted' à la création du groupe.
 */
@Entity('group_members')
@Unique(['groupId', 'userId'])
export class GroupMember {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'group_id', type: 'uuid' })
  groupId: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ type: 'text', default: 'member' })
  role: GroupMemberRole;

  @Column({ type: 'text', default: 'pending' })
  status: GroupMemberStatus;

  /** Auteur de l'invitation (null pour l'owner auto-inséré). */
  @Column({ name: 'invited_by', type: 'uuid', nullable: true })
  invitedBy: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
