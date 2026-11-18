//! Mobile `ExtensionManager` object: the offline library backend's owner. Local
//! tag, cover, scan and lyric-file operations run through it.

use sistrum_extensions::backend::Backend as CoreBackend;
use std::path::Path;
use std::sync::Arc;

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum ExtensionManagerError {
    #[error("{0}")]
    Operation(String),
}

impl From<sistrum_extensions::environment::EnvironmentError> for ExtensionManagerError {
    fn from(error: sistrum_extensions::environment::EnvironmentError) -> Self {
        Self::Operation(error.to_string())
    }
}

/// Owns the local data directory and its library caches. Use a background native
/// thread for operations that read large folders or files.
#[derive(uniffi::Object)]
pub struct ExtensionManager {
    pub(crate) inner: CoreBackend,
}

#[uniffi::export]
impl ExtensionManager {
    #[uniffi::constructor]
    pub fn new(data_directory: String, app_version: String) -> Result<Self, ExtensionManagerError> {
        Ok(Self {
            inner: CoreBackend::new(Path::new(&data_directory), &app_version)?,
        })
    }

    pub fn environment(&self) -> Arc<crate::extensions::ExtensionEnvironment> {
        Arc::new(crate::extensions::ExtensionEnvironment {
            inner: self.inner.environment(),
        })
    }

    pub fn release_memory(&self, under_pressure: bool) -> Result<(), ExtensionManagerError> {
        self.inner
            .release_memory(under_pressure)
            .map_err(ExtensionManagerError::Operation)
    }

    pub fn shutdown(&self) {
        self.inner.shutdown();
    }
}
