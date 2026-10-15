//! Toolbar Keys host options (Rootshell KeyboardToolbar settings).
//! Layout document stays in [`crate::model`]; these prefs are separate.

use serde::{Deserialize, Serialize};

/// Rootshell `TerminalTouchKeyboardModel.Style` ids that also tint toolbar chrome.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "kebab-case")]
pub enum ToolbarChromeStyle {
    #[default]
    Flat,
    Sculpted,
    Steampunk,
    Phosphor,
    #[serde(rename = "beige-box")]
    BeigeBox,
    #[serde(rename = "neon-grid")]
    NeonGrid,
    #[serde(rename = "circuit-board")]
    CircuitBoard,
}

impl ToolbarChromeStyle {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Flat => "flat",
            Self::Sculpted => "sculpted",
            Self::Steampunk => "steampunk",
            Self::Phosphor => "phosphor",
            Self::BeigeBox => "beige-box",
            Self::NeonGrid => "neon-grid",
            Self::CircuitBoard => "circuit-board",
        }
    }

    pub fn parse(raw: &str) -> Option<Self> {
        match raw {
            "flat" => Some(Self::Flat),
            "sculpted" => Some(Self::Sculpted),
            "steampunk" => Some(Self::Steampunk),
            "phosphor" => Some(Self::Phosphor),
            "beige-box" => Some(Self::BeigeBox),
            "neon-grid" => Some(Self::NeonGrid),
            "circuit-board" => Some(Self::CircuitBoard),
            _ => None,
        }
    }

    pub fn display_name(self) -> &'static str {
        match self {
            Self::Flat => "Flat",
            Self::Sculpted => "Sculpted",
            Self::Steampunk => "Steampunk",
            Self::Phosphor => "Phosphor",
            Self::BeigeBox => "Beige Box",
            Self::NeonGrid => "Neon Grid",
            Self::CircuitBoard => "Circuit Board",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum ArrowJoystickMode {
    #[default]
    Joystick,
    Cluster,
}

impl ArrowJoystickMode {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Joystick => "joystick",
            Self::Cluster => "cluster",
        }
    }

    pub fn parse(raw: &str) -> Option<Self> {
        match raw {
            "joystick" => Some(Self::Joystick),
            "cluster" => Some(Self::Cluster),
            _ => None,
        }
    }
}

/// Rootshell `Settings.KeyboardToolbar` host options (not layout slots).
#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ToolbarOptions {
    /// Keep toolbar docked while a hardware keyboard is connected.
    pub show_with_hardware_keyboard: bool,
    /// Keep toolbar visible when the software keyboard is dismissed.
    pub persistent_toolbar: bool,
    /// Follow terminal / host theme for keycap and glass tint.
    pub theme_aware: bool,
    pub chrome_style: ToolbarChromeStyle,
    pub arrow_joystick_mode: ArrowJoystickMode,
}

impl Default for ToolbarOptions {
    fn default() -> Self {
        Self {
            show_with_hardware_keyboard: false,
            persistent_toolbar: false,
            theme_aware: true,
            chrome_style: ToolbarChromeStyle::Flat,
            arrow_joystick_mode: ArrowJoystickMode::Joystick,
        }
    }
}

impl ToolbarOptions {
    pub fn to_json(&self) -> String {
        serde_json::to_string(self).unwrap_or_else(|_| "{}".into())
    }

    pub fn from_json(raw: &str) -> Result<Self, String> {
        serde_json::from_str(raw).map_err(|e| e.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn round_trip_defaults() {
        let opts = ToolbarOptions::default();
        let again = ToolbarOptions::from_json(&opts.to_json()).unwrap();
        assert_eq!(opts, again);
        assert!(!opts.show_with_hardware_keyboard);
        assert!(opts.theme_aware);
    }
}
