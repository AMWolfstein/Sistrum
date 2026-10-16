package com.amwolfstein.sistrum.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.sync.LibraryTrack
import com.amwolfstein.sistrum.sync.isExplicit
import com.amwolfstein.sistrum.ui.theme.LocalReducedMotion
import com.amwolfstein.sistrum.ui.theme.LocalSistrumTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LocalMornyeHaze = staticCompositionLocalOf<HazeState?> { null }
internal val MornyeFallbackTint = Color(0xFF242428)

/** Even a white cover yields a dark page; white text has at least 7:1 contrast. */
internal fun mornyePageTint(dominant: Color?): Color = dominant?.takeIf { it.alpha > 0f }?.let {
    Color(it.red * 0.32f, it.green * 0.32f, it.blue * 0.32f)
} ?: MornyeFallbackTint

enum class MornyePlayShape { Pill, Circle }

/** Shared page only; callers retain their lists, menus, metadata and playback callbacks. */
@Composable
fun MornyeDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    artworkPath: String? = null,
    audioPath: String? = null,
    listState: LazyListState = rememberLazyListState(),
    onHeroColorChanged: (Color) -> Unit = {},
    artworkContent: (@Composable BoxScope.() -> Unit)? = null,
    header: @Composable ColumnScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val bitmap = rememberArtworkThumbnail(artworkPath, audioPath, targetPx = 720)
    var page by remember(artworkPath, audioPath) { mutableStateOf(MornyeFallbackTint) }
    LaunchedEffect(bitmap) {
        val dominant = bitmap?.let { withContext(Dispatchers.Default) { artworkDominantColor(it) } }
        page = mornyePageTint(dominant)
        onHeroColorChanged(dominant ?: MornyeFallbackTint)
    }
    val tokens = LocalSistrumTokens.current
    val tintedTokens = tokens.copy(colors = tokens.colors.copy(
        page = page, opaqueFill = page, glassTint = Color.White.copy(alpha = 0.10f),
        text = Color.White, mutedText = Color.White, divider = Color.White.copy(alpha = 0.22f),
    ))
    val haze = rememberHazeState()
    val policy = GlassPolicy.resolve(LocalReduceTransparency.current, Build.VERSION.SDK_INT >= 33,
        LocalGlassPerformanceFallback.current || LocalReducedMotion.current)
    val glassHaze = haze.takeIf { policy == GlassPolicy.Blur }
    val collapsed by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    CompositionLocalProvider(LocalSistrumTokens provides tintedTokens, LocalMornyeHaze provides glassHaze) {
        Box(modifier.fillMaxSize().testTag("mornye-page")) {
            Box(Modifier.matchParentSize().hazeSource(haze).background(page))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("mornye-list"),
                contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "mornye-header") {
                    Column {
                        Box(Modifier.fillMaxWidth().heightIn(min = 340.dp)) {
                            Box(Modifier.matchParentSize().hazeSource(haze)) {
                                if (artworkContent != null) artworkContent()
                                else if (bitmap != null) Image(bitmap, null, Modifier.matchParentSize().testTag("mornye-artwork-image"), contentScale = ContentScale.Crop)
                                else Box(Modifier.matchParentSize().background(page).testTag("mornye-artwork-fallback"))
                                Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                                    0f to Color.Transparent, 0.45f to page.copy(alpha = 0.20f), 1f to page)))
                            }
                            // The opaque tint under text guarantees contrast even over a bright photograph.
                            Column(Modifier.align(Alignment.BottomCenter)) {
                                Spacer(Modifier.height(240.dp))
                                Text(title, Modifier.fillMaxWidth().background(page)
                                    .padding(horizontal = 24.dp, vertical = 12.dp).testTag("mornye-header-title"),
                                    color = Color.White, style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, content = header)
                    }
                }
                content()
            }
            Row(Modifier.fillMaxWidth().background(if (collapsed) page else Color.Transparent)
                .statusBarsPadding().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                MornyeRoundAction(MaterialSymbols.ChevronLeft, stringResource(R.string.navigate_back), onBack)
                if (collapsed) Text(title, Modifier.weight(1f).padding(vertical = 12.dp).testTag("mornye-collapsed-title"),
                    color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                else Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/** Reusable frosted card treatment, including the same tinted opaque accessibility fallback. */
@Composable
fun MornyeGlassSurface(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = LocalSistrumTokens.current.shapes.medium
    GlassSurface(LocalMornyeHaze.current, modifier.clip(shape), borderShape = shape, content = content)
}

@Composable
fun MornyeActionRow(
    onShuffle: () -> Unit,
    onPlay: () -> Unit,
    menu: @Composable (@Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    playShape: MornyePlayShape = MornyePlayShape.Pill,
    onMore: () -> Unit = {},
    moreLabel: String = stringResource(R.string.player_more),
) {
    val pill = playShape == MornyePlayShape.Pill
    val playLabel = stringResource(R.string.player_play)
    Row(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        MornyeRoundAction(MaterialSymbols.Shuffle, stringResource(R.string.player_shuffle), onShuffle)
        Box(Modifier.weight(1f, fill = false).heightIn(min = 56.dp).then(if (pill) Modifier.widthIn(min = 144.dp) else Modifier.size(64.dp))
            .clip(CircleShape).then(if (pill) Modifier.liquidGlassBackground(LocalMornyeHaze.current, borderShape = CircleShape) else Modifier)
            .background(if (pill) Color.White.copy(alpha = 0.86f) else Color.White)
            .semantics { contentDescription = playLabel }.clickable(role = Role.Button, onClick = onPlay)
            .padding(horizontal = if (pill) 20.dp else 0.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MaterialSymbol(MaterialSymbols.PlayArrow, null, size = (28 / LocalDensity.current.fontScale).dp, tint = Color.Black, filled = true)
                if (pill) Text(playLabel, color = Color.Black, style = MaterialTheme.typography.labelLarge)
            }
        }
        menu { MornyeRoundAction("more_horiz", moreLabel, onMore) }
    }
}

@Composable
private fun MornyeRoundAction(symbol: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(40.dp).clip(CircleShape).liquidGlassBackground(LocalMornyeHaze.current, borderShape = CircleShape),
            contentAlignment = Alignment.Center) { MaterialSymbol(symbol, null, size = (20 / LocalDensity.current.fontScale).dp, tint = Color.White) }
    }
}

/** Numbered album rows or artwork-led playlist/artist rows, with caller-owned menu actions. */
@Composable
fun MornyeSongRow(
    track: LibraryTrack,
    onClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    onLongClick: (() -> Unit)? = null,
    menu: @Composable (@Composable () -> Unit) -> Unit = { it() },
) {
    val bitmap = rememberArtworkThumbnail(track.artworkPath, track.audioPath)
    Row(modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (number != null) Text(number.toString(), Modifier.widthIn(min = 32.dp), color = Color.White,
            style = MaterialTheme.typography.bodySmall)
        else Box(Modifier.size(44.dp).clip(LocalSistrumTokens.current.shapes.small)
            .background(LocalSistrumTokens.current.colors.opaqueFill), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else MaterialSymbol(MaterialSymbols.MusicNote, null, tint = Color.White)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(track.artists, color = Color.White, style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (track.isExplicit()) Text(stringResource(R.string.track_explicit), color = Color.White, style = MaterialTheme.typography.labelSmall)
                TrackQualityBadge(trackAudioQuality(track))
            }
        }
        menu { MornyeRoundAction("more_horiz", stringResource(R.string.track_row_more_options), onMore) }
    }
}

@Composable
fun MornyeDetailFooter(songCount: Int, durationMinutes: Int, modifier: Modifier = Modifier) {
    Text(stringResource(R.string.mornye_footer,
        formatDisplay(pluralStringResource(R.plurals.mornye_songs, songCount), songCount),
        formatDisplay(pluralStringResource(R.plurals.mornye_minutes, durationMinutes), durationMinutes)),
        modifier.fillMaxWidth().padding(16.dp), color = Color.White, style = MaterialTheme.typography.bodySmall)
}
