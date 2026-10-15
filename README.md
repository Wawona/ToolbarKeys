# ToolbarKeys

The toolbar starts from Rootshell. Copyright (c) 2026 Rootshell LLC, Kit Knox
(MIT). That is the implementation Wawona picked up: built-in keys, drawer
rows, custom sequences, and the bytes those keys send.

This repo is the Rust model plus the host UI:

- `apple/Keyboard`: UIKit views (iOS / iPadOS / tvOS / visionOS)
- `android/com/wawona/toolbarkeys`: Jetpack Compose (`ToolbarKeysBar`,
  chrome, layout store, settings sheet)

Wawona compiles / overlays those trees. It does not keep a second copy.
This crate does not link UIKit or Android views into the Rust staticlib.

iOS deployment stays 13.0. The same document is the Android layout. Watch,
tvOS, and visionOS can use the `phone` or `pad` form. They do not get a
second key catalog.

Post-13 UIKit (UIMenu, `showsMenuAsPrimaryAction`, GCKeyboard,
`registerForTraitChanges`, UIHoverStyle on visionOS) stays behind
`#available`. Pre-17 trait updates use `traitCollectionDidChange`.
Do not raise Wawona's `IPHONEOS_DEPLOYMENT_TARGET` for this tree.

## What lives here

- Stable key ids (`esc`, `drawerToggle`, `pipe`, ...). Do not rename a shipped id.
- Phone and pad default layouts at document version 14.
- Hide, unhide, drawer row count (merge on shrink, max 5), custom keys.
- Effective rows for a measured width, including the drawer-toggle guarantee.
- Custom sequence bytes (Ctrl+A, Shift+Up, and the other Rootshell cases).
- UniFFI object `ToolbarSession` plus `toolbar_catalog_json`.

Generated Swift and Kotlin bindings are Nix outputs. Do not commit them.

## UIKit views

`apple/Keyboard` is the iOS, iPadOS, tvOS, and visionOS drawing code
(buttons, drawer, accessory chrome). Wawona compiles that directory from
this repo. It does not keep a second copy.

Settings: `KeyboardToolbarSettingsView` (Done + EditButton rearrange),
presented as a page sheet from `WWNKeyboardAccessoryView`.

`WWNKeyboardAccessoryView` stays in Wawona. That file is the Wayland seat
bridge (evdev keycodes into the compositor), not the toolbar library.

## Android Compose

`android/com/wawona/toolbarkeys` owns the accessory bar, key chrome, layout
prefs, and settings `ModalBottomSheet` (Done + Edit rearrange). Package
`com.wawona.toolbarkeys`. Wawona overlays or `sourceSets` that tree; inject
callbacks stay in `MainActivity` (no Wawona JNI inside this library).

The Rust crate is the layout and byte model. Swift / Compose still carry
the Rootshell types the views switch on. New layout rules land in Rust.
Do not grow a third copy in Wawona.

## License

MIT. Copyright (c) 2026 Rootshell LLC, Kit Knox, and Copyright (c) 2026
Wawona. The toolbar implementation starts from Rootshell.

## Layer

L3′ library. `github.com/Wawona/ToolbarKeys`. Wawona (L4) may depend on it.
This repo does not depend on Wawona or iland.
