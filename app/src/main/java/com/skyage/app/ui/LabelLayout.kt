package com.skyage.app.ui

/**
 * Greedy placement for the globe's airport codes: a code is only drawn where it clears every code
 * already placed, so a cluster like JFK and EWR — 33 km apart, which is the same two pixels on a
 * phone-sized globe — shows one readable label instead of two stacked into a blob.
 *
 * Preferred spots run outward from the marker: above, below, right, left, then the four diagonals.
 * A label that fits nowhere is dropped; its dot still shows, so nothing is lost but the clutter.
 */
internal class LabelLayout {

    private val taken = ArrayList<FloatArray>(16)

    fun clear() {
        taken.clear()
    }

    /**
     * Places one label box of [w] x [h] centred on the marker at ([x], [y]) and writes its top-left
     * corner into [out]. [gap] keeps the text off the dot, [pad] the text off the canvas edge.
     * Returns false when every candidate spot is taken or out of bounds.
     */
    fun place(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        gap: Float,
        pad: Float,
        boundsW: Float,
        boundsH: Float,
        out: FloatArray,
    ): Boolean {
        val spots = floatArrayOf(
            x - w / 2f, y - gap - h, // above
            x - w / 2f, y + gap, // below
            x + gap, y - h / 2f, // right
            x - gap - w, y - h / 2f, // left
            x + gap * 0.6f, y - gap - h * 0.85f, // upper right
            x - gap * 0.6f - w, y - gap - h * 0.85f, // upper left
            x + gap * 0.6f, y + gap - h * 0.15f, // lower right
            x - gap * 0.6f - w, y + gap - h * 0.15f, // lower left
        )
        var i = 0
        while (i < spots.size) {
            val left = spots[i]
            val top = spots[i + 1]
            val right = left + w
            val bottom = top + h
            if (left >= pad && top >= pad && right <= boundsW - pad && bottom <= boundsH - pad) {
                var free = true
                for (o in taken) {
                    if (left < o[2] && right > o[0] && top < o[3] && bottom > o[1]) {
                        free = false
                        break
                    }
                }
                if (free) {
                    taken.add(floatArrayOf(left, top, right, bottom))
                    out[0] = left
                    out[1] = top
                    return true
                }
            }
            i += 2
        }
        return false
    }
}
