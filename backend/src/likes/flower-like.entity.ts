import { Column, Entity, Index, PrimaryColumn } from 'typeorm';

/**
 * Réactions possibles sur une fleur (TÂCHE 3.5). Le jeu est fermé et partagé
 * avec l'app (mêmes codes). `heart` est la réaction par défaut : elle vaut pour
 * les cœurs historiques (NODE-139) et pour tout POST /like sans corps (anciennes
 * apps qui ne connaissent pas encore les réactions typées — compat ascendante).
 */
export const REACTIONS = [
  'heart', // ❤️ défaut / cœur historique
  'love', // 😍
  'blossom', // 🌸
  'rose', // 🌹
  'daisy', // 🌼
  'lavender', // 🪻
  'magnify', // 🔍
  'thumbsup', // 👍
] as const;

export type Reaction = (typeof REACTIONS)[number];

/** Réaction posée quand aucune n'est précisée (cœur). */
export const DEFAULT_REACTION: Reaction = 'heart';

/**
 * Réaction posée par un utilisateur sur une fleur (NODE-139, enrichie TÂCHE 3.5).
 * Clé composite (flower_id, user_id) : une seule réaction par fleur et par
 * utilisateur. Changer d'emoji met à jour la colonne `reaction` (pas d'insert).
 */
@Entity('flower_likes')
export class FlowerLike {
  @PrimaryColumn({ name: 'flower_id', type: 'uuid' })
  @Index()
  flowerId: string;

  @PrimaryColumn({ name: 'user_id', type: 'uuid' })
  userId: string;

  /** Type de réaction (cf. REACTIONS). Défaut `heart` pour la compat ascendante. */
  @Column({ name: 'reaction', type: 'text', default: DEFAULT_REACTION })
  reaction: Reaction;

  @Column({ name: 'created_at', type: 'timestamptz', default: () => 'now()' })
  createdAt: Date;
}
