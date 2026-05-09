package com.forketyfork.walkthrough

import com.intellij.util.messages.Topic

internal interface WalkthroughSettingsListener {
    fun paletteChanged(palette: WalkthroughPalette)

    companion object {
        val TOPIC: Topic<WalkthroughSettingsListener> = Topic.create(
            "Walkthrough settings changes",
            WalkthroughSettingsListener::class.java
        )
    }
}
