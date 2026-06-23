import {
  Column,
  CreateDateColumn,
  Entity,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

/** Compte utilisateur (cf. backend/db/schema.sql). */
@Entity('users')
export class User {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  // Stocké en minuscules (normalisation applicative) ; unique.
  @Column({ unique: true })
  email: string;

  @Column({ name: 'password_hash' })
  passwordHash: string;

  @Column({ name: 'display_name' })
  displayName: string;

  /** Vérification d'email opt-in (NODE-117) : jamais bloquant. */
  @Column({ name: 'email_verified', type: 'boolean', default: false })
  emailVerified: boolean;

  @Column({ name: 'email_verified_at', type: 'timestamptz', nullable: true })
  emailVerifiedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
