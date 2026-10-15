#if canImport(UIKit)
import UIKit

/// Rootshell `TerminalTouchKeyboardModel.Style` ids used to tint Toolbar Keys
/// chrome (glass plate + keycap highlight). Full OSK artwork stays optional;
/// accessory hosts read these prefs from `KeyboardToolbarManager`.
public enum ToolbarChromeStyle: String, CaseIterable, Sendable {
    case flat
    case sculpted
    case steampunk
    case phosphor
    case beigeBox = "beige-box"
    case neonGrid = "neon-grid"
    case circuitBoard = "circuit-board"

    public var displayName: String {
        switch self {
        case .flat: return "Flat"
        case .sculpted: return "Sculpted"
        case .steampunk: return "Steampunk"
        case .phosphor: return "Phosphor"
        case .beigeBox: return "Beige Box"
        case .neonGrid: return "Neon Grid"
        case .circuitBoard: return "Circuit Board"
        }
    }
}

public enum ToolbarArrowJoystickMode: String, CaseIterable, Sendable {
    case joystick
    case cluster

    public var displayName: String {
        switch self {
        case .joystick: return "Joystick"
        case .cluster: return "Cluster"
        }
    }
}

public enum ToolbarChromePalette {
    /// Glass plate tint. Theme-aware follows light/dark; styled looks pick a
    /// Rootshell-inspired accent wash over the same blur plate.
    public static func glassTint(
        style: ToolbarChromeStyle,
        themeAware: Bool,
        traits: UITraitCollection
    ) -> UIColor {
        let dark = traits.userInterfaceStyle == .dark
        if themeAware {
            let base = dark ? UIColor.black : UIColor.white
            return base.withAlphaComponent(dark ? 0.22 : 0.55)
        }
        switch style {
        case .flat, .sculpted:
            let base = dark ? UIColor.black : UIColor.white
            return base.withAlphaComponent(dark ? 0.28 : 0.62)
        case .steampunk:
            return UIColor(red: 0.42, green: 0.28, blue: 0.12, alpha: dark ? 0.55 : 0.35)
        case .phosphor:
            return UIColor(red: 0.05, green: 0.35, blue: 0.12, alpha: dark ? 0.55 : 0.30)
        case .beigeBox:
            return UIColor(red: 0.78, green: 0.72, blue: 0.58, alpha: dark ? 0.40 : 0.55)
        case .neonGrid:
            return UIColor(red: 0.55, green: 0.10, blue: 0.65, alpha: dark ? 0.45 : 0.28)
        case .circuitBoard:
            return UIColor(red: 0.05, green: 0.28, blue: 0.18, alpha: dark ? 0.55 : 0.32)
        }
    }

    public static func keyHighlight(
        style: ToolbarChromeStyle,
        themeAware: Bool,
        traits: UITraitCollection
    ) -> UIColor {
        if themeAware || style == .flat || style == .sculpted {
            return KeyboardKeyColors.highlight
        }
        return glassTint(style: style, themeAware: false, traits: traits)
            .withAlphaComponent(traits.userInterfaceStyle == .dark ? 0.45 : 0.35)
    }
}
#endif
