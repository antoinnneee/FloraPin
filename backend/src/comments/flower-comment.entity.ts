import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * Commentaire posté sur une fleur (fil de discussion). Toute personne qui voit
 * la fleur (propriétaire, partage ciblé ou diffusion au réseau) peut commenter.
 * Cf. backend/db/schema.sql (table flower_comments).
 */
@Entity('flower_comments')
export class FlowerComment {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'flower_id', type: 'uuid' })
  flowerId: string;

  /** Auteur du commentaire. */
  @Column({ name: 'authored_by', type: 'uuid' })
  authoredBy: string;

  @Column({ type: 'text' })
  body: string;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  /** Dernière édition par l'auteur, `null` si jamais modifié. */
  @Column({ name: 'edited_at', type: 'timestamptz', nullable: true })
  editedAt: Date | null;
}
