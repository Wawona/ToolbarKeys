//! Keyboard toolbar model shared by Wawona hosts.
//!
//! The catalog and layout behavior follow Rootshell's keyboard toolbar.
//! This crate does not draw. UIKit, Jetpack, AppKit, and WatchKit render
//! the slots. Bytes from [`press_built_in`] and [`CustomKey::terminal_bytes`]
//! go to the Wawona PTY or the Ghostty external I/O fd.

mod bytes;
mod model;

pub use model::*;

uniffi::setup_scaffolding!();

use std::sync::{Arc, Mutex};

#[derive(uniffi::Object)]
pub struct ToolbarSession {
    inner: Mutex<ToolbarStore>,
}

#[derive(Debug, uniffi::Error)]
pub enum ToolbarError {
    Invalid { reason: String },
}

impl std::fmt::Display for ToolbarError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Invalid { reason } => write!(f, "{reason}"),
        }
    }
}

#[uniffi::export]
impl ToolbarSession {
    #[uniffi::constructor]
    pub fn new(form: String) -> Result<Arc<Self>, ToolbarError> {
        let form = FormFactor::parse(&form).ok_or_else(|| ToolbarError::Invalid {
            reason: format!("unknown form '{form}' (phone or pad)"),
        })?;
        Ok(Arc::new(Self {
            inner: Mutex::new(ToolbarStore::new(form)),
        }))
    }

    pub fn json(&self) -> String {
        self.inner.lock().expect("toolbar").to_json()
    }

    pub fn replace_json(&self, document: String) -> Result<(), ToolbarError> {
        let store = ToolbarStore::from_json(&document).map_err(|err| ToolbarError::Invalid {
            reason: err,
        })?;
        *self.inner.lock().expect("toolbar") = store;
        Ok(())
    }

    pub fn hide(&self, key_id: String) -> Result<(), ToolbarError> {
        self.inner
            .lock()
            .expect("toolbar")
            .hide(&key_id)
            .map_err(|err| ToolbarError::Invalid { reason: err })
    }

    pub fn unhide(&self, key_id: String) -> Result<(), ToolbarError> {
        self.inner
            .lock()
            .expect("toolbar")
            .unhide(&key_id)
            .map_err(|err| ToolbarError::Invalid { reason: err })
    }

    pub fn set_drawer_row_count(&self, count: u32) {
        self.inner
            .lock()
            .expect("toolbar")
            .set_drawer_row_count(count as usize);
    }

    pub fn reset(&self) {
        self.inner.lock().expect("toolbar").reset();
    }

    pub fn effective_json(&self, available_width: f64, button_width: f64) -> String {
        self.inner
            .lock()
            .expect("toolbar")
            .effective_json(available_width, button_width)
    }

    pub fn press_built_in(&self, key_id: String) -> Vec<u8> {
        let _ = self;
        press_built_in(&key_id).unwrap_or_default()
    }
}

#[uniffi::export]
pub fn toolbar_catalog_json() -> String {
    catalog_json()
}
