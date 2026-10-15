package com.amwolfstein.sistrum.ui.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Harness checks only; screen/token acceptance is added by subsequent tasks without changing these tests. */
@RunWith(Parameterized::class)
class UiMatrixTest(private val case: UiMatrix.Case) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = UiMatrix.cases.map { arrayOf<Any>(it) }
    }

    @get:Rule val rule = createComposeRule()
    private fun matrix(name: String) = UiMatrix(rule, case, UiMatrix.outputDirectory(name))

    @Test fun rendersConfigurationAndSavesArtifact() {
        val matrix = matrix("configuration")
        matrix.setContent { reduce ->
            assertEquals(case.reduceTransparency, reduce)
            assertEquals(case.fontScale, LocalDensity.current.fontScale)
            assertEquals(case.language, LocalConfiguration.current.locales[0].language)
            assertEquals(if (case.language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalLayoutDirection.current)
            Button(onClick = {}) { Text(if (case.language == "ar") "تشغيل" else "Play") }
        }
        matrix.assertNoClippedActions()
        val image = matrix.captureToImage()
        assertTrue(image.isFile && image.length() > 0)
        matrix.assertContrast(Color.Black, Color.White)
        matrix.assertContrast(Color.White, Color.Black)
        // WCAG sRGB boundary fixtures: #767676/white is 4.542; #777777 is 4.478.
        matrix.assertContrast(Color(0xFF767676), Color.White)
        assertThrows(IllegalStateException::class.java) { matrix.assertContrast(Color(0xFF777777), Color.White) }
        matrix.assertContrast(Color(0xFF949494), Color.White, largeText = true)
        assertThrows(IllegalStateException::class.java) {
            matrix.assertContrast(Color(0xFF959595), Color.White, largeText = true)
        }
        assertThrows(IllegalStateException::class.java) { matrix.assertContrast(Color.White, Color.White) }
        assertThrows(IllegalStateException::class.java) {
            matrix.assertContrast(Color.Black.copy(alpha = 0.1f), Color.White)
        }
        assertThrows(IllegalArgumentException::class.java) {
            matrix.assertContrast(Color.Black, Color.White.copy(alpha = 0.5f))
        }
    }

    @Test fun rejectsPartiallyOffscreenAction() {
        val matrix = matrix("clipping")
        matrix.setContent {
            Box(Modifier.size(200.dp)) {
                Button(onClick = {}, modifier = Modifier.offset(y = (-30).dp)) { Text("Play") }
            }
        }
        assertThrows(IllegalStateException::class.java) { matrix.assertNoClippedActions() }
    }

    @Test fun rejectsOverflowingActionLabel() {
        val matrix = matrix("overflow")
        matrix.setContent {
            Button(onClick = {}, modifier = Modifier.size(80.dp)) {
                Text("Long primary action label that must not be clipped", maxLines = 1)
            }
        }
        assertThrows(IllegalStateException::class.java) { matrix.assertNoClippedActions() }
    }
}
