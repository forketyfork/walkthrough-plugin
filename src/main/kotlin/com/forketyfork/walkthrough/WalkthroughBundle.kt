package com.forketyfork.walkthrough

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.WalkthroughBundle"

object WalkthroughBundle {
    private val bundle = DynamicBundle(WalkthroughBundle::class.java, BUNDLE)

    // Provided for programmatic access to bundle strings; part of the standard DynamicBundle pattern
    @Suppress("unused")
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        bundle.getMessage(key, *params)
}
