import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { DevicePlatform, DeviceToken } from './device-token.entity';

@Injectable()
export class DeviceTokensService {
  constructor(
    @InjectRepository(DeviceToken)
    private readonly devices: Repository<DeviceToken>,
  ) {}

  /**
   * Enregistre (ou ré-attribue) un jeton d'appareil. Un même jeton est réassigné
   * à l'utilisateur courant s'il était lié à un autre compte (changement de
   * session sur l'appareil).
   */
  async register(
    userId: string,
    token: string,
    platform: DevicePlatform,
  ): Promise<DeviceToken> {
    const existing = await this.devices.findOne({ where: { token } });
    if (existing) {
      existing.userId = userId;
      existing.platform = platform;
      return this.devices.save(existing);
    }
    return this.devices.save(this.devices.create({ userId, token, platform }));
  }

  /**
   * Supprime un jeton (déconnexion / désinstallation). Restreint au
   * propriétaire du jeton : un utilisateur ne peut pas désenregistrer
   * l'appareil d'un autre compte (idempotent si le jeton est absent).
   */
  async unregister(userId: string, token: string): Promise<void> {
    await this.devices.delete({ token, userId });
  }

  /**
   * Purge interne d'un jeton signalé invalide par le fournisseur push (FCM),
   * quel que soit son propriétaire. À ne PAS exposer via l'API : les clients
   * passent par unregister(userId, token).
   */
  async purgeToken(token: string): Promise<void> {
    await this.devices.delete({ token });
  }

  /** Jetons actifs d'un utilisateur (destinataires d'un push). */
  async tokensFor(userId: string): Promise<string[]> {
    const rows = await this.devices.find({ where: { userId } });
    return rows.map((d) => d.token);
  }
}
