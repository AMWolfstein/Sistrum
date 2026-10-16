package com.amwolfstein.sistrum.ui.screens

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.sync.LibraryArtist
import androidx.compose.foundation.lazy.rememberLazyListState
import com.amwolfstein.sistrum.ui.testing.UiMatrix
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ArtistDetailsPresentationTest(private val case: UiMatrix.Case) {
    @get:Rule val rule = createComposeRule()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases() = UiMatrix.cases.map { arrayOf(it) }
    }

    private fun label(id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(case.language))
        }).getString(id)
    }

    @Test
    fun longTitleMissingArtKeepsPrimaryActionsVisibleAtNarrowWidth() {
        val title = if (case.language == "ar") "اسم فنان عربي طويل للغاية مع موسيقى كلاسيكية" else "An exceptionally long artist name with classical music"
        val matrix = UiMatrix(rule, case, UiMatrix.outputDirectory("ArtistDetails"))
        var plays = 0
        var shuffles = 0
        matrix.setContent {
            Box(Modifier.width(280.dp)) {
                ArtistDetailsContent(
                    ArtistDetailsUiState(artist = LibraryArtist("artist", title)),
                    listState = rememberLazyListState(),
                    onPlay = { plays++ }, onShuffle = { shuffles++ },
                )
            }
        }
        rule.onNodeWithText(title).assertExists()
        rule.onNode(hasTestTag("mornye-artwork-image")).assertDoesNotExist()
        val play = hasContentDescription(label(R.string.player_play))
        val shuffle = hasContentDescription(label(R.string.player_shuffle))
        rule.onNode(play).performScrollTo().assertIsDisplayed()
        matrix.assertNoClippedActions(play)
        matrix.assertNoClippedActions(shuffle)
        rule.onNode(play).performClick()
        rule.onNode(shuffle).performClick()
        assertEquals(1, plays)
        assertEquals(1, shuffles)
        matrix.captureToImage("long-title-missing-art")
    }

}
