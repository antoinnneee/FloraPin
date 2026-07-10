package com.florapin.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.abs

class MapIconScaleTest {

    @Test
    fun movementInterpolation_advancesWithoutTeleporting() {
        val point = interpolateScreenPoint(
            from = ScreenPoint(0f, 20f),
            to = ScreenPoint(100f, 220f),
            fraction = 0.25f,
        )

        assertEquals(25f, point.x, 0.001f)
        assertEquals(70f, point.y, 0.001f)
    }

    @Test
    fun consecutiveIds_areSpreadAcrossAllSlots() {
        assertEquals((0 until CALLOUT_SLOT_COUNT).toList(), (0L until 8L).map(::calloutSlot))
    }

    @Test
    fun slots_wrapAfterOneTurn() {
        assertEquals(calloutSlot(0), calloutSlot(CALLOUT_SLOT_COUNT.toLong()))
    }

    @Test
    fun negativeIds_stillProduceAValidSlot() {
        assertTrue(calloutSlot(-1) in 0 until CALLOUT_SLOT_COUNT)
    }

    @Test
    fun firstSlotPointsUpAndOppositeSlotPointsDown() {
        assertEquals((-Math.PI / 2).toFloat(), calloutAngleRadians(0), 0.0001f)
        assertEquals((Math.PI / 2).toFloat(), calloutAngleRadians(4), 0.0001f)
    }

    @Test
    fun denseFlowers_arePushedApart() {
        val anchors = (0L until 6L).map { id ->
            CalloutAnchor(id, ScreenPoint(400f + id * 3f, 600f + id * 2f))
        }

        val positions = repelCallouts(anchors, viewportWidth = 800f, viewportHeight = 1200f)
        val minimum = positions.values.flatMapIndexed { index, first ->
            positions.values.drop(index + 1).map { second ->
                hypot(second.x - first.x, second.y - first.y)
            }
        }.minOrNull()!!

        assertTrue("distance minimale: $minimum", minimum >= CALLOUT_MIN_SEPARATION_PX - 1f)
    }

    @Test
    fun isolatedPhoto_leavesItsDottedLinkVisible() {
        val anchor = CalloutAnchor(1L, ScreenPoint(400f, 600f))
        val photo = repelCallouts(listOf(anchor), 800f, 1200f).getValue(anchor.id)

        assertTrue(hypot(photo.x - anchor.point.x, photo.y - anchor.point.y) >= 200f)
        assertTrue("une place libre au-dessus doit être préférée", photo.y < anchor.point.y)
        assertTrue(abs(photo.x - anchor.point.x) < 1f)
    }

    @Test
    fun curvedPath_routesAroundAnEmoji() {
        val obstacle = ScreenPoint(120f, 100f)
        val path = harmoniousCalloutPath(
            anchor = ScreenPoint(0f, 100f),
            bubble = ScreenPoint(240f, 100f),
            emojiObstacles = listOf(obstacle),
        )
        val closest = path.drop(1).dropLast(1).minOf { point ->
            hypot(point.x - obstacle.x, point.y - obstacle.y)
        }

        assertTrue("distance de la courbe à l'emoji: $closest", closest >= 55f)
    }
}
