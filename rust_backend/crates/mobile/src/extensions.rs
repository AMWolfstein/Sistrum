use sistrum_extensions::environment::{EnvironmentError, ExtensionEnvironment as CoreEnvironment};
use std::path::Path;
use std::sync::{Arc, Mutex};

/// Keeps operation directories accessible until native finalization finishes.
/// Explicit close and object destruction both release only these grants.
#[derive(uniffi::Object)]
pub struct DownloadDirectoryScope {
    grants: Mutex<Option<Vec<sistrum_extensions::files::TemporaryGrant>>>,
}

#[uniffi::export]
impl DownloadDirectoryScope {
    pub fn release(&self) {
        self.grants
            .lock()
            .expect("download directory scope lock")
            .take();
    }
}

#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum BackendError {
    #[error("backend closed")]
    Closed,
    #[error("{0}")]
    Storage(String),
    #[error("{0}")]
    Index(String),
}

impl From<EnvironmentError> for BackendError {
    fn from(error: EnvironmentError) -> Self {
        match error {
            EnvironmentError::Closed => Self::Closed,
            EnvironmentError::Storage(error) => Self::Storage(error.to_string()),
            EnvironmentError::Index(error) => Self::Index(error),
        }
    }
}

#[derive(uniffi::Object)]
pub struct ExtensionEnvironment {
    pub(crate) inner: Arc<CoreEnvironment>,
}

#[uniffi::export]
impl ExtensionEnvironment {
    pub fn log_buffer(&self) -> Arc<crate::logging::LogBuffer> {
        Arc::new(crate::logging::LogBuffer {
            inner: self.inner.log_buffer(),
        })
    }

    pub fn set_allowed_download_directories(
        &self,
        directories: Vec<String>,
    ) -> Result<(), BackendError> {
        self.inner
            .set_allowed_download_directories(
                &directories
                    .into_iter()
                    .map(std::path::PathBuf::from)
                    .collect::<Vec<_>>(),
            )
            .map_err(Into::into)
    }

    pub fn grant_download_directories(
        &self,
        directories: Vec<String>,
    ) -> Result<Arc<DownloadDirectoryScope>, BackendError> {
        let grants = directories
            .iter()
            .map(|path| {
                self.inner
                    .grant_temporary_download_directory(Path::new(path))
            })
            .collect::<Result<Vec<_>, _>>()
            .map_err(BackendError::Storage)?;
        Ok(Arc::new(DownloadDirectoryScope {
            grants: Mutex::new(Some(grants)),
        }))
    }

    pub fn shutdown(&self) {
        self.inner.shutdown();
    }
}
