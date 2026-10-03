//! Terminal bytes for a custom-key sequence. Matches Rootshell
//! `SequenceStep.KeyCombo.terminalData`.

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct KeyCombo {
    pub modifiers: Vec<Modifier>,
    pub key: ComboKey,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Modifier {
    Ctrl,
    Alt,
    Shift,
    Cmd,
}

impl Modifier {
    pub fn parse(raw: &str) -> Option<Self> {
        match raw {
            "ctrl" => Some(Self::Ctrl),
            "alt" => Some(Self::Alt),
            "shift" => Some(Self::Shift),
            "cmd" => Some(Self::Cmd),
            _ => None,
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Ctrl => "ctrl",
            Self::Alt => "alt",
            Self::Shift => "shift",
            Self::Cmd => "cmd",
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum ComboKey {
    Letter(char),
    Digit(char),
    Symbol(char),
    Special(SpecialKey),
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SpecialKey {
    ReturnKey,
    Tab,
    Escape,
    Space,
    Backspace,
    Delete,
    ArrowUp,
    ArrowDown,
    ArrowLeft,
    ArrowRight,
    Home,
    End,
    PageUp,
    PageDown,
}

impl SpecialKey {
    pub fn parse(raw: &str) -> Option<Self> {
        Some(match raw {
            "returnKey" => Self::ReturnKey,
            "tab" => Self::Tab,
            "escape" => Self::Escape,
            "space" => Self::Space,
            "backspace" => Self::Backspace,
            "delete" => Self::Delete,
            "arrowUp" => Self::ArrowUp,
            "arrowDown" => Self::ArrowDown,
            "arrowLeft" => Self::ArrowLeft,
            "arrowRight" => Self::ArrowRight,
            "home" => Self::Home,
            "end" => Self::End,
            "pageUp" => Self::PageUp,
            "pageDown" => Self::PageDown,
            _ => return None,
        })
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::ReturnKey => "returnKey",
            Self::Tab => "tab",
            Self::Escape => "escape",
            Self::Space => "space",
            Self::Backspace => "backspace",
            Self::Delete => "delete",
            Self::ArrowUp => "arrowUp",
            Self::ArrowDown => "arrowDown",
            Self::ArrowLeft => "arrowLeft",
            Self::ArrowRight => "arrowRight",
            Self::Home => "home",
            Self::End => "end",
            Self::PageUp => "pageUp",
            Self::PageDown => "pageDown",
        }
    }

    fn base(self) -> &'static [u8] {
        match self {
            Self::ReturnKey => b"\r",
            Self::Tab => b"\t",
            Self::Escape => b"\x1b",
            Self::Space => b" ",
            Self::Backspace => b"\x7f",
            Self::Delete => b"\x1b[3~",
            Self::ArrowUp => b"\x1b[A",
            Self::ArrowDown => b"\x1b[B",
            Self::ArrowLeft => b"\x1b[D",
            Self::ArrowRight => b"\x1b[C",
            Self::Home => b"\x1b[H",
            Self::End => b"\x1b[F",
            Self::PageUp => b"\x1b[5~",
            Self::PageDown => b"\x1b[6~",
        }
    }

    fn csi_letter(self) -> Option<char> {
        match self {
            Self::ArrowUp => Some('A'),
            Self::ArrowDown => Some('B'),
            Self::ArrowRight => Some('C'),
            Self::ArrowLeft => Some('D'),
            Self::Home => Some('H'),
            Self::End => Some('F'),
            _ => None,
        }
    }

    fn csi_tilde(self) -> Option<char> {
        match self {
            Self::Delete => Some('3'),
            Self::PageUp => Some('5'),
            Self::PageDown => Some('6'),
            _ => None,
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum SequenceStep {
    Text(String),
    KeyCombo(KeyCombo),
}

impl KeyCombo {
    pub fn terminal_bytes(&self) -> Vec<u8> {
        let ctrl = self.modifiers.contains(&Modifier::Ctrl);
        let alt = self.modifiers.contains(&Modifier::Alt);
        let shift = self.modifiers.contains(&Modifier::Shift);
        match &self.key {
            ComboKey::Letter(ch) => letter_bytes(*ch, ctrl, alt, shift),
            ComboKey::Digit(ch) | ComboKey::Symbol(ch) => char_bytes(*ch, ctrl, alt, shift),
            ComboKey::Special(key) => special_bytes(*key, ctrl, alt, shift),
        }
    }
}

impl SequenceStep {
    pub fn terminal_bytes(&self) -> Vec<u8> {
        match self {
            Self::Text(text) => text.as_bytes().to_vec(),
            Self::KeyCombo(combo) => combo.terminal_bytes(),
        }
    }
}

fn letter_bytes(ch: char, ctrl: bool, alt: bool, shift: bool) -> Vec<u8> {
    let lower = ch.to_ascii_lowercase();
    let mut byte = lower as u8;
    if ctrl {
        byte = byte.wrapping_sub(0x60);
    } else if shift {
        byte = ch.to_ascii_uppercase() as u8;
    }
    if alt {
        vec![0x1b, byte]
    } else {
        vec![byte]
    }
}

fn char_bytes(ch: char, ctrl: bool, alt: bool, _shift: bool) -> Vec<u8> {
    if !ch.is_ascii() {
        return ch.to_string().into_bytes();
    }
    let mut result = ch as u8;
    if ctrl && (0x40..=0x7f).contains(&result) {
        result &= 0x1f;
    }
    if alt {
        vec![0x1b, result]
    } else {
        vec![result]
    }
}

fn special_bytes(key: SpecialKey, ctrl: bool, alt: bool, shift: bool) -> Vec<u8> {
    if !ctrl && !alt && !shift {
        return key.base().to_vec();
    }
    let mut mod_param = 1;
    if shift {
        mod_param += 1;
    }
    if alt {
        mod_param += 2;
    }
    if ctrl {
        mod_param += 4;
    }
    if let Some(code) = key.csi_letter() {
        return format!("\u{1b}[1;{mod_param}{code}").into_bytes();
    }
    if let Some(code) = key.csi_tilde() {
        return format!("\u{1b}[{code};{mod_param}~").into_bytes();
    }
    match key {
        SpecialKey::ReturnKey if alt => vec![0x1b, 0x0d],
        SpecialKey::ReturnKey => vec![0x0d],
        SpecialKey::Tab if shift => b"\x1b[Z".to_vec(),
        SpecialKey::Tab if alt => vec![0x1b, 0x09],
        SpecialKey::Tab => vec![0x09],
        SpecialKey::Escape if alt => vec![0x1b, 0x1b],
        SpecialKey::Escape => vec![0x1b],
        SpecialKey::Space if ctrl && alt => vec![0x1b, 0x00],
        SpecialKey::Space if ctrl => vec![0x00],
        SpecialKey::Space if alt => vec![0x1b, 0x20],
        SpecialKey::Space => vec![0x20],
        SpecialKey::Backspace if alt => vec![0x1b, 0x7f],
        SpecialKey::Backspace if ctrl => vec![0x08],
        SpecialKey::Backspace => vec![0x7f],
        other => other.base().to_vec(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ctrl_a_and_shift_up() {
        let ctrl_a = KeyCombo {
            modifiers: vec![Modifier::Ctrl],
            key: ComboKey::Letter('a'),
        };
        assert_eq!(ctrl_a.terminal_bytes(), vec![0x01]);
        let shift_up = KeyCombo {
            modifiers: vec![Modifier::Shift],
            key: ComboKey::Special(SpecialKey::ArrowUp),
        };
        assert_eq!(shift_up.terminal_bytes(), b"\x1b[1;2A");
    }
}
