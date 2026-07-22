package com.florapin.app.ui.theme

import androidx.compose.ui.graphics.Color

// Palette botanique FloraPin — alignée sur la DA de la vitrine (NODE-77/78) :
// verts profonds + accent floral rose/violet, fonds blanc cassé.
//
// Chaque teinte est déclinée pour couvrir TOUS les rôles Material 3
// (primary/secondary/tertiary + container, background, surface, outline, error…)
// en clair et en sombre — voir Theme.kt pour le mapping.

// ── Teintes de marque (rappel des hex de la vitrine) ───────────────────────
val Green80 = Color(0xFFA8D5BA)   // vert clair / fond doux
val Green40 = Color(0xFF386A53)   // vert profond (primaire)
val GreenGrey80 = Color(0xFFC5D6CC)
val GreenGrey40 = Color(0xFF52635B)
val Bloom80 = Color(0xFFEFB8C8)   // accent floral clair
val Bloom40 = Color(0xFF7D5260)   // accent floral

// ── Schéma CLAIR ───────────────────────────────────────────────────────────
val md_light_primary = Color(0xFF386A53)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFFA8D5BA)
val md_light_onPrimaryContainer = Color(0xFF052014)

val md_light_secondary = Color(0xFF52635B)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFD5E8DC)
val md_light_onSecondaryContainer = Color(0xFF101F18)

val md_light_tertiary = Color(0xFF7D5260)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFFFD9E2)
val md_light_onTertiaryContainer = Color(0xFF31101D)

val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)

val md_light_background = Color(0xFFFBFDFB)
val md_light_onBackground = Color(0xFF1B1C1A)
val md_light_surface = Color(0xFFFBFDFB)
val md_light_onSurface = Color(0xFF1B1C1A)
val md_light_surfaceVariant = Color(0xFFDCE5DD)
val md_light_onSurfaceVariant = Color(0xFF404943)
val md_light_outline = Color(0xFF707972)
val md_light_outlineVariant = Color(0xFFC0C9C0)

val md_light_inverseSurface = Color(0xFF2F312E)
val md_light_inverseOnSurface = Color(0xFFF0F1EC)
val md_light_inversePrimary = Color(0xFF8DD8AE)
val md_light_scrim = Color(0xFF000000)

// ── Schéma SOMBRE ──────────────────────────────────────────────────────────
val md_dark_primary = Color(0xFF8DD8AE)
val md_dark_onPrimary = Color(0xFF00391E)
val md_dark_primaryContainer = Color(0xFF1E4F37)
val md_dark_onPrimaryContainer = Color(0xFFA8D5BA)

val md_dark_secondary = Color(0xFFB9CCC0)
val md_dark_onSecondary = Color(0xFF24352C)
val md_dark_secondaryContainer = Color(0xFF3A4B42)
val md_dark_onSecondaryContainer = Color(0xFFD5E8DC)

val md_dark_tertiary = Color(0xFFEFB8C8)
val md_dark_onTertiary = Color(0xFF4A2533)
val md_dark_tertiaryContainer = Color(0xFF633B49)
val md_dark_onTertiaryContainer = Color(0xFFFFD9E2)

val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_dark_background = Color(0xFF1B1C1A)
val md_dark_onBackground = Color(0xFFE2E3DE)
val md_dark_surface = Color(0xFF1B1C1A)
val md_dark_onSurface = Color(0xFFE2E3DE)
val md_dark_surfaceVariant = Color(0xFF404943)
val md_dark_onSurfaceVariant = Color(0xFFC0C9C0)
val md_dark_outline = Color(0xFF8A938B)
val md_dark_outlineVariant = Color(0xFF404943)

val md_dark_inverseSurface = Color(0xFFE2E3DE)
val md_dark_inverseOnSurface = Color(0xFF2F312E)
val md_dark_inversePrimary = Color(0xFF386A53)
val md_dark_scrim = Color(0xFF000000)

// ── États des badges (TÂCHE 5.5) ─────────────────────────────────────────────
// Palette dédiée à la grille de badges. Une étoile « or » chaude signale un
// palier atteint et tranche volontairement sur les verts/roses de la marque ;
// sa version « creuse » marque un palier atteignable non débloqué. L'or reste
// lisible en clair comme en sombre (couleurs fixes, non déclinées par thème).
// Les états entièrement grisés (badge sans aucun palier) réutilisent
// `onSurfaceVariant` / `surfaceVariant` du thème directement dans le composant.
val BadgeStarFilled = Color(0xFFF5B301)   // or chaud — palier atteint
val BadgeStarEmpty = Color(0xFFB9BDB5)    // gris doux — palier atteignable
val BadgeNewHighlight = Color(0xFFF5B301) // liseré de célébration (nouveau palier)
val BadgeCompletedBanner = Color(0xFFF2D56B) // bandeau d'une collection accomplie
