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

  /**
   * Clé de l'objet image d'avatar dans le stockage (TÂCHE 5.1). L'objet lui-même
   * (WebP) vit sur MinIO ; l'URL présignée est calculée à la lecture. `null` =
   * pas d'avatar (l'app affiche alors les initiales).
   */
  @Column({ name: 'avatar_key', type: 'text', nullable: true })
  avatarKey: string | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;
}
