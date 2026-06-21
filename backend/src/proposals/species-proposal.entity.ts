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

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
