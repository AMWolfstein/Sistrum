//! Pushed events that replace the mobile EventChannels: library scan progress
//! (kind 11, `MainActivityProgressStreams.kt:122-156`). Kind 13 log records
//! are pushed only while subscribed. Kind 12 inbound calls are always sent.

use crate::kind;
use crate::runtime::{self, Runtime};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::Duration;

const SCAN_INTERVAL: Duration = Duration::from_millis(400);

#[derive(Default)]
struct StreamState {
    /// Incremented on every start and stop; a loop exits once it differs.
    generation: u64,
    active: bool,
}

#[derive(Default)]
struct Stream {
    state: Mutex<StreamState>,
}

impl Stream {
    /// Returns the new generation, or `None` when already running.
    fn start(&self) -> Option<u64> {
        let mut state = self.state.lock().expect("stream state");
        if state.active {
            return None;
        }
        state.active = true;
        state.generation += 1;
        Some(state.generation)
    }

    fn stop(&self) {
        let mut state = self.state.lock().expect("stream state");
        if state.active {
            state.active = false;
            state.generation += 1;
        }
    }

    fn is_current(&self, generation: u64) -> bool {
        self.state.lock().expect("stream state").generation == generation
    }
}

#[derive(Default)]
pub(crate) struct Events {
    scan: Stream,
    logs: AtomicBool,
}

impl Events {
    pub(crate) fn logs_enabled(&self) -> bool {
        self.logs.load(Ordering::SeqCst)
    }
}

/// `sistrum_subscribe`. Idempotent; unknown kinds are ignored.
pub(crate) fn subscribe(runtime: &'static Runtime, event: i32) {
    match event {
        kind::LIBRARY_SCAN_PROGRESS => {
            if let Some(generation) = runtime.events.scan.start() {
                spawn("sistrum-scan-progress", move || {
                    scan_loop(runtime, generation)
                });
            }
        }
        kind::LOG => runtime.events.logs.store(true, Ordering::SeqCst),
        _ => {}
    }
}

/// `sistrum_unsubscribe`. Idempotent; unknown kinds are ignored.
pub(crate) fn unsubscribe(runtime: &Runtime, event: i32) {
    match event {
        kind::LIBRARY_SCAN_PROGRESS => runtime.events.scan.stop(),
        kind::LOG => runtime.events.logs.store(false, Ordering::SeqCst),
        _ => {}
    }
}

fn spawn(name: &str, body: impl FnOnce() + Send + 'static) {
    if let Err(error) = std::thread::Builder::new().name(name.into()).spawn(body) {
        runtime::log("error", &format!("Event thread could not start: {error}"));
    }
}


fn scan_loop(runtime: &Runtime, generation: u64) {
    let stream = &runtime.events.scan;
    let mut last_payload: Option<String> = None;
    while stream.is_current(generation) {
        match runtime.backend.library_scan_progress() {
            Ok(payload) => {
                if last_payload.as_deref() != Some(payload.as_str())
                    && stream.is_current(generation)
                {
                    runtime::emit(0, kind::LIBRARY_SCAN_PROGRESS, payload.clone().into_bytes());
                    last_payload = Some(payload);
                }
            }
            Err(error) => runtime::log(
                "warn",
                &format!(
                    "Library scan progress stream poll failed: {}",
                    error.message
                ),
            ),
        }
        std::thread::sleep(SCAN_INTERVAL);
    }
}
