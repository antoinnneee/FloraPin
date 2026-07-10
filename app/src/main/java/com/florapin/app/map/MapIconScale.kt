package com.florapin.app.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.sin

/** Un point projeté dans la vue de la carte, en pixels écran. */
data class ScreenPoint(val x: Float, val y: Float)

/** Interpolation bornée entre une position affichée et la prochaine cible. */
fun interpolateScreenPoint(from: ScreenPoint, to: ScreenPoint, fraction: Float): ScreenPoint {
    val t = fraction.coerceIn(0f, 1f)
    return ScreenPoint(
        x = from.x + (to.x - from.x) * t,
        y = from.y + (to.y - from.y) * t,
    )
}

/** Ancrage GPS projeté d'une bulle photo. */
data class CalloutAnchor(val id: Long, val point: ScreenPoint)

/** Nombre de directions utilisées pour amorcer le solveur. */
const val CALLOUT_SLOT_COUNT = 8

/** Diamètre visuel cible d'une bulle, marge de respiration comprise. */
const val CALLOUT_MIN_SEPARATION_PX = 190f

private const val PREFERRED_TETHER_PX = 205f
private const val MAX_TETHER_PX = 440f
private const val ANCHOR_CLEARANCE_PX = 138f
private const val VIEWPORT_MARGIN_PX = 88f
const val CALLOUT_RELAXATION_STEPS = 64
const val CALLOUT_LIVE_RELAXATION_STEPS = 18
private const val SPRING_STRENGTH = 0.025f
private const val LINE_EMOJI_CLEARANCE_PX = 58f

/** Directions testées : haut, diagonales hautes, côtés, bas. */
private val PREFERRED_SLOT_ORDER = listOf(0, 7, 1, 6, 2, 5, 3, 4)

fun calloutSlot(markerId: Long): Int = Math.floorMod(markerId.hashCode(), CALLOUT_SLOT_COUNT)

fun calloutAngleRadians(slot: Int): Float =
    (-PI / 2.0 + Math.floorMod(slot, CALLOUT_SLOT_COUNT) * 2.0 * PI / CALLOUT_SLOT_COUNT).toFloat()

/**
 * Placement de type force-directed : chaque bulle est attirée doucement vers
 * son ancrage, repoussée par les autres bulles et tenue à l'écart des emojis.
 * Le résultat reste stable car l'ordre et l'amorce dépendent uniquement des ids.
 */
fun repelCallouts(
    anchors: List<CalloutAnchor>,
    viewportWidth: Float = 0f,
    viewportHeight: Float = 0f,
    relaxationSteps: Int = CALLOUT_RELAXATION_STEPS,
): Map<Long, ScreenPoint> {
    if (anchors.isEmpty()) return emptyMap()

    val nodes = mutableListOf<BubbleNode>()
    anchors.sortedBy { it.id }.forEach { anchor ->
        val preferred = choosePreferredPosition(
            anchor = anchor,
            anchors = anchors,
            placed = nodes,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
        )
        nodes += BubbleNode(
            id = anchor.id,
            anchorX = anchor.point.x,
            anchorY = anchor.point.y,
            preferredX = preferred.x,
            preferredY = preferred.y,
        )
    }

    repeat(relaxationSteps) {
        // Le ressort empêche une bulle isolée de partir inutilement trop loin.
        nodes.forEach { node ->
            node.x += (node.preferredX - node.x) * SPRING_STRENGTH
            node.y += (node.preferredY - node.y) * SPRING_STRENGTH
        }

        // Répulsion bulle-bulle : correction symétrique du chevauchement.
        for (i in 0 until nodes.lastIndex) {
            for (j in i + 1 until nodes.size) {
                separate(nodes[i], nodes[j], CALLOUT_MIN_SEPARATION_PX + 4f)
            }
        }

        // Les bulles ne doivent pas recouvrir les fleurs, y compris l'ancrage
        // d'une voisine. Les emojis restent ainsi toujours identifiables.
        nodes.forEach { node ->
            anchors.forEach { anchor ->
                repelFromPoint(node, anchor.point, ANCHOR_CLEARANCE_PX)
            }
            node.clampTether()
            node.clampViewport(viewportWidth, viewportHeight)
        }
    }

    return nodes.associate { it.id to ScreenPoint(it.x, it.y) }
}

/** Choisit la direction libre la plus naturelle, avec une préférence vers le haut. */
private fun choosePreferredPosition(
    anchor: CalloutAnchor,
    anchors: List<CalloutAnchor>,
    placed: List<BubbleNode>,
    viewportWidth: Float,
    viewportHeight: Float,
): ScreenPoint = PREFERRED_SLOT_ORDER.mapIndexed { preference, slot ->
    val angle = calloutAngleRadians(slot)
    val candidate = ScreenPoint(
        anchor.point.x + cos(angle) * PREFERRED_TETHER_PX,
        anchor.point.y + sin(angle) * PREFERRED_TETHER_PX,
    )
    var score = preference * 180f

    anchors.forEach { obstacle ->
        val distance = hypot(candidate.x - obstacle.point.x, candidate.y - obstacle.point.y)
        val overlap = (ANCHOR_CLEARANCE_PX - distance).coerceAtLeast(0f)
        score += overlap * overlap * 8f
        if (obstacle.id != anchor.id) {
            val routeDistance = distanceToSegment(obstacle.point, anchor.point, candidate)
            val routeOverlap = (LINE_EMOJI_CLEARANCE_PX - routeDistance).coerceAtLeast(0f)
            score += routeOverlap * routeOverlap * 12f
        }
    }
    placed.forEach { bubble ->
        val distance = hypot(candidate.x - bubble.x, candidate.y - bubble.y)
        val overlap = (CALLOUT_MIN_SEPARATION_PX - distance).coerceAtLeast(0f)
        score += overlap * overlap * 10f
    }
    score += viewportPenalty(candidate, viewportWidth, viewportHeight)
    candidate to score
}.minBy { it.second }.first

/**
 * Construit une courbe quadratique douce en choisissant le côté qui s'éloigne
 * le plus des autres emojis. Les points retournés peuvent devenir une LineString.
 */
fun harmoniousCalloutPath(
    anchor: ScreenPoint,
    bubble: ScreenPoint,
    emojiObstacles: List<ScreenPoint>,
    samples: Int = 14,
): List<ScreenPoint> {
    val dx = bubble.x - anchor.x
    val dy = bubble.y - anchor.y
    val length = hypot(dx, dy).coerceAtLeast(1f)
    val normalX = -dy / length
    val normalY = dx / length
    val midpointX = (anchor.x + bubble.x) / 2f
    val midpointY = (anchor.y + bubble.y) / 2f
    val bends = listOf(28f, -28f, 56f, -56f, 96f, -96f, 140f, -140f)

    val bestBend = bends.minBy { bend ->
        var score = (abs(bend) - 28f) * 0.4f
        for (index in 1 until samples) {
            val t = index.toFloat() / samples
            val point = quadraticPoint(
                anchor = anchor,
                control = ScreenPoint(midpointX + normalX * bend, midpointY + normalY * bend),
                bubble = bubble,
                t = t,
            )
            emojiObstacles.forEach { obstacle ->
                val distance = hypot(point.x - obstacle.x, point.y - obstacle.y)
                val overlap = (LINE_EMOJI_CLEARANCE_PX - distance).coerceAtLeast(0f)
                score += overlap * overlap * 20f
            }
        }
        score
    }
    val control = ScreenPoint(
        midpointX + normalX * bestBend,
        midpointY + normalY * bestBend,
    )
    return (0..samples).map { index ->
        quadraticPoint(anchor, control, bubble, index.toFloat() / samples)
    }
}

private fun quadraticPoint(
    anchor: ScreenPoint,
    control: ScreenPoint,
    bubble: ScreenPoint,
    t: Float,
): ScreenPoint {
    val inverse = 1f - t
    return ScreenPoint(
        x = inverse * inverse * anchor.x + 2f * inverse * t * control.x + t * t * bubble.x,
        y = inverse * inverse * anchor.y + 2f * inverse * t * control.y + t * t * bubble.y,
    )
}

private fun viewportPenalty(point: ScreenPoint, width: Float, height: Float): Float {
    var score = 0f
    if (width > VIEWPORT_MARGIN_PX * 2f) {
        if (point.x < VIEWPORT_MARGIN_PX) score += (VIEWPORT_MARGIN_PX - point.x) * 5_000f
        if (point.x > width - VIEWPORT_MARGIN_PX) {
            score += (point.x - width + VIEWPORT_MARGIN_PX) * 5_000f
        }
    }
    if (height > VIEWPORT_MARGIN_PX * 2f) {
        if (point.y < VIEWPORT_MARGIN_PX) score += (VIEWPORT_MARGIN_PX - point.y) * 5_000f
        if (point.y > height - VIEWPORT_MARGIN_PX) {
            score += (point.y - height + VIEWPORT_MARGIN_PX) * 5_000f
        }
    }
    return score
}

private fun distanceToSegment(point: ScreenPoint, start: ScreenPoint, end: ScreenPoint): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.001f) return hypot(point.x - start.x, point.y - start.y)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared)
        .coerceIn(0f, 1f)
    return hypot(point.x - (start.x + t * dx), point.y - (start.y + t * dy))
}

private class BubbleNode(
    val id: Long,
    val anchorX: Float,
    val anchorY: Float,
    val preferredX: Float,
    val preferredY: Float,
) {
    var x: Float = preferredX
    var y: Float = preferredY

    fun clampTether() {
        val dx = x - anchorX
        val dy = y - anchorY
        val distance = hypot(dx, dy)
        if (distance > MAX_TETHER_PX) {
            x = anchorX + dx / distance * MAX_TETHER_PX
            y = anchorY + dy / distance * MAX_TETHER_PX
        }
    }

    fun clampViewport(width: Float, height: Float) {
        if (width > VIEWPORT_MARGIN_PX * 2f) {
            x = x.coerceIn(VIEWPORT_MARGIN_PX, width - VIEWPORT_MARGIN_PX)
        }
        if (height > VIEWPORT_MARGIN_PX * 2f) {
            y = y.coerceIn(VIEWPORT_MARGIN_PX, height - VIEWPORT_MARGIN_PX)
        }
    }
}

private fun separate(first: BubbleNode, second: BubbleNode, minimum: Float) {
    var dx = second.x - first.x
    var dy = second.y - first.y
    var distance = hypot(dx, dy)
    if (distance >= minimum) return
    if (distance < 0.001f) {
        val angle = calloutAngleRadians(calloutSlot(first.id xor second.id xor 0x5fL))
        dx = cos(angle)
        dy = sin(angle)
        distance = 1f
    }
    val shift = (minimum - distance) * 0.51f
    val ux = dx / distance
    val uy = dy / distance
    first.x -= ux * shift
    first.y -= uy * shift
    second.x += ux * shift
    second.y += uy * shift
}

private fun repelFromPoint(node: BubbleNode, point: ScreenPoint, minimum: Float) {
    var dx = node.x - point.x
    var dy = node.y - point.y
    var distance = hypot(dx, dy)
    if (distance >= minimum) return
    if (distance < 0.001f) {
        val angle = calloutAngleRadians(calloutSlot(node.id))
        dx = cos(angle)
        dy = sin(angle)
        distance = 1f
    }
    val shift = minimum - distance
    node.x += dx / distance * shift
    node.y += dy / distance * shift
}
