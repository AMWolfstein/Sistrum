package com.amwolfstein.sistrum

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

internal suspend fun Context.handleReadFileMetadata(
    call: MethodCall,
    result: MethodChannel.Result,
    coreBackend: CoreBackend,
) {
    val filePath = call.argument<String>("file_path") ?: ""
    val displayName = call.argument<String>("display_name") ?: ""
    val response = withContext(Dispatchers.IO) {
        try {
            if (filePath.startsWith("content://")) {
                readCompleteMetadataFromUri(coreBackend, Uri.parse(filePath), displayName)
                    ?.toString() ?: errorJson("Failed to read SAF metadata")
            } else {
                coreBackend.readFileMetadata(filePath, displayName)
            }
        } catch (e: Exception) {
            errorJson(e.message ?: "Failed to read metadata")
        }
    }
    result.success(response)
}

internal suspend fun MainActivity.handleCheckHiResAuthenticity(call: MethodCall, result: MethodChannel.Result) {
    val filePath = call.argument<String>("file_path") ?: ""
    val optionsJson = call.argument<String>("options_json") ?: ""
    val response = withContext(Dispatchers.IO) {
        try {
            if (filePath.startsWith("content://")) {
                checkHiResAuthenticityFromUri(coreBackend, Uri.parse(filePath), optionsJson)
                    ?.toString()
                    ?: errorJson("Failed to read SAF audio file")
            } else {
                coreBackend.checkHiResAuthenticity(filePath, optionsJson)
            }
        } catch (e: Exception) {
            errorJson(e.message ?: "Hi-Res check failed")
        }
    }
    result.success(response)
}

internal suspend fun MainActivity.handleEditFileMetadata(call: MethodCall, result: MethodChannel.Result) {
    val filePath = call.argument<String>("file_path") ?: ""
    val metadataJson = call.argument<String>("metadata_json") ?: "{}"
    val response = withContext(Dispatchers.IO) {
        try {
            if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                val tempPath = copyUriToTemp(coreBackend, uri)
                    ?: return@withContext """{"error":"Failed to copy SAF file to temp"}"""
                try {
                    val raw = coreBackend.editFileMetadata(tempPath, metadataJson)
                    val obj = JSONObject(raw)
                    val method = obj.optString("method", "")
                    if (method == "ffmpeg") {
                        // MP3/Opus: Dart needs to FFmpeg the temp file, then call writeTempToSaf
                        obj.put("temp_path", tempPath)
                        obj.put("saf_uri", filePath)
                        return@withContext obj.toString()
                        // Note: temp file NOT deleted here - Dart will clean up after FFmpeg + writeTempToSaf
                    }
                    if (!writeUriFromPath(uri, tempPath)) {
                        try { File(tempPath).delete() } catch (_: Exception) {}
                        return@withContext """{"error":"Failed to write metadata back to SAF file"}"""
                    }
                    try { File(tempPath).delete() } catch (_: Exception) {}
                    raw
                } catch (e: Exception) {
                    try { File(tempPath).delete() } catch (_: Exception) {}
                    throw e
                }
            } else {
                coreBackend.editFileMetadata(filePath, metadataJson)
            }
        } catch (e: Exception) {
            android.util.Log.e("Sistrum", "editFileMetadata failed: ${e.message}", e)
            """{"error":${org.json.JSONObject.quote(e.message ?: "unknown")}}"""
        }
    }
    result.success(response)
}

internal suspend fun MainActivity.handleWriteM4AFreeformTags(call: MethodCall, result: MethodChannel.Result) {
    val filePath = call.argument<String>("file_path") ?: ""
    val metadataJson = call.argument<String>("metadata_json") ?: "{}"
    val response = withContext(Dispatchers.IO) {
        try {
            coreBackend.writeM4aFreeformTags(filePath, metadataJson)
        } catch (e: Exception) {
            android.util.Log.e("Sistrum", "writeM4AFreeformTags failed: ${e.message}", e)
            """{"error":${org.json.JSONObject.quote(e.message ?: "unknown")}}"""
        }
    }
    result.success(response)
}

internal suspend fun MainActivity.handleExtractCoverToFile(call: MethodCall, result: MethodChannel.Result) {
    val audioPath = call.argument<String>("audio_path") ?: ""
    val outputPath = call.argument<String>("output_path") ?: ""
    val response = withContext(Dispatchers.IO) {
        try {
            if (audioPath.startsWith("content://")) {
                extractCoverFromUri(coreBackend, Uri.parse(audioPath), outputPath)
                """{"success":true}"""
            } else {
                coreBackend.extractCoverToFile(audioPath, outputPath)
                """{"success":true}"""
            }
        } catch (e: Exception) {
            """{"success":false,"error":"${e.message?.replace("\"", "'")}"}"""
        }
    }
    result.success(response)
}

internal suspend fun MainActivity.handleReadAudioMetadata(call: MethodCall, result: MethodChannel.Result) {
    val filePath = call.argument<String>("file_path") ?: ""
    val response = withContext(Dispatchers.IO) {
        try {
            if (filePath.startsWith("content://")) {
                val uri = Uri.parse(filePath)
                val metadata = readAudioMetadataFromUri(
                    coreBackend,
                    uri,
                    coverCacheKey = buildLibraryCoverCacheKey(filePath, 0),
                )
                    ?: return@withContext """{"error":"Failed to read SAF audio metadata"}"""
                metadata.put("filePath", filePath)
                metadata.toString()
            } else {
                coreBackend.readAudioMetadata(filePath, "", "")
            }
        } catch (e: Exception) {
            """{"error":${org.json.JSONObject.quote(e.message ?: "unknown")}}"""
        }
    }
    result.success(response)
}

internal suspend fun MainActivity.handleParseCueSheet(call: MethodCall, result: MethodChannel.Result) {
    val cuePath = call.argument<String>("cue_path") ?: ""
    val audioDir = call.argument<String>("audio_dir") ?: ""
    val response = withContext(Dispatchers.IO) {
        try {
            if (cuePath.startsWith("content://")) {
                val uri = Uri.parse(cuePath)
                val tempCuePath = copyUriToTemp(coreBackend, uri, ".cue")
                    ?: return@withContext """{"error":"Failed to copy CUE file to temp"}"""
                try {
                    val audioFileName = extractCueAudioFileName(tempCuePath)

                    val parentDir = safParentDir(uri)
                    val audioDoc = if (parentDir != null) {
                        val cueName = try {
                            DocumentFile.fromSingleUri(this@handleParseCueSheet, uri)?.name ?: ""
                        } catch (_: Exception) { "" }
                        findSafCueAudioSibling(this@handleParseCueSheet, parentDir, cueName, audioFileName)
                    } else {
                        null
                    }

                    if (audioDoc == null || !audioDoc.isFile) {
                        return@withContext JSONObject()
                            .put("error", "Audio file not found for CUE sheet")
                            .toString()
                    }

                    // Preview parses the CUE text only; the selected audio
                    // stays in SAF instead of copying a whole album to cache.
                    val resultJson = coreBackend.parseCueSheetWithResolvedAudio(
                        tempCuePath,
                        audioDoc.uri.toString(),
                    )
                    JSONObject(resultJson)
                        .put("cue_path", cuePath)
                        .toString()
                } finally {
                    try { File(tempCuePath).delete() } catch (_: Exception) {}
                }
            } else {
                coreBackend.parseCueSheet(cuePath, audioDir)
            }
        } catch (e: Exception) {
            """{"error":${org.json.JSONObject.quote(e.message ?: "unknown")}}"""
        }
    }
    result.success(response)
}
