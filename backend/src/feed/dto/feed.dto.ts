import { Type } from 'class-transformer';
import {
  IsIn,
  IsInt,
  IsISO8601,
  IsOptional,
  Matches,
  Max,
  Min,
} from 'class-validator';

export type FeedSort = 'date' | 'likes';

/**
 * Forme d'un item du feed (TÂCHE 3.6). Les items sont sérialisés depuis
 * `FlowerResponse` (cf. flowers.service.ts) ; deux champs y servent le
 * regroupement en lot côté client, documentés ici comme contrat du feed :
 * - `shareId`  : partage ciblé de rattachement, ou null pour une diffusion
 *   réseau (`visibility='friends'`). Clé de lot fiable et stable entre pages.
 * - `sharedAt` : date du partage (`share.createdAt`, ISO8601), ou null hors
 *   partage. Repli de fenêtre temporelle pour les fleurs sans `shareId`.
 */
export interface FeedItemFields {
  shareId: string | null;
  sharedAt: string | null;
}

export class FeedQueryDto {
  @IsOptional()
  @IsISO8601()
  since?: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(200)
  limit?: number;

  /** Ordre du feed : 'date' (défaut) ou 'likes' (par cœurs, NODE-139). */
  @IsOptional()
  @IsIn(['date', 'likes'])
  sort?: FeedSort;

  /**
   * Curseur de pagination keyset descendante (TÂCHE 1.2) : ne renvoie que les
   * fleurs *strictement plus anciennes* que ce repère. Format `<ISO8601>_<id>`,
   * construit par le client à partir de la dernière fleur reçue (createdAt + id)
   * — le couple (createdAt, id) garantit un ordre stable même à date égale.
   *
   * Réservé au tri par date : `before` est INCOMPATIBLE avec `sort=likes` (ordre
   * par cœurs, non temporel) et provoque alors un 400. Complémentaire de `since`
   * (borne basse du delta) : les deux peuvent coexister.
   */
  @IsOptional()
  @Matches(/^.+_.+$/, {
    message: 'before doit être au format `<ISO8601>_<id>`.',
  })
  before?: string;
}
