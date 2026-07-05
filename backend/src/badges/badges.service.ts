import { Injectable } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { FlowerComment } from '../comments/flower-comment.entity';
import { Flower } from '../flowers/flower.entity';
import { Friendship } from '../friendships/friendship.entity';
import { FlowerLike } from '../likes/flower-like.entity';
import { SpeciesProposal } from '../proposals/species-proposal.entity';

/**
 * Compteurs d'entraide de l'utilisateur courant (TÂCHE 5.4). Ces valeurs
 * alimentent les badges « entraide » (paliers) côté app. Le serveur ne renvoie
 * que les **compteurs bruts** : le mapping vers les paliers (seuils, étoiles,
 * progression « 34 / 50 ») et la fusion avec les badges « collection » locaux
 * vivent côté app (TÂCHE 5.5), qui partage le catalogue de seuils.
 */
export interface BadgeCounts {
  /** 🤝 Amis acceptés (paliers 1/3/5/10). */
  friends: number;
  /** 🔍 Propositions d'espèce que j'ai faites (toutes, quel que soit le statut). */
  proposalsMade: number;
  /** 🎓 Mes propositions qui ont été acceptées (paliers 1/5/10/25/50). */
  proposalsAccepted: number;
  /** ❓ Demandes d'identification ouvertes sur mes fleurs (needsIdentification). */
  identificationRequests: number;
  /** ✅ Propositions que j'ai acceptées en tant que propriétaire. */
  proposalsAcceptedAsOwner: number;
  /** 💬 Commentaires que j'ai postés. */
  comments: number;
  /** 👍 Réactions que j'ai données (paliers). */
  reactionsGiven: number;
  /** ❤️ Réactions reçues par mes fleurs, des autres (paliers). */
  reactionsReceived: number;
}

/**
 * Agrégation en lecture des compteurs d'entraide (sur le modèle du module
 * `likes` : pas de table dédiée, recalcul à la volée). Une seule méthode qui
 * lance quelques COUNT ciblés **en parallèle** (pas de N+1). Une table
 * `user_badges` ne serait justifiée que si le serveur devait un jour notifier
 * l'obtention d'un palier.
 */
@Injectable()
export class BadgesService {
  constructor(
    @InjectRepository(Friendship)
    private readonly friendships: Repository<Friendship>,
    @InjectRepository(SpeciesProposal)
    private readonly proposals: Repository<SpeciesProposal>,
    @InjectRepository(FlowerComment)
    private readonly comments: Repository<FlowerComment>,
    @InjectRepository(FlowerLike)
    private readonly likes: Repository<FlowerLike>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
  ) {}

  /** Renvoie tous les compteurs d'entraide de [userId] en une passe. */
  async countsFor(userId: string): Promise<BadgeCounts> {
    const [
      friends,
      proposalsMade,
      proposalsAccepted,
      identificationRequests,
      proposalsAcceptedAsOwner,
      comments,
      reactionsGiven,
      reactionsReceived,
    ] = await Promise.all([
      // 🤝 Amis : une amitié acceptée où je suis demandeur OU destinataire. Une
      // même ligne ne peut satisfaire les deux (auto-amitié interdite) : pas de
      // double comptage.
      this.friendships.count({
        where: [
          { requesterId: userId, status: 'accepted' },
          { addresseeId: userId, status: 'accepted' },
        ],
      }),
      this.proposals.count({ where: { proposedBy: userId } }),
      this.proposals.count({
        where: { proposedBy: userId, status: 'accepted' },
      }),
      // ❓ Demander : proxy sur l'état courant `needsIdentification` (la colonne
      // repasse à false une fois la fleur identifiée — il n'y a pas d'historique
      // des demandes). Les fleurs supprimées (soft-delete) sont exclues d'office.
      this.flowers.count({
        where: { ownerId: userId, needsIdentification: true },
      }),
      this.countAcceptedProposalsOnOwnedFlowers(userId),
      this.comments.count({ where: { authoredBy: userId } }),
      this.likes.count({ where: { userId } }),
      this.countReactionsReceived(userId),
    ]);

    return {
      friends,
      proposalsMade,
      proposalsAccepted,
      identificationRequests,
      proposalsAcceptedAsOwner,
      comments,
      reactionsGiven,
      reactionsReceived,
    };
  }

  /**
   * ✅ Accepter : propositions acceptées portées sur MES fleurs. Jointure
   * propositions → fleurs (owner_id), une seule requête COUNT (pas de N+1). On
   * exclut les fleurs supprimées (soft-delete).
   */
  private countAcceptedProposalsOnOwnedFlowers(
    userId: string,
  ): Promise<number> {
    return this.proposals
      .createQueryBuilder('p')
      .innerJoin(
        Flower,
        'f',
        'f.id = p.flower_id AND f.deleted_at IS NULL',
      )
      .where('f.owner_id = :userId', { userId })
      .andWhere("p.status = 'accepted'")
      .getCount();
  }

  /**
   * ❤️ Réactions reçues : cœurs posés par LES AUTRES sur mes fleurs. Jointure
   * réactions → fleurs (owner_id) en une seule requête COUNT. On exclut mes
   * propres réactions (`l.user_id != userId`) — « reçues » = venant d'autrui —
   * ainsi que les fleurs supprimées.
   */
  private countReactionsReceived(userId: string): Promise<number> {
    return this.likes
      .createQueryBuilder('l')
      .innerJoin(
        Flower,
        'f',
        'f.id = l.flower_id AND f.deleted_at IS NULL',
      )
      .where('f.owner_id = :userId', { userId })
      .andWhere('l.user_id != :userId', { userId })
      .getCount();
  }
}
