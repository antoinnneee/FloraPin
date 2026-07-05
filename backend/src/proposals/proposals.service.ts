import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';
import { SpeciesService } from '../species/species.service';
import { UsersService } from '../users/users.service';
import { ProposalStatus, SpeciesProposal } from './species-proposal.entity';

/** Proposition enrichie du nom d'affichage de son auteur (NODE-31/134). */
export interface ProposalResponse {
  id: string;
  flowerId: string;
  proposedBy: string;
  /** Nom d'affichage de l'ami qui a proposé (vide si introuvable). */
  proposedByName: string;
  species: string;
  status: ProposalStatus;
  /** Horodatage du « Merci 🌸 » du propriétaire (null si non remercié — TÂCHE 4.3). */
  thankedAt: Date | null;
  createdAt: Date;
}

@Injectable()
export class ProposalsService {
  constructor(
    @InjectRepository(SpeciesProposal)
    private readonly proposals: Repository<SpeciesProposal>,
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
    private readonly shares: SharesService,
    private readonly notifications: NotificationsService,
    private readonly species: SpeciesService,
    private readonly users: UsersService,
  ) {}

  /** Un ami propose une espèce pour une fleur non identifiée qui lui est partagée. */
  async propose(
    proposerId: string,
    flowerId: string,
    species: string,
  ): Promise<SpeciesProposal> {
    const flower = await this.flowers.findOne({ where: { id: flowerId } });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    if (flower.ownerId === proposerId) {
      throw new BadRequestException(
        'Le propriétaire identifie sa fleur directement.',
      );
    }
    // On se base sur l'état autoritaire « ouverte aux propositions »
    // (needsIdentification), pas sur le texte d'espèce : après une suppression
    // puis une nouvelle demande, request() repose needsIdentification=true alors
    // qu'un ancien flower.species peut subsister. Tester species ici renvoyait
    // un 409 à tort sur ces fleurs ré-ouvertes.
    if (!flower.needsIdentification) {
      throw new ConflictException('Cette fleur n’attend pas d’identification.');
    }
    // Même périmètre que la liste « Fleurs à identifier » (NODE-133/134) : on
    // autorise la proposition sur toute fleur « à identifier » d'un ami, sans
    // exiger un partage ciblé ni une publication au flux. Contrôle booléen léger
    // (needsIdentification + amitié acceptée), sans recalculer le feed ni
    // présigner d'URL juste pour vérifier l'accès.
    if (!(await this.shares.needsIdentificationVisibleTo(proposerId, flower))) {
      throw new ForbiddenException('Fleur non accessible.');
    }

    const saved = await this.proposals.save(
      this.proposals.create({
        flowerId,
        proposedBy: proposerId,
        species,
        status: 'pending',
      }),
    );
    await this.notifications.create(flower.ownerId, 'species_proposed', {
      flowerId,
      proposalId: saved.id,
      byUserId: proposerId,
      species,
    });
    return saved;
  }

  /**
   * Le propriétaire liste les propositions reçues sur sa fleur, enrichies du nom
   * d'affichage de leur auteur (pour montrer « de qui » vient la proposition).
   */
  async listForFlower(
    ownerId: string,
    flowerId: string,
  ): Promise<ProposalResponse[]> {
    await this.ownedFlowerOrThrow(ownerId, flowerId);
    const proposals = await this.proposals.find({
      where: { flowerId },
      order: { createdAt: 'DESC' },
    });
    // Batch des auteurs : un seul chargement pour tout le lot au lieu d'un
    // users.findById par proposition (N+1).
    const authorIds = [...new Set(proposals.map((p) => p.proposedBy))];
    const authors = await this.users.findByIds(authorIds);
    const nameById = new Map(authors.map((u) => [u.id, u.displayName]));
    return proposals.map((p) => ({
      id: p.id,
      flowerId: p.flowerId,
      proposedBy: p.proposedBy,
      proposedByName: nameById.get(p.proposedBy) ?? '',
      species: p.species,
      status: p.status,
      thankedAt: p.thankedAt ?? null,
      createdAt: p.createdAt,
    }));
  }

  /**
   * Propositions reçues sur un lot de fleurs, regroupées par fleur (TÂCHE 4.1).
   *
   * Un seul chargement des propositions (In(flowerIds)) et un seul batch des
   * auteurs pour tout le lot, afin d'éviter le N+1 lorsque l'appelant compose
   * plusieurs fleurs (« Mes demandes ») en une requête.
   */
  async listForFlowerIds(
    flowerIds: string[],
  ): Promise<Map<string, ProposalResponse[]>> {
    const byFlower = new Map<string, ProposalResponse[]>();
    if (flowerIds.length === 0) {
      return byFlower;
    }
    const proposals = await this.proposals.find({
      where: { flowerId: In(flowerIds) },
      order: { createdAt: 'DESC' },
    });
    const authorIds = [...new Set(proposals.map((p) => p.proposedBy))];
    const authors = await this.users.findByIds(authorIds);
    const nameById = new Map(authors.map((u) => [u.id, u.displayName]));
    for (const p of proposals) {
      const response: ProposalResponse = {
        id: p.id,
        flowerId: p.flowerId,
        proposedBy: p.proposedBy,
        proposedByName: nameById.get(p.proposedBy) ?? '',
        species: p.species,
        status: p.status,
        thankedAt: p.thankedAt ?? null,
        createdAt: p.createdAt,
      };
      const list = byFlower.get(p.flowerId);
      if (list) list.push(response);
      else byFlower.set(p.flowerId, [response]);
    }
    return byFlower;
  }

  /** Le propriétaire refuse une proposition : elle est retirée de sa fleur. */
  async reject(
    ownerId: string,
    flowerId: string,
    proposalId: string,
  ): Promise<void> {
    await this.ownedFlowerOrThrow(ownerId, flowerId);
    const proposal = await this.proposals.findOne({
      where: { id: proposalId, flowerId },
    });
    if (!proposal) {
      throw new NotFoundException('Proposition introuvable.');
    }
    await this.proposals.delete(proposal.id);
  }

  /** Nombre de propositions de [proposerId] qui ont été acceptées (profil). */
  acceptedCountForProposer(proposerId: string): Promise<number> {
    return this.proposals.count({
      where: { proposedBy: proposerId, status: 'accepted' },
    });
  }

  /** Le propriétaire accepte une proposition : l'espèce est appliquée à la fleur. */
  async accept(
    ownerId: string,
    flowerId: string,
    proposalId: string,
  ): Promise<SpeciesProposal> {
    const flower = await this.ownedFlowerOrThrow(ownerId, flowerId);
    const proposal = await this.proposals.findOne({
      where: { id: proposalId, flowerId },
    });
    if (!proposal) {
      throw new NotFoundException('Proposition introuvable.');
    }

    // Normalisation vers le référentiel (NODE-127) : la fleur reçoit species_id
    // et son texte libre adopte le nom scientifique canonique de la fiche.
    const resolved = await this.species.resolveOrCreateByName(proposal.species);
    if (resolved) {
      flower.species = resolved.scientificName;
      flower.speciesId = resolved.id;
    } else {
      flower.species = proposal.species;
    }
    // L'identification est trouvée : la demande collaborative est close (NODE-133).
    flower.needsIdentification = false;
    await this.flowers.save(flower);

    proposal.status = 'accepted';
    const saved = await this.proposals.save(proposal);

    await this.notifications.create(proposal.proposedBy, 'species_confirmed', {
      flowerId,
      species: proposal.species,
      // Émetteur = le propriétaire qui confirme (« Marie a confirmé Coquelicot ») :
      // le nom d'affichage est résolu à l'envoi côté push (TÂCHE 2.1).
      byUserId: ownerId,
    });
    return saved;
  }

  /**
   * Le propriétaire remercie l'auteur d'une proposition (« Merci 🌸 » en un tap,
   * TÂCHE 4.3). Idempotent : un seul merci par proposition — un re-tap renvoie
   * l'état courant sans renotifier le proposeur.
   */
  async thank(
    ownerId: string,
    flowerId: string,
    proposalId: string,
  ): Promise<SpeciesProposal> {
    await this.ownedFlowerOrThrow(ownerId, flowerId);
    const proposal = await this.proposals.findOne({
      where: { id: proposalId, flowerId },
    });
    if (!proposal) {
      throw new NotFoundException('Proposition introuvable.');
    }
    // Déjà remercié : on ne renotifie pas (un seul merci par proposition).
    if (proposal.thankedAt) {
      return proposal;
    }
    proposal.thankedAt = new Date();
    const saved = await this.proposals.save(proposal);

    await this.notifications.create(proposal.proposedBy, 'species_thanked', {
      flowerId,
      species: proposal.species,
      // Émetteur = le propriétaire qui remercie (« Marie vous remercie ») :
      // le nom d'affichage est résolu à l'envoi côté push (TÂCHE 2.1).
      byUserId: ownerId,
    });
    return saved;
  }

  private async ownedFlowerOrThrow(
    ownerId: string,
    flowerId: string,
  ): Promise<Flower> {
    const flower = await this.flowers.findOne({
      where: { id: flowerId, ownerId },
    });
    if (!flower) {
      throw new NotFoundException('Fleur introuvable.');
    }
    return flower;
  }
}
