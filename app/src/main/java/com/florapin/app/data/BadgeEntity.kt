package com.florapin.app.data

import androidx.room.Entity

/**
 * Un palier de badge « collection » débloqué localement (TÂCHE 5.3).
 *
 * Chaque ligne représente **un palier** atteint d'un badge, pas seulement le
 * badge : franchir un nouveau seuil (ex. Herbier 10 puis 50) ajoute une nouvelle
 * ligne `(badgeId, tier)` distincte, célébrable indépendamment. Les badges à
 * palier unique (première fleur, saisons, outre-mer…) utilisent `tier = 1`.
 *
 * La clé primaire composite `(badgeId, tier)` garantit l'idempotence du recalcul
 * (`OnConflictStrategy.REPLACE` ne réinsère pas un palier déjà présent).
 *
 * Calcul 100 % local et device-first : voir
 * [com.florapin.app.badges.BadgeCalculator] pour la dérivation et
 * [BadgeRepository] pour la persistance (dont l'initialisation « en masse » du
 * `seen` à la première exécution).
 */
@Entity(
    tableName = "badges",
    primaryKeys = ["badgeId", "tier"],
)
data class BadgeEntity(
    /** Identifiant stable du badge (voir les constantes de `BadgeCalculator`). */
    val badgeId: String,

    /**
     * Palier atteint. Pour les badges à seuils, c'est la **valeur du seuil**
     * franchi (ex. `50` pour « Herbier 50 »), afin que l'UI puisse compter les
     * étoiles/afficher la progression. Les badges à palier unique valent `1`.
     */
    val tier: Int,

    /** Date de déblocage du palier (epoch millis). */
    val unlockedAt: Long,

    /**
     * `false` tant que le palier n'a pas été « vu » (à célébrer). Initialisé en
     * masse à `true` lors de la première exécution sur une base existante pour
     * éviter une pluie de célébrations (cf. onboarding 1.4 / [BadgeRepository]).
     */
    val seen: Boolean = false,
)
