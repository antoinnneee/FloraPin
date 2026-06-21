import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

export type DevicePlatform = 'android' | 'ios' | 'web';

/** Jeton d'appareil pour le push (FCM/APNs), associé à un utilisateur. */
@Entity('device_tokens')
export class DeviceToken {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Index()
  @Column({ name: 'user_id', type: 'uuid' })
  userId: string;

  /** Jeton de l'appareil (unique : un même jeton ne sert qu'un utilisateur). */
  @Column({ type: 'text', unique: true })
  token: string;

  @Column({ type: 'text' })
  platform: DevicePlatform;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
