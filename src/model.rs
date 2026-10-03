//! Layout document. Defaults match Rootshell `ToolbarLayoutConfig` version 14.

use crate::bytes::{ComboKey, KeyCombo, Modifier, SequenceStep, SpecialKey};
use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;

pub const LAYOUT_VERSION: i32 = 14;
pub const MAX_DRAWER_ROWS: usize = 5;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum FormFactor {
    Phone,
    Pad,
}

impl FormFactor {
    pub fn parse(raw: &str) -> Option<Self> {
        match raw {
            "phone" => Some(Self::Phone),
            "pad" => Some(Self::Pad),
            _ => None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Phone => "phone",
            Self::Pad => "pad",
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum DrawerToggleMode {
    Stack,
    Cycle,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Slot {
    BuiltIn(String),
    Custom(String),
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct KeyInfo {
    pub id: &'static str,
    pub display_name: &'static str,
    pub icon: Option<&'static str>,
    pub value: &'static str,
    pub category: &'static str,
}

pub fn catalog() -> &'static [KeyInfo] {
    &CATALOG
}

pub fn lookup(id: &str) -> Option<&'static KeyInfo> {
    CATALOG.iter().find(|key| key.id == id)
}

pub fn press_built_in(id: &str) -> Option<Vec<u8>> {
    let key = lookup(id)?;
    if key.category == "modifier" || key.value.starts_with("__") {
        return Some(Vec::new());
    }
    Some(key.value.as_bytes().to_vec())
}

pub fn catalog_json() -> String {
    let rows: Vec<_> = CATALOG
        .iter()
        .map(|key| {
            serde_json::json!({
                "id": key.id,
                "displayName": key.display_name,
                "icon": key.icon,
                "value": key.value,
                "category": key.category,
            })
        })
        .collect();
    serde_json::to_string(&rows).unwrap_or_else(|_| "[]".into())
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct CustomKey {
    pub id: String,
    pub label: String,
    pub icon_name: Option<String>,
    pub sequence: Vec<SequenceStep>,
}

impl CustomKey {
    pub fn terminal_bytes(&self) -> Vec<u8> {
        let mut out = Vec::new();
        for step in &self.sequence {
            out.extend(step.terminal_bytes());
        }
        out
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ToolbarStore {
    pub form: FormFactor,
    pub version: i32,
    pub main_row: Vec<Slot>,
    pub drawer_rows: Vec<Vec<Slot>>,
    pub hidden: BTreeSet<String>,
    pub custom_keys: Vec<CustomKey>,
    pub drawer_open_by_default: bool,
    pub drawer_toggle_mode: DrawerToggleMode,
}

impl ToolbarStore {
    pub fn new(form: FormFactor) -> Self {
        let (main_row, drawer_rows) = defaults(form);
        Self {
            form,
            version: LAYOUT_VERSION,
            main_row,
            drawer_rows,
            hidden: BTreeSet::new(),
            custom_keys: Vec::new(),
            drawer_open_by_default: false,
            drawer_toggle_mode: DrawerToggleMode::Stack,
        }
    }

    pub fn reset(&mut self) {
        let form = self.form;
        *self = Self::new(form);
    }

    pub fn set_drawer_row_count(&mut self, count: usize) {
        let target = count.clamp(1, MAX_DRAWER_ROWS);
        if target == self.drawer_rows.len() {
            return;
        }
        if self.drawer_rows.is_empty() {
            self.drawer_rows.push(Vec::new());
        }
        if target > self.drawer_rows.len() {
            self.drawer_rows
                .resize_with(target, Vec::new);
        } else {
            let overflow: Vec<Slot> = self.drawer_rows.split_off(target).into_iter().flatten().collect();
            self.drawer_rows[target - 1].extend(overflow);
        }
    }

    pub fn set_layout(&mut self, main_row: Vec<Slot>, mut drawer_rows: Vec<Vec<Slot>>) {
        if drawer_rows.is_empty() {
            drawer_rows.push(Vec::new());
        }
        self.main_row = main_row;
        self.drawer_rows = drawer_rows;
    }

    pub fn hide(&mut self, key_id: &str) -> Result<(), String> {
        if lookup(key_id).is_none() {
            return Err(format!("unknown key '{key_id}'"));
        }
        let slot = Slot::BuiltIn(key_id.to_string());
        self.main_row.retain(|item| item != &slot);
        for row in &mut self.drawer_rows {
            row.retain(|item| item != &slot);
        }
        self.hidden.insert(key_id.to_string());
        Ok(())
    }

    pub fn unhide(&mut self, key_id: &str) -> Result<(), String> {
        if lookup(key_id).is_none() {
            return Err(format!("unknown key '{key_id}'"));
        }
        self.hidden.remove(key_id);
        self.ensure_drawer();
        self.drawer_rows[0].push(Slot::BuiltIn(key_id.to_string()));
        Ok(())
    }

    pub fn create_custom(&mut self, key: CustomKey) {
        let slot = Slot::Custom(key.id.clone());
        self.custom_keys.push(key);
        self.ensure_drawer();
        self.drawer_rows[0].push(slot);
    }

    pub fn update_custom(&mut self, key: CustomKey) -> Result<(), String> {
        let Some(found) = self.custom_keys.iter_mut().find(|item| item.id == key.id) else {
            return Err(format!("unknown custom key '{}'", key.id));
        };
        *found = key;
        Ok(())
    }

    pub fn delete_custom(&mut self, id: &str) {
        self.custom_keys.retain(|key| key.id != id);
        let slot = Slot::Custom(id.to_string());
        self.main_row.retain(|item| item != &slot);
        for row in &mut self.drawer_rows {
            row.retain(|item| item != &slot);
        }
    }

    pub fn to_json(&self) -> String {
        serde_json::to_string_pretty(&self.to_doc()).unwrap_or_else(|_| "{}".into())
    }

    pub fn from_json(raw: &str) -> Result<Self, String> {
        let doc: Document = serde_json::from_str(raw).map_err(|err| err.to_string())?;
        let form = FormFactor::parse(&doc.form).ok_or_else(|| format!("unknown form '{}'", doc.form))?;
        let mut store = Self {
            form,
            version: doc.version,
            main_row: doc.main_row,
            drawer_rows: if doc.drawer_rows.is_empty() {
                doc.drawer_row.map(|row| vec![row]).unwrap_or_else(|| vec![Vec::new()])
            } else {
                doc.drawer_rows
            },
            hidden: doc.hidden_keys.into_iter().collect(),
            custom_keys: doc.custom_keys.into_iter().map(CustomKey::from_doc).collect::<Result<Vec<_>, _>>()?,
            drawer_open_by_default: doc.drawer_open_by_default,
            drawer_toggle_mode: doc.drawer_toggle_mode,
        };
        store.migrate();
        Ok(store)
    }

    pub fn effective_json(&self, available_width: f64, button_width: f64) -> String {
        let (main, drawers) = self.effective(available_width, button_width);
        serde_json::to_string(&serde_json::json!({
            "mainRow": main,
            "drawerRows": drawers,
            "drawerToggleMode": self.drawer_toggle_mode,
            "drawerOpenByDefault": self.drawer_open_by_default,
        }))
        .unwrap_or_else(|_| "{}".into())
    }

    fn effective(&self, available_width: f64, button_width: f64) -> (Vec<Slot>, Vec<Vec<Slot>>) {
        let capacity = if button_width > 0.0 {
            (available_width / button_width).floor().max(1.0) as usize
        } else {
            0
        };
        let main_valid = self.valid_slots(&self.main_row);
        let mut visible: Vec<Slot> = main_valid.iter().take(capacity).cloned().collect();
        let mut overflow: Vec<Slot> = main_valid.iter().skip(capacity).cloned().collect();
        let mut drawers: Vec<Vec<Slot>> = self.drawer_rows.iter().map(|row| self.valid_slots(row)).collect();
        if drawers.is_empty() {
            drawers.push(Vec::new());
        }
        let any_drawer = !overflow.is_empty()
            || drawers.iter().any(|row| !row.is_empty());
        let toggle = Slot::BuiltIn("drawerToggle".into());
        if any_drawer
            && !visible.contains(&toggle)
            && !self.hidden.contains("drawerToggle")
        {
            overflow.retain(|slot| slot != &toggle);
            if let Some(last) = visible.pop() {
                if last != toggle {
                    overflow.insert(0, last);
                }
                visible.push(toggle);
            } else {
                visible = vec![toggle];
            }
        } else {
            overflow.retain(|slot| slot != &toggle);
        }
        drawers[0] = overflow.into_iter().chain(drawers[0].iter().cloned()).collect();
        (visible, drawers)
    }

    fn valid_slots(&self, slots: &[Slot]) -> Vec<Slot> {
        let custom: BTreeSet<&str> = self.custom_keys.iter().map(|key| key.id.as_str()).collect();
        slots
            .iter()
            .filter(|slot| match slot {
                Slot::BuiltIn(id) => !self.hidden.contains(id),
                Slot::Custom(id) => custom.contains(id.as_str()),
            })
            .cloned()
            .collect()
    }

    fn ensure_drawer(&mut self) {
        if self.drawer_rows.is_empty() {
            self.drawer_rows.push(Vec::new());
        }
    }

    fn migrate(&mut self) {
        if self.version >= LAYOUT_VERSION {
            self.ensure_drawer();
            return;
        }
        self.ensure_drawer();
        if self.version < 3 && !self.hidden.contains("toolbarSettings") {
            let settings = Slot::BuiltIn("toolbarSettings".into());
            self.main_row.retain(|slot| slot != &settings);
            for row in &mut self.drawer_rows {
                row.retain(|slot| slot != &settings);
            }
            if let Some(index) = self
                .main_row
                .iter()
                .position(|slot| slot == &Slot::BuiltIn("drawerToggle".into()))
            {
                self.main_row.insert(index + 1, settings);
            } else {
                self.main_row.push(settings);
            }
        }
        let present: BTreeSet<String> = self
            .main_row
            .iter()
            .chain(self.drawer_rows.iter().flatten())
            .filter_map(|slot| match slot {
                Slot::BuiltIn(id) => Some(id.clone()),
                Slot::Custom(_) => None,
            })
            .collect();
        let (default_main, default_drawers) = defaults(self.form);
        for slot in default_main {
            if let Slot::BuiltIn(id) = &slot {
                if !present.contains(id) && !self.hidden.contains(id) {
                    self.main_row.push(slot);
                }
            }
        }
        let present_after: BTreeSet<String> = self
            .main_row
            .iter()
            .chain(self.drawer_rows.iter().flatten())
            .filter_map(|slot| match slot {
                Slot::BuiltIn(id) => Some(id.clone()),
                Slot::Custom(_) => None,
            })
            .collect();
        for slot in default_drawers.into_iter().flatten() {
            if let Slot::BuiltIn(id) = &slot {
                if !present_after.contains(id) && !self.hidden.contains(id) {
                    self.drawer_rows[0].push(slot);
                }
            }
        }
        self.main_row.retain(|slot| match slot {
            Slot::BuiltIn(id) => lookup(id).is_some(),
            Slot::Custom(_) => true,
        });
        self.hidden.retain(|id| lookup(id).is_some());
        self.version = LAYOUT_VERSION;
    }

    fn to_doc(&self) -> Document {
        Document {
            version: self.version,
            form: self.form.as_str().into(),
            main_row: self.main_row.clone(),
            drawer_rows: self.drawer_rows.clone(),
            drawer_row: self.drawer_rows.first().cloned(),
            hidden_keys: self.hidden.iter().cloned().collect(),
            custom_keys: self.custom_keys.iter().map(CustomKey::to_doc).collect(),
            drawer_open_by_default: self.drawer_open_by_default,
            drawer_toggle_mode: self.drawer_toggle_mode,
        }
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Document {
    version: i32,
    form: String,
    main_row: Vec<Slot>,
    #[serde(default)]
    drawer_rows: Vec<Vec<Slot>>,
    #[serde(default)]
    drawer_row: Option<Vec<Slot>>,
    #[serde(default)]
    hidden_keys: Vec<String>,
    #[serde(default)]
    custom_keys: Vec<CustomKeyDoc>,
    #[serde(default)]
    drawer_open_by_default: bool,
    #[serde(default)]
    drawer_toggle_mode: DrawerToggleMode,
}

impl Default for DrawerToggleMode {
    fn default() -> Self {
        Self::Stack
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CustomKeyDoc {
    id: String,
    label: String,
    #[serde(default)]
    icon_name: Option<String>,
    sequence: Vec<StepDoc>,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
enum StepDoc {
    Text(String),
    KeyCombo(ComboDoc),
}

#[derive(Serialize, Deserialize)]
struct ComboDoc {
    modifiers: Vec<String>,
    key: ComboKeyDoc,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
enum ComboKeyDoc {
    Letter(String),
    Digit(String),
    Symbol(String),
    Special(String),
}

impl CustomKey {
    fn to_doc(&self) -> CustomKeyDoc {
        CustomKeyDoc {
            id: self.id.clone(),
            label: self.label.clone(),
            icon_name: self.icon_name.clone(),
            sequence: self
                .sequence
                .iter()
                .map(|step| match step {
                    SequenceStep::Text(text) => StepDoc::Text(text.clone()),
                    SequenceStep::KeyCombo(combo) => StepDoc::KeyCombo(ComboDoc {
                        modifiers: combo.modifiers.iter().copied().map(Modifier::as_str).map(str::to_string).collect(),
                        key: match &combo.key {
                            ComboKey::Letter(ch) => ComboKeyDoc::Letter(ch.to_string()),
                            ComboKey::Digit(ch) => ComboKeyDoc::Digit(ch.to_string()),
                            ComboKey::Symbol(ch) => ComboKeyDoc::Symbol(ch.to_string()),
                            ComboKey::Special(key) => ComboKeyDoc::Special(key.as_str().into()),
                        },
                    }),
                })
                .collect(),
        }
    }

    fn from_doc(doc: CustomKeyDoc) -> Result<Self, String> {
        let mut sequence = Vec::new();
        for step in doc.sequence {
            sequence.push(match step {
                StepDoc::Text(text) => SequenceStep::Text(text),
                StepDoc::KeyCombo(combo) => {
                    let mut modifiers = Vec::new();
                    for raw in combo.modifiers {
                        modifiers.push(Modifier::parse(&raw).ok_or_else(|| format!("unknown modifier '{raw}'"))?);
                    }
                    let key = match combo.key {
                        ComboKeyDoc::Letter(value) => ComboKey::Letter(first_char(&value)?),
                        ComboKeyDoc::Digit(value) => ComboKey::Digit(first_char(&value)?),
                        ComboKeyDoc::Symbol(value) => ComboKey::Symbol(first_char(&value)?),
                        ComboKeyDoc::Special(value) => ComboKey::Special(
                            SpecialKey::parse(&value).ok_or_else(|| format!("unknown special '{value}'"))?,
                        ),
                    };
                    SequenceStep::KeyCombo(KeyCombo { modifiers, key })
                }
            });
        }
        Ok(Self {
            id: doc.id,
            label: doc.label,
            icon_name: doc.icon_name,
            sequence,
        })
    }
}

fn first_char(value: &str) -> Result<char, String> {
    value.chars().next().ok_or_else(|| "empty combo key".into())
}

fn slots(ids: &[&str]) -> Vec<Slot> {
    ids.iter().map(|id| Slot::BuiltIn((*id).to_string())).collect()
}

fn defaults(form: FormFactor) -> (Vec<Slot>, Vec<Vec<Slot>>) {
    const PHONE_MAIN: &[&str] = &[
        "dismiss",
        "tabSwitcher",
        "esc",
        "ctrl",
        "writingAssistance",
        "shift",
        "tab",
        "arrowDrawerToggle",
        "drawerToggle",
        "toolbarSettings",
    ];
    const PHONE_DRAWER: &[&str] = &[
        "alt", "cmd", "backtick", "tilde", "caret", "underscore", "backslash", "pipe",
        "leftBracket", "rightBracket", "leftBrace", "rightBrace", "slash", "questionMark",
        "dash", "equals", "singleQuote", "doubleQuote", "leftParen", "rightParen", "atSign",
        "hash", "dollar", "percent", "semicolon", "colon", "lessThan", "greaterThan",
        "ampersand", "asterisk", "paste", "compose", "voiceAgent", "toggleFullScreen",
        "toggleTabBar", "newConnection", "toggleMouseCapture", "aiAgent", "brightnessBoost",
        "clipboardManager", "appSettings",
    ];
    const PAD_EXTRA: &[&str] = &[
        "alt", "cmd", "backtick", "dash", "slash", "singleQuote", "semicolon", "leftBracket",
        "rightBracket",
    ];
    const PAD_DRAWER: &[&str] = &[
        "tilde", "caret", "underscore", "backslash", "pipe", "leftBrace", "rightBrace",
        "lessThan", "greaterThan", "questionMark", "equals", "doubleQuote", "colon",
        "leftParen", "rightParen", "atSign", "hash", "dollar", "percent", "ampersand",
        "asterisk", "paste", "compose", "voiceAgent", "toggleFullScreen", "toggleTabBar",
        "newConnection", "toggleMouseCapture", "aiAgent", "brightnessBoost", "clipboardManager",
        "appSettings",
    ];
    match form {
        FormFactor::Phone => (slots(PHONE_MAIN), vec![slots(PHONE_DRAWER)]),
        FormFactor::Pad => {
            let mut main = slots(PHONE_MAIN);
            main.extend(slots(PAD_EXTRA));
            (main, vec![slots(PAD_DRAWER)])
        }
    }
}

const CATALOG: [KeyInfo; 55] = [
    key("esc", "Escape", Some("escape"), "Esc", "modifier"),
    key("ctrl", "Control", Some("control"), "Ctrl", "modifier"),
    key("alt", "Option", Some("option"), "Alt", "modifier"),
    key("shift", "Shift", Some("shift"), "Shift", "modifier"),
    key("cmd", "Command", Some("command"), "Cmd", "modifier"),
    key("tab", "Tab", Some("arrow.right.to.line"), "\t", "special"),
    key("arrowDrawerToggle", "Arrow Joystick", Some("arrow.up.and.down.and.arrow.left.and.right"), "__arrowDrawer__", "navigation"),
    key("arrowUp", "Arrow Up", Some("arrow.up"), "\u{1b}[A", "navigation"),
    key("arrowDown", "Arrow Down", Some("arrow.down"), "\u{1b}[B", "navigation"),
    key("arrowLeft", "Arrow Left", Some("arrow.left"), "\u{1b}[D", "navigation"),
    key("arrowRight", "Arrow Right", Some("arrow.right"), "\u{1b}[C", "navigation"),
    key("backtick", "Backtick `", None, "`", "symbol"),
    key("tilde", "Tilde ~", None, "~", "symbol"),
    key("caret", "Caret ^", None, "^", "symbol"),
    key("underscore", "Underscore _", None, "_", "symbol"),
    key("backslash", "Backslash \\", None, "\\", "symbol"),
    key("pipe", "Pipe |", None, "|", "symbol"),
    key("leftBracket", "Left Bracket [", None, "[", "symbol"),
    key("rightBracket", "Right Bracket ]", None, "]", "symbol"),
    key("leftBrace", "Left Brace {", None, "{", "symbol"),
    key("rightBrace", "Right Brace }", None, "}", "symbol"),
    key("lessThan", "Less Than <", None, "<", "symbol"),
    key("greaterThan", "Greater Than >", None, ">", "symbol"),
    key("slash", "Slash /", None, "/", "symbol"),
    key("questionMark", "Question Mark ?", None, "?", "symbol"),
    key("dash", "Dash -", None, "-", "symbol"),
    key("equals", "Equals =", None, "=", "symbol"),
    key("singleQuote", "Single Quote '", None, "'", "symbol"),
    key("doubleQuote", "Double Quote \"", None, "\"", "symbol"),
    key("semicolon", "Semicolon ;", None, ";", "symbol"),
    key("colon", "Colon :", None, ":", "symbol"),
    key("leftParen", "Left Paren (", None, "(", "symbol"),
    key("rightParen", "Right Paren )", None, ")", "symbol"),
    key("atSign", "At Sign @", None, "@", "symbol"),
    key("hash", "Hash #", None, "#", "symbol"),
    key("dollar", "Dollar $", None, "$", "symbol"),
    key("percent", "Percent %", None, "%", "symbol"),
    key("ampersand", "Ampersand &", None, "&", "symbol"),
    key("asterisk", "Asterisk *", None, "*", "symbol"),
    key("dismiss", "Dismiss Keyboard", Some("chevron.down"), "__dismiss__", "action"),
    key("tabSwitcher", "Tab Switcher", Some("rectangle.stack"), "__tabswitcher__", "action"),
    key("compose", "Compose", Some("character.cursor.ibeam"), "__compose__", "action"),
    key("writingAssistance", "Writing Assistance", None, "__writingAssistance__", "action"),
    key("toolbarSettings", "Toolbar Settings", Some("gearshape"), "__toolbarSettings__", "action"),
    key("paste", "Paste", Some("doc.on.clipboard"), "__paste__", "action"),
    key("voiceAgent", "Voice Agent", Some("waveform.circle"), "__voiceAgent__", "action"),
    key("toggleFullScreen", "Toggle Full Screen", Some("arrow.up.left.and.arrow.down.right"), "__toggleFullScreen__", "action"),
    key("toggleTabBar", "Toggle Top Tab Bar", Some("menubar.rectangle"), "__toggleTabBar__", "action"),
    key("newConnection", "New Connection", Some("plus"), "__newConnection__", "action"),
    key("appSettings", "App Settings", Some("slider.horizontal.3"), "__appSettings__", "action"),
    key("toggleMouseCapture", "Toggle Mouse Capture", Some("computermouse"), "__toggleMouseCapture__", "action"),
    key("aiAgent", "AI Agent", Some("sparkles"), "__aiAgent__", "action"),
    key("brightnessBoost", "Brightness Boost", Some("sun.max"), "__brightnessBoost__", "action"),
    key("clipboardManager", "Clipboard Manager", Some("list.clipboard"), "__clipboardManager__", "action"),
    key("drawerToggle", "Drawer Toggle", Some("ellipsis"), "__extraDrawer__", "toggle"),
];

const fn key(
    id: &'static str,
    display_name: &'static str,
    icon: Option<&'static str>,
    value: &'static str,
    category: &'static str,
) -> KeyInfo {
    KeyInfo {
        id,
        display_name,
        icon,
        value,
        category,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn phone_default_starts_like_rootshell() {
        let store = ToolbarStore::new(FormFactor::Phone);
        assert_eq!(store.version, 14);
        assert_eq!(
            store.main_row.first(),
            Some(&Slot::BuiltIn("dismiss".into()))
        );
        assert!(store.main_row.contains(&Slot::BuiltIn("drawerToggle".into())));
        assert!(store.drawer_rows[0].contains(&Slot::BuiltIn("pipe".into())));
    }

    #[test]
    fn pad_keeps_phone_prefix_then_symbols() {
        let phone = ToolbarStore::new(FormFactor::Phone);
        let pad = ToolbarStore::new(FormFactor::Pad);
        assert_eq!(pad.main_row[..phone.main_row.len()], phone.main_row[..]);
        assert_eq!(pad.main_row[phone.main_row.len()], Slot::BuiltIn("alt".into()));
    }

    #[test]
    fn shrinking_drawer_merges_overflow() {
        let mut store = ToolbarStore::new(FormFactor::Phone);
        store.set_drawer_row_count(2);
        store.drawer_rows[1].push(Slot::BuiltIn("pipe".into()));
        store.set_drawer_row_count(1);
        assert_eq!(store.drawer_rows.len(), 1);
        assert!(store.drawer_rows[0].contains(&Slot::BuiltIn("pipe".into())));
    }

    #[test]
    fn narrow_width_keeps_drawer_toggle() {
        let store = ToolbarStore::new(FormFactor::Phone);
        let (main, drawers) = store.effective(80.0, 40.0);
        assert_eq!(main.len(), 2);
        assert_eq!(main[1], Slot::BuiltIn("drawerToggle".into()));
        assert!(!drawers[0].is_empty());
    }

    #[test]
    fn json_round_trip_and_ctrl_b() {
        let mut store = ToolbarStore::new(FormFactor::Phone);
        store.create_custom(CustomKey {
            id: "k1".into(),
            label: "tmux".into(),
            icon_name: None,
            sequence: vec![SequenceStep::KeyCombo(KeyCombo {
                modifiers: vec![Modifier::Ctrl],
                key: ComboKey::Letter('b'),
            })],
        });
        let loaded = ToolbarStore::from_json(&store.to_json()).unwrap();
        assert_eq!(loaded.custom_keys[0].terminal_bytes(), vec![0x02]);
    }

    #[test]
    fn tab_sends_a_tab_byte() {
        assert_eq!(press_built_in("tab").unwrap(), b"\t");
        assert!(press_built_in("dismiss").unwrap().is_empty());
    }
}
