package com.forketyfork.walkthrough

internal object WalkthroughDebugOptions {
    val disablePopupContentAnimation = booleanFlag(
        propertyName = "walkthrough.debug.disablePopupContentAnimation",
        environmentName = "WALKTHROUGH_DEBUG_DISABLE_POPUP_CONTENT_ANIMATION"
    )

    val disablePopupAvoidAnimation = booleanFlag(
        propertyName = "walkthrough.debug.disablePopupAvoidAnimation",
        environmentName = "WALKTHROUGH_DEBUG_DISABLE_POPUP_AVOID_ANIMATION"
    )

    fun summary(): String =
        "disablePopupContentAnimation=$disablePopupContentAnimation " +
            "disablePopupAvoidAnimation=$disablePopupAvoidAnimation"

    private fun booleanFlag(propertyName: String, environmentName: String): Boolean =
        System.getProperty(propertyName)?.toBooleanStrictOrNull()
            ?: System.getenv(environmentName)?.toBooleanStrictOrNull()
            ?: false
}
