// Copyright (c) 2026 Rootshell LLC, Kit Knox. Toolbar starting point. MIT.

import Foundation

public enum TerminalWritingAssistanceMode: String, Codable, CaseIterable, Hashable, Sendable {
    case off, suggestions, autocorrect

    /// Stable feature identity for the toolbar and customization palette.
    /// Mode-specific icons belong only to the choices inside the menu.
    public static let toolbarIcon = "textformat.abc"

    public var title: String {
        switch self {
        case .off: NSLocalizedString("Off", comment: "")
        case .suggestions: NSLocalizedString("Suggestions", comment: "")
        case .autocorrect: NSLocalizedString("Autocorrect", comment: "")
        }
    }

    public var icon: String {
        switch self {
        case .off: "textformat.abc.dottedunderline"
        case .suggestions: "textformat.abc"
        case .autocorrect: "wand.and.stars"
        }
    }
}
