package com.forketyfork.walkthrough

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

internal data class PopupGeometry(val x: Int, val y: Int, val width: Int, val height: Int)

@State(
    name = "com.forketyfork.walkthrough.WalkthroughSettings",
    storages = [Storage("com.forketyfork.walkthrough.settings.xml")],
)
internal class WalkthroughSettings : PersistentStateComponent<WalkthroughSettings.State> {
    internal var state = State()
        private set

    var selectedPaletteId: String
        get() = state.selectedPaletteId
        set(value) {
            val palette = WalkthroughPalettes.byId(value)
            if (state.selectedPaletteId == palette.id) {
                return
            }
            state.selectedPaletteId = palette.id
            notifyPaletteChanged(palette)
        }

    val selectedPalette: WalkthroughPalette
        get() = WalkthroughPalettes.byId(selectedPaletteId)

    fun loadGeometry(): PopupGeometry? {
        val s = state
        val fields = intArrayOf(s.popupX, s.popupY, s.popupWidth, s.popupHeight)
        if (fields.any { it == Int.MIN_VALUE }) {
            return null
        }
        return PopupGeometry(s.popupX, s.popupY, s.popupWidth, s.popupHeight)
    }

    fun saveGeometry(geometry: PopupGeometry) {
        val s = state
        val current = PopupGeometry(s.popupX, s.popupY, s.popupWidth, s.popupHeight)
        if (current == geometry) {
            return
        }
        s.popupX = geometry.x
        s.popupY = geometry.y
        s.popupWidth = geometry.width
        s.popupHeight = geometry.height
    }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        this.state.selectedPaletteId = WalkthroughPalettes.byId(state.selectedPaletteId).id
    }

    class State {
        var selectedPaletteId: String = WalkthroughPalettes.default.id
        var popupX: Int = Int.MIN_VALUE
        var popupY: Int = Int.MIN_VALUE
        var popupWidth: Int = Int.MIN_VALUE
        var popupHeight: Int = Int.MIN_VALUE
    }

    private fun notifyPaletteChanged(palette: WalkthroughPalette) {
        ApplicationManager.getApplication()
            .messageBus
            .syncPublisher(WalkthroughSettingsListener.TOPIC)
            .paletteChanged(palette)
    }

    companion object {
        fun getInstance(): WalkthroughSettings =
            ApplicationManager.getApplication().getService(WalkthroughSettings::class.java)
    }
}
