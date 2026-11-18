//! Offline library support for the native backend: local tag and artwork work,
//! scoped file access, and the diagnostic log buffer. Nothing here opens network
//! connections or runs extension code.
//!
//! Comments that mention "Go" describe the retired Go backend, whose observable
//! behavior (wire formats, parsing quirks, file layouts) remains a compatibility
//! contract.

pub mod backend;
pub mod environment;
pub mod files;
pub mod logging;
mod redact;
// Pure path checks, so host tests cover them on every platform.
#[cfg(any(not(unix), test))]
mod windows_path;
