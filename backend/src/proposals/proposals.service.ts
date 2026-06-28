import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { NotificationsService } from '../notifications/notifications.service';
import { SharesService } from '../shares/shares.service';
import { SpeciesService } from '../species/species.service';
import { SpeciesProposal } from './species-proposal.entity';

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
    // exiger un partage ciblé ni une publication au flux. Aligné sur
    // listForViewer pour éviter qu'une fleur visible reste non « proposable ».
    const visible = await this.shares.needsIdentificationFromFriends(proposerId);
    if (!visible.some((f) => f.id === flowerId)) {
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

  /** Le propriétaire liste les propositions reçues sur sa fleur. */
  async listForFlower(
    ownerId: string,
    flowerId: string,
  ): Promise<SpeciesProposal[]> {
    await this.ownedFlowerOrThrow(ownerId, flowerId);
    return this.proposals.find({
      where: { flowerId },
      order: { createdAt: 'DESC' },
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
