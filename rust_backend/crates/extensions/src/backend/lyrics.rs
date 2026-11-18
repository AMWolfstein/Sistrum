use super::Backend;
use crate::files::ExtensionFiles;
use cap_std::fs::OpenOptions;
use serde::Serialize;
use sistrum_core::lyrics::{file, lrc};
use std::collections::BTreeMap;

type Check<'a> = dyn Fn() -> Result<(), String> + 'a;

#[derive(Default, Serialize)]
struct LrcResult {
    lyrics: String,
    source: String,
    sync_type: String,
    instrumental: bool,
}

impl Backend {
    pub fn embed_lyrics_to_file(
        &self,
        path: &str,
        lyrics: &str,
        check: &Check<'_>,
    ) -> Result<String, String> {
        let fields = BTreeMap::from([
            ("LYRICS".into(), lyrics.into()),
            ("UNSYNCEDLYRICS".into(), lyrics.into()),
        ]);
        self.embed_flac_fields_response(
            path,
            &fields,
            "",
            ("Failed to embed lyrics", "Lyrics embedded successfully"),
            check,
        )
    }

    /// Lyrics already stored in the file's tags or sidecar. Never consults a
    /// remote lyrics service.
    pub fn get_lyrics_lrc_with_source(
        &self,
        file_path: &str,
        check: &Check<'_>,
    ) -> Result<String, String> {
        let _operation = self.enter()?;
        let check = || self.check().and_then(|()| check());
        let result = if file_path.is_empty() {
            LrcResult::default()
        } else {
            let files = self.environment().native_files()?;
            match existing(&files, file_path, &check)? {
                Some(lyrics) => LrcResult {
                    source: {
                        let source = lrc::extract_source(&lyrics);
                        if source.is_empty() {
                            "Embedded".into()
                        } else {
                            source
                        }
                    },
                    sync_type: "EMBEDDED".into(),
                    instrumental: lrc::is_instrumental_marker(&lyrics),
                    lyrics,
                },
                None => LrcResult::default(),
            }
        };
        serde_json::to_string(&result).map_err(|error| error.to_string())
    }
}

fn existing(
    files: &ExtensionFiles,
    path: &str,
    check: &dyn Fn() -> Result<(), String>,
) -> Result<Option<String>, String> {
    let result = file::extract(
        path,
        &|path| {
            files
                .resolve_legacy(path)?
                .open(OpenOptions::new().read(true))
                .map_err(|error| error.to_string())
        },
        check,
    );
    check()?;
    Ok(result.ok().filter(|text| lrc::has_usable_content(text)))
}
