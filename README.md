# ToolbarKeys

Rust model of the Rootshell keyboard toolbar: built-in keys, drawer rows,
custom sequences, and the bytes those keys send. Wawona and any other host
draw the slots. This crate does not link UIKit or Android views.

iOS deployment stays 13.0. The same document is the Android layout. Watch,
tvOS, and visionOS can use the `phone` or `pad` form. They do not get a
second key catalog.

## What lives here

- Stable key ids (`esc`, `drawerToggle`, `pipe`, ...). Do not rename a shipped id.
- Phone and pad default layouts at document version 14.
- Hide, unhide, drawer row count (merge on shrink, max 5), custom keys.
- Effective rows for a measured width, including the drawer-toggle guarantee.
- Custom sequence bytes (Ctrl+A, Shift+Up, and the other Rootshell cases).
- UniFFI object `ToolbarSession` plus `toolbar_catalog_json`.

Generated Swift and Kotlin bindings are Nix outputs. Do not commit them.

## What stays in the app

The row of buttons above the software keyboard is a platform view:

- iOS and iPadOS: UIKit input accessory, iOS 13 and later.
- Android: a Compose or View row above the IME.
- The host sends text bytes to the Wawona PTY or to Ghostty external I/O.
  Action ids (`__dismiss__`, `__paste__`, ...) stay in the host.

## License

MIT. The key catalog and layout behavior are adapted from Rootshell
(Copyright 2026 Rootshell LLC, Kit Knox), also MIT.

## Layer

L3′ library. `github.com/Wawona/ToolbarKeys`. Wawona (L4) may depend on it.
This repo does not depend on Wawona, Ghostty, or iland.
