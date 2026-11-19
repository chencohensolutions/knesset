package com.chencohensolutions.knesset.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Seat positions for a half-circle parliament chart (docs/UX-REDESIGN.md 3.12). */
object Hemicycle {
    data class Seat(val x: Float, val y: Float)

    /**
     * [count] seats in [rows] concentric arcs, inner radius 0.4 and outer 1.0, in a box from -1..1 (x) and 0..1 (y,
     * upwards). Seats are returned ordered by angle from the start side (x = -1) to the end side, so consecutive
     * seats of one group sit together. Mirror x for right to left.
     */
    fun layout(count: Int, rows: Int = 5): List<Seat> {
        if (count <= 0) return emptyList()
        val radii = (0 until rows).map { 0.4f + 0.6f * it / (rows - 1).coerceAtLeast(1) }
        val total = radii.sum()
        val perRow = radii.map { (count * it / total).roundToInt() }.toMutableList()
        // Fix rounding so the rows add up exactly.
        var diff = count - perRow.sum()
        var i = rows - 1
        while (diff != 0) {
            val step = if (diff > 0) 1 else -1
            if (perRow[i] + step >= 1) { perRow[i] += step; diff -= step }
            i = (i - 1 + rows) % rows
        }
        val seats = mutableListOf<Pair<Double, Seat>>()
        radii.forEachIndexed { r, radius ->
            val n = perRow[r]
            for (k in 0 until n) {
                val angle = if (n == 1) PI / 2 else PI - PI * k / (n - 1)
                seats += angle to Seat((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
            }
        }
        return seats.sortedWith(compareByDescending<Pair<Double, Seat>> { it.first }.thenBy { it.second.x * it.second.x + it.second.y * it.second.y }).map { it.second }
    }
}

/** How far a bill got, for the stage tracker on the bill screen. */
object BillStages {
    const val COUNT = 5 // preliminary, first reading, committee, second and third reading, published

    /** The index of the reached stage, from the status wording. */
    fun stageOf(status: String?, publicationDate: Boolean = false): Int {
        val s = status.orEmpty()
        return when {
            publicationDate || s.contains("פורסם") || s.contains("קריאה שלישית") && s.contains("אושר") || s.contains("התקבל") -> 4
            s.contains("שנייה") || s.contains("שלישית") -> 3
            s.contains("ראשונה") && (s.contains("עבר") || s.contains("התקבל")) -> 2
            s.contains("ראשונה") -> 1
            else -> 0
        }
    }
}
