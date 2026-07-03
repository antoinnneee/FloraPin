import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { ILike, Repository } from 'typeorm';
import { Species } from './species.entity';
import {
  PaginatedSpeciesResponse,
  SpeciesResponse,
} from './dto/species.dto';

const DEFAULT_PAGE_SIZE = 50;
const MAX_PAGE_SIZE = 200;
const DEFAULT_SEARCH_LIMIT = 10;
const MAX_SEARCH_LIMIT = 50;

/**
 * Échappe les métacaractères LIKE/ILIKE (`\`, `%`, `_`) d'un terme utilisateur
 * pour qu'ils soient recherchés littéralement (échappement par `\`, défaut
 * Postgres). Sans ça, « R_sa » matcherait « Rosa » et rattacherait la fleur au
 * mauvais `speciesId`. Le paramètre reste lié (pas d'injection SQL) ; on neutralise
 * seulement l'interprétation des jokers.
 */
function escapeLike(term: string): string {
  return term.replace(/[\\%_]/g, '\\$&');
}

/** Encyclopédie des espèces (NODE-125) : liste, autocomplétion, fiche. */
@Injectable()
export class SpeciesService {
  constructor(
    @InjectRepository(Species)
    private readonly species: Repository<Species>,
  ) {}

  /** Liste paginée, triée par nom scientifique. */
  async list(page = 1, limit = DEFAULT_PAGE_SIZE): Promise<PaginatedSpeciesResponse> {
    const safePage = Math.max(1, page);
    const safeLimit = Math.min(Math.max(1, limit), MAX_PAGE_SIZE);
    const [rows, total] = await this.species.findAndCount({
      order: { scientificName: 'ASC' },
      skip: (safePage - 1) * safeLimit,
      take: safeLimit,
    });
    return {
      items: rows.map(SpeciesService.toResponse),
      total,
      page: safePage,
      limit: safeLimit,
    };
  }

  /**
   * Autocomplétion : sous-chaîne insensible à la casse sur le nom scientifique
   * OU le nom commun. Trié par nom scientifique, limité.
   */
  async search(q: string, limit = DEFAULT_SEARCH_LIMIT): Promise<SpeciesResponse[]> {
    const term = q.trim();
    if (!term) return [];
    const safeLimit = Math.min(Math.max(1, limit), MAX_SEARCH_LIMIT);
    const pattern = `%${escapeLike(term)}%`;
    const rows = await this.species.find({
      where: [
        { scientificName: ILike(pattern) },
        { commonName: ILike(pattern) },
      ],
      order: { scientificName: 'ASC' },
      take: safeLimit,
    });
    return rows.map(SpeciesService.toResponse);
  }

  /** Fiche détaillée d'une espèce. */
  async getById(id: string): Promise<SpeciesResponse> {
    const found = await this.species.findOne({ where: { id } });
    if (!found) {
      throw new NotFoundException('Espèce introuvable.');
    }
    return SpeciesService.toResponse(found);
  }

  /** Résout l'espèce liée à une fleur (NODE-125) : null si non rapprochée. */
  async findById(id: string | null): Promise<Species | null> {
    if (!id) return null;
    return this.species.findOne({ where: { id } });
  }

  /**
   * Rattache un texte d'identification au référentiel (NODE-127).
   *
   * Rapprochement exact insensible à la casse : d'abord par nom scientifique
   * (upsert si fourni), sinon par nom commun. À défaut de correspondance, crée
   * une fiche minimale où le texte devient le nom scientifique (le reste reste
   * à compléter). Renvoie null si le texte est vide.
   */
  async resolveOrCreateByName(name: string): Promise<Species | null> {
    const term = name.trim();
    if (!term) return null;

    // Rapprochement exact insensible à la casse : on échappe les jokers pour que
    // « Rosa % » ou « R_sa » ne matchent pas des espèces voisines par accident.
    const exact = escapeLike(term);
    const existing =
      (await this.species.findOne({
        where: { scientificName: ILike(exact) },
      })) ??
      (await this.species.findOne({ where: { commonName: ILike(exact) } }));
    if (existing) return existing;

    try {
      return await this.species.save(
        this.species.create({
          scientificName: term,
          commonName: '',
          family: '',
          description: '',
          emoji: null,
        }),
      );
    } catch {
      // Course possible sur l'unicité de scientific_name : on relit.
      return this.species.findOne({ where: { scientificName: ILike(exact) } });
    }
  }

  static toResponse(species: Species): SpeciesResponse {
    return {
      id: species.id,
      scientificName: species.scientificName,
      commonName: species.commonName,
      family: species.family,
      description: species.description,
      emoji: species.emoji,
    };
  }
}
