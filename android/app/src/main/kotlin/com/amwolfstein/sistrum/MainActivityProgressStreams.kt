package com.amwolfstein.sistrum

import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.json.JSONTokener

private val LIBRARY_SCAN_PROGRESS_STREAM_CHANNEL =
    "com.amwolfstein.sistrum/library_scan_progress_stream"
// A progress bar can't show sub-second granularity; 400ms halves the
// disk-read wakeups during a scan vs the previous 200ms.
private val LIBRARY_SCAN_PROGRESS_STREAM_POLLING_INTERVAL_MS = 400L

internal fun MainActivity.registerProgressStreams(messenger: BinaryMessenger) {
    EventChannel(
        messenger,
        LIBRARY_SCAN_PROGRESS_STREAM_CHANNEL,
    ).setStreamHandler(
        object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                if (events != null) {
                    startLibraryScanProgressStream(events)
                }
            }

            override fun onCancel(arguments: Any?) {
                stopLibraryScanProgressStream()
            }
        },
    )
}

private fun MainActivity.startLibraryScanProgressStream(sink: EventChannel.EventSink) {
    stopLibraryScanProgressStream()
    libraryScanProgressEventSink = sink
    lastLibraryScanProgressPayload = null
    libraryScanProgressStreamJob = scope.launch {
        try {
            val initialPayload = withContext(Dispatchers.IO) {
                readLibraryScanProgressJsonForStream()
            }
            if (!isActive || libraryScanProgressEventSink !== sink) return@launch
            lastLibraryScanProgressPayload = initialPayload
            sink.success(parseJsonPayload(initialPayload))
        } catch (e: Exception) {
            android.util.Log.w(
                "Sistrum",
                "Library scan progress initial poll failed: ${e.message}",
            )
        }
        while (isActive && libraryScanProgressEventSink === sink) {
            try {
                val payload = withContext(Dispatchers.IO) {
                    readLibraryScanProgressJsonForStream()
                }
                if (!isActive || libraryScanProgressEventSink !== sink) break
                if (payload != lastLibraryScanProgressPayload) {
                    lastLibraryScanProgressPayload = payload
                    sink.success(parseJsonPayload(payload))
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "Sistrum",
                    "Library scan progress stream poll failed: ${e.message}",
                )
            }
            delay(LIBRARY_SCAN_PROGRESS_STREAM_POLLING_INTERVAL_MS)
        }
    }
}

internal fun MainActivity.stopLibraryScanProgressStream() {
    libraryScanProgressStreamJob?.cancel()
    libraryScanProgressStreamJob = null
    libraryScanProgressEventSink = null
    lastLibraryScanProgressPayload = null
}
