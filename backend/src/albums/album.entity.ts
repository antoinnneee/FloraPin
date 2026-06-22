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
 * Album : regroupement nommé de fleurs appartenant à un utilisateur
 * (cf. backend/db/schema.sql). Les fleurs sont rattachées via la table de
 * jointure `flower_albums`.
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
