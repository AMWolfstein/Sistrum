package com.amwolfstein.sistrum

/**
 * The removable volume whose mount state must be checked before a SAF document
 * can be answered, or null when the provider's own answer stands. Only the
 * ExternalStorage provider names a volume by its uuid before the colon. Other
 * providers (media `audio:`/`document:`, `msf:`, Downloads `raw:`, `home:`) and
 * the internal `primary`/`emulated`/`home` roots always keep the provider answer.
 */
internal fun safVolumeUuidToCheck(authority: String?, documentId: String?): String? {
    if (authority != "com.android.externalstorage.documents") return null
    val volume = documentId?.substringBefore(':', missingDelimiterValue = "") ?: return null
    if (volume.isEmpty() || volume in alwaysAnsweredVolumes) return null
    return volume
}

private val alwaysAnsweredVolumes = setOf("primary", "emulated", "home", "raw")
