import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/**
 * Photo d'une fleur (NODE-104 : plusieurs photos par fleur).
 * Une fleur a 1..n photos ordonnées ; exactement une est la couverture
 * (`isCover`). Cf. backend/db/schema.sql (table flower_photos).
 */
@Entity('flower_photos')
export class FlowerPhoto {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'flower_id', type: 'uuid' })
  flowerId: string;

  /** Clé de l'objet image dans le stockage (MinIO). */
  @Column({ name: 'image_key' })
  imageKey: string;

  /** Ordre d'affichage. */
  @Column({ type: 'int', default: 0 })
  position: number;

  /** Photo de couverture (au plus une par fleur). */
  @Column({ name: 'is_cover', type: 'boolean', default: false })
  isCover: boolean;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
