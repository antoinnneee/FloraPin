package com.florapin.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Formes FloraPin (NODE-85) — coins plus arrondis que les défauts Material 3
// pour une douceur « organique » (galets, pétales). Material mappe ces rôles :
//   • extraSmall → puces, petits champs
//   • small      → boutons, chips
//   • medium     → cartes (Card)
//   • large      → conteneurs larges, feuilles modales (ModalBottomSheet)
//   • extraLarge → grandes surfaces / sheets pleine largeur
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
