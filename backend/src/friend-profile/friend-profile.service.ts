import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { FlowerResponse } from '../flowers/flowers.service';
import { FriendshipsService } from '../friendships/friendships.service';
import { SharesService } from '../shares/shares.service';
import { UsersService } from '../users/users.service';

/**
 * Profil public *limité* d'un ami (TÂCHE 5.7). Ne contient QUE ce qui est déjà
 * accessible au spectateur : les fleurs de l'ami visibles par lui (partages
 * reçus ou diffusion au réseau) et des agrégats calculés dessus. Jamais les
 * stats privées de l'ami (fleurs privées, herbier complet…).
 */
export interface FriendProfileResponse {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  /** Inscription de l'ami (ancienneté du compte). */
  memberSince: Date;
  /** Amitié acceptée : depuis quand (« Amis depuis mai 2026 »). */
  friendsSince: Date;
  /** Nombre d'amis en commun (hors le spectateur et l'ami lui-même). */
  mutualFriendsCount: number;
  /** Nombre de fleurs de l'ami visibles par le spectateur. */
  visibleFlowerCount: number;
  /** Espèces présentes à la fois dans mon herbier ET chez l'ami (visibles). */
  commonSpecies: string[];
  /** Fleurs de l'ami visibles par le spectateur (partagées ou diffusées). */
  sharedFlowers: FlowerResponse[];
}

@Injectable()
export class FriendProfileService {
  constructor(
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly users: UsersService,
    private readonly friendships: FriendshipsService,
    private readonly shares: SharesService,
  ) {}

  /**
   * Assemble le profil de [targetId] tel que visible par [viewerId]. Réservé aux
   * amis acceptés : un compte inexistant ou un non-ami répondent tous deux 404
   * (anti-énumération I3, on ne révèle pas l'existence d'un compte non lié).
   */
  async getProfile(
    viewerId: string,
    targetId: string,
  ): Promise<FriendProfileResponse> {
    if (viewerId === targetId) {
      throw new BadRequestException(
        'Consultez votre propre profil depuis l’onglet Profil.',
      );
    }

    const target = await this.users.findById(targetId);
    const friendship = target
      ? await this.friendships.acceptedBetween(viewerId, targetId)
      : null;
    if (!target || !friendship) {
      throw new NotFoundException('Profil introuvable.');
    }

    // Fleurs de l'ami déjà accessibles au spectateur (partages ciblés/réseau +
    // diffusion 'friends'). On NE lit jamais ses fleurs privées.
    const visible = await this.visibleFlowersOf(viewerId, targetId);
    const [mutualFriendsCount, commonSpecies] = await Promise.all([
      this.mutualFriendsCount(viewerId, targetId),
      this.commonSpecies(viewerId, visible),
    ]);

    return {
      id: target.id,
      displayName: target.displayName,
      avatarUrl: await this.users.avatarUrl(target),
      memberSince: target.createdAt,
      friendsSince: friendship.createdAt,
      mutualFriendsCount,
      visibleFlowerCount: visible.length,
      commonSpecies,
      sharedFlowers: visible,
    };
  }

  /**
   * Fleurs de [targetId] visibles par [viewerId], dédupliquées (plus récentes
   * d'abord). Réutilise le périmètre de visibilité déjà éprouvé du feed : partages
   * reçus (`sharedWithMe`) + diffusion au réseau (`broadcastWithMe`), filtrés au
   * seul propriétaire ciblé. La variante « partagée » (shareId renseigné) prime.
   */
  private async visibleFlowersOf(
    viewerId: string,
    targetId: string,
  ): Promise<FlowerResponse[]> {
    const [shared, broadcast] = await Promise.all([
      this.shares.sharedWithMe(viewerId),
      this.shares.broadcastWithMe(viewerId),
    ]);
    const byId = new Map<string, FlowerResponse>();
    for (const f of broadcast) {
      if (f.ownerId === targetId) byId.set(f.id, f);
    }
    // Après le broadcast : une même fleur partagée écrase la version diffusée
    // (elle porte shareId/sharedAt, utiles au regroupement côté client).
    for (const f of shared) {
      if (f.ownerId === targetId) byId.set(f.id, f);
    }
    return [...byId.values()].sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }

  /** Amis en commun : intersection des deux réseaux acceptés, hors les deux intéressés. */
  private async mutualFriendsCount(
    viewerId: string,
    targetId: string,
  ): Promise<number> {
    const [mine, theirs] = await Promise.all([
      this.friendships.acceptedFriendIds(viewerId),
      this.friendships.acceptedFriendIds(targetId),
    ]);
    const mineSet = new Set(mine);
    return theirs.filter(
      (id) => id !== viewerId && id !== targetId && mineSet.has(id),
    ).length;
  }

  /**
   * Espèces communes : présentes dans MON herbier ET parmi les fleurs de l'ami
   * VISIBLES par moi (jamais son herbier complet). Comparaison insensible à la
   * casse ; on renvoie le libellé tel qu'affiché chez l'ami.
   */
  private async commonSpecies(
    viewerId: string,
    visible: FlowerResponse[],
  ): Promise<string[]> {
    const myFlowers = await this.flowers.find({ where: { ownerId: viewerId } });
    const mine = new Set<string>();
    for (const f of myFlowers) {
      const key = normalizeSpecies(f.species);
      if (key) mine.add(key);
    }

    const common = new Map<string, string>(); // clé normalisée -> libellé affiché
    for (const f of visible) {
      const label = f.speciesRef?.scientificName ?? f.species;
      const key = normalizeSpecies(label);
      if (key && mine.has(key) && !common.has(key)) {
        common.set(key, (label as string).trim());
      }
    }
    return [...common.values()];
  }
}

/** Clé de comparaison d'espèce (trim + minuscules), ou null si vide. */
function normalizeSpecies(species: string | null | undefined): string | null {
  const trimmed = (species ?? '').trim().toLowerCase();
  return trimmed.length ? trimmed : null;
}
