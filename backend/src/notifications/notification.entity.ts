import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

export type NotificationType =
  | 'friend_request'
  | 'friend_accepted'
  | 'flower_shared'
  | 'species_proposed'
  | 'species_confirmed'
  | 'identification_requested'
  | 'flower_liked'
  | 'flower_commented';

/** Notification in-app destinée à un utilisateur. */
@Entity('notifications')
export class Notification {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ type: 'text' })
  type: NotificationType;

  /** Données contextuelles (ids référencés, etc.). */
  @Column({ type: 'jsonb', default: {} })
  data: Record<string, unknown>;

  @Column({ name: 'read_at', type: 'timestamptz', nullable: true })
  readAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
