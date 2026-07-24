import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

/** Rapport technique envoyé volontairement depuis l'application Android. */
@Entity('client_logs')
export class ClientLog {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  @Column({ name: 'app_version', type: 'text' })
  appVersion: string;

  @Column({ name: 'version_code', type: 'integer' })
  versionCode: number;

  @Column({ name: 'device_model', type: 'text' })
  deviceModel: string;

  @Column({ name: 'android_version', type: 'text' })
  androidVersion: string;

  @Column({ type: 'text' })
  locale: string;

  @Column({ name: 'sync_status', type: 'text' })
  syncStatus: string;

  @Column({ name: 'sync_error', type: 'text', nullable: true })
  syncError: string | null;

  @Column({ type: 'text' })
  logs: string;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
