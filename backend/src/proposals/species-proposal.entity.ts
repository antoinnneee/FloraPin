import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

export type ProposalStatus = 'pending' | 'accepted';

/** Proposition d'espèce faite par un ami sur une fleur non identifiée. */
@Entity('species_proposals')
export class SpeciesProposal {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'flower_id', type: 'uuid' })
  flowerId: string;

  @Column({ name: 'proposed_by', type: 'uuid' })
  proposedBy: string;

  @Column({ type: 'text' })
  species: string;

  @Column({ type: 'text', default: 'pending' })
  status: ProposalStatus;

  /**
   * Horodatage du « Merci 🌸 » envoyé par le propriétaire (TÂCHE 4.3). Null tant
   * qu'aucun merci n'a été envoyé ; sa présence rend le merci idempotent (un
   * seul merci par proposition).
   */
  @Column({ name: 'thanked_at', type: 'timestamptz', nullable: true })
  thankedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
