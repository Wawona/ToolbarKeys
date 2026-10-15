package com.wawona.toolbarkeys

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt

object LinuxKey {
    const val ESC = 1
    const val KEY_1 = 2
    const val KEY_2 = 3
    const val KEY_3 = 4
    const val KEY_4 = 5
    const val KEY_5 = 6
    const val KEY_6 = 7
    const val KEY_7 = 8
    const val KEY_8 = 9
    const val KEY_9 = 10
    const val KEY_0 = 11
    const val GRAVE = 41
    const val TAB = 15
    const val BACKSPACE = 14
    const val SLASH = 53
    const val MINUS = 12
    const val EQUAL = 13
    const val ENTER = 28
    const val SPACE = 57
    const val LEFTBRACE = 26
    const val RIGHTBRACE = 27
    const val BACKSLASH = 43
    const val SEMICOLON = 39
    const val APOSTROPHE = 40
    const val COMMA = 51
    const val DOT = 52
    const val HOME = 102
    const val UP = 103
    const val END = 107
    const val PAGEUP = 104
    const val LEFTSHIFT = 42
    const val LEFTCTRL = 29
    const val LEFTALT = 56
    const val LEFTMETA = 125
    const val LEFT = 105
    const val DOWN = 108
    const val RIGHT = 106
    const val PAGEDOWN = 109
}

data class LinuxKeyMapping(
    val keycode: Int,
    val needsShift: Boolean = false
)

object ModifierState {
    var shiftActive by mutableStateOf(false)
    var ctrlActive by mutableStateOf(false)
    var altActive by mutableStateOf(false)
    var superActive by mutableStateOf(false)
    var shiftLocked by mutableStateOf(false)
    var ctrlLocked by mutableStateOf(false)
    var altLocked by mutableStateOf(false)
    var superLocked by mutableStateOf(false)

    fun hasActiveModifiers(): Boolean = shiftActive || ctrlActive || altActive || superActive

    fun clearStickyModifiers() {
        if (shiftActive && !shiftLocked) shiftActive = false
        if (ctrlActive && !ctrlLocked) ctrlActive = false
        if (altActive && !altLocked) altActive = false
        if (superActive && !superLocked) superActive = false
    }

    fun clearAllModifiers() {
        shiftActive = false
        ctrlActive = false
        altActive = false
        superActive = false
        shiftLocked = false
        ctrlLocked = false
        altLocked = false
        superLocked = false
    }

    fun syncShiftFromNative(active: Boolean, locked: Boolean = false) {
        shiftActive = active || locked
        shiftLocked = locked
    }
}

private object XkbMod {
    const val SHIFT = 1 shl 0
    const val CTRL = 1 shl 2
    const val ALT = 1 shl 3
    const val LOGO = 1 shl 6
}

private const val DOUBLE_TAP_THRESHOLD_MS = 400L

private val INJECT_KEY_IDS = setOf("esc", "tab", "arrowUp", "arrowDown", "arrowLeft", "arrowRight")

/** Terminal fallback only. Printable text is TI v3 (host-keymap-bridge). */
fun controlToLinuxKeycode(ch: Char): LinuxKeyMapping? {
    return when (ch) {
        '\n', '\r' -> LinuxKeyMapping(LinuxKey.ENTER)
        '\t' -> LinuxKeyMapping(LinuxKey.TAB)
        '\u0008', '\u007f' -> LinuxKeyMapping(LinuxKey.BACKSPACE)
        else -> null
    }
}

/**
 * Toolbar Keys chrome (Rootshell layout + Compose faces). Host wires seat /
 * Terminal inject callbacks; this library never calls into Wawona JNI.
 */
@Composable
fun ToolbarKeysBar(
    modifier: Modifier = Modifier,
    keyboardExpanded: Boolean,
    onToggleKeyboardExpanded: () -> Unit,
    onCollapseToPipByDrag: () -> Unit,
    onTabSwitcher: () -> Unit = {},
    onAppSettings: () -> Unit = {},
    onNewConnection: () -> Unit = {},
    onToggleTabBar: () -> Unit = {},
    onToggleFullScreen: () -> Unit = {},
    onToggleMouseCapture: () -> Unit = {},
    onClipboardManager: () -> Unit = {},
    onWritingAssistance: (Int) -> Unit = {},
    onCompose: () -> Unit = {},
    onBrightnessBoost: () -> Unit = {},
    onAiAgent: () -> Unit = {},
    onVoiceAgent: () -> Unit = {},
    /** Printable / sequence text. Host: Wayland TI commit or Terminal echo. */
    onCommitText: (String) -> Unit = {},
    /** Linux keycode inject. Host: compositor seat or Terminal remap. */
    onInjectKey: (keycode: Int, pressed: Boolean, timestamp: Int) -> Unit = { _, _, _ -> },
    onInjectModifiers: (mods: Int, group: Int, locked: Int, latched: Int) -> Unit =
        { _, _, _, _ -> },
    /**
     * Total chrome height (main row + open drawer/arrow rows). Mirrors
     * ToolbarKeys `intrinsicContentSize` growth when the ellipsis drawer opens
     * *above* the main row so the host can pad Terminal / compositor content.
     */
    onOccupiedHeightChanged: (Dp) -> Unit = {},
) {
    var pressTimestamp by remember { mutableStateOf(0) }

    var lastModShiftTap by remember { mutableLongStateOf(0L) }
    var lastModCtrlTap by remember { mutableLongStateOf(0L) }
    var lastModAltTap by remember { mutableLongStateOf(0L) }
    var lastModSuperTap by remember { mutableLongStateOf(0L) }

    fun nextTimestamp(): Int {
        val now = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        if (now > pressTimestamp) {
            pressTimestamp = now
        } else {
            pressTimestamp += 1
        }
        return pressTimestamp
    }

    fun handleModifierTap(
        active: Boolean,
        locked: Boolean,
        lastTap: Long,
        onActive: (Boolean) -> Unit,
        onLocked: (Boolean) -> Unit,
        onLastTap: (Long) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastTap
        onLastTap(now)

        when {
            locked -> {
                onActive(false)
                onLocked(false)
            }
            active && elapsed < DOUBLE_TAP_THRESHOLD_MS -> {
                onLocked(true)
            }
            active -> {
                onActive(false)
                onLocked(false)
            }
            else -> {
                onActive(true)
                onLocked(false)
            }
        }
    }

    fun sendAccessoryKey(keycode: Int) {
        val ts = nextTimestamp()
        var mods = 0
        if (ModifierState.shiftActive) {
            mods = mods or XkbMod.SHIFT
            onInjectKey(LinuxKey.LEFTSHIFT, true, ts)
        }
        if (ModifierState.ctrlActive) {
            mods = mods or XkbMod.CTRL
            onInjectKey(LinuxKey.LEFTCTRL, true, ts)
        }
        if (ModifierState.altActive) {
            mods = mods or XkbMod.ALT
            onInjectKey(LinuxKey.LEFTALT, true, ts)
        }
        if (ModifierState.superActive) {
            mods = mods or XkbMod.LOGO
            onInjectKey(LinuxKey.LEFTMETA, true, ts)
        }
        if (mods != 0) {
            onInjectModifiers(mods, 0, 0, 0)
        }
        onInjectKey(keycode, true, ts)
        onInjectKey(keycode, false, ts)
        if (ModifierState.shiftActive) onInjectKey(LinuxKey.LEFTSHIFT, false, ts)
        if (ModifierState.ctrlActive) onInjectKey(LinuxKey.LEFTCTRL, false, ts)
        if (ModifierState.altActive) onInjectKey(LinuxKey.LEFTALT, false, ts)
        if (ModifierState.superActive) onInjectKey(LinuxKey.LEFTMETA, false, ts)
        if (mods != 0) onInjectModifiers(0, 0, 0, 0)
        ModifierState.clearStickyModifiers()
    }

    fun sendBuiltInKey(id: String, spec: ToolbarKeySpec) {
        val builtIn = ToolbarCatalog.pressBuiltInValue(id)
        if (!builtIn.isNullOrEmpty() && id !in INJECT_KEY_IDS) {
            onCommitText(builtIn)
            ModifierState.clearStickyModifiers()
            return
        }
        when (spec.kind) {
            ToolbarKeyKind.KEY -> sendAccessoryKey(spec.keycode)
            ToolbarKeyKind.SHIFTED -> {
                val wasShift = ModifierState.shiftActive
                ModifierState.shiftActive = true
                sendAccessoryKey(spec.keycode)
                if (!wasShift && !ModifierState.shiftLocked) ModifierState.shiftActive = false
            }
            else -> Unit
        }
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPad = configuration.screenWidthDp >= 600
    val layout = remember {
        ToolbarLayoutStore(context.applicationContext, if (isPad) "pad" else "phone")
    }
    val hostOptions = remember { ToolbarHostOptions.State(context.applicationContext) }
    DisposableEffect(hostOptions) {
        val prefs = ToolbarHostOptions.prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                ToolbarHostOptions.KEY_SHOW_WITH_HARDWARE,
                ToolbarHostOptions.KEY_PERSISTENT,
                ToolbarHostOptions.KEY_THEME_AWARE,
                ToolbarHostOptions.KEY_CHROME_STYLE,
                ToolbarHostOptions.KEY_ARROW_MODE,
                -> hostOptions.reload()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var showSettings by remember { mutableStateOf(false) }
    var arrowsOpen by remember { mutableStateOf(false) }
    var revealedDrawers by remember { mutableStateOf(if (layout.drawerOpenByDefault) 1 else 0) }
    var cycleIndex by remember { mutableStateOf(0) }
    val writingPrefs = remember {
        context.applicationContext.getSharedPreferences(PREFS_WRITING, Context.MODE_PRIVATE)
    }
    var writingMode by remember { mutableIntStateOf(writingPrefs.getInt("mode", 0)) }
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp
    val sizes = remember(isPad, landscape) {
        ToolbarSizes.current(isPad = isPad, landscape = landscape)
    }
    var trayDragX by remember { mutableStateOf(0f) }
    var trayDragY by remember { mutableStateOf(0f) }

    // Apple KeyboardToolbarView: drawer container sits above mainToolbarContainer
    // and grows by drawerHeight * visibleDrawerRowCount. Compose must Column-stack
    // the same way (never Box-overlay drawer onto the main row).
    val visibleExtraRows = when {
        layout.drawerToggleMode == "cycle" && revealedDrawers > 0 -> 1
        else -> revealedDrawers
    } + if (arrowsOpen) 1 else 0
    val occupiedHeight = sizes.height + sizes.drawerHeight * visibleExtraRows
    LaunchedEffect(occupiedHeight) {
        onOccupiedHeightChanged(occupiedHeight)
    }

    fun pressId(id: String) {
        val custom = layout.custom(id)
        if (custom != null) {
            onCommitText(custom.text)
            return
        }
        val spec = ToolbarCatalog.spec(id)
        when (spec.kind) {
            ToolbarKeyKind.KEY, ToolbarKeyKind.SHIFTED -> sendBuiltInKey(id, spec)
            ToolbarKeyKind.MOD -> when (spec.mod) {
                "shift" -> handleModifierTap(
                    ModifierState.shiftActive, ModifierState.shiftLocked, lastModShiftTap,
                    { ModifierState.shiftActive = it },
                    { ModifierState.shiftLocked = it },
                    { lastModShiftTap = it }
                )
                "ctrl" -> handleModifierTap(
                    ModifierState.ctrlActive, ModifierState.ctrlLocked, lastModCtrlTap,
                    { ModifierState.ctrlActive = it },
                    { ModifierState.ctrlLocked = it },
                    { lastModCtrlTap = it }
                )
                "alt" -> handleModifierTap(
                    ModifierState.altActive, ModifierState.altLocked, lastModAltTap,
                    { ModifierState.altActive = it },
                    { ModifierState.altLocked = it },
                    { lastModAltTap = it }
                )
                "super" -> handleModifierTap(
                    ModifierState.superActive, ModifierState.superLocked, lastModSuperTap,
                    { ModifierState.superActive = it },
                    { ModifierState.superLocked = it },
                    { lastModSuperTap = it }
                )
                else -> sendBuiltInKey(id, spec)
            }
            ToolbarKeyKind.ACTION, ToolbarKeyKind.TOGGLE -> when (id) {
                "dismiss" -> if (keyboardExpanded) onToggleKeyboardExpanded()
                "toolbarSettings" -> showSettings = true
                "arrowDrawerToggle" -> arrowsOpen = !arrowsOpen
                "drawerToggle" -> {
                    val count = layout.drawers.size
                    if (layout.drawerToggleMode == "cycle" && count > 1) {
                        if (revealedDrawers == 0) {
                            revealedDrawers = 1
                            cycleIndex = 0
                        } else {
                            cycleIndex = (cycleIndex + 1) % count
                            if (cycleIndex == 0) revealedDrawers = 0
                        }
                    } else if (revealedDrawers >= count) {
                        revealedDrawers = 0
                    } else {
                        revealedDrawers += 1
                    }
                }
                "paste" -> {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val data = clip?.primaryClip
                    val text = if (data != null && data.itemCount > 0) {
                        data.getItemAt(0).coerceToText(context)?.toString()
                    } else {
                        null
                    }
                    if (!text.isNullOrEmpty()) onCommitText(text)
                }
                "tabSwitcher" -> onTabSwitcher()
                "appSettings" -> onAppSettings()
                "newConnection" -> onNewConnection()
                "toggleTabBar" -> onToggleTabBar()
                "toggleFullScreen" -> onToggleFullScreen()
                "toggleMouseCapture" -> onToggleMouseCapture()
                "clipboardManager" -> onClipboardManager()
                "writingAssistance" -> {
                    writingMode = (writingMode + 1) % 3
                    writingPrefs.edit().putInt("mode", writingMode).apply()
                    val label = when (writingMode) {
                        1 -> "Basic"
                        2 -> "Advanced"
                        else -> "Off"
                    }
                    Toast.makeText(context, "Writing assistance: $label", Toast.LENGTH_SHORT).show()
                    onWritingAssistance(writingMode)
                }
                "compose" -> onCompose()
                "brightnessBoost" -> onBrightnessBoost()
                "aiAgent" -> onAiAgent()
                "voiceAgent" -> onVoiceAgent()
            }
        }
    }

    if (showSettings) {
        ToolbarKeysSettingsSheet(
            layout = layout,
            hostOptions = hostOptions,
            onDismiss = { showSettings = false },
        )
    }

    val density = LocalDensity.current
    ToolbarGlassPlate(
        sizes = sizes,
        chromeStyle = hostOptions.chromeStyle,
        themeAware = hostOptions.themeAware,
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        trayDragX = 0f
                        trayDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        trayDragX += dragAmount.x
                        trayDragY += dragAmount.y
                    },
                    onDragEnd = {
                        if (abs(trayDragX) > 28f || abs(trayDragY) > 28f) {
                            onCollapseToPipByDrag()
                        }
                        trayDragX = 0f
                        trayDragY = 0f
                    },
                    onDragCancel = {
                        trayDragX = 0f
                        trayDragY = 0f
                    },
                )
            },
    ) {
        // Vertical stack only: drawer rows then main (UIKit: drawer.bottom = main.top).
        Column(modifier = Modifier.fillMaxWidth()) {
            if (arrowsOpen) {
                ToolbarKeysEqualRow(
                    ids = listOf("arrowLeft", "arrowDown", "arrowUp", "arrowRight"),
                    sizes = sizes,
                    rowHeight = sizes.drawerHeight,
                    layout = layout,
                    writingMode = writingMode,
                    keyboardExpanded = keyboardExpanded,
                    fillEqually = true,
                    onPress = { pressId(it) },
                )
                ToolbarDrawerSeparator()
            }
            // Measure width for overflow; Column (not Box) places drawers above main.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val buttonWidthPx = with(density) { sizes.button.normalWidth.toPx() }
                val edgePadPx = with(density) { (sizes.edgePadding * 2).toPx() }
                val availablePx = (constraints.maxWidth.toFloat() - edgePadPx).coerceAtLeast(0f)
                val effectiveDrawers = layout.effectiveDrawers(availablePx, buttonWidthPx)
                val effectiveMain = layout.effectiveMain(availablePx, buttonWidthPx)
                // Stack mode: Apple adds drawer indices high→low so row 0 sits
                // nearest the main toolbar. Reverse take() to match.
                val drawerRows = if (layout.drawerToggleMode == "cycle" && revealedDrawers > 0) {
                    listOf(effectiveDrawers.getOrElse(cycleIndex) { emptyList() })
                } else {
                    effectiveDrawers.take(revealedDrawers).asReversed()
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (row in drawerRows) {
                        ToolbarKeysEqualRow(
                            ids = row,
                            sizes = sizes,
                            rowHeight = sizes.drawerHeight,
                            layout = layout,
                            writingMode = writingMode,
                            keyboardExpanded = keyboardExpanded,
                            fillEqually = false,
                            onPress = { pressId(it) },
                        )
                        ToolbarDrawerSeparator()
                    }
                    ToolbarKeysEqualRow(
                        ids = effectiveMain,
                        sizes = sizes,
                        rowHeight = sizes.height,
                        layout = layout,
                        writingMode = writingMode,
                        keyboardExpanded = keyboardExpanded,
                        fillEqually = true,
                        trailing = {
                            ToolbarKeyChip(
                                face = toolbarKeyFace(
                                    id = "ime",
                                    keyboardExpanded = keyboardExpanded,
                                ),
                                sizes = sizes.button,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .pointerInput(keyboardExpanded) {
                                        var dragDistanceY = 0f
                                        detectDragGestures(
                                            onDragStart = { dragDistanceY = 0f },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragDistanceY += dragAmount.y
                                            },
                                            onDragEnd = {
                                                if (!keyboardExpanded && abs(dragDistanceY) > 28f) {
                                                    onCollapseToPipByDrag()
                                                }
                                            },
                                            onDragCancel = { dragDistanceY = 0f },
                                        )
                                    },
                                onClick = onToggleKeyboardExpanded,
                            )
                        },
                        onPress = { pressId(it) },
                    )
                }
            }
        }
    }
}

private const val PREFS_WRITING = "wawona_toolbar_writing_assistance"

@Composable
private fun ToolbarKeysEqualRow(
    ids: List<String>,
    sizes: ToolbarSizes.Toolbar,
    rowHeight: Dp = sizes.height,
    layout: ToolbarLayoutStore,
    writingMode: Int,
    keyboardExpanded: Boolean,
    fillEqually: Boolean,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onPress: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .padding(horizontal = sizes.edgePadding, vertical = sizes.padding)
            .then(if (fillEqually) Modifier else Modifier.horizontalScroll(scroll)),
        horizontalArrangement = Arrangement.spacedBy(sizes.spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ids.forEach { id ->
            val custom = layout.custom(id)
            val spec = ToolbarCatalog.spec(id)
            val active = when (spec.mod) {
                "shift" -> ModifierState.shiftActive
                "ctrl" -> ModifierState.ctrlActive
                "alt" -> ModifierState.altActive
                "super" -> ModifierState.superActive
                else -> false
            }
            val isLocked = when (spec.mod) {
                "shift" -> ModifierState.shiftLocked
                "ctrl" -> ModifierState.ctrlLocked
                "alt" -> ModifierState.altLocked
                "super" -> ModifierState.superLocked
                else -> false
            }
            val wide = spec.kind == ToolbarKeyKind.MOD ||
                id == "esc" ||
                id == "dismiss" ||
                id == "tabSwitcher"
            val intrinsicW = if (wide) sizes.button.wideWidth else sizes.button.normalWidth
            ToolbarKeyChip(
                face = toolbarKeyFace(
                    id = id,
                    writingMode = writingMode,
                    keyboardExpanded = keyboardExpanded,
                    customLabel = custom?.label,
                ),
                sizes = sizes.button,
                modifier = if (fillEqually) {
                    Modifier.weight(1f).fillMaxHeight()
                } else {
                    Modifier
                        .width(intrinsicW)
                        .height(sizes.button.height)
                },
                active = active,
                locked = isLocked,
                onClick = { onPress(id) },
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * Rootshell-style customize sheet. Dismiss via sheet drag handle (Material).
 * Trailing Edit toggles rearrange; drag handles reorder rows (not Up/Down).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarKeysSettingsSheet(
    layout: ToolbarLayoutStore,
    hostOptions: ToolbarHostOptions.State,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scroll = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge.copy(
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        MaterialTheme.shapes.extraSmall,
                    ),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            TopAppBar(
                title = {
                    Text(
                        "Toolbar Keys",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    TextButton(onClick = { editing = !editing }) {
                        Text(if (editing) "Done" else "Edit")
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Docking", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = {
                    hostOptions.updatePersistent(!hostOptions.persistentToolbar)
                }) {
                    Text(
                        if (hostOptions.persistentToolbar) {
                            "Persistent Toolbar: On"
                        } else {
                            "Persistent Toolbar: Off"
                        },
                    )
                }
                TextButton(onClick = {
                    hostOptions.updateShowWithHardware(!hostOptions.showWithHardwareKeyboard)
                }) {
                    Text(
                        if (hostOptions.showWithHardwareKeyboard) {
                            "Show with Hardware Keyboard: On"
                        } else {
                            "Show with Hardware Keyboard: Off"
                        },
                    )
                }
                Text("Appearance", style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = {
                    hostOptions.updateThemeAware(!hostOptions.themeAware)
                }) {
                    Text(
                        if (hostOptions.themeAware) "Follow Theme: On" else "Follow Theme: Off",
                    )
                }
                TextButton(onClick = {
                    val styles = ToolbarHostOptions.ChromeStyle.entries
                    val next = styles[(styles.indexOf(hostOptions.chromeStyle) + 1) % styles.size]
                    hostOptions.updateChromeStyle(next)
                }) {
                    Text("Toolbar Style: ${hostOptions.chromeStyle.label}")
                }
                TextButton(onClick = {
                    val modes = ToolbarHostOptions.ArrowMode.entries
                    val next = modes[(modes.indexOf(hostOptions.arrowMode) + 1) % modes.size]
                    hostOptions.updateArrowMode(next)
                }) {
                    Text("Arrow Key Mode: ${hostOptions.arrowMode.label}")
                }
                Text("Main Row", style = MaterialTheme.typography.labelLarge)
                ToolbarReorderableSection(
                    ids = layout.main,
                    editing = editing,
                    titleFor = { id ->
                        layout.custom(id)?.label ?: ToolbarCatalog.spec(id).label
                    },
                    onReorder = { from, to -> layout.reorder("main", from, to) },
                    onMoveSection = { id -> layout.moveTo(id, "main", "drawer0") },
                    moveLabel = "Drawer",
                    onHide = { id -> layout.hide(id) },
                )
                layout.drawers.forEachIndexed { index, row ->
                    Text("Drawer ${index + 1}", style = MaterialTheme.typography.labelLarge)
                    ToolbarReorderableSection(
                        ids = row,
                        editing = editing,
                        titleFor = { id ->
                            layout.custom(id)?.label ?: ToolbarCatalog.spec(id).label
                        },
                        onReorder = { from, to -> layout.reorder("drawer$index", from, to) },
                        onMoveSection = { id ->
                            layout.moveTo(id, "drawer$index", "main")
                        },
                        moveLabel = "Main",
                        onHide = { id -> layout.hide(id) },
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Drawer Rows", modifier = Modifier.weight(1f))
                    TextButton(onClick = { layout.setDrawerRowCount(layout.drawers.size - 1) }) {
                        Text("-")
                    }
                    Text("${layout.drawers.size}")
                    TextButton(onClick = { layout.setDrawerRowCount(layout.drawers.size + 1) }) {
                        Text("+")
                    }
                }
                TextButton(onClick = { layout.updateDrawerOpenByDefault(!layout.drawerOpenByDefault) }) {
                    Text(
                        if (layout.drawerOpenByDefault) {
                            "Open Drawer by Default: On"
                        } else {
                            "Open Drawer by Default: Off"
                        },
                    )
                }
                TextButton(onClick = {
                    layout.updateDrawerToggleMode(
                        if (layout.drawerToggleMode == "cycle") "stack" else "cycle",
                    )
                }) {
                    Text(
                        if (layout.drawerToggleMode == "cycle") {
                            "More Button: Cycle"
                        } else {
                            "More Button: Stack"
                        },
                    )
                }
                Text("Custom Keys", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text to send") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TextButton(onClick = {
                    layout.addCustom(label, text)
                    label = ""
                    text = ""
                }) { Text("New Custom Key") }
                if (layout.hidden.isNotEmpty()) {
                    Text("Hidden Keys", style = MaterialTheme.typography.labelLarge)
                    layout.hidden.forEach { id ->
                        TextButton(onClick = { layout.unhide(id) }) {
                            Text(layout.custom(id)?.label ?: ToolbarCatalog.spec(id).label)
                        }
                    }
                }
                TextButton(onClick = { layout.reset() }) {
                    Text("Reset to Defaults")
                }
            }
        }
    }
}

/**
 * Material rearrangeable rows: drag handle in Edit mode (ItemTouchHelper-style).
 * Drawer / Hide stay available like Rootshell list actions.
 */
@Composable
private fun ToolbarReorderableSection(
    ids: List<String>,
    editing: Boolean,
    titleFor: (String) -> String,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onMoveSection: (String) -> Unit,
    moveLabel: String,
    onHide: (String) -> Unit,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 48.dp.toPx() }
    ids.forEachIndexed { index, id ->
        key(id) {
            var dragOffset by remember(id) { mutableFloatStateOf(0f) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .zIndex(if (dragOffset != 0f) 1f else 0f)
                    .graphicsLayer { translationY = dragOffset },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (editing) {
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(id, ids) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val delta = (dragOffset / rowHeightPx).roundToInt()
                                        val target = (index + delta).coerceIn(0, ids.lastIndex)
                                        if (target != index) onReorder(index, target)
                                        dragOffset = 0f
                                    },
                                    onDragCancel = { dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                    },
                                )
                            },
                    )
                }
                Text(
                    titleFor(id),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyLarge,
                )
                TextButton(onClick = { onMoveSection(id) }) { Text(moveLabel) }
                TextButton(onClick = { onHide(id) }) { Text("Hide") }
            }
        }
    }
}
