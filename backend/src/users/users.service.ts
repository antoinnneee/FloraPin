import {
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import * as bcrypt from 'bcryptjs';
import { In, Repository } from 'typeorm';
import { EmailVerificationToken } from '../auth/email-verification-token.entity';
import { FlowerPhoto } from '../flowers/flower-photo.entity';
import { Flower } from '../flowers/flower.entity';
import { encodeWebp } from '../storage/image-processing';
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
    @InjectRepository(EmailVerificationToken)
    private readonly emailTokens: Repository<EmailVerificationToken>,
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

  /**
   * Charge plusieurs utilisateurs en une requête (`IN`). Utilisé pour résoudre
   * en lot les noms d'affichage (auteurs de commentaires / propositions) sans
   * multiplier les `findById` (N+1).
   */
  findByIds(ids: string[]): Promise<User[]> {
    if (ids.length === 0) {
      return Promise.resolve([]);
    }
    return this.users.find({ where: { id: In(ids) } });
  }

  /** Met à jour le hash du mot de passe d'un utilisateur (reset — NODE-116). */
  async setPasswordHash(userId: string, passwordHash: string): Promise<void> {
    await this.users.update({ id: userId }, { passwordHash });
  }

  /** Marque l'email d'un utilisateur comme vérifié (NODE-117). */
  async setEmailVerified(userId: string): Promise<void> {
    await this.users.update(
      { id: userId },
      { emailVerified: true, emailVerifiedAt: new Date() },
    );
  }

  /**
   * Change l'adresse email (NODE-117). Autorisé UNIQUEMENT tant que l'email
   * n'est pas vérifié. Vérifie l'unicité, applique la nouvelle adresse (non
   * vérifiée) et invalide les tokens de vérification en cours. Renvoie le user.
   */
  async changeEmail(userId: string, newEmail: string): Promise<User> {
    const user = await this.users.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    if (user.emailVerified) {
      throw new ForbiddenException(
        'Adresse déjà vérifiée : changement non autorisé ici.',
      );
    }

    const normalized = UsersService.normalizeEmail(newEmail);
    if (normalized !== user.email) {
      const existing = await this.users.findOne({
        where: { email: normalized },
      });
      if (existing) {
        throw new ConflictException('Un compte existe déjà avec cet email.');
      }
    }

    user.email = normalized;
    user.emailVerified = false;
    user.emailVerifiedAt = null;
    await this.users.save(user);
    // Les éventuels liens de vérification pointaient sur l'ancienne adresse.
    await this.emailTokens.delete({ userId });
    return user;
  }

  /**
   * Met à jour le nom d'affichage (TÂCHE 1.7). Le nom est déjà trimmé/validé
   * par le DTO. Renvoie l'utilisateur mis à jour. Aucun figement ailleurs : les
   * push « incarnés » (2.1) résolvent toujours le nom au moment de l'envoi.
   */
  async updateDisplayName(userId: string, displayName: string): Promise<User> {
    const user = await this.users.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('Utilisateur introuvable.');
    }
    user.displayName = displayName;
    await this.users.save(user);
    return user;
  }

  /**
   * Téléverse (ou remplace) l'avatar du compte [userId] (TÂCHE 5.1). L'image est
   * réencodée en WebP (on ne conserve que la miniature ~400px, largement
   * suffisante pour un avatar) puis stockée sur MinIO. L'ancien objet est retiré
   * best-effort. Renvoie l'utilisateur mis à jour (avec sa nouvelle `avatarKey`).
   */
  async uploadAvatar(userId: string, input: Buffer): Promise<User> {
    const user = await this.users.findOne({ where: { id: userId } });
    if (!user) {
      throw new NotFoundException('Utilisateur introuvable.');
    }

    const { thumbnail } = await encodeWebp(input);
    const avatarKey = this.storage.buildKey(userId, 'webp');
    await this.storage.putObject(avatarKey, thumbnail, 'image/webp');

    const oldKey = user.avatarKey;
    user.avatarKey = avatarKey;
    const saved = await this.users.save(user);

    // Retrait best-effort de l'ancien avatar : un objet déjà absent (ou une
    // panne MinIO) ne doit pas faire échouer le changement d'avatar.
    if (oldKey && oldKey !== avatarKey) {
      await this.storage.delete(oldKey).catch(() => undefined);
    }

    return saved;
  }

  /**
   * URL présignée de lecture de l'avatar de [user], ou `null` s'il n'en a pas.
   * Calculée à la volée (les URLs présignées expirent) — jamais persistée.
   */
  async avatarUrl(user: User): Promise<string | null> {
    return user.avatarKey ? this.storage.presignDownload(user.avatarKey) : null;
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
    // Avatar (TÂCHE 5.1) : objet distinct, hors cascade — à retirer explicitement.
    if (user.avatarKey) keys.add(user.avatarKey);
    for (const flower of flowers) {
      if (flower.imageKey) keys.add(flower.imageKey);
      // La miniature WebP est un objet distinct : sans ça, chaque fleur
      // (soft-deletes inclus, cf. withDeleted) laissait fuiter sa preview.
      if (flower.thumbnailKey) keys.add(flower.thumbnailKey);
    }
    for (const photo of photos) {
      if (photo.imageKey) keys.add(photo.imageKey);
      if (photo.thumbnailKey) keys.add(photo.thumbnailKey);
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
