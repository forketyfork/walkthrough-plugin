package com.forketyfork.walkthrough

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.ui.component.Text

private object WalkthroughSettingsStyle {
    val contentPadding = 16.dp
    val paletteSpacing = 10.dp
    val rowSpacing = 12.dp
    val rowCornerRadius = 8.dp
    val rowPaddingHorizontal = 10.dp
    val rowPaddingVertical = 8.dp
    val swatchSize = 92.dp
    val swatchHeight = 24.dp
    val swatchCornerRadius = 6.dp
    val swatchBorderWidth = 1.dp
    val selectedBorderWidth = 2.dp
    val unselectedBorderWidth = 1.dp
    val titleTextSize = 13.sp
    val unselectedBorderColor = Color.Gray.copy(alpha = 0.24f)
    val rowBackgroundColor = Color.Gray.copy(alpha = 0.08f)
    val swatchBorderColor = Color.Black.copy(alpha = 0.16f)
}

internal class WalkthroughSettingsConfigurable : Configurable {
    private var selectedPaletteId by mutableStateOf(WalkthroughSettings.getInstance().selectedPaletteId)

    override fun getDisplayName(): String = "Walkthrough"

    override fun createComponent(): JComponent =
        JewelComposePanel {
            WalkthroughSettingsPanel(
                selectedPaletteId = selectedPaletteId,
                onPaletteSelected = { selectedPaletteId = it }
            )
        }

    override fun isModified(): Boolean =
        selectedPaletteId != WalkthroughSettings.getInstance().selectedPaletteId

    override fun apply() {
        WalkthroughSettings.getInstance().selectedPaletteId = selectedPaletteId
        selectedPaletteId = WalkthroughSettings.getInstance().selectedPaletteId
    }

    override fun reset() {
        selectedPaletteId = WalkthroughSettings.getInstance().selectedPaletteId
    }
}

@Composable
private fun WalkthroughSettingsPanel(
    selectedPaletteId: String,
    onPaletteSelected: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(WalkthroughSettingsStyle.paletteSpacing),
        modifier = Modifier
            .fillMaxWidth()
            .padding(WalkthroughSettingsStyle.contentPadding)
    ) {
        WalkthroughPalettes.all.forEach { palette ->
            WalkthroughPaletteOption(
                palette = palette,
                selected = palette.id == selectedPaletteId,
                onSelected = { onPaletteSelected(palette.id) }
            )
        }
    }
}

@Composable
private fun WalkthroughPaletteOption(
    palette: WalkthroughPalette,
    selected: Boolean,
    onSelected: () -> Unit
) {
    val borderWidth = if (selected) {
        WalkthroughSettingsStyle.selectedBorderWidth
    } else {
        WalkthroughSettingsStyle.unselectedBorderWidth
    }
    val borderColor = if (selected) {
        palette.navPrimaryBorderColor
    } else {
        WalkthroughSettingsStyle.unselectedBorderColor
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(WalkthroughSettingsStyle.rowSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WalkthroughSettingsStyle.rowCornerRadius))
            .background(WalkthroughSettingsStyle.rowBackgroundColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(WalkthroughSettingsStyle.rowCornerRadius)
            )
            .clickable(onClick = onSelected)
            .padding(
                horizontal = WalkthroughSettingsStyle.rowPaddingHorizontal,
                vertical = WalkthroughSettingsStyle.rowPaddingVertical
            )
    ) {
        Box(
            modifier = Modifier
                .size(WalkthroughSettingsStyle.swatchSize, WalkthroughSettingsStyle.swatchHeight)
                .clip(RoundedCornerShape(WalkthroughSettingsStyle.swatchCornerRadius))
                .background(Brush.linearGradient(palette.swatchGradientColors))
                .border(
                    width = WalkthroughSettingsStyle.swatchBorderWidth,
                    color = WalkthroughSettingsStyle.swatchBorderColor,
                    shape = RoundedCornerShape(WalkthroughSettingsStyle.swatchCornerRadius)
                )
        )
        Text(
            text = palette.displayName,
            color = Color.Unspecified,
            fontSize = WalkthroughSettingsStyle.titleTextSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}
