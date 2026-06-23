import {
  Injectable,
  Logger,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { In, Repository } from 'typeorm';
import { FlowerPhoto } from '../flowers/flower-photo.entity';
import { Flower } from '../flowers/flower.entity';
import { StorageService } from '../storage/storage.service';
import { User } from './user.entity';

/** Accès, création et suppression des utilisateurs. */
@Injectable()
export class UsersService {
  private readonly logger = new Logger(UsersService.name);

  constructor(
    @InjectRepository(User)
    private readonly users: Repository<User>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    @InjectRepository(FlowerPhoto)
    private readonly photos: Repository<FlowerPhoto>,
    private readonly storage: StorageService,
  ) {}

  /** Normalise un email (trim + minuscules). */
  static normalizeEmail(email: string): string {
    return email.trim().toLowerCase();
  }

  findByEmail(email: string): Promise<User | null> {
    return this.users.findOne({
      where: { email: UsersService.normalizeEmail(email) },
    });
  }

  findById(id: string): Promise<User | null> {
    return this.users.findOne({ where: { id } });
  }

  create(params: {
    email: string;
    passwordHash: string;
    displayName: string;
  }): Promise<User> {
    const user = this.users.create({
      email: UsersService.normalizeEmail(params.email),
      passwordHash: params.passwordHash,
      displayName: params.displayName,
    });
    return this.users.save(user);
  }

  /**
   * Efface définitivement le compte [userId] et toutes ses données (NODE-118,
   * droit à l'effacement RGPD). Exige le mot de passe ([password]) en
   * re-authentification car l'action est irréversible.
   *
   * Purge les objets image du stockage (fleurs + photos, soft-deletes inclus)
   * AVANT de supprimer la ligne `users` : toutes les tables liées sont en
   * `ON DELETE CASCADE`, donc un seul DELETE suffit pour la base, mais les
   * objets MinIO échappent au cascade et doivent être retirés explicitement.
   */
  async deleteAccount(userId: string, password: string): Promise<void> {
    const user = await this.users.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    if (!(await bcrypt.compare(password, user.passwordHash))) {
      throw new UnauthorizedException('Mot de passe incorrect.');
    }

    const flowers = await this.flowers.find({
      where: { ownerId: userId },
      withDeleted: true,
    });
    const flowerIds = flowers.map((flower) => flower.id);
    const photos = flowerIds.length
      ? await this.photos.find({ where: { flowerId: In(flowerIds) } })
      : [];

    const keys = new Set<string>();
    for (const flower of flowers) {
      if (flower.imageKey) keys.add(flower.imageKey);
    }
    for (const photo of photos) {
      if (photo.imageKey) keys.add(photo.imageKey);
    }

    // Suppression best-effort : un objet déjà absent ne doit pas bloquer
    // l'effacement du compte.
    for (const key of keys) {
      try {
        await this.storage.delete(key);
      } catch (error) {
        this.logger.warn(
          `Échec suppression objet "${key}" lors de l'effacement du compte : ${String(error)}`,
        );
      }
    }

    // Le DELETE déclenche le cascade sur fleurs, photos, albums, amitiés,
    // partages, propositions, notifications et device tokens.
    await this.users.delete({ id: userId });
  }
}
