# Android Compose (Toolbar Keys)

Package `com.wawona.toolbarkeys`. Owns the accessory bar, key chrome, layout
store, and settings sheet. Wawona overlays this tree (flake input
`toolbar-keys`) or adds `ToolbarKeys/android` via Gradle `sourceSets`.

Do not put Wawona JNI defaults here. Hosts pass `onCommitText` /
`onInjectKey` / `onInjectModifiers`.

Settings: `ModalBottomSheet` (swipe/drag dismiss). Trailing **Edit** toggles
rearrange. In Edit mode each row shows a Material **drag handle**; drag to
reorder within Main / Drawer (no leading Done, no Up/Down).

Meta face is diamond ◆ (`U+25C6`), not Win ⊞ and not Mac ⌘.
