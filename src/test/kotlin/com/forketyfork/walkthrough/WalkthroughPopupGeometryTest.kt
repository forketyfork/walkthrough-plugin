package com.forketyfork.walkthrough

import java.awt.geom.Point2D
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkthroughPopupGeometryTest {

    @Test
    fun reverseLinearShiftStartsAtZero() {
        assertEquals(0f, reverseLinearShift(0L, 1000), 0.001f)
    }

    @Test
    fun reverseLinearShiftPeaksAtHalfPeriod() {
        assertEquals(1f, reverseLinearShift(1000L, 1000), 0.001f)
    }

    @Test
    fun reverseLinearShiftReturnsToZeroAtFullPeriod() {
        assertEquals(0f, reverseLinearShift(2000L, 1000), 0.001f)
    }

    @Test
    fun reverseLinearShiftStaysInUnitInterval() {
        val halfPeriod = 1000
        for (t in 0..4000 step 50) {
            val v = reverseLinearShift(t.toLong(), halfPeriod)
            assertTrue(v in 0f..1f, "Expected value in [0,1] at t=$t, got $v")
        }
    }

    @Test
    fun reverseLinearShiftIsSymmetricAroundHalfPeriod() {
        val halfPeriod = 1000
        for (t in 0..1000 step 50) {
            val before = reverseLinearShift(t.toLong(), halfPeriod)
            val after = reverseLinearShift((2 * halfPeriod - t).toLong(), halfPeriod)
            assertEquals(before, after, 0.001f, "Expected symmetry around half-period at t=$t")
        }
    }

    @Test
    fun reverseLinearShiftRepeatsEachPeriod() {
        val halfPeriod = 1000
        val period = 2 * halfPeriod
        for (t in 0..period step 50) {
            val first = reverseLinearShift(t.toLong(), halfPeriod)
            val nextCycle = reverseLinearShift((t + period).toLong(), halfPeriod)
            assertEquals(first, nextCycle, 0.001f, "Expected periodicity at t=$t")
        }
    }

    @Test
    fun connectorLineEndsAtArrowHeadBase() {
        val arrowTip = Point2D.Float(100f, 50f)
        val endControl = Point2D.Float(60f, 50f)

        val lineEnd = connectorLineEnd(arrowTip, endControl)

        assertEquals(87f, lineEnd.x, 0.001f)
        assertEquals(50f, lineEnd.y, 0.001f)
    }

    @Test
    fun curlyBraceArrowTargetsItsOuterCenter() {
        val brace = CurlyBraceGeometry(leftX = 100f, topY = 20f, bottomY = 140f, width = 12f)

        assertEquals(112f, brace.arrowPoint.x, 0.001f)
        assertEquals(80f, brace.arrowPoint.y, 0.001f)
    }

    @Test
    fun leftOpeningCurlyBraceArrowTargetsItsOuterCenter() {
        val brace = CurlyBraceGeometry(
            leftX = 100f,
            topY = 20f,
            bottomY = 140f,
            width = 12f,
            opensRight = false,
        )

        assertEquals(100f, brace.arrowPoint.x, 0.001f)
        assertEquals(80f, brace.arrowPoint.y, 0.001f)
    }

    @Test
    fun curlyBracePathSpansTheMentionedRange() {
        val brace = CurlyBraceGeometry(leftX = 100f, topY = 20f, bottomY = 140f, width = 12f)

        val bounds = buildCurlyBracePath(brace).bounds2D

        assertEquals(100.0, bounds.minX, 0.001)
        assertEquals(112.0, bounds.maxX, 0.001)
        assertEquals(20.0, bounds.minY, 0.001)
        assertEquals(140.0, bounds.maxY, 0.001)
    }
}
