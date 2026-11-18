//! Platform-owned storage and lifecycle context for the offline library backend.

mod index;

use crate::files::{self, FileRegistry};
use sistrum_core::app_version::AppVersion;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Condvar, Mutex};

#[derive(Debug, thiserror::Error)]
pub enum EnvironmentError {
    #[error("extension environment closed")]
    Closed,
    #[error("{0}")]
    Storage(#[from] std::io::Error),
    #[error("{0}")]
    Index(String),
}

/// Owns the backend's data directory, the diagnostic log and the scoped file
/// grants that native callers take for each operation.
pub struct ExtensionEnvironment {
    logs: Arc<crate::logging::LogBuffer>,
    data_directory: PathBuf,
    data_directory_alias: PathBuf,
    app_version: AppVersion,
    files: Arc<FileRegistry>,
    isrc: Arc<sistrum_core::isrc::IndexCache>,
    closed: Arc<AtomicBool>,
    pending: Mutex<usize>,
    idle: Condvar,
}

impl ExtensionEnvironment {
    pub fn new(data_directory: &Path, app_version: &str) -> Result<Self, EnvironmentError> {
        if data_directory.as_os_str().is_empty() {
            return Err(std::io::Error::other("data directory is empty").into());
        }
        let mut builder = fs::DirBuilder::new();
        builder.recursive(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::DirBuilderExt;
            builder.mode(0o700);
        }
        builder.create(data_directory)?;
        let data_directory_alias = files::clean(&std::path::absolute(data_directory)?);
        let data_directory = fs::canonicalize(data_directory)?;
        Ok(Self {
            logs: Arc::new(crate::logging::LogBuffer::default()),
            data_directory,
            data_directory_alias,
            app_version: app_version.into(),
            files: Arc::new(FileRegistry::default()),
            isrc: Arc::new(sistrum_core::isrc::IndexCache::default()),
            closed: Arc::new(AtomicBool::new(false)),
            pending: Mutex::new(0),
            idle: Condvar::new(),
        })
    }

    fn enter(&self) -> Result<Operation<'_>, EnvironmentError> {
        let mut pending = self.pending.lock().expect("extension environment lock");
        if self.closed.load(Ordering::Acquire) {
            return Err(EnvironmentError::Closed);
        }
        *pending += 1;
        Ok(Operation(self))
    }

    pub fn get_app_version(&self) -> Result<String, EnvironmentError> {
        let _operation = self.enter()?;
        Ok(self.app_version.get())
    }

    pub fn set_allowed_download_directories(
        &self,
        directories: &[PathBuf],
    ) -> Result<(), EnvironmentError> {
        let _operation = self.enter()?;
        self.files.set_allowed_directories(directories)?;
        Ok(())
    }

    /// Trusted native callers retain this grant until the operation completes.
    pub fn grant_temporary_download_directory(
        &self,
        path: &Path,
    ) -> Result<files::TemporaryGrant, String> {
        let _operation = self.enter().map_err(|error| error.to_string())?;
        self.files
            .grant_temporary_directory(path)
            .map_err(|error| error.to_string())
    }

    pub fn is_closed(&self) -> bool {
        self.closed.load(Ordering::Acquire)
    }

    pub fn log_buffer(&self) -> Arc<crate::logging::LogBuffer> {
        Arc::clone(&self.logs)
    }

    /// Trusted native operations share download grants. Relative native paths
    /// resolve inside the application's data root.
    pub(crate) fn native_files(&self) -> Result<Arc<files::ExtensionFiles>, String> {
        if self.is_closed() {
            return Err("extension environment closed".into());
        }
        self.files
            .extension_with_alias(&self.data_directory, &self.data_directory_alias)
            .map_err(|error| error.to_string())
    }

    /// Stop new operations, wait for active ones, then drop cached indexes.
    pub fn shutdown(&self) {
        self.closed.store(true, Ordering::Release);
        self.app_version.close();
        self.logs.shutdown();
        let mut pending = self.pending.lock().expect("extension environment lock");
        while *pending > 0 {
            pending = self.idle.wait(pending).expect("extension environment idle");
        }
        drop(pending);
        self.isrc.clear();
    }
}

struct Operation<'a>(&'a ExtensionEnvironment);

impl Drop for Operation<'_> {
    fn drop(&mut self) {
        *self.0.pending.lock().expect("extension environment lock") -= 1;
        self.0.idle.notify_all();
    }
}
