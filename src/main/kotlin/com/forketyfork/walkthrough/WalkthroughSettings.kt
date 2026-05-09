package com.forketyfork.walkthrough

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "WalkthroughSettings",
    storages = [Storage("walkthrough.xml")]
)
internal class WalkthroughSettings : PersistentStateComponent<WalkthroughSettings.State> {
    private var state = State()

    var selectedPaletteId: String
        get() = state.selectedPaletteId
        set(value) {
            state.selectedPaletteId = WalkthroughPalettes.byId(value).id
        }

    val selectedPalette: WalkthroughPalette
        get() = WalkthroughPalettes.byId(selectedPaletteId)

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        selectedPaletteId = state.selectedPaletteId
    }

    class State {
        var selectedPaletteId: String = WalkthroughPalettes.default.id
    }

    companion object {
        fun getInstance(): WalkthroughSettings =
            ApplicationManager.getApplication().getService(WalkthroughSettings::class.java)
    }
}
