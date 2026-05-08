package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkthroughPopupGeometryTest {

    @Test
    fun lerpReturnsStartAtProgressZero() {
        assertEquals(10f, lerp(10f, 20f, 0f), 0.001f)
    }

    @Test
    fun lerpReturnsEndAtProgressOne() {
        assertEquals(20f, lerp(10f, 20f, 1f), 0.001f)
    }

    @Test
    fun lerpReturnsMidpointAtHalf() {
        assertEquals(15f, lerp(10f, 20f, 0.5f), 0.001f)
    }

    @Test
    fun lerpHandlesNegativeRange() {
        assertEquals(-5f, lerp(-10f, 0f, 0.5f), 0.001f)
    }

    @Test
    fun lerpHandlesIdenticalStartEnd() {
        assertEquals(7f, lerp(7f, 7f, 0.5f), 0.001f)
    }

    @Test
    fun cubicEaseInOutReturnsZeroAtStart() {
        assertEquals(0f, cubicEaseInOut(0f), 0.001f)
    }

    @Test
    fun cubicEaseInOutReturnsOneAtEnd() {
        assertEquals(1f, cubicEaseInOut(1f), 0.001f)
    }

    @Test
    fun cubicEaseInOutReturnsMidpointAtHalf() {
        assertEquals(0.5f, cubicEaseInOut(0.5f), 0.001f)
    }

    @Test
    fun cubicEaseInOutIsMonotonicallyIncreasing() {
        var previous = cubicEaseInOut(0f)
        for (i in 1..100) {
            val current = cubicEaseInOut(i / 100f)
            assertTrue(current >= previous, "Not monotonically increasing at ${i / 100f}")
            previous = current
        }
    }

    @Test
    fun cubicEaseInOutIsSymmetricAroundMidpoint() {
        for (i in 0..50) {
            val t = i / 100f
            val fromStart = cubicEaseInOut(t)
            val fromEnd = 1f - cubicEaseInOut(1f - t)
            assertEquals(fromStart, fromEnd, 0.01f, "Expected symmetry at t=$t")
        }
    }

    @Test
    fun cubicEaseInOutStartsSlowly() {
        val earlyProgress = cubicEaseInOut(0.1f)
        assertTrue(earlyProgress < 0.1f, "Expected slow start: f(0.1)=$earlyProgress should be < 0.1")
    }

    @Test
    fun cubicEaseInOutEndsSlowly() {
        val lateProgress = cubicEaseInOut(0.9f)
        assertTrue(lateProgress > 0.9f, "Expected slow end: f(0.9)=$lateProgress should be > 0.9")
    }

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
}
