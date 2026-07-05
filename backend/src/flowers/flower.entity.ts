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

  /**
   * Identifiant local stable généré par le client (sync push, NODE-19). Rend la
   * création idempotente : un re-push (réponse perdue / retry) retombe sur la
   * fleur existante au lieu d'en créer un doublon. Nullable : les fleurs créées
   * via l'API standard (POST /flowers) n'en ont pas. Cf. `albums.client_id`.
   */
  @Column({ name: 'client_id', type: 'text', nullable: true })
  clientId: string | null;

  /** Clé de l'objet image (pleine résolution) dans le stockage (MinIO). */
  @Column({ name: 'image_key' })
  imageKey: string;

  /**
   * Clé de la miniature WebP (NODE : preview en galerie/feed). Nullable : les
   * fleurs créées avant le réencodage serveur n'en ont pas — l'app retombe
   * alors sur l'image pleine résolution.
   */
  @Column({ name: 'thumbnail_key', type: 'text', nullable: true })
  thumbnailKey: string | null;

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
   * Diffusion au feed des amis (NODE-136) : quand la fleur est visible 'friends',
   * inclut (true) ou masque (false) ses coordonnées GPS pour les amis, à l'image
   * de l'option includeGps des partages ciblés (NODE-22). Sans effet en 'private'.
   */
  @Column({ name: 'feed_include_gps', type: 'boolean', default: true })
  feedIncludeGps: boolean;

  /**
   * Demande d'identification collaborative (NODE-133) : le propriétaire sollicite
   * ses amis. Repassé à false quand une proposition est acceptée.
   */
  @Column({ name: 'needs_identification', type: 'boolean', default: false })
  needsIdentification: boolean;

  /**
   * Horodatage de la dernière sollicitation des amis pour cette fleur (TÂCHE 4.4)
   * — posé à l'ouverture de la demande puis à chaque relance manuelle. Sert
   * d'anti-spam côté serveur : une relance est refusée tant que le délai minimal
   * n'est pas écoulé (cf. IdentificationRequestsService.REMIND_COOLDOWN_MS).
   * Nullable : fleurs jamais mises « à identifier ».
   */
  @Column({ name: 'last_reminded_at', type: 'timestamptz', nullable: true })
  lastRemindedAt: Date | null;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;

  @UpdateDateColumn({ name: 'updated_at', type: 'timestamptz' })
  updatedAt: Date;

  @DeleteDateColumn({ name: 'deleted_at', type: 'timestamptz' })
  deletedAt: Date | null;
}
