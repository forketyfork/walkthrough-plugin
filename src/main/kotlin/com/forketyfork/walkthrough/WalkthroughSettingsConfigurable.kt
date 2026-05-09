package com.forketyfork.walkthrough

import androidx.compose.ui.graphics.toArgb
import com.intellij.openapi.options.Configurable
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color as AwtColor
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JRadioButton

private object WalkthroughSettingsStyle {
    const val HEADING_BOTTOM_GAP = 4
    const val DESCRIPTION_BOTTOM_GAP = 14
    const val SWATCH_WIDTH = 92
    const val SWATCH_HEIGHT = 24
    const val SWATCH_ARC = 8
    const val PANEL_PADDING = 16
    const val ROW_GAP = 10
    const val COLUMN_GAP = 12
    const val RGB_MIN = 0
    const val RGB_MAX = 255
    const val SWATCH_BORDER_ALPHA = 48
    const val MIN_PAINT_SIZE = 1
    const val MIN_GRADIENT_COLOR_COUNT = 2
}

internal class WalkthroughSettingsConfigurable : Configurable {
    private var selectedPaletteId = WalkthroughSettings.getInstance().selectedPaletteId
    private var paletteButtons = emptyMap<String, JRadioButton>()

    override fun getDisplayName(): String = "Walkthrough"

    override fun createComponent(): JComponent {
        val buttonGroup = ButtonGroup()
        val buttons = mutableMapOf<String, JRadioButton>()
        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(WalkthroughSettingsStyle.PANEL_PADDING)
            background = JBColor.PanelBackground
        }
        var gridRow = 0

        panel.add(
            settingsHeading(),
            fullWidthConstraints(gridRow++, WalkthroughSettingsStyle.HEADING_BOTTOM_GAP)
        )
        panel.add(
            settingsDescription(),
            fullWidthConstraints(gridRow++, WalkthroughSettingsStyle.DESCRIPTION_BOTTOM_GAP)
        )

        WalkthroughPalettes.all.forEach { palette ->
            val selectOnPress = object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    selectPalette(palette.id)
                }
            }
            val radioButton = JRadioButton(palette.displayName).apply {
                isSelected = palette.id == selectedPaletteId
                isOpaque = false
                addActionListener { selectPalette(palette.id) }
            }
            val swatch = PaletteSwatch(palette).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                addMouseListener(selectOnPress)
            }
            val row = PaletteRow(radioButton, swatch).apply {
                addMouseListener(selectOnPress)
            }

            buttonGroup.add(radioButton)
            buttons[palette.id] = radioButton
            panel.add(row, rowConstraints(gridRow++))
        }

        panel.add(JPanel().apply { isOpaque = false }, fillerConstraints(gridRow))
        paletteButtons = buttons
        return panel
    }

    override fun isModified(): Boolean =
        selectedPaletteId != WalkthroughSettings.getInstance().selectedPaletteId

    override fun apply() {
        WalkthroughSettings.getInstance().selectedPaletteId = selectedPaletteId
        selectedPaletteId = WalkthroughSettings.getInstance().selectedPaletteId
        updateSelection()
    }

    override fun reset() {
        selectedPaletteId = WalkthroughSettings.getInstance().selectedPaletteId
        updateSelection()
    }

    private fun selectPalette(paletteId: String) {
        selectedPaletteId = paletteId
        updateSelection()
    }

    private fun updateSelection() {
        paletteButtons[selectedPaletteId]?.isSelected = true
    }
}

private fun settingsHeading(): JLabel =
    JLabel("Walkthrough popup colors").apply {
        font = font.deriveFont(Font.BOLD)
    }

private fun settingsDescription(): JLabel =
    JLabel("These palettes style the popup background, border, controls, scrollbar, and source connector.").apply {
        foreground = JBColor.GRAY
    }

private fun fullWidthConstraints(gridRow: Int, bottomInset: Int): GridBagConstraints =
    GridBagConstraints().apply {
        gridx = 0
        gridy = gridRow
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.NORTHWEST
        insets = Insets(0, 0, bottomInset, 0)
    }

private fun rowConstraints(gridRow: Int): GridBagConstraints =
    GridBagConstraints().apply {
        gridx = 0
        gridy = gridRow
        weightx = 1.0
        fill = GridBagConstraints.HORIZONTAL
        anchor = GridBagConstraints.NORTHWEST
        insets = Insets(0, 0, WalkthroughSettingsStyle.ROW_GAP, 0)
    }

private fun fillerConstraints(gridRow: Int): GridBagConstraints =
    GridBagConstraints().apply {
        gridx = 0
        gridy = gridRow
        weightx = 1.0
        weighty = 1.0
        fill = GridBagConstraints.BOTH
    }

private class PaletteRow(
    radioButton: JRadioButton,
    swatch: JComponent
) : JPanel(GridBagLayout()) {
    init {
        isOpaque = false
        cursor = radioButton.cursor
        add(swatch, swatchConstraints())
        add(radioButton, labelConstraints())
    }

    private fun swatchConstraints(): GridBagConstraints =
        GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            insets = Insets(0, 0, 0, WalkthroughSettingsStyle.COLUMN_GAP)
        }

    private fun labelConstraints(): GridBagConstraints =
        GridBagConstraints().apply {
            gridx = 1
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }
}

private class PaletteSwatch(
    private val palette: WalkthroughPalette
) : JComponent() {
    init {
        preferredSize = Dimension(WalkthroughSettingsStyle.SWATCH_WIDTH, WalkthroughSettingsStyle.SWATCH_HEIGHT)
        minimumSize = preferredSize
        maximumSize = preferredSize
        toolTipText = palette.displayName
        accessibleContext?.accessibleName = "${palette.displayName} palette"
    }

    override fun paintComponent(graphics: Graphics) {
        super.paintComponent(graphics)
        val graphics2d = graphics.create() as Graphics2D
        try {
            graphics2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val shape = RoundRectangle2D.Float(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                WalkthroughSettingsStyle.SWATCH_ARC.toFloat(),
                WalkthroughSettingsStyle.SWATCH_ARC.toFloat()
            )

            graphics2d.paint = swatchPaint()
            graphics2d.fill(shape)
            graphics2d.color = JBColor(
                AwtColor(
                    WalkthroughSettingsStyle.RGB_MIN,
                    WalkthroughSettingsStyle.RGB_MIN,
                    WalkthroughSettingsStyle.RGB_MIN,
                    WalkthroughSettingsStyle.SWATCH_BORDER_ALPHA
                ),
                AwtColor(
                    WalkthroughSettingsStyle.RGB_MAX,
                    WalkthroughSettingsStyle.RGB_MAX,
                    WalkthroughSettingsStyle.RGB_MAX,
                    WalkthroughSettingsStyle.SWATCH_BORDER_ALPHA
                )
            )
            graphics2d.draw(shape)
        } finally {
            graphics2d.dispose()
        }
    }

    private fun swatchPaint(): Paint {
        val colors = palette.swatchGradientColors.map { AwtColor(it.toArgb(), true) }
        if (
            colors.size < WalkthroughSettingsStyle.MIN_GRADIENT_COLOR_COUNT ||
            width < WalkthroughSettingsStyle.MIN_PAINT_SIZE ||
            height < WalkthroughSettingsStyle.MIN_PAINT_SIZE
        ) {
            return colors.firstOrNull() ?: JBColor.GRAY
        }

        val fractions = FloatArray(colors.size) { index ->
            index.toFloat() / colors.lastIndex.toFloat()
        }

        return LinearGradientPaint(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            fractions,
            colors.toTypedArray()
        )
    }
}
