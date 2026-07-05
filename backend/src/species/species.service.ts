import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { ILike, Repository } from 'typeorm';
import { Flower } from '../flowers/flower.entity';
import { Species } from './species.entity';
import {
  HerbierFamily,
  HerbierResponse,
  HerbierSpecies,
  PaginatedSpeciesResponse,
  SpeciesResponse,
} from './dto/species.dto';

const DEFAULT_PAGE_SIZE = 50;
const MAX_PAGE_SIZE = 200;
const DEFAULT_SEARCH_LIMIT = 10;
const MAX_SEARCH_LIMIT = 50;

/** Libellé de repli pour les espèces sans famille botanique connue. */
const UNCLASSIFIED_FAMILY = 'Non classées';

/** Ligne brute d'agrégat « fleurs rattachées au référentiel » (herbier). */
interface HerbierSpeciesRow {
  speciesId: string;
  scientificName: string;
  commonName: string;
  family: string | null;
  emoji: string | null;
  flowerCount: number;
}

/** Ligne brute d'agrégat « espèces en texte libre » (herbier). */
interface HerbierFreeTextRow {
  name: string;
  flowerCount: number;
}

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
    @InjectRepository(Flower)
    private readonly flowers: Repository<Flower>,
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

  /**
   * Herbier de l'utilisateur (TÂCHE 5.6) : ses espèces distinctes regroupées par
   * famille botanique. Deux agrégats en base (pas de N+1) :
   *  - les fleurs rattachées au référentiel (`species_id`) → famille connue ;
   *  - les fleurs en espèce **texte libre** non rapprochée (`species_id IS NULL`)
   *    → regroupées sous « Non classées » (le rapprochement best-effort du texte
   *    libre vit dans schema.sql / [resolveOrCreateByName]).
   *
   * Le regroupement par famille vit **côté serveur** (la `family` est portée par
   * `Species`) : côté app, le volet familles est donc partiel hors-ligne (assumé).
   */
  async herbierFor(userId: string): Promise<HerbierResponse> {
    const speciesRows = await this.flowers
      .createQueryBuilder('f')
      .innerJoin(Species, 's', 's.id = f.species_id')
      .select('s.id', 'speciesId')
      .addSelect('s.scientific_name', 'scientificName')
      .addSelect('s.common_name', 'commonName')
      .addSelect('s.family', 'family')
      .addSelect('s.emoji', 'emoji')
      .addSelect('COUNT(*)', 'flowerCount')
      .where('f.owner_id = :userId', { userId })
      .andWhere('f.deleted_at IS NULL')
      .groupBy('s.id')
      .addGroupBy('s.scientific_name')
      .addGroupBy('s.common_name')
      .addGroupBy('s.family')
      .addGroupBy('s.emoji')
      .getRawMany<{
        speciesId: string;
        scientificName: string;
        commonName: string;
        family: string | null;
        emoji: string | null;
        flowerCount: string;
      }>();

    const freeTextRows = await this.flowers
      .createQueryBuilder('f')
      .select('min(f.species)', 'name')
      .addSelect('COUNT(*)', 'flowerCount')
      .where('f.owner_id = :userId', { userId })
      .andWhere('f.deleted_at IS NULL')
      .andWhere('f.species_id IS NULL')
      .andWhere("btrim(coalesce(f.species, '')) <> ''")
      .groupBy('lower(btrim(f.species))')
      .getRawMany<{ name: string; flowerCount: string }>();

    return SpeciesService.buildHerbier(
      speciesRows.map((r) => ({ ...r, flowerCount: Number(r.flowerCount) })),
      freeTextRows.map((r) => ({
        name: r.name,
        flowerCount: Number(r.flowerCount),
      })),
    );
  }

  /**
   * Assemble la réponse herbier à partir des lignes brutes (fonction pure,
   * testable sans base). Les familles connues sont **normalisées** ([normalizeFamily])
   * pour fusionner les variantes de casse/espaces ; les espèces sans famille (ou
   * en texte libre) tombent dans « Non classées ». Tri : familles classées par
   * nombre d'espèces décroissant (« Non classées » en dernier), espèces par nom.
   */
  static buildHerbier(
    speciesRows: HerbierSpeciesRow[],
    freeTextRows: HerbierFreeTextRow[],
  ): HerbierResponse {
    const families = new Map<string, HerbierFamily>();
    const bucket = (label: string): HerbierFamily => {
      let fam = families.get(label);
      if (!fam) {
        fam = { family: label, speciesCount: 0, flowerCount: 0, species: [] };
        families.set(label, fam);
      }
      return fam;
    };
    const add = (label: string, species: HerbierSpecies) => {
      const fam = bucket(label);
      fam.species.push(species);
      fam.speciesCount += 1;
      fam.flowerCount += species.flowerCount;
    };

    let distinctSpecies = 0;
    let totalFlowers = 0;

    for (const r of speciesRows) {
      const label =
        SpeciesService.normalizeFamily(r.family ?? '') || UNCLASSIFIED_FAMILY;
      add(label, {
        id: r.speciesId,
        scientificName: r.scientificName,
        commonName: r.commonName,
        emoji: r.emoji,
        flowerCount: r.flowerCount,
      });
      distinctSpecies += 1;
      totalFlowers += r.flowerCount;
    }
    for (const r of freeTextRows) {
      add(UNCLASSIFIED_FAMILY, {
        id: null,
        scientificName: r.name,
        commonName: '',
        emoji: null,
        flowerCount: r.flowerCount,
      });
      distinctSpecies += 1;
      totalFlowers += r.flowerCount;
    }

    const list = [...families.values()];
    for (const fam of list) {
      fam.species.sort((a, b) =>
        a.scientificName.localeCompare(b.scientificName, 'fr'),
      );
    }
    list.sort((a, b) => {
      // « Non classées » toujours en dernier, quel que soit son effectif.
      const au = a.family === UNCLASSIFIED_FAMILY ? 1 : 0;
      const bu = b.family === UNCLASSIFIED_FAMILY ? 1 : 0;
      if (au !== bu) return au - bu;
      if (b.speciesCount !== a.speciesCount) return b.speciesCount - a.speciesCount;
      return a.family.localeCompare(b.family, 'fr');
    });

    return {
      distinctSpecies,
      totalFlowers,
      familyCount: list.filter((f) => f.family !== UNCLASSIFIED_FAMILY).length,
      families: list,
    };
  }

  /**
   * Normalise un nom de famille botanique (TÂCHE 5.6) sur le modèle de
   * [resolveOrCreateByName] : rapprochement insensible à la casse et aux espaces
   * vers une forme canonique en casse Titre (« rosaceae  » → « Rosaceae »), qui
   * correspond au référentiel embarqué (familles du seed `seed-species.sql`).
   * Les familles inconnues sont acceptées telles quelles (créables), simplement
   * nettoyées : deux orthographes équivalentes se regroupent ainsi sous un même
   * libellé. Renvoie '' pour une entrée vide.
   */
  static normalizeFamily(input: string): string {
    const collapsed = input.trim().replace(/\s+/g, ' ');
    if (!collapsed) return '';
    return collapsed.charAt(0).toUpperCase() + collapsed.slice(1).toLowerCase();
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
