package com.wawona.toolbarkeys

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Visual chrome port of ToolbarKeys `KeyboardSizes` + `KeyboardKeyColors` +
 * SF Symbol faces (`KeyID.iconName` / modifier glyphs). Keep press routing in
 * [ToolbarKeysBar]; this file only draws.
 */
object ToolbarSizes {
    data class Button(
        val height: Dp,
        val iconWidth: Dp,
        val normalWidth: Dp,
        val wideWidth: Dp,
        val cornerRadius: Dp,
        val fontSize: TextUnit,
        val symbolSize: Dp,
    )

    data class Toolbar(
        val height: Dp,
        val padding: Dp,
        val spacing: Dp,
        val cornerRadius: Dp,
        val drawerHeight: Dp,
        val edgePadding: Dp,
        val button: Button,
    )

    fun phonePortrait() = Toolbar(
        height = 44.dp,
        padding = 0.dp,
        spacing = 0.dp,
        cornerRadius = 16.dp,
        drawerHeight = 44.dp,
        edgePadding = 2.dp,
        button = Button(
            height = 38.dp,
            iconWidth = 48.dp,
            normalWidth = 33.dp,
            wideWidth = 48.dp,
            cornerRadius = 10.dp,
            fontSize = 15.sp,
            symbolSize = 16.dp,
        ),
    )

    fun phoneLandscape() = Toolbar(
        height = 38.dp,
        padding = 0.dp,
        spacing = 0.dp,
        cornerRadius = 14.dp,
        drawerHeight = 38.dp,
        edgePadding = 6.dp,
        button = Button(
            height = 32.dp,
            iconWidth = 44.dp,
            normalWidth = 30.dp,
            wideWidth = 44.dp,
            cornerRadius = 9.dp,
            fontSize = 14.sp,
            symbolSize = 15.dp,
        ),
    )

    fun pad() = Toolbar(
        height = 55.dp,
        padding = 6.dp,
        spacing = 0.dp,
        cornerRadius = 18.dp,
        drawerHeight = 55.dp,
        edgePadding = 2.dp,
        button = Button(
            height = 43.dp,
            iconWidth = 58.dp,
            normalWidth = 48.dp,
            wideWidth = 68.dp,
            cornerRadius = 12.dp,
            fontSize = 17.sp,
            symbolSize = 18.dp,
        ),
    )

    fun current(isPad: Boolean, landscape: Boolean): Toolbar = when {
        isPad -> pad()
        landscape -> phoneLandscape()
        else -> phonePortrait()
    }
}

/** Mirrors `KeyboardKeyColors` (black/white at 0.18 highlight / 0.28 active). */
object ToolbarKeyColors {
    fun highlight(dark: Boolean): Color {
        val base = if (dark) Color.White else Color.Black
        return base.copy(alpha = 0.18f)
    }

    fun active(dark: Boolean): Color {
        val base = if (dark) Color.White else Color.Black
        return base.copy(alpha = 0.28f)
    }

    fun glassTint(dark: Boolean): Color {
        val base = if (dark) Color.Black else Color.White
        return base.copy(alpha = 0.16f)
    }

    fun plate(dark: Boolean): Color {
        // Approximate UIBlurEffect.systemUltraThinMaterial + tint.
        return if (dark) {
            Color(0xFF2C2C2E).copy(alpha = 0.92f)
        } else {
            Color(0xFFF2F2F7).copy(alpha = 0.92f)
        }
    }

    fun separator(dark: Boolean): Color =
        if (dark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.18f)

    fun label(dark: Boolean): Color =
        if (dark) Color.White else Color.Black
}

sealed class ToolbarKeyFace {
    data class Glyph(val text: String, val accessibility: String) : ToolbarKeyFace()
    data class Icon(val image: ImageVector, val accessibility: String) : ToolbarKeyFace()
    data class Symbol(val text: String, val accessibility: String) : ToolbarKeyFace()
}

/**
 * Catalog key id → Compose face.
 *
 * Match ToolbarKeys: **symbols** on the key face, words only in a11y.
 * Modifier legends: ⌃ control, ⌥ alt (Apple Option), ⇧ shift, ◆ meta
 * (catalog id `cmd`). Meta stays diamond, not Mac ⌘ / Win ⊞.
 */
fun toolbarKeyFace(
    id: String,
    writingMode: Int = 0,
    keyboardExpanded: Boolean = true,
    customLabel: String? = null,
): ToolbarKeyFace {
    customLabel?.takeIf { it.isNotBlank() }?.let { label ->
        return ToolbarKeyFace.Symbol(label.take(2), label)
    }
    return when (id) {
        "dismiss" -> ToolbarKeyFace.Icon(Icons.Filled.KeyboardArrowDown, "Dismiss keyboard")
        "tabSwitcher" -> ToolbarKeyFace.Icon(Icons.Filled.FilterNone, "Tab switcher")
        // ⎋ (U+238B). Same symbol ToolbarKeys uses; a11y stays "Escape".
        "esc" -> ToolbarKeyFace.Glyph("\u238B", "Escape")
        "ctrl" -> ToolbarKeyFace.Glyph("\u2303", "Ctrl") // ⌃
        "alt" -> ToolbarKeyFace.Glyph("\u2325", "Alt") // ⌥ Apple Option
        "shift" -> ToolbarKeyFace.Glyph("\u21E7", "Shift") // ⇧
        // Shared catalog id `cmd`; Meta is diamond ◆, not Win ⊞ and not Mac ⌘.
        "cmd" -> ToolbarKeyFace.Glyph("\u25C6", "Meta")
        "tab" -> ToolbarKeyFace.Icon(Icons.AutoMirrored.Filled.KeyboardTab, "Tab")
        "writingAssistance" -> ToolbarKeyFace.Icon(
            Icons.Filled.TextFields,
            when (writingMode) {
                1 -> "Writing assistance basic"
                2 -> "Writing assistance advanced"
                else -> "Writing assistance off"
            },
        )
        "arrowDrawerToggle" -> ToolbarKeyFace.Icon(Icons.Filled.OpenWith, "Arrow pad")
        "drawerToggle" -> ToolbarKeyFace.Icon(Icons.Filled.MoreHoriz, "More keys")
        "toolbarSettings" -> ToolbarKeyFace.Icon(Icons.Filled.Settings, "Toolbar settings")
        "paste" -> ToolbarKeyFace.Icon(Icons.Filled.ContentPaste, "Paste")
        "compose" -> ToolbarKeyFace.Icon(Icons.Filled.TextFields, "Compose")
        "voiceAgent" -> ToolbarKeyFace.Icon(Icons.Filled.Mic, "Voice agent")
        "toggleFullScreen" -> ToolbarKeyFace.Icon(Icons.Filled.Fullscreen, "Full screen")
        "toggleTabBar" -> ToolbarKeyFace.Icon(Icons.Filled.ViewAgenda, "Tab bar")
        "newConnection" -> ToolbarKeyFace.Icon(Icons.Filled.Add, "New connection")
        "toggleMouseCapture" -> ToolbarKeyFace.Icon(Icons.Filled.Mouse, "Mouse capture")
        "aiAgent" -> ToolbarKeyFace.Icon(Icons.Filled.AutoAwesome, "AI agent")
        "brightnessBoost" -> ToolbarKeyFace.Icon(Icons.Filled.WbSunny, "Brightness")
        "clipboardManager" -> ToolbarKeyFace.Icon(Icons.AutoMirrored.Filled.ListAlt, "Clipboard")
        "appSettings" -> ToolbarKeyFace.Icon(Icons.Filled.Tune, "App settings")
        "arrowUp" -> ToolbarKeyFace.Icon(Icons.Filled.KeyboardArrowUp, "Up")
        "arrowDown" -> ToolbarKeyFace.Icon(Icons.Filled.KeyboardArrowDown, "Down")
        "arrowLeft" -> ToolbarKeyFace.Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left")
        "arrowRight" -> ToolbarKeyFace.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right")
        "ime" -> ToolbarKeyFace.Icon(
            if (keyboardExpanded) Icons.Filled.KeyboardHide else Icons.Filled.Keyboard,
            if (keyboardExpanded) "Hide keyboard" else "Show keyboard",
        )
        else -> {
            val label = ToolbarCatalog.spec(id).label
            ToolbarKeyFace.Symbol(label, label)
        }
    }
}

@Composable
fun ToolbarKeyChip(
    face: ToolbarKeyFace,
    sizes: ToolbarSizes.Button,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = when {
        locked || active -> ToolbarKeyColors.active(dark)
        pressed -> ToolbarKeyColors.highlight(dark)
        else -> Color.Transparent
    }
    val fg = ToolbarKeyColors.label(dark)
    val shape = RoundedCornerShape(sizes.cornerRadius)

    Box(
        modifier = modifier
            .height(sizes.height)
            .clip(shape)
            .background(bg, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = when (face) {
                    is ToolbarKeyFace.Glyph -> face.accessibility
                    is ToolbarKeyFace.Icon -> face.accessibility
                    is ToolbarKeyFace.Symbol -> face.accessibility
                }
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        when (face) {
            is ToolbarKeyFace.Glyph -> Text(
                text = face.text,
                color = fg,
                fontSize = (sizes.symbolSize.value + 2).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            is ToolbarKeyFace.Icon -> Icon(
                imageVector = face.image,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(sizes.symbolSize),
            )
            is ToolbarKeyFace.Symbol -> Text(
                text = face.text,
                color = fg,
                fontSize = sizes.fontSize,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .width(sizes.normalWidth * 0.55f) // ~60% of chip, matches UIKit lock bar
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(fg),
            )
        }
    }
}

@Composable
fun ToolbarGlassPlate(
    sizes: ToolbarSizes.Toolbar,
    modifier: Modifier = Modifier,
    chromeStyle: ToolbarHostOptions.ChromeStyle = ToolbarHostOptions.ChromeStyle.Flat,
    themeAware: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(sizes.cornerRadius)
    val tint = ToolbarHostOptions.glassTint(chromeStyle, themeAware, dark)
    Box(
        modifier = modifier
            .clip(shape)
            .background(ToolbarKeyColors.plate(dark), shape)
            .background(tint, shape),
    ) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(ToolbarKeyColors.separator(dark)),
        )
    }
}

@Composable
fun ToolbarDrawerSeparator(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(ToolbarKeyColors.separator(dark).copy(alpha = 0.5f)),
    )
}