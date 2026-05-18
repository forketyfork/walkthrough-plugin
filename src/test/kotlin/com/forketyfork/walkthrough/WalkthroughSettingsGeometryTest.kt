package com.forketyfork.walkthrough

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class WalkthroughSettingsGeometryTest {

    @Test
    fun loadGeometryReturnsNullWhenStateIsUnset() {
        val settings = WalkthroughSettings()
        assertNull(settings.loadGeometry())
    }

    @Test
    fun saveGeometryThenLoadGeometryRoundTrips() {
        val settings = WalkthroughSettings()
        val geometry = PopupGeometry(x = 120, y = 240, width = 720, height = 480)

        settings.saveGeometry(geometry)

        assertEquals(geometry, settings.loadGeometry())
    }

    @Test
    fun loadGeometryReturnsNullWhenAnyFieldIsSentinel() {
        val settings = WalkthroughSettings()
        settings.saveGeometry(PopupGeometry(x = 100, y = 100, width = 800, height = 600))
        val state = settings.state
        state.popupX = Int.MIN_VALUE

        assertNull(settings.loadGeometry())
    }

    @Test
    fun stateSerializationRoundTripsGeometry() {
        val original = WalkthroughSettings()
        original.saveGeometry(PopupGeometry(x = 10, y = 20, width = 600, height = 400))
        val serialized = original.state

        val restored = WalkthroughSettings()
        restored.loadState(serialized)

        assertEquals(
            PopupGeometry(x = 10, y = 20, width = 600, height = 400),
            restored.loadGeometry()
        )
    }

    @Test
    fun saveGeometryIsIdempotent() {
        val settings = WalkthroughSettings()
        val geometry = PopupGeometry(x = 1, y = 2, width = 600, height = 400)
        settings.saveGeometry(geometry)
        val stateBefore = settings.state

        settings.saveGeometry(geometry)

        assertSame(stateBefore, settings.state)
    }
}
