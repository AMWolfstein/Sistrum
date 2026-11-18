//! Root ownership for the offline library: local tags, artwork and scans.

mod cover;
mod library;
mod lyrics;
mod tags;

pub use cover::{LIBRARY_COVER_REGENERATE_MARKER, library_cover_is_current, library_cover_stem};

use crate::environment::{EnvironmentError, ExtensionEnvironment};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Condvar, Mutex};

/// Retained by the native owner. Shutdown waits for in-flight local work before
/// releasing the environment.
pub struct Backend {
    environment: Arc<ExtensionEnvironment>,
    library_cover_directory: Mutex<String>,
    library_scan: library::ScanState,
    closed: AtomicBool,
    operations: Mutex<usize>,
    idle: Condvar,
    shutdown: Mutex<Option<Result<(), String>>>,
}

impl Backend {
    pub fn new(data: &std::path::Path, app_version: &str) -> Result<Self, EnvironmentError> {
        Ok(Self {
            environment: Arc::new(ExtensionEnvironment::new(data, app_version)?),
            library_cover_directory: Mutex::default(),
            library_scan: library::ScanState::default(),
            closed: AtomicBool::new(false),
            operations: Mutex::new(0),
            idle: Condvar::new(),
            shutdown: Mutex::new(None),
        })
    }

    pub fn environment(&self) -> Arc<ExtensionEnvironment> {
        Arc::clone(&self.environment)
    }

    /// Drop disposable resources while active requests retain their owner and IO.
    pub fn release_memory(&self, under_pressure: bool) -> Result<(), String> {
        let _operation = self.enter()?;
        if under_pressure {
            self.environment
                .clear_isrc_cache()
                .map_err(|error| error.to_string())?;
        }
        Ok(())
    }

    pub fn shutdown_checked(&self) -> Result<(), String> {
        let mut state = self.shutdown.lock().expect("backend shutdown lock");
        if let Some(result) = state.as_ref() {
            return result.clone();
        }
        self.closed.store(true, Ordering::Release);
        let mut operations = self.operations.lock().expect("backend operations lock");
        while *operations != 0 {
            operations = self.idle.wait(operations).expect("backend operations wait");
        }
        drop(operations);
        self.environment.shutdown();
        *state = Some(Ok(()));
        Ok(())
    }

    /// Preserve the native manager's void shutdown API.
    pub fn shutdown(&self) {
        let _ = self.shutdown_checked();
    }

    fn check(&self) -> Result<(), String> {
        if self.closed.load(Ordering::Acquire) || self.environment.is_closed() {
            Err("backend is closed".into())
        } else {
            Ok(())
        }
    }

    fn enter(&self) -> Result<Operation<'_>, String> {
        let mut operations = self.operations.lock().expect("backend operations lock");
        self.check()?;
        *operations += 1;
        Ok(Operation(self))
    }
}

struct Operation<'a>(&'a Backend);

impl Drop for Operation<'_> {
    fn drop(&mut self) {
        *self.0.operations.lock().expect("backend operations lock") -= 1;
        self.0.idle.notify_all();
    }
}

impl Drop for Backend {
    fn drop(&mut self) {
        self.shutdown();
    }
}
