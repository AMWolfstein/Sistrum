/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-FileCopyrightText: 2026 AMWolfstein <https://github.com/AMWolfstein>
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Ported from Rhythm's ArtistSeparator (https://github.com/cromaguy/Rhythm,
 * app/src/main/java/chromahub/rhythm/app/util/ArtistSeparator.kt). Changes: the default
 * delimiters are every token Rhythm's UI offers plus the Arabic comma (Rhythm defaults to
 * ";" and "/"), the JSON format uses kotlinx.serialization instead of
 * Gson, a "|" token is always stored as JSON (the compact form mis-parsed it), and the
 * display helpers (getPrimaryArtist, formatArtists, escapeArtistName) are not ported
 * because nothing in this app uses them.
 */
package com.amwolfstein.sistrum.sync

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Splits one artist (or composer) tag into several names on configurable delimiters.
 *
 * A delimiter may be a symbol (";", "/", "،") or a word ("feat.", "ft."); word delimiters
 * only match on word boundaries, so "ft" never splits "Daft Punk". A backslash before a
 * delimiter keeps it literal ("AC\/DC" stays "AC/DC").
 *
 * - "Artist1; Artist2" -> ["Artist1", "Artist2"]
 * - "Artist1 (feat. Artist2)" -> ["Artist1", "Artist2"] (with "feat." enabled)
 */
internal object ArtistSeparator {
    /**
     * Every delimiter Rhythm's UI offers, plus the Arabic comma "،" (U+060C) that Arabic tags
     * use between artists. Deliberately broad: it also splits names that contain one of
     * these ("Simon & Garfunkel", "Tyler, The Creator"); a backslash escape keeps those.
     */
    val DEFAULT_TOKENS: List<String> = listOf(
        ";", "/", ",", "\u060C", "+", "&",
        "feat.", "ft.", "featuring", "x", "with", "vs.",
        "、", "／", "・", "•",
    )
    /**
     * Genre defaults: ";" and "،" only. "/" and "&" routinely appear inside one genre name
     * ("Hip-Hop/Rap", "R&B/Soul"), so the artist set would break correctly tagged genres.
     */
    val DEFAULT_GENRE_TOKENS: List<String> = listOf(";", "\u060C")
    private const val ESCAPE_CHAR = '\\'
    private const val PLACEHOLDER_PREFIX = "\u0000\u0001"
    private const val PLACEHOLDER_SUFFIX = '\u0002'

    private val json = Json
    private val tokenListSerializer = ListSerializer(String.serializer())

    /** [DEFAULT_TOKENS] in stored form (a JSON array, since it includes words). */
    val DEFAULT_DELIMITERS: String = json.encodeToString(tokenListSerializer, DEFAULT_TOKENS)
    private val regexCache = ConcurrentHashMap<String, Regex>()

    /**
     * Parses a stored delimiters string into tokens. Three formats are accepted: a JSON
     * array, a newline- or pipe-separated list, or the compact form where every character
     * is its own delimiter (";/،").
     */
    fun parseDelimiters(delimiters: String?): List<String> {
        if (delimiters.isNullOrBlank()) {
            return DEFAULT_TOKENS
        }

        val trimmed = delimiters.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val fromJson = runCatching { json.decodeFromString(tokenListSerializer, trimmed) }.getOrNull()
            if (!fromJson.isNullOrEmpty()) {
                return fromJson.filter { it.isNotEmpty() }.distinct()
            }
        }

        if (trimmed.contains('\n') || trimmed.contains('|')) {
            val tokens = trimmed.split(Regex("[\n|]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (tokens.isNotEmpty()) {
                return tokens
            }
        }

        // Compact form: each character is a delimiter
        return trimmed.map { it.toString() }.distinct()
    }

    /**
     * Serializes tokens for storage: the compact form when every token is a single
     * character that the compact parser reads back unchanged, otherwise a JSON array.
     */
    fun serializeDelimiters(delimiters: List<String>): String {
        val clean = delimiters.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return DEFAULT_DELIMITERS

        // "|" is excluded because parseDelimiters treats any "|" as a list separator, so a
        // compact ";/|" used to come back as the single token ";/".
        val isSimpleCharsOnly = clean.all {
            it.length == 1 && !it[0].isWhitespace() && it != "[" && it != "]" && it != "|"
        }
        return if (isSimpleCharsOnly) {
            clean.joinToString("")
        } else {
            json.encodeToString(tokenListSerializer, clean)
        }
    }

    /**
     * Cleans up unbalanced parentheses, square brackets, and curly braces that often
     * wrap collaboration phrases (e.g. "(ft. Artist B)" or "[feat. Artist B]"),
     * which otherwise leave a trailing "(" on the preceding artist and a trailing ")" on the featured artist.
     * Preserves balanced brackets within an artist's name (e.g. "Band (UK)", "(Hed) P.E.").
     */
    fun cleanArtistSegment(segment: String): String {
        var s = segment.trim()

        // 1. Remove unbalanced trailing opening brackets (e.g., "Artist (", "Artist [", "Artist {")
        while (s.isNotEmpty() && (s.endsWith('(') || s.endsWith('[') || s.endsWith('{'))) {
            s = s.dropLast(1).trim()
        }

        // 2. Remove unbalanced leading closing brackets (e.g., ") Artist", "] Artist", "} Artist")
        while (s.isNotEmpty() && (s.startsWith(')') || s.startsWith(']') || s.startsWith('}'))) {
            s = s.drop(1).trim()
        }

        // 3. Remove unbalanced trailing closing brackets (e.g., "Artist)" or "Artist]")
        // Only remove if there are more closing brackets than opening brackets in this segment
        while (s.isNotEmpty() && s.endsWith(')') && s.count { it == ')' } > s.count { it == '(' }) {
            s = s.dropLast(1).trim()
        }
        while (s.isNotEmpty() && s.endsWith(']') && s.count { it == ']' } > s.count { it == '[' }) {
            s = s.dropLast(1).trim()
        }
        while (s.isNotEmpty() && s.endsWith('}') && s.count { it == '}' } > s.count { it == '{' }) {
            s = s.dropLast(1).trim()
        }

        // 4. Remove unbalanced leading opening brackets (e.g., "(Artist" when the whole artist was enclosed)
        while (s.isNotEmpty() && s.startsWith('(') && s.count { it == '(' } > s.count { it == ')' }) {
            s = s.drop(1).trim()
        }
        while (s.isNotEmpty() && s.startsWith('[') && s.count { it == '[' } > s.count { it == ']' }) {
            s = s.drop(1).trim()
        }
        while (s.isNotEmpty() && s.startsWith('{') && s.count { it == '{' } > s.count { it == '}' }) {
            s = s.drop(1).trim()
        }

        return s
    }

    /** One compiled pattern per distinct token set, so a scan doesn't rebuild it per track. */
    private fun getOrCreateRegex(tokens: List<String>): Regex {
        val key = tokens.sorted().joinToString("|||")
        return regexCache.getOrPut(key) {
            val sorted = tokens.sortedByDescending { it.length }
            val patternString = sorted.joinToString("|") { token ->
                val escaped = Regex.escape(token)
                val startsWithWordChar = token.firstOrNull()?.let { it.isLetterOrDigit() || it == '_' } == true
                val endsWithWordChar = token.lastOrNull()?.let { it.isLetterOrDigit() || it == '_' } == true
                val prefix = if (startsWithWordChar) "(?<![\\p{L}\\p{N}_])" else ""
                val suffix = if (endsWithWordChar) "(?![\\p{L}\\p{N}_])" else ""
                "$prefix$escaped$suffix"
            }
            patternString.toRegex(RegexOption.IGNORE_CASE)
        }
    }

    /** Splits [artistName] on already-parsed delimiter [tokens]. */
    fun splitArtistNames(
        artistName: String?,
        tokens: List<String>,
        enabled: Boolean = true,
    ): List<String> {
        if (artistName.isNullOrBlank()) {
            return emptyList()
        }

        if (!enabled || tokens.isEmpty()) {
            return listOf(artistName.trim()).filter { it.isNotBlank() }
        }

        val cleanTokens = tokens.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanTokens.isEmpty()) {
            return listOf(artistName.trim()).filter { it.isNotBlank() }
        }

        // Protect escaped "\<delim>" occurrences so they are never treated as split points
        val escapedTokens = mutableListOf<String>()
        val escaped = StringBuilder(artistName.length)
        val sortedTokens = cleanTokens.sortedByDescending { it.length }
        var i = 0

        while (i < artistName.length) {
            val c = artistName[i]
            if (c == ESCAPE_CHAR && i + 1 < artistName.length) {
                val remaining = artistName.substring(i + 1)
                val matchedToken = sortedTokens.firstOrNull { remaining.startsWith(it, ignoreCase = true) }
                if (matchedToken != null) {
                    val originalSlice = artistName.substring(i + 1, i + 1 + matchedToken.length)
                    escapedTokens.add(originalSlice)
                    escaped.append(PLACEHOLDER_PREFIX)
                    escaped.append((escapedTokens.size - 1).toChar())
                    escaped.append(PLACEHOLDER_SUFFIX)
                    i += 1 + matchedToken.length
                    continue
                }
            }
            escaped.append(c)
            i++
        }

        val regex = getOrCreateRegex(sortedTokens)
        return regex.split(escaped.toString())
            .map { segment ->
                var restored = segment
                for ((index, originalToken) in escapedTokens.withIndex()) {
                    val tokenPlaceholder = PLACEHOLDER_PREFIX + index.toChar() + PLACEHOLDER_SUFFIX
                    restored = restored.replace(tokenPlaceholder, originalToken)
                }
                cleanArtistSegment(restored)
            }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Splits [artistName] on delimiters stored in any of [parseDelimiters]' formats. */
    fun splitArtistNames(
        artistName: String?,
        delimiters: String = DEFAULT_DELIMITERS,
        enabled: Boolean = true,
    ): List<String> {
        if (artistName.isNullOrBlank()) {
            return emptyList()
        }

        if (!enabled || delimiters.isEmpty()) {
            return listOf(artistName.trim()).filter { it.isNotBlank() }
        }

        val tokens = parseDelimiters(delimiters)
        return splitArtistNames(artistName, tokens, enabled)
    }
}
