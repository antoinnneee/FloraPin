import { Column, Entity, Index, PrimaryColumn } from 'typeorm';

/**
 * Cœur posé par un utilisateur sur une fleur (NODE-139). Clé composite
 * (flower_id, user_id) : un seul cœur par fleur et par utilisateur (idempotent).
 */
@Entity('flower_likes')
export class FlowerLike {
  @PrimaryColumn({ name: 'flower_id', type: 'uuid' })
  @Index()
  flowerId: string;

  @PrimaryColumn({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'created_at', type: 'timestamptz', default: () => 'now()' })
  createdAt: Date;
}
