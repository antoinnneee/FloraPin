import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  Unique,
  UpdateDateColumn,
} from 'typeorm';

export type FriendshipStatus = 'pending' | 'accepted' | 'blocked';

/** Relation d'amitié dirigée demandeur → destinataire (cf. db/schema.sql). */
@Entity('friendships')
@Unique(['requesterId', 'addresseeId'])
export class Friendship {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'requester_id', type: 'uuid' })
  requesterId: string;

  @Index()
  @Column({ name: 'addressee_id', type: 'uuid' })
  addresseeId: string;

  @Column({ type: 'text', default: 'pending' })
  status: FriendshipStatus;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
