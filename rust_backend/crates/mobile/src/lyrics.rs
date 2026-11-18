use crate::cancellation::RequestLease;
use crate::manager::{ExtensionManager, ExtensionManagerError};
use std::sync::Arc;

fn check(lease: &Option<Arc<RequestLease>>) -> Result<(), String> {
    lease.as_ref().map_or(Ok(()), |lease| {
        lease
            .inner
            .check_active()
            .map_err(|error| error.to_string())
    })
}

/// Lyrics stored in the file itself: embedded tags or a sidecar. No remote
/// lyrics service is consulted.
#[uniffi::export]
impl ExtensionManager {
    pub fn embed_lyrics_to_file(
        &self,
        path: String,
        lyrics: String,
        lease: Option<Arc<RequestLease>>,
    ) -> Result<String, ExtensionManagerError> {
        self.inner
            .embed_lyrics_to_file(&path, &lyrics, &|| check(&lease))
            .map_err(ExtensionManagerError::Operation)
    }

    pub fn get_lyrics_lrc_with_source(
        &self,
        file_path: String,
        lease: Option<Arc<RequestLease>>,
    ) -> Result<String, ExtensionManagerError> {
        self.inner
            .get_lyrics_lrc_with_source(&file_path, &|| check(&lease))
            .map_err(ExtensionManagerError::Operation)
    }
}
