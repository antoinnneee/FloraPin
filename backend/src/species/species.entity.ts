import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

/**
 * Référentiel d'espèces botaniques (NODE-124).
 *
 * Source structurée vers laquelle pointe `flower.species_id`. Le texte libre
 * `flower.species` reste conservé : le rapprochement est best-effort, sans
 * perte de l'existant.
 */
@Entity('species')
export class Species {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  /**
   * Nom scientifique (binôme latin), unique. Indexé pour la recherche et
   * l'autocomplétion.
   */
  @Index('idx_species_scientific_name', { unique: true })
  @Column({ name: 'scientific_name', type: 'text' })
  scientificName: string;

  /** Nom commun français. */
  @Column({ name: 'common_name', type: 'text' })
  commonName: string;

  /** Famille botanique (ex. Rosaceae). */
  @Column({ type: 'text' })
  family: string;

  /** Description libre (optionnelle). */
  @Column({ type: 'text', default: '' })
  description: string;

  /** Emoji représentatif (optionnel) — utilisé en repli côté carte/app. */
  @Column({ type: 'text', nullable: true })
  emoji: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
