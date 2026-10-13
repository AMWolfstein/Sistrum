package com.amwolfstein.sistrum.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Decodes the persisted artwork file, falling back to the audio file's embedded
 * picture. Shared by every artwork surface (in-app UI and the Now Playing
 * MediaSession metadata behind the notification and lock screen) so they
 * cannot resolve artwork differently.
 */
internal fun decodeArtworkBitmaps(
    absolutePath: String?,
    audioPath: String?,
    targetPx: Int,
    config: Bitmap.Config,
): Bitmap? {
    absolutePath?.takeIf { File(it).isFile }?.let { path ->
        decodeBitmapFile(path, targetPx, config)?.let { return it }
    }
    audioPath?.takeIf { File(it).isFile }?.let { audio ->
        EmbeddedTagReader.embeddedArtworkBytes(audio)?.let { bytes ->
            decodeBitmapBytes(bytes, targetPx, config)?.let { return it }
        }
    }
    return null
}

private fun decodeBitmapFile(path: String, targetPx: Int, config: Bitmap.Config): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    BitmapFactory.decodeFile(path, bitmapOptionsFor(bounds.outWidth, bounds.outHeight, targetPx, config))
}.getOrNull()

private fun decodeBitmapBytes(bytes: ByteArray, targetPx: Int, config: Bitmap.Config): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptionsFor(bounds.outWidth, bounds.outHeight, targetPx, config))
}.getOrNull()

private fun bitmapOptionsFor(width: Int, height: Int, targetPx: Int, config: Bitmap.Config) =
    BitmapFactory.Options().apply {
        var sampleSize = 1
        while (width / (sampleSize * 2) >= targetPx && height / (sampleSize * 2) >= targetPx) {
            sampleSize *= 2
        }
        inSampleSize = sampleSize
        inPreferredConfig = config
    }
