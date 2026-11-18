//! The desktop method table: the one place that lists every MethodChannel
//! method desktop implements. Anything not listed answers `unimplemented`.
//!
//! Ported from the mobile hosts with the same argument names and result
//! shapes (iOS wins where the hosts differ, because desktop follows the iOS
//! model):
//! - class (a) forwards: `ios/Runner/CoreBackend.swift` `invokeApplication`
//!   and Android `RustCoreBackendApplication.kt` `dispatchApplication`;
//! - Rust calls the hosts wrap for OS reasons: the `osMethods` of
//!   `ios/Runner/AppDelegate.swift` `handleMethodCall`, with plain paths;
//! - the Android extension callback inbox, fed by a desktop-only method.

use crate::call::{Args, CallError, CallResult, CallState, OrFail, Reply};
use crate::paths::{self, media_path};
use crate::runtime::{self, Runtime};
use sistrum_mobile::{ExtensionManager, LyricsRequest, RequestLease};
use serde_json::{Value, json};
use std::collections::HashMap;
use std::sync::{Arc, OnceLock};

/// What a handler sees of its call.
pub(crate) struct Ctx<'a> {
    rt: &'static Runtime,
    state: &'a CallState,
    /// Lets one handler serve several methods, like the hosts' `case a, b:`.
    method: &'static str,
}

impl Ctx<'_> {
    fn owner(&self) -> Result<Arc<ExtensionManager>, CallError> {
        self.rt.backend.owner()
    }

    /// Best-effort `sistrum_cancel` support for this call.
    fn on_cancel(&self, hook: impl FnOnce(&'static Runtime) + Send + 'static) {
        let rt = self.rt;
        self.state.on_cancel(move || hook(rt));
    }
}

type Handler = fn(&Ctx<'_>, &Args) -> CallResult;

pub(crate) struct Entry {
    pub(crate) name: &'static str,
    /// Runs on the calling thread, before later calls are queued (iOS runs
    /// `cancelNativeDataJob` synchronously on the main thread).
    pub(crate) inline: bool,
    handler: Handler,
}

const fn pool(name: &'static str, handler: Handler) -> Entry {
    Entry {
        name,
        inline: false,
        handler,
    }
}

const fn inline(name: &'static str, handler: Handler) -> Entry {
    Entry {
        name,
        inline: true,
        handler,
    }
}

fn null(result: Result<(), CallError>) -> CallResult {
    result.map(|()| Reply::null())
}

fn text<E: std::fmt::Display>(result: Result<String, E>) -> CallResult {
    result.map(Reply::from).or_fail()
}

/// `ids(_:)`: a JSON array of strings.
fn ids(raw: &str) -> Result<Vec<String>, CallError> {
    serde_json::from_str::<Vec<String>>(raw)
        .map_err(|_| CallError::failure("Expected an array of extension IDs"))
}

fn limit(args: &Args) -> i64 {
    args.i64("limit").unwrap_or(20)
}

pub(crate) static METHODS: &[Entry] = &[
    // CoreBackend.swift invokeApplication: methods that do not need the owner.
    pool("runNativeDataJob", run_native_data_job),
    inline("cancelNativeDataJob", |c, a| {
        null(c.rt.backend.cancel_data_job(&a.string("request_id", "")))
    }),
    pool("customSearchWithExtension", extension_request),
    pool("getExtensionHomeFeed", extension_request),
    pool("getExtensionRelatedTracks", extension_request),
    pool("initExtensionSystem", |c, a| {
        null(c.rt.backend.initialize_owner(a))
    }),
    pool("setDownloadDirectory", |c, a| {
        null(c.rt.backend.set_download_directory(&a.string("path", "")))
    }),
    pool("acquireDownloadDirectory", |c, a| {
        c.rt.backend
            .acquire_download_directory(&a.string("path", ""))
            .map(Reply::from)
    }),
    pool("releaseDownloadDirectory", |c, a| {
        c.rt.backend
            .release_download_directory(&a.string("token", ""));
        Ok(Reply::null())
    }),
    pool("cleanupExtensions", |c, _| {
        c.rt.backend.shutdown_owner();
        Ok(Reply::null())
    }),
    pool("buildFilename", |_, a| {
        text(sistrum_mobile::build_filename(
            a.string("template", ""),
            a.string("metadata", "{}"),
        ))
    }),
    pool("sanitizeFilename", |_, a| {
        Ok(sistrum_mobile::sanitize_filename(a.string("filename", "")).into())
    }),
    pool("readFileMetadata", |_, a| {
        text(sistrum_mobile::read_file_metadata(
            a.string("file_path", ""),
            a.string("display_name", ""),
            None,
        ))
    }),
    pool("editFileMetadata", edit_file_metadata),
    pool("getLogsSince", |c, a| {
        let index = a.i64("index").unwrap_or(0);
        match c.rt.backend.manager_snapshot() {
            Some(manager) => text(manager.environment().log_buffer().since(index)),
            None => Ok(r#"{"logs":[],"next_index":0}"#.into()),
        }
    }),
    pool("clearLogs", |c, _| {
        if let Some(manager) = c.rt.backend.manager_snapshot() {
            manager.environment().log_buffer().clear().or_fail()?;
        }
        Ok(Reply::null())
    }),
    pool("setLoggingEnabled", |c, a| {
        let enabled = a.bool("enabled", false);
        null(c.rt.backend.update_settings(|settings| {
            if let Some(manager) = settings.manager {
                manager
                    .environment()
                    .log_buffer()
                    .set_enabled(enabled)
                    .or_fail()?;
            }
            *settings.logging_enabled = enabled;
            Ok(())
        }))
    }),
    pool("setAllowPrivateNetwork", |c, a| {
        let allowed = a.bool("allowed", false);
        null(c.rt.backend.update_settings(|settings| {
            if let Some(manager) = settings.manager {
                manager
                    .environment()
                    .set_allow_private_network(allowed)
                    .or_fail()?;
            }
            *settings.allow_private_network = allowed;
            Ok(())
        }))
    }),
    pool("setNetworkCompatibilityOptions", |c, a| {
        let allowed = a.bool("allow_http", false);
        let insecure_tls = a.bool("insecure_tls", false);
        null(c.rt.backend.update_settings(|settings| {
            if let Some(manager) = settings.manager {
                manager
                    .environment()
                    .set_network_compatibility_options(allowed, insecure_tls)
                    .or_fail()?;
            }
            *settings.allow_http_fallback = allowed;
            Ok(())
        }))
    }),
    pool("setDownloadFallbackExtensionIds", |c, a| {
        let raw = a.string("extension_ids", "");
        let raw = raw.trim();
        let value = if raw.is_empty() || raw == "null" {
            None
        } else {
            Some(ids(raw)?)
        };
        null(c.rt.backend.update_settings(|settings| {
            if let Some(manager) = settings.manager {
                manager.set_fallback_providers(value.clone()).or_fail()?;
            }
            *settings.fallback_providers = value;
            Ok(())
        }))
    }),
    pool("setLyricsProviders", |c, a| {
        let raw = a.string("providers_json", "[]");
        c.rt.backend
            .update_settings(|settings| match settings.manager {
                Some(manager) => manager.set_lyrics_providers_json(raw).or_fail(),
                None => ids(&raw).map(drop),
            })?;
        Ok(r#"{"success":true}"#.into())
    }),
    pool("setLyricsFetchOptions", |c, a| {
        let raw = a.string("options_json", "{}");
        c.rt.backend
            .update_settings(|settings| match settings.manager {
                Some(manager) => manager.set_lyrics_fetch_options_json(raw).or_fail(),
                None => serde_json::from_str::<Value>(&raw).map(drop).or_fail(),
            })?;
        Ok(r#"{"success":true}"#.into())
    }),
    // CoreBackend.swift invokeApplication: methods that need the owner.
    pool("invokeExtensionAction", |c, a| {
        let manager = c.owner()?;
        let id = a.string("extension_id", "");
        let action = a.string("action", "");
        if a.has("arguments_json") {
            // Transient form input bypasses persistent extension settings.
            return text(manager.call(id, action, a.string("arguments_json", "[]"), None, 120_000));
        }
        text(manager.invoke_action(id, action))
    }),
    pool("searchTracksWithMetadataProviders", |c, a| {
        text(c.owner()?.search_metadata_providers(
            a.string("query", ""),
            limit(a),
            a.bool("include_extensions", true),
            String::new(),
            30_000,
        ))
    }),
    pool("searchTracksWithMetadataProvider", |c, a| {
        text(c.owner()?.search_metadata_provider(
            a.string("extension_id", ""),
            a.string("query", ""),
            limit(a),
            30_000,
        ))
    }),
    pool("getProviderMetadata", |c, a| {
        text(c.owner()?.get_provider_metadata_json(
            a.string("provider_id", ""),
            a.string("resource_type", ""),
            a.string("resource_id", ""),
            None,
        ))
    }),
    pool("findCollectionAcrossExtensions", |c, a| {
        let request = a.as_string().unwrap_or("{}").to_owned();
        text(
            c.owner()?
                .find_collection_across_extensions_json(request, None),
        )
    }),
    pool("handleURLWithExtension", |c, a| {
        text(c.owner()?.handle_url_json(a.string("url", "")))
    }),
    pool("findURLHandler", |c, a| {
        let handler = c.owner()?.find_url_handler(a.string("url", "")).or_fail()?;
        Ok(handler.unwrap_or_default().into())
    }),
    pool("getTrackPlatformLinks", |c, a| {
        text(c.owner()?.get_track_platform_links_json(
            a.string("spotify_id", ""),
            a.string("isrc", ""),
            None,
        ))
    }),
    pool("fetchMusicBrainzTags", |c, a| {
        let manager = c.owner()?;
        let isrc = a.string("isrc", "");
        let genre = manager
            .fetch_music_brainz_genre_by_isrc(isrc.clone(), None)
            .unwrap_or_default();
        let album_artist = manager
            .fetch_music_brainz_album_artist_by_isrc(isrc, a.string("album_name", ""), None)
            .unwrap_or_default();
        Ok(json!({"genre": genre, "album_artist": album_artist})
            .to_string()
            .into())
    }),
    pool("getTrackCacheSize", |c, _| {
        Ok(json!(c.owner()?.get_track_cache_size().or_fail()?).into())
    }),
    pool("readAudioMetadata", |c, a| {
        let path = a.string("file_path", "");
        c.rt.backend.with_media_files(&[&path], |manager| {
            text(manager.read_audio_metadata(media_path(&path), String::new(), String::new(), None))
        })
    }),
    pool("clearTrackCache", |c, _| {
        null(c.owner()?.clear_track_id_cache().or_fail())
    }),
    pool("setMetadataLanguage", |c, a| {
        null(
            c.owner()?
                .set_metadata_language(a.string("tag", ""))
                .or_fail(),
        )
    }),
    pool("getLyricsLRC", lyrics),
    pool("getLyricsLRCWithSource", lyrics),
    pool("fetchAndSaveLyrics", lyrics),
    pool("getLyricsFromProvider", |c, a| {
        let request = LyricsRequest {
            spotify_id: a.string("spotify_id", ""),
            track: a.string("track_name", ""),
            artist: a.string("artist_name", ""),
            file_path: String::new(),
            duration_ms: a.i64("duration_ms").unwrap_or(0),
        };
        text(
            c.owner()?
                .get_lyrics_from_provider(a.string("provider", ""), request, None),
        )
    }),
    pool("getEnabledLyricsProviders", |c, _| {
        text(c.owner()?.get_enabled_lyrics_providers_json())
    }),
    pool("embedLyricsToFile", |c, a| {
        let path = a.string("file_path", "");
        c.rt.backend.with_media_files(&[&path], |manager| {
            text(manager.embed_lyrics_to_file(media_path(&path), a.string("lyrics", ""), None))
        })
    }),
    // AppDelegate.swift osMethods backed by Rust, with plain paths.
    pool("getBackendImplementations", |_, _| {
        Ok(json!({
            "filename": "rust",
            "file_metadata": "rust",
            "extensions": "rust",
            "downloads": "rust",
        })
        .into())
    }),
    pool("writeM4AFreeformTags", |c, a| {
        let path = a.string("file_path", "");
        c.rt.backend.with_media_files(&[&path], |manager| {
            text(manager.write_m4a_freeform_tags(
                media_path(&path),
                a.string("metadata_json", "{}"),
                None,
            ))
        })
    }),
    pool("downloadCoverToFile", download_cover_to_file),
    pool("extractCoverToFile", |c, a| {
        let audio = a.string("audio_path", "");
        let output = a.string("output_path", "");
        c.rt.backend
            .with_media_files(&[&audio, &output], |manager| {
                manager
                    .extract_cover_to_file(media_path(&audio), media_path(&output), None)
                    .or_fail()
            })?;
        Ok(r#"{"success":true}"#.into())
    }),
    pool("reEnrichFile", re_enrich_file),
    pool("checkHiResAuthenticity", |_, a| {
        text(sistrum_mobile::check_hires_authenticity(
            a.string("file_path", ""),
            a.string("options_json", ""),
            None,
        ))
    }),
    pool("releaseMemory", |c, _| {
        null(c.owner()?.release_memory(false).or_fail())
    }),
    pool("releaseMemoryUnderPressure", |c, _| {
        c.owner()?.release_memory(true).or_fail()?;
        runtime::log("info", "Backend memory pressure release completed");
        Ok(Reply::null())
    }),
    pool("setLibraryCoverCacheDir", |c, a| {
        null(
            c.rt.backend
                .set_library_cover_cache_directory(&a.string("cache_dir", "")),
        )
    }),
    pool(
        "scanLibraryFolderToNDJSONFile",
        scan_library_folder_to_ndjson_file,
    ),
    pool("scanLibraryFolderIncremental", |c, a| {
        let folder = a.string("folder_path", "");
        let existing = a.string("existing_files", "{}");
        c.on_cancel(cancel_library_scan);
        c.rt.backend
            .with_library_directories(std::slice::from_ref(&folder), |manager| {
                text(manager.scan_library_folder_incremental(
                    paths::resolve_string(&folder),
                    existing,
                    None,
                ))
            })
    }),
    pool("getLibraryScanProgress", |c, _| {
        Ok(parse_json_payload(c.rt.backend.library_scan_progress()?).into())
    }),
    pool("cancelLibraryScan", |c, _| {
        cancel_library_scan(c.rt);
        Ok(Reply::null())
    }),
    pool("pauseLibraryScan", |c, _| {
        if let Some(manager) = c.rt.backend.manager_snapshot() {
            manager.pause_library_scan().or_fail()?;
        }
        Ok(Reply::null())
    }),
    pool("resumeLibraryScan", |c, _| {
        if let Some(manager) = c.rt.backend.manager_snapshot() {
            manager.resume_library_scan().or_fail()?;
        }
        Ok(Reply::null())
    }),
    pool("parseCueSheet", |c, a| {
        c.rt.backend
            .parse_cue_sheet(&a.string("cue_path", ""), &a.string("audio_dir", ""))
            .map(Reply::from)
    }),
    // Desktop only: Dart forwards a `sistrum://` URL it received from the OS.
    // Desktop only: OS services (keep_awake.rs, discord.rs, watcher.rs).
    pool("beginKeepAwake", |c, a| {
        let reason = a.string("reason", "Downloading music");
        match c.rt.keep_awake.begin(&reason) {
            Ok(()) => Ok(true.into()),
            Err(error) => {
                runtime::log("warn", &format!("Keep awake unavailable: {error}"));
                Ok(false.into())
            }
        }
    }),
    pool("endKeepAwake", |c, _| Ok(c.rt.keep_awake.end().into())),
    pool("discordPresenceInitialize", |c, _| {
        Ok(c.rt.discord.initialize().into())
    }),
    pool("discordPresenceUpdate", |c, a| {
        c.rt.discord.update(a.value().clone());
        Ok(Reply::null())
    }),
    pool("discordPresenceClear", |c, _| {
        c.rt.discord.clear();
        Ok(Reply::null())
    }),
    pool("discordPresenceShutdown", |c, _| {
        c.rt.discord.shutdown();
        Ok(Reply::null())
    }),
    pool("watchLibraryFolders", |c, a| {
        let folders = match a.string_list("paths") {
            Some(Ok(folders)) => folders,
            Some(Err(())) => {
                return Err(CallError::invalid_arguments(
                    "paths must be a list of strings",
                ));
            }
            None => Vec::new(),
        };
        let watched = c.rt.watcher.watch(&folders).map_err(CallError::failure)?;
        Ok(json!(watched).into())
    }),
    pool("unwatchLibraryFolders", |c, _| {
        c.rt.watcher.stop();
        Ok(Reply::null())
    }),
];

fn table() -> &'static HashMap<&'static str, &'static Entry> {
    static TABLE: OnceLock<HashMap<&'static str, &'static Entry>> = OnceLock::new();
    TABLE.get_or_init(|| METHODS.iter().map(|entry| (entry.name, entry)).collect())
}

pub(crate) fn find(method: &str) -> Option<&'static Entry> {
    table().get(method).copied()
}

/// Work that must happen synchronously in `sistrum_call`.
pub(crate) fn prepare(
    rt: &Runtime,
    method: &str,
    args: &Args,
    state: &CallState,
) -> Result<(), CallError> {
    if method != "runNativeDataJob" {
        return Ok(());
    }
    // Only acquire the cancellation lease here; the job itself runs on a
    // worker. This preserves cancellation of a queued job.
    let id = args
        .opt_string("request_id")
        .filter(|id| !id.is_empty())
        .ok_or_else(|| CallError::failure("Missing native data job ID"))?;
    let lease = rt.backend.acquire_data_job(id)?;
    *state.lease.lock().expect("call lease") = Some(lease);
    Ok(())
}

/// Releases what `prepare` acquired for a call that never ran.
pub(crate) fn abandon(rt: &Runtime, method: &str, args: &Args, state: &CallState) {
    if method == "runNativeDataJob"
        && let Some(lease) = state.lease.lock().expect("call lease").take()
    {
        rt.backend
            .finish_data_job(&args.string("request_id", ""), &lease);
    }
}

pub(crate) fn run(
    rt: &'static Runtime,
    entry: &Entry,
    args: &Args,
    state: &CallState,
) -> CallResult {
    let ctx = Ctx {
        rt,
        state,
        method: entry.name,
    };
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| (entry.handler)(&ctx, args)))
        .unwrap_or_else(|_| {
            Err(CallError::failure(format!(
                "Desktop backend panicked in {}",
                entry.name
            )))
        })
}

fn run_native_data_job(c: &Ctx<'_>, a: &Args) -> CallResult {
    struct Finish<'a>(&'static Runtime, &'a str, Arc<RequestLease>);
    impl Drop for Finish<'_> {
        fn drop(&mut self) {
            self.0.backend.finish_data_job(self.1, &self.2);
        }
    }
    let lease = c
        .state
        .lease
        .lock()
        .expect("call lease")
        .take()
        .ok_or_else(|| CallError::failure("Missing native data job lease"))?;
    let id = a.string("request_id", "");
    let _finish = Finish(c.rt, &id, Arc::clone(&lease));
    let cancel_id = id.clone();
    c.on_cancel(move |rt| {
        let _ = rt.backend.cancel_data_job(&cancel_id);
    });
    c.rt.backend.run_data_job(a, lease).map(Reply::from)
}

fn extension_request(c: &Ctx<'_>, a: &Args) -> CallResult {
    let id = a.string("request_id", "");
    let (manager, lease) = c.rt.backend.acquire_request(&id)?;
    struct Release(Arc<RequestLease>);
    impl Drop for Release {
        fn drop(&mut self) {
            self.0.release();
        }
    }
    let _release = Release(Arc::clone(&lease));
    let cancel_id = id.clone();
    c.on_cancel(move |rt| {
        let _ = rt.backend.cancel_request(&cancel_id);
    });
    let extension_id = a.string("extension_id", "");
    if c.method == "customSearchWithExtension" {
        return text(manager.custom_search_json(
            extension_id,
            a.string("query", ""),
            a.string("options", ""),
            Some(lease),
        ));
    }
    if c.method == "getExtensionRelatedTracks" {
        return text(manager.get_extension_related_tracks_json(
            extension_id,
            a.string("seeds", "[]"),
            a.string("options", ""),
            Some(lease),
        ));
    }
    text(manager.get_extension_home_feed_json(extension_id, Some(lease)))
}

fn edit_file_metadata(c: &Ctx<'_>, a: &Args) -> CallResult {
    let path = a.string("file_path", "");
    let metadata = a.string("metadata_json", "{}");
    let fields: Value = serde_json::from_str(&metadata).or_fail()?;
    let cover = fields["cover_path"].as_str().unwrap_or("").to_owned();
    c.rt.backend.with_media_files(&[&path, &cover], |manager| {
        text(manager.edit_file_metadata(media_path(&path), metadata.clone(), None))
    })
}

/// `getLyricsLRC`, `getLyricsLRCWithSource` and `fetchAndSaveLyrics`.
fn lyrics(c: &Ctx<'_>, a: &Args) -> CallResult {
    let save = c.method == "fetchAndSaveLyrics";
    let source = a.string(if save { "audio_file_path" } else { "file_path" }, "");
    let output = if save {
        a.string("output_path", "")
    } else {
        String::new()
    };
    c.rt.backend
        .with_media_files(&[&source, &output], |manager| {
            let request = LyricsRequest {
                spotify_id: a.string("spotify_id", ""),
                track: a.string("track_name", ""),
                artist: a.string("artist_name", ""),
                file_path: media_path(&source),
                duration_ms: a.i64("duration_ms").unwrap_or(0),
            };
            match c.method {
                "getLyricsLRC" => text(manager.get_lyrics_lrc(request, None)),
                "getLyricsLRCWithSource" => text(manager.get_lyrics_lrc_with_source(request, None)),
                _ => {
                    manager
                        .fetch_and_save_lyrics(request, media_path(&output), None)
                        .or_fail()?;
                    Ok(r#"{"success":true}"#.into())
                }
            }
        })
}

fn download_cover_to_file(c: &Ctx<'_>, a: &Args) -> CallResult {
    let url = a.string("cover_url", "");
    let output = a.string("output_path", "");
    let max_dimension = a.i64("max_dimension").unwrap_or(0).max(0);
    let temporary = if output.is_empty() {
        Some(c.rt.backend.create_temporary_media_file("cover_", ".jpg")?)
    } else {
        None
    };
    let destination = temporary
        .as_ref()
        .map(|path| path.to_string_lossy().into_owned())
        .unwrap_or(output);
    let downloaded = c.owner().and_then(|manager| {
        manager
            .download_cover_to_file_sized(
                url,
                paths::resolve_string(&destination),
                max_dimension,
                None,
            )
            .or_fail()
    });
    if let Err(error) = downloaded {
        if let Some(temporary) = &temporary {
            let _ = std::fs::remove_file(temporary);
        }
        return Err(error);
    }
    Ok(match temporary {
        Some(temporary) => {
            json!({"success": true, "file_path": temporary.to_string_lossy()}).to_string()
        }
        None => r#"{"success":true}"#.to_owned(),
    }
    .into())
}

fn re_enrich_file(c: &Ctx<'_>, a: &Args) -> CallResult {
    let raw = a.string("request_json", "{}");
    let mut request: Value = serde_json::from_str(&raw).or_fail()?;
    if !request.is_object() {
        return Err(CallError::failure("Re-enrich request must be an object"));
    }
    let preview = request["preview_only"].as_bool() == Some(true);
    if !preview
        && let Some(path) = request["file_path"]
            .as_str()
            .filter(|path| path.starts_with('/'))
    {
        request["file_path"] = media_path(path).into();
    }
    let resolved = request.to_string();
    let path = request["file_path"].as_str().unwrap_or("").to_owned();
    if !preview && !path.is_empty() {
        return c.rt.backend.with_media_files(&[&path], |manager| {
            text(manager.reenrich_file(resolved, None))
        });
    }
    text(c.owner()?.reenrich_file(resolved, None))
}

fn cancel_library_scan(rt: &'static Runtime) {
    if let Some(manager) = rt.backend.manager_snapshot() {
        let _ = manager.cancel_library_scan();
    }
}

fn scan_library_folder_to_ndjson_file(c: &Ctx<'_>, a: &Args) -> CallResult {
    let invalid = |method: &str| CallError::failure(format!("Invalid arguments for {method}"));
    let folder = a
        .opt_string("folder_path")
        .filter(|value| !value.is_empty())
        .ok_or_else(|| invalid("scanLibraryFolderToNDJSONFile"))?;
    let output = a
        .opt_string("output_path")
        .filter(|value| !value.is_empty())
        .ok_or_else(|| invalid("scanLibraryFolderToNDJSONFile"))?;
    c.on_cancel(cancel_library_scan);
    let count =
        c.rt.backend
            .scan_library_folder_to_ndjson_file(folder, output)?;
    let progress: Value =
        serde_json::from_str(&c.rt.backend.library_scan_progress()?).unwrap_or(Value::Null);
    let error_count = progress["error_count"]
        .as_i64()
        .ok_or_else(|| invalid("scanLibraryFolderToNDJSONFile: missing scan summary"))?;
    Ok(json!({"path": output, "count": count, "error_count": error_count}).into())
}

/// `parseJsonPayload`: the decoded value, or the text when it is not JSON.
fn parse_json_payload(payload: String) -> Value {
    serde_json::from_str(&payload).unwrap_or(Value::String(payload))
}
