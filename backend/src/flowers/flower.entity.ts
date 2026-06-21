import {
  Column,
  CreateDateColumn,
  DeleteDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
  UpdateDateColumn,
} from 'typeorm';

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

  @Column({ type: 'text', default: 'private' })
  visibility: FlowerVisibility;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;

  @DeleteDateColumn({ name: 'deleted_at', type: 'timestamptz' })
  deletedAt: Date | null;
}
