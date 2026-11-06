package com.skyage.app

import com.skyage.app.ui.LabelLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The globe's airport codes must never stack: Heathrow, Amsterdam and Frankfurt all live inside a
 * few hundred pixels, and JFK / EWR are the same pixel. Placement is checked here rather than by
 * eye, because the labels are drawn straight onto the Canvas.
 */
class LabelLayoutTest {

    private val out = FloatArray(2)

    private fun place(
        layout: LabelLayout,
        x: Float,
        y: Float,
        w: Float = 30f,
        h: Float = 12f,
        boundsW: Float = 400f,
        boundsH: Float = 400f,
    ) = layout.place(x, y, w, h, gap = 13f, pad = 8f, boundsW, boundsH, out)

    private fun clash(a: FloatArray, b: FloatArray): Boolean =
        a[0] < b[2] && a[2] > b[0] && a[1] < b[3] && a[3] > b[1]

    @Test
    fun thePreferredSpotIsAboveTheMarker() {
        val layout = LabelLayout()
        assertTrue(place(layout, 200f, 200f))
        assertEquals(185f, out[0], 0.01f)
        assertEquals(200f - 13f - 12f, out[1], 0.01f)
    }

    @Test
    fun codesOnTheSamePixelDoNotStack() {
        val layout = LabelLayout()
        assertTrue(place(layout, 200f, 200f))
        val first = floatArrayOf(out[0], out[1], out[0] + 30f, out[1] + 12f)
        // A neighbouring airport a couple of pixels away, as JFK and EWR really are.
        assertTrue(place(layout, 202f, 200f))
        val second = floatArrayOf(out[0], out[1], out[0] + 30f, out[1] + 12f)
        assertFalse("the second label landed on the first", clash(first, second))
    }

    @Test
    fun aCrowdedClusterKeepsEveryLabelApart() {
        val layout = LabelLayout()
        val boxes = ArrayList<FloatArray>()
        repeat(12) {
            if (place(layout, 200f, 200f)) {
                boxes.add(floatArrayOf(out[0], out[1], out[0] + 30f, out[1] + 12f))
            }
        }
        assertTrue("a cluster should still show a few labels, not none", boxes.size >= 3)
        for (i in boxes.indices) {
            for (j in i + 1 until boxes.size) {
                assertFalse("labels $i and $j overlap", clash(boxes[i], boxes[j]))
            }
        }
    }

    @Test
    fun labelsStayInsideTheCanvas() {
        val layout = LabelLayout()
        // Hard against the top edge: "above" is off-screen, so the label must flip underneath.
        assertTrue(place(layout, 200f, 10f))
        assertTrue(out[1] >= 8f)
        // Hard against the left edge: the label shifts right of the marker.
        assertTrue(place(layout, 12f, 300f))
        assertTrue(out[0] >= 8f)
        assertTrue(out[0] + 30f <= 392f)
    }
}
