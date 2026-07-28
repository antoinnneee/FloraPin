package com.florapin.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.florapin.app.R
import com.florapin.app.ui.layout.bottomBarBumpHeight
import com.florapin.app.ui.layout.bottomBarHeight
import com.florapin.app.ui.layout.isLandscape

/**
 * Destinations racines persistantes de FloraPin. La photo devient l'action
 * centrale de la barre ; Carte, Amis, Détail et les autres écrans restent
 * poussés par-dessus.
 */
enum class TopLevelDestination(
    val route: String,
    @DrawableRes val icon: Int,
    val label: String,
) {
    HOME("gallery", R.drawable.ic_nav_home, "Accueil"),
    ALBUMS("albums", R.drawable.ic_album_option_01_bookmark, "Albums"),
    FEED("feed", R.drawable.ic_nav_feed, "Partagées"),
    PROFILE("profile", R.drawable.ic_nav_profile, "Profil"),
}

/** Routes des onglets racine, pour décider de l'affichage de la barre. */
val topLevelRoutes: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

/**
 * Barre photo-first : une base fine pour les quatre destinations et une
 * remontée organique uniquement autour de l'action de capture centrale.
 */
@Composable
fun FloraBottomBar(
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
    onCapture: () -> Unit,
    feedBadge: Int = 0,
) {
    val landscape = isLandscape()
    val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val cameraSize = if (landscape) 48.dp else 72.dp
    val cameraOffset = if (landscape) 6.dp else 8.dp
    // Une seule mesure pilote l'espace autour du bouton photo : berceau
    // vertical et indicateurs sélectionnés des deux onglets centraux.
    val cameraClearance = if (landscape) 6.dp else 8.dp
    val outerRadius = cameraSize / 2 + cameraClearance
    val circleCenterY = cameraOffset + cameraSize / 2
    // Un tiers du bouton dépasse de la base ; le berceau ajoute seulement son
    // espacement concentrique. La mesure partagée sert aussi à dégager la fin
    // des contenus défilables.
    val bumpHeight = bottomBarBumpHeight
    val joinSoftness = if (landscape) 4.dp else 6.dp
    val topCornerRadius = if (landscape) 14.dp else 22.dp
    val reservedHeight = bottomBarHeight + systemInset
    val totalHeight = reservedHeight + bumpHeight
    val leftDestinations = listOf(
        TopLevelDestination.HOME,
        TopLevelDestination.ALBUMS,
    )
    val rightDestinations = listOf(
        TopLevelDestination.FEED,
        TopLevelDestination.PROFILE,
    )

    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .height(reservedHeight),
        content = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight),
            ) {
                // Le contenu des écrans racine défile derrière la navigation.
                // Ce voile progressif évite une coupure nette avec sa surface,
                // tout en restant transparent au-dessus de la barre.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(bumpHeight)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface,
                                ),
                            ),
                        ),
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(totalHeight),
                    shape = CameraCradleShape(
                        baseTop = bumpHeight,
                        outerRadius = outerRadius,
                        circleCenterY = circleCenterY,
                        joinSoftness = joinSoftness,
                        cornerRadius = topCornerRadius,
                    ),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                ) {
                    Box {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(reservedHeight)
                                .padding(bottom = systemInset),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            leftDestinations.forEach { destination ->
                                DestinationItem(
                                    destination = destination,
                                    currentRoute = currentRoute,
                                    badge = 0,
                                    landscape = landscape,
                                    cameraClearance = cameraClearance,
                                    onSelect = onSelect,
                                )
                            }

                            // La réserve centrale suit exactement le diamètre du
                            // bouton ; les onglets adjacents ajoutent ensuite le
                            // même dégagement que le berceau.
                            Spacer(modifier = Modifier.width(cameraSize))

                            rightDestinations.forEach { destination ->
                                DestinationItem(
                                    destination = destination,
                                    currentRoute = currentRoute,
                                    badge = if (destination == TopLevelDestination.FEED) {
                                        feedBadge
                                    } else {
                                        0
                                    },
                                    landscape = landscape,
                                    cameraClearance = cameraClearance,
                                    onSelect = onSelect,
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onCapture,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(cameraSize)
                        .offset(y = cameraOffset),
                    shape = CircleShape,
                    containerColor = Color(0xFFA8D5BA),
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_photo_botanical),
                        contentDescription = "Prendre une photo",
                        modifier = Modifier.size(if (landscape) 28.dp else 38.dp),
                        tint = Color.Unspecified,
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val overlapPx = bumpHeight.roundToPx()
        val reservedHeightPx = constraints.maxHeight
        val placeable = measurables.single().measure(
            constraints.copy(
                minHeight = reservedHeightPx + overlapPx,
                maxHeight = reservedHeightPx + overlapPx,
            ),
        )
        layout(constraints.maxWidth, reservedHeightPx) {
            placeable.placeRelative(0, -overlapPx)
        }
    }
}

/**
 * Surface fine dont seul le centre remonte pour former un écrin autour de la
 * photo. Les épaules courbes et les coins supérieurs évitent l'effet rectangle.
 */
private class CameraCradleShape(
    private val baseTop: Dp,
    private val outerRadius: Dp,
    private val circleCenterY: Dp,
    private val joinSoftness: Dp,
    private val cornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val rise = with(density) { baseTop.toPx() }
        val radius = with(density) { outerRadius.toPx() }
        val centerY = with(density) { circleCenterY.toPx() }
        val softness = with(density) { joinSoftness.toPx() }
        val corner = with(density) { cornerRadius.toPx() }
            .coerceAtMost(size.height - rise)
        val centerX = size.width / 2f
        val centerBelowBase = (centerY - rise).coerceIn(0f, radius)
        val angleOffset = Math.toDegrees(
            kotlin.math.asin((centerBelowBase / radius).toDouble()),
        ).toFloat()
        val arcStart = 180f + angleOffset
        val arcSweep = 180f - angleOffset * 2f
        val transitionAngle = minOf(8f, arcSweep / 4f)
        val exactArcStart = arcStart + transitionAngle
        val exactArcSweep = arcSweep - transitionAngle * 2f
        val arcEnd = arcStart + arcSweep
        val exactArcEnd = arcEnd - transitionAngle
        val circleBounds = Rect(
            left = centerX - radius,
            top = centerY - radius,
            right = centerX + radius,
            bottom = centerY + radius,
        )

        fun circleX(angle: Float): Float =
            centerX + radius * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()

        fun circleY(angle: Float): Float =
            centerY + radius * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()

        val leftIntersection = circleX(arcStart)
        val rightIntersection = circleX(arcEnd)
        val leftArcX = circleX(exactArcStart)
        val leftArcY = circleY(exactArcStart)
        val rightArcX = circleX(exactArcEnd)
        val rightArcY = circleY(exactArcEnd)

        val path = Path().apply {
            moveTo(0f, rise + corner)
            quadraticTo(0f, rise, corner, rise)
            lineTo(leftIntersection - softness, rise)
            quadraticTo(leftIntersection, rise, leftArcX, leftArcY)
            arcTo(
                rect = circleBounds,
                startAngleDegrees = exactArcStart,
                sweepAngleDegrees = exactArcSweep,
                forceMoveTo = false,
            )
            quadraticTo(rightIntersection, rise, rightIntersection + softness, rise)
            lineTo(size.width - corner, rise)
            quadraticTo(size.width, rise, size.width, rise + corner)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun RowScope.DestinationItem(
    destination: TopLevelDestination,
    currentRoute: String?,
    badge: Int,
    landscape: Boolean,
    cameraClearance: Dp,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val horizontalArrangement = when (destination) {
        TopLevelDestination.ALBUMS -> Arrangement.End
        TopLevelDestination.FEED -> Arrangement.Start
        else -> Arrangement.Center
    }
    val cameraSidePadding = when (destination) {
        TopLevelDestination.ALBUMS -> Modifier.padding(end = cameraClearance)
        TopLevelDestination.FEED -> Modifier.padding(start = cameraClearance)
        else -> Modifier
    }
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .then(cameraSidePadding),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationBarItem(
            selected = currentRoute == destination.route,
            onClick = { onSelect(destination) },
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight(),
            icon = {
                BadgedBox(
                    badge = {
                        if (badge > 0) {
                            Badge { Text(if (badge > 99) "99+" else "$badge") }
                        }
                    },
                ) {
                    // L'image n'est annoncée que lorsque le libellé est masqué.
                    Image(
                        painter = painterResource(destination.icon),
                        contentDescription = destination.label.takeIf { landscape },
                        modifier = Modifier.size(if (landscape) 24.dp else 28.dp),
                    )
                }
            },
            label = if (landscape) {
                null
            } else {
                {
                    Text(
                        text = destination.label,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            },
        )
    }
}
