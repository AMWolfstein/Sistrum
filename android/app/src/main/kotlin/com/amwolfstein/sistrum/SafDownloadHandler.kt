package com.amwolfstein.sistrum

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Shared SAF download wrapper for foreground activity calls and service-owned
 * native workers.
 */
object SafDownloadHandler {
    private val safDirLock = Any()
    private const val MAX_SAF_DISPLAY_NAME_UTF8_BYTES = 180

    // Serializes the exists-check/create/write/publish sequence per target
    // file so concurrent downloads that sanitize to the same display name
    // cannot interleave writes into one document. Different names keep
    // downloading in parallel; the second same-name caller blocks, then hits
    // the exists check and reports already_exists.
    private val safNameLocks = KeyedLockPool<String>()

    data class UniqueWriteResult(
        val uri: String,
        val fileName: String,
        val publishTimingsMs: Map<String, Long> = emptyMap(),
    )
    data class ExistingAwareWriteResult(
        val uri: String,
        val fileName: String,
        val alreadyExists: Boolean,
        val publishTimingsMs: Map<String, Long> = emptyMap(),
    )

    private class PublishTrace {
        private val started = System.nanoTime()
        private var previous = started
        private val stages = linkedMapOf<String, Long>()

        fun mark(stage: String) {
            val now = System.nanoTime()
            stages[stage] = (now - previous) / 1_000_000
            previous = now
        }

        fun finish(): Map<String, Long> = stages.toMap() +
            ("total" to (System.nanoTime() - started) / 1_000_000)
    }

    private fun <T> withSafNameLock(
        treeUriStr: String,
        relativeDir: String,
        fileName: String,
        block: () -> T
    ): T {
        val key = "$treeUriStr|$relativeDir|${fileName.lowercase(Locale.ROOT)}"
        return safNameLocks.withLock(key, block)
    }

    /**
     * Flushes and fsyncs a SAF output stream so the copied bytes are durable
     * before the staged document is renamed to its final name; without this a
     * power loss right after the rename can leave a truncated "complete" file.
     */
    fun syncOutputStream(output: OutputStream) {
        try {
            output.flush()
            (output as? FileOutputStream)?.fd?.sync()
        } catch (_: Exception) {
        }
    }

    /**
     * Swaps [document] into place under [finalName] without a window where the
     * previous file is gone but the new one is not yet in place. Any existing
     * file is renamed aside first and only deleted once the staged file holds
     * the final name; a failed swap restores it. Returns the published
     * document, or null when the swap failed (the caller owns [document]).
     */

    fun copyContentUriToTemp(context: Context, uriStr: String): String? {
        var temp: File? = null
        return try {
            val uri = Uri.parse(uriStr)
            val extension = DocumentFile.fromSingleUri(context, uri)
                ?.name
                ?.substringAfterLast('.', "")
                ?.takeIf { it.isNotBlank() }
                ?.let { ".$it" }
                ?: ".tmp"
            val createdTemp = createCoreBackend(context).createTemporaryMediaFile(context, "native_saf_", extension)
            temp = createdTemp
            context.contentResolver.openInputStream(uri)?.use { input ->
                createdTemp.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                createdTemp.delete()
                return null
            }
            createdTemp.absolutePath
        } catch (e: Exception) {
            try { temp?.delete() } catch (_: Exception) {}
            android.util.Log.w("Sistrum", "Failed to copy SAF URI to temp: ${e.message}")
            null
        }
    }

    fun writeFileToSaf(
        context: Context,
        treeUriStr: String,
        relativeDir: String,
        fileName: String,
        mimeType: String,
        srcPath: String
    ): String? {
        val finalName = sanitizeFilename(fileName)
        return withSafNameLock(treeUriStr, sanitizeRelativeDir(relativeDir), finalName) {
            try {
                val targetDir = ensureDocumentDir(context, Uri.parse(treeUriStr), relativeDir)
                    ?: return@withSafNameLock null
                writeFileToSafLocked(context, targetDir, finalName, mimeType, srcPath)
            } catch (e: Exception) {
                android.util.Log.w("Sistrum", "Failed to write file to SAF: ${e.message}")
                null
            }
        }
    }

    fun writeFileToSafUnique(
        context: Context,
        treeUriStr: String,
        relativeDir: String,
        fileName: String,
        mimeType: String,
        srcPath: String,
        preservedSuffix: String = "",
    ): UniqueWriteResult? {
        val safeRelativeDir = sanitizeRelativeDir(relativeDir)
        val preferredName = sanitizeFilenamePreservingSuffix(fileName, preservedSuffix)
        val trace = PublishTrace()
        return withSafNameLock(treeUriStr, safeRelativeDir, preferredName) {
            trace.mark("lock_wait")
            val treeUri = Uri.parse(treeUriStr)
            val targetDir = ensureDocumentDir(context, treeUri, safeRelativeDir) ?: return@withSafNameLock null
            trace.mark("directory")
            val availableName = findAvailableFileName(
                context,
                targetDir,
                preferredName,
                preservedSuffix,
            )
            trace.mark("existing_check")
            val uri = writeFileToSafLocked(
                context,
                targetDir,
                availableName,
                mimeType,
                srcPath,
                trace,
            ) ?: return@withSafNameLock null
            UniqueWriteResult(uri = uri, fileName = availableName, publishTimingsMs = trace.finish())
        }
    }

    fun writeFileToSafCollisionAware(
        context: Context,
        treeUriStr: String,
        relativeDir: String,
        cleanFileName: String,
        variantFileName: String,
        mimeType: String,
        srcPath: String,
        preservedSuffix: String = "",
    ): UniqueWriteResult? {
        val safeRelativeDir = sanitizeRelativeDir(relativeDir)
        val cleanName = sanitizeFilename(cleanFileName)
        val preferredVariant = sanitizeFilenamePreservingSuffix(
            variantFileName,
            preservedSuffix,
        )
        val trace = PublishTrace()
        return withSafNameLock(treeUriStr, safeRelativeDir, cleanName) {
            trace.mark("lock_wait")
            val treeUri = Uri.parse(treeUriStr)
            val targetDir = ensureDocumentDir(context, treeUri, safeRelativeDir)
                ?: return@withSafNameLock null
            trace.mark("directory")
            val selectedName = if (findSafChild(context, targetDir, cleanName) == null) {
                cleanName
            } else {
                findAvailableFileName(context, targetDir, preferredVariant, preservedSuffix)
            }
            trace.mark("existing_check")
            val uri = writeFileToSafLocked(
                context,
                targetDir,
                selectedName,
                mimeType,
                srcPath,
                trace,
            ) ?: return@withSafNameLock null
            UniqueWriteResult(uri = uri, fileName = selectedName, publishTimingsMs = trace.finish())
        }
    }

    fun writeFileToSafIfAbsent(
        context: Context,
        treeUriStr: String,
        relativeDir: String,
        fileName: String,
        mimeType: String,
        srcPath: String,
    ): ExistingAwareWriteResult? {
        val safeRelativeDir = sanitizeRelativeDir(relativeDir)
        val finalName = sanitizeFilename(fileName)
        val trace = PublishTrace()
        return withSafNameLock(treeUriStr, safeRelativeDir, finalName) {
            trace.mark("lock_wait")
            val treeUri = Uri.parse(treeUriStr)
            val targetDir = ensureDocumentDir(context, treeUri, safeRelativeDir)
                ?: return@withSafNameLock null
            trace.mark("directory")
            val existing = findSafChild(context, targetDir, finalName)
            trace.mark("existing_check")
            if (existing != null && existing.isFile && existing.length() > 0L) {
                return@withSafNameLock ExistingAwareWriteResult(
                    uri = existing.uri.toString(),
                    fileName = existing.name ?: finalName,
                    alreadyExists = true,
                    publishTimingsMs = trace.finish(),
                )
            }
            val uri = writeFileToSafLocked(
                context,
                targetDir,
                finalName,
                mimeType,
                srcPath,
                trace,
            ) ?: return@withSafNameLock null
            ExistingAwareWriteResult(
                uri = uri,
                fileName = finalName,
                alreadyExists = false,
                publishTimingsMs = trace.finish(),
            )
        }
    }

    private fun sanitizeFilenamePreservingSuffix(fileName: String, suffix: String): String {
        val sanitized = sanitizeFilename(fileName)
        val trimmedSuffix = suffix.trim()
        if (trimmedSuffix.isEmpty() || sanitized.contains(trimmedSuffix)) return sanitized

        val dotIndex = fileName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < fileName.length - 1
        val extension = if (hasExtension) fileName.substring(dotIndex) else ""
        val rawStem = if (hasExtension) fileName.substring(0, dotIndex) else fileName
        val rawPrefix = rawStem.replace(trimmedSuffix, "").trim(' ', '_', '-')
        val safeSuffix = sanitizeFilename(trimmedSuffix)
        val reserved = " - $safeSuffix$extension"
        val prefixBytes = (MAX_SAF_DISPLAY_NAME_UTF8_BYTES - reserved.toByteArray(Charsets.UTF_8).size)
            .coerceAtLeast(1)
        val safePrefix = truncateUtf8Bytes(sanitizeFilename(rawPrefix), prefixBytes)
            .trim()
            .trim('.', ' ', '_', '-')
            .ifBlank { "track" }
        return "$safePrefix$reserved"
    }

    private fun findAvailableFileName(
        context: Context,
        parent: DocumentFile,
        preferredName: String,
        preservedSuffix: String,
    ): String {
        if (findSafChild(context, parent, preferredName) == null) return preferredName
        for (counter in 2..9999) {
            val candidate = appendFilenameCounter(
                preferredName,
                counter.toLong(),
                preservedSuffix,
            )
            if (findSafChild(context, parent, candidate) == null) return candidate
        }
        return appendFilenameCounter(
            preferredName,
            System.currentTimeMillis(),
            preservedSuffix,
        )
    }

    private fun appendFilenameCounter(
        fileName: String,
        counter: Long,
        preservedSuffix: String,
    ): String {
        val dotIndex = fileName.lastIndexOf('.')
        val hasExtension = dotIndex > 0 && dotIndex < fileName.length - 1
        val extension = if (hasExtension) fileName.substring(dotIndex) else ""
        val originalStem = if (hasExtension) fileName.substring(0, dotIndex) else fileName
        val safePreservedSuffix = preservedSuffix.trim()
        val hasPreservedSuffix = safePreservedSuffix.isNotEmpty() && originalStem.contains(safePreservedSuffix)
        val stem = if (hasPreservedSuffix) {
            originalStem.replace(safePreservedSuffix, "").trim(' ', '_', '-')
        } else {
            originalStem
        }
        val suffix = if (hasPreservedSuffix) {
            " - $safePreservedSuffix ($counter)"
        } else {
            " ($counter)"
        }
        val reservedBytes = extension.toByteArray(Charsets.UTF_8).size +
            suffix.toByteArray(Charsets.UTF_8).size
        val maxStemBytes = (MAX_SAF_DISPLAY_NAME_UTF8_BYTES - reservedBytes).coerceAtLeast(1)
        val safeStem = truncateUtf8Bytes(stem, maxStemBytes).trim().trim('.', ' ').ifBlank { "track" }
        return "$safeStem$suffix$extension"
    }

    private fun writeFileToSafLocked(
        context: Context,
        targetDir: DocumentFile,
        finalName: String,
        mimeType: String,
        srcPath: String,
        trace: PublishTrace = PublishTrace(),
    ): String? {
        var stagedDocument: DocumentFile? = null
        return try {
            val ext = normalizeExt(finalName.substringAfterLast('.', ""))
            val stagedName = buildStagedSafFileName(finalName)
            deleteStaleStagedFiles(context, targetDir, finalName, ext)
            trace.mark("cleanup")
            val document = createOrReuseDocumentFile(
                context, targetDir, mimeType, stagedName, privateStaging = true,
            )
                ?: return null
            trace.mark("create")
            stagedDocument = document
            val outputStream = context.contentResolver.openOutputStream(document.uri, "wt")
            if (outputStream == null) {
                document.delete()
                stagedDocument = null
                return null
            }
            trace.mark("open")
            outputStream.use { output ->
                File(srcPath).inputStream().use { input ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
                trace.mark("copy")
                syncOutputStream(output)
                trace.mark("sync")
            }
            trace.mark("close")

            val published = replaceFinalDocument(context, targetDir, document, finalName)
            trace.mark("replace")
            if (published == null) {
                document.delete()
                return null
            }
            stagedDocument = null
            published.uri.toString()
        } catch (e: Exception) {
            stagedDocument?.delete()
            android.util.Log.w("Sistrum", "Failed to write file to SAF: ${e.message}")
            null
        }
    }

    fun deleteContentUri(context: Context, uriStr: String): Boolean {
        return try {
            DocumentFile.fromSingleUri(context, Uri.parse(uriStr))?.delete() == true
        } catch (_: Exception) {
            false
        }
    }

    // Same result for every input; pinned by ExtensionMimeTablesTest.
    internal fun normalizeExt(ext: String?): String {
        val trimmed = ext?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase(Locale.ROOT)
        return if (lower.startsWith(".")) lower else ".$lower"
    }

    /**
     * Swaps [document] into place under [finalName] without a window where the
     * previous file is gone but the new one is not yet in place. Any existing
     * file is renamed aside first and only deleted once the staged file holds
     * the final name; a failed swap restores it. Returns the published
     * document, or null when the swap failed (the caller owns [document]).
     */
    private fun replaceFinalDocument(
        context: Context,
        targetDir: DocumentFile,
        document: DocumentFile,
        finalName: String
    ): DocumentFile? {
        val existingFinal = findSafChild(context, targetDir, finalName)
        var aside: DocumentFile? = null
        if (existingFinal != null && existingFinal.uri != document.uri) {
            val asideName = buildReplacedSafFileName(finalName)
            try {
                findSafChild(context, targetDir, asideName)?.delete()
            } catch (_: Exception) {
            }
            if (!existingFinal.renameTo(asideName)) {
                return null
            }
            // TreeDocumentFile.renameTo updates this object's URI, including
            // providers whose document IDs change with the display name.
            aside = existingFinal
        }
        if (!document.renameTo(finalName)) {
            aside?.renameTo(finalName)
            return null
        }
        aside?.delete()
        return document
    }

    internal fun buildReplacedSafFileName(fileName: String): String {
        val suffix = ".replaced"
        val name = fitPrivatePublicationName(
            fileName, MAX_SAF_DISPLAY_NAME_UTF8_BYTES - suffix.length - 1,
        )
        return ".$name$suffix"
    }

    private fun forceFilenameExt(name: String, outputExt: String): String {
        val normalizedExt = normalizeExt(outputExt)
        if (normalizedExt.isBlank()) return sanitizeFilename(name)

        val safeName = sanitizeFilename(name)
        val lower = safeName.lowercase(Locale.ROOT)
        val knownExts = listOf(".flac", ".m4a", ".mp4", ".mp3", ".opus", ".lrc")
        for (knownExt in knownExts) {
            if (lower.endsWith(knownExt)) {
                return safeName.dropLast(knownExt.length) + normalizedExt
            }
        }
        return safeName + normalizedExt
    }

    internal fun buildStagedSafFileName(fileName: String): String {
        // Filesystem providers append an audio extension when it does not
        // match the MIME. Keep the extension and hide the staging name so
        // MediaStore and other players cannot index unfinished audio.
        val name = fitPrivatePublicationName(
            fileName, MAX_SAF_DISPLAY_NAME_UTF8_BYTES - ".partial".length - 1,
        )
        return ".${buildLegacyStagedSafFileName(name, "")}"
    }

    private fun fitPrivatePublicationName(fileName: String, maxBytes: Int): String {
        val safeName = sanitizeFilename(fileName)
        if (safeName.toByteArray(Charsets.UTF_8).size <= maxBytes) return safeName
        // Keep distinct final names distinct when reserving staging suffixes.
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(safeName.toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val suffix = "-$hash"
        val fitted = truncateSafDisplayName(safeName, maxBytes - suffix.length)
        val dot = fitted.lastIndexOf('.')
        return if (dot > 0 && fitted.length - dot <= 10) {
            fitted.substring(0, dot) + suffix + fitted.substring(dot)
        } else {
            fitted + suffix
        }
    }

    private fun buildLegacyStagedSafFileName(fileName: String, outputExt: String): String {
        val safeName = sanitizeFilename(fileName)
        val ext = normalizeExt(outputExt)
        if (ext.isNotBlank() && safeName.lowercase(Locale.ROOT).endsWith(ext)) {
            return safeName.dropLast(ext.length).trimEnd('.', ' ') + ".partial$ext"
        }
        val dot = safeName.lastIndexOf('.')
        if (dot > 0 && dot < safeName.lastIndex) {
            return safeName.substring(0, dot).trimEnd('.', ' ') +
                ".partial" +
                safeName.substring(dot)
        }
        return "$safeName.partial"
    }

    private fun deleteStaleStagedFiles(context: Context, parent: DocumentFile, fileName: String, outputExt: String) {
        val stagedNames = linkedSetOf(
            buildStagedSafFileName(fileName),
            "${sanitizeFilename(fileName)}.partial",
            buildLegacyStagedSafFileName(fileName, outputExt),
            buildReplacedSafFileName(fileName),
            "${sanitizeFilename(fileName)}.replaced",
        )
        val staleDocuments = try {
            findSafChildren(context, parent, stagedNames)
        } catch (_: Exception) {
            return
        }
        for (document in staleDocuments.values) {
            try {
                document.delete()
            } catch (_: Exception) {
            }
        }
    }

    internal fun mimeTypeForExt(ext: String?): String {
        return when (normalizeExt(ext)) {
            ".m4a", ".mp4" -> "audio/mp4"
            ".mp3" -> "audio/mpeg"
            ".opus", ".ogg" -> "audio/ogg"
            ".flac" -> "audio/flac"
            ".wav" -> "audio/wav"
            ".aiff", ".aif", ".aifc" -> "audio/aiff"
            ".lrc" -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    internal fun sanitizeFilename(name: String): String {
        var sanitized = name
            .replace("/", " ")
            .replace(Regex("[\\\\:*?\"<>|]"), " ")
            .filter { ch ->
                val code = ch.code
                !((code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') ||
                    code == 0x7F ||
                    (Character.isISOControl(ch) && ch != '\t' && ch != '\n' && ch != '\r'))
            }
            .trim()
            .trim('.', ' ')

        sanitized = sanitized
            .replace(Regex("\\s+"), " ")
            .replace(Regex("_+"), "_")
            .trim('_', ' ')

        sanitized = truncateSafDisplayName(sanitized, MAX_SAF_DISPLAY_NAME_UTF8_BYTES)
        sanitized = sanitized.trim().trim('.', ' ').trim('_', ' ')
        return if (sanitized.isBlank()) "Unknown" else sanitized
    }

    private fun truncateSafDisplayName(name: String, maxBytes: Int): String {
        if (maxBytes <= 0 || name.toByteArray(Charsets.UTF_8).size <= maxBytes) return name

        val dotIndex = name.lastIndexOf('.')
        val ext = if (
            dotIndex > 0 &&
            dotIndex < name.length - 1 &&
            name.length - dotIndex <= 10
        ) {
            name.substring(dotIndex)
        } else {
            ""
        }
        val stem = if (ext.isNotEmpty()) name.substring(0, dotIndex) else name
        val maxStemBytes = (maxBytes - ext.toByteArray(Charsets.UTF_8).size).coerceAtLeast(1)
        return truncateUtf8Bytes(stem, maxStemBytes).trim().trim('.', ' ').trim('_', ' ') + ext
    }

    private fun truncateUtf8Bytes(value: String, maxBytes: Int): String {
        if (maxBytes <= 0 || value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value

        val builder = StringBuilder()
        var usedBytes = 0
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val char = String(Character.toChars(codePoint))
            val charBytes = char.toByteArray(Charsets.UTF_8).size
            if (usedBytes + charBytes > maxBytes) break
            builder.append(char)
            usedBytes += charBytes
            index += Character.charCount(codePoint)
        }
        return builder.toString()
    }

    internal fun sanitizeRelativeDir(relativeDir: String): String {
        if (relativeDir.isBlank()) return ""
        return relativeDir
            .split("/")
            .map { sanitizeFilename(it) }
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/")
    }

    internal fun ensureDocumentDir(
        context: Context,
        treeUri: Uri,
        relativeDir: String
    ): DocumentFile? {
        val safeRelativeDir = sanitizeRelativeDir(relativeDir)
        if (safeRelativeDir.isBlank()) {
            return DocumentFile.fromTreeUri(context, treeUri)
        }

        synchronized(safDirLock) {
            var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val parts = safeRelativeDir.split("/").filter { it.isNotBlank() }
            for (part in parts) {
                val existing = findSafChild(context, current, part)
                current = if (existing != null && existing.isDirectory) {
                    existing
                } else {
                    val created = current.createDirectory(part) ?: return null
                    val createdName = created.name ?: part
                    if (createdName != part) {
                        created.delete()
                        findSafChild(context, current, part) ?: return null
                    } else {
                        created
                    }
                }
            }
            return current
        }
    }

    internal fun findDocumentDir(
        context: Context,
        treeUri: Uri,
        relativeDir: String
    ): DocumentFile? {
        var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val safeRelativeDir = sanitizeRelativeDir(relativeDir)
        if (safeRelativeDir.isBlank()) return current

        val parts = safeRelativeDir.split("/").filter { it.isNotBlank() }
        for (part in parts) {
            val existing = findSafChild(context, current, part)
            if (existing == null || !existing.isDirectory) return null
            current = existing
        }
        return current
    }

    internal fun createOrReuseDocumentFile(
        context: Context,
        parent: DocumentFile,
        mimeType: String,
        fileName: String,
        privateStaging: Boolean = false,
    ): DocumentFile? {
        val safeFileName = if (privateStaging) {
            ".${truncateSafDisplayName(sanitizeFilename(fileName), MAX_SAF_DISPLAY_NAME_UTF8_BYTES - 1)}"
        } else {
            sanitizeFilename(fileName)
        }
        if (safeFileName.isBlank()) return null

        synchronized(safDirLock) {
            val existing = findSafChild(context, parent, safeFileName)
            if (existing != null && existing.isFile) {
                return existing
            }

            val created = parent.createFile(mimeType, safeFileName) ?: return null
            val createdName = created.name ?: safeFileName
            if (createdName == safeFileName) {
                return created
            }

            val winner = findSafChild(context, parent, safeFileName)
            if (winner != null && winner.isFile) {
                if (winner.uri != created.uri) {
                    try {
                        created.delete()
                    } catch (_: Exception) {
                    }
                }
                return winner
            }

            return created
        }
    }

}
