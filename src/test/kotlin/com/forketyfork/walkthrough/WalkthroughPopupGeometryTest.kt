package com.forketyfork.walkthrough

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
}
