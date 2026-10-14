@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.amwolfstein.sistrum.player.decoders.kotlin

import androidx.media3.common.util.ParsableByteArray
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Runs the untouched T002 contracts with Media3's strict legacy parser checks enabled.
 * Its automatic test detection otherwise dereferences the plain host's null Build.FINGERPRINT.
 * This is the same setup as Mp3LameProvenanceTest, scoped to this suite rather than production.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(KotlinDecodingExtractorTest::class, ExtractorRandomAccessSourceTest::class,
    KotlinDecodingExtractorLifecycleTest::class)
class KotlinDecoderAdapterTestSuite {
    companion object {
        @JvmStatic @BeforeClass fun enableParserBoundsChecks() {
            ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(true)
        }

        @JvmStatic @AfterClass fun restoreParserBoundsChecks() {
            ParsableByteArray.setShouldEnforceLimitOnLegacyMethods(null)
        }
    }
}
