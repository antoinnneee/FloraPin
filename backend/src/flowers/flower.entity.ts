import {
  Column,
  CreateDateColumn,
  DeleteDateColumn,
  Entity,
  Index,
  JoinColumn,
  ManyToOne,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';
import { Species } from '../species/species.entity';

/** Point GeoJSON (longitude, latitude) tel que stocké/lu par PostGIS. */
export interface GeoJsonPoint {
  type: 'Point';
  coordinates: [number, number]; // [lng, lat]
}

export type FlowerVisibility = 'private' | 'friends';

/** Fleur côté serveur (cf. backend/db/schema.sql). */
@Entity('flowers')
export class Flower {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'owner_id', type: 'uuid' })
  ownerId: string;

  /** Clé de l'objet image dans le stockage (MinIO). */
  @Column({ name: 'image_key' })
  imageKey: string;

  /** Position WGS84 (SRID 4326), nullable. */
  @Index({ spatial: true })
  @Column({
    type: 'geography',
    spatialFeatureType: 'Point',
    srid: 4326,
    nullable: true,
  })
  location: GeoJsonPoint | null;

  @Column({ name: 'accuracy_m', type: 'real', nullable: true })
  accuracyM: number | null;

  @Column({ name: 'taken_at', type: 'timestamptz' })
  takenAt: Date;

  @Column({ type: 'text', default: '' })
  notes: string;

  /** Espèce (nom scientifique), renseignée via identification ou manuellement. */
  @Column({ type: 'text', nullable: true })
  species: string | null;

  /**
   * Référence (best-effort) vers le référentiel d'espèces (NODE-124). Nullable :
   * le texte libre `species` ci-dessus reste la source tant qu'aucune
   * correspondance n'est établie. ON DELETE SET NULL : retirer une espèce du
   * référentiel ne supprime pas les fleurs.
   */
  @Index()
  @Column({ name: 'species_id', type: 'uuid', nullable: true })
  speciesId: string | null;

  @ManyToOne(() => Species, { nullable: true, onDelete: 'SET NULL' })
  @JoinColumn({ name: 'species_id' })
  speciesRef: Species | null;

  /** Étiquettes libres. */
  @Column({ type: 'text', array: true, default: () => "'{}'" })
  tags: string[];

  @Column({ type: 'text', default: 'private' })
  visibility: FlowerVisibility;

  /**
   * Demande d'identification collaborative (NODE-133) : le propriétaire sollicite
   * ses amis. Repassé à false quand une proposition est acceptée.
   */
  @Column({ name: 'needs_identification', type: 'boolean', default: false })
  needsIdentification: boolean;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;

  @DeleteDateColumn({ name: 'deleted_at', type: 'timestamptz' })
  deletedAt: Date | null;
}
