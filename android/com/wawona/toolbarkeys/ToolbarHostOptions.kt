package com.wawona.toolbarkeys

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Rootshell `Settings.KeyboardToolbar` host options (not layout slots).
 * Keys match Apple `KeyboardToolbarManager` / Wawona Settings Input.
 */
object ToolbarHostOptions {
    /** Same file as Wawona Settings (`MainActivity` / `SettingsDialog`). */
    const val PREFS = "wawona_prefs"
    const val KEY_SHOW_WITH_HARDWARE = "showToolbarWithHardwareKeyboard"
    const val KEY_PERSISTENT = "persistentToolbar"
    const val KEY_THEME_AWARE = "toolbarThemeAware"
    const val KEY_CHROME_STYLE = "toolbarChromeStyle"
    const val KEY_ARROW_MODE = "arrowJoystickMode"

    /** Rootshell touch-keyboard style ids that also tint toolbar chrome. */
    enum class ChromeStyle(val id: String, val label: String) {
        Flat("flat", "Flat"),
        Sculpted("sculpted", "Sculpted"),
        Steampunk("steampunk", "Steampunk"),
        Phosphor("phosphor", "Phosphor"),
        BeigeBox("beige-box", "Beige Box"),
        NeonGrid("neon-grid", "Neon Grid"),
        CircuitBoard("circuit-board", "Circuit Board"),
        ;

        companion object {
            fun fromId(raw: String?): ChromeStyle =
                entries.firstOrNull { it.id == raw } ?: Flat
        }
    }

    enum class ArrowMode(val id: String, val label: String) {
        Joystick("joystick", "Joystick"),
        Cluster("cluster", "Cluster"),
        ;

        companion object {
            fun fromId(raw: String?): ArrowMode =
                entries.firstOrNull { it.id == raw } ?: Joystick
        }
    }

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun showWithHardwareKeyboard(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_SHOW_WITH_HARDWARE, false)

    fun persistentToolbar(prefs: SharedPreferences): Boolean =
        prefs.getBoolean(KEY_PERSISTENT, false)

    fun themeAware(prefs: SharedPreferences): Boolean =
        if (prefs.contains(KEY_THEME_AWARE)) prefs.getBoolean(KEY_THEME_AWARE, true) else true

    fun chromeStyle(prefs: SharedPreferences): ChromeStyle =
        ChromeStyle.fromId(prefs.getString(KEY_CHROME_STYLE, ChromeStyle.Flat.id))

    fun arrowMode(prefs: SharedPreferences): ArrowMode =
        ArrowMode.fromId(prefs.getString(KEY_ARROW_MODE, ArrowMode.Joystick.id))

    fun setShowWithHardwareKeyboard(prefs: SharedPreferences, value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WITH_HARDWARE, value).apply()
    }

    fun setPersistentToolbar(prefs: SharedPreferences, value: Boolean) {
        prefs.edit().putBoolean(KEY_PERSISTENT, value).apply()
    }

    fun setThemeAware(prefs: SharedPreferences, value: Boolean) {
        prefs.edit().putBoolean(KEY_THEME_AWARE, value).apply()
    }

    fun setChromeStyle(prefs: SharedPreferences, style: ChromeStyle) {
        prefs.edit().putString(KEY_CHROME_STYLE, style.id).apply()
    }

    fun setArrowMode(prefs: SharedPreferences, mode: ArrowMode) {
        prefs.edit().putString(KEY_ARROW_MODE, mode.id).apply()
    }

    /** Observable holder for Compose toolbar chrome. */
    class State(context: Context) {
        private val prefs = prefs(context)
        var showWithHardwareKeyboard by mutableStateOf(showWithHardwareKeyboard(prefs))
            private set
        var persistentToolbar by mutableStateOf(persistentToolbar(prefs))
            private set
        var themeAware by mutableStateOf(themeAware(prefs))
            private set
        var chromeStyle by mutableStateOf(chromeStyle(prefs))
            private set
        var arrowMode by mutableStateOf(arrowMode(prefs))
            private set

        fun updateShowWithHardware(value: Boolean) {
            showWithHardwareKeyboard = value
            setShowWithHardwareKeyboard(prefs, value)
        }

        fun updatePersistent(value: Boolean) {
            persistentToolbar = value
            setPersistentToolbar(prefs, value)
        }

        fun updateThemeAware(value: Boolean) {
            themeAware = value
            setThemeAware(prefs, value)
        }

        fun updateChromeStyle(style: ChromeStyle) {
            chromeStyle = style
            setChromeStyle(prefs, style)
        }

        fun updateArrowMode(mode: ArrowMode) {
            arrowMode = mode
            setArrowMode(prefs, mode)
        }

        fun reload() {
            showWithHardwareKeyboard = showWithHardwareKeyboard(prefs)
            persistentToolbar = persistentToolbar(prefs)
            themeAware = themeAware(prefs)
            chromeStyle = chromeStyle(prefs)
            arrowMode = arrowMode(prefs)
        }
    }

    fun glassTint(style: ChromeStyle, themeAware: Boolean, dark: Boolean): Color {
        if (themeAware) {
            return if (dark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.55f)
        }
        return when (style) {
            ChromeStyle.Flat, ChromeStyle.Sculpted ->
                if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.62f)
            ChromeStyle.Steampunk -> Color(0xFF6B471F).copy(alpha = if (dark) 0.55f else 0.35f)
            ChromeStyle.Phosphor -> Color(0xFF0D5920).copy(alpha = if (dark) 0.55f else 0.30f)
            ChromeStyle.BeigeBox -> Color(0xFFC7B894).copy(alpha = if (dark) 0.40f else 0.55f)
            ChromeStyle.NeonGrid -> Color(0xFF8C1AA6).copy(alpha = if (dark) 0.45f else 0.28f)
            ChromeStyle.CircuitBoard -> Color(0xFF0D472E).copy(alpha = if (dark) 0.55f else 0.32f)
        }
    }
}
