package com.amwolfstein.sistrum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafVolumePolicyTest {
    private val externalStorage = "com.android.externalstorage.documents"

    @Test fun removableVolumeIsCheckedOnlyForExternalStorageDocuments() {
        assertEquals("1234-ABCD", safVolumeUuidToCheck(externalStorage, "1234-ABCD:Music/a.mp3"))
    }

    @Test fun internalAndRootIdsAreAlwaysAnswered() {
        for (id in listOf("primary:Music/a.mp3", "primary:", "emulated:0/Music/a.mp3", "home:", "home:Music/a.mp3", "raw:/storage/emulated/0/x", ":x", "1234-ABCD")) {
            assertNull(id, safVolumeUuidToCheck(externalStorage, id))
        }
    }

    @Test fun otherProvidersKeepTheirOwnIdsUnchecked() {
        for (id in listOf("audio:123", "msf:123", "document:123", "raw:/storage/emulated/0/x", "1234-ABCD:Music/a.mp3", "home:")) {
            assertNull(id, safVolumeUuidToCheck("com.android.providers.media.documents", id))
            assertNull(id, safVolumeUuidToCheck("com.android.providers.downloads.documents", id))
            assertNull(id, safVolumeUuidToCheck("com.example.provider", id))
        }
    }

    @Test fun missingAuthorityOrIdIsAnsweredByTheProvider() {
        assertNull(safVolumeUuidToCheck(null, "1234-ABCD:Music/a.mp3"))
        assertNull(safVolumeUuidToCheck(externalStorage, null))
    }
}
