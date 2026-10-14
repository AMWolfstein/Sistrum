package com.amwolfstein.sistrum.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.material3.Button
import com.amwolfstein.sistrum.analysis.AnalysisProgress
import com.amwolfstein.sistrum.ui.components.formatDisplay
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.amwolfstein.sistrum.R
import com.amwolfstein.sistrum.ui.components.ActionList
import com.amwolfstein.sistrum.ui.components.ActionListContainerStyle
import com.amwolfstein.sistrum.ui.components.ActionListDivider
import com.amwolfstein.sistrum.ui.components.ActionListDividerStyle
import com.amwolfstein.sistrum.ui.components.ActionListItem
import com.amwolfstein.sistrum.ui.components.LabeledCard
import com.amwolfstein.sistrum.ui.components.Selection
import com.amwolfstein.sistrum.ui.components.SelectionOption
import com.amwolfstein.sistrum.ui.components.SistrumTrackSlider
import com.amwolfstein.sistrum.ui.theme.LocalSistrumColors
import com.amwolfstein.sistrum.player.EqualizerFrequenciesHz
import com.amwolfstein.sistrum.player.EqualizerSettings
import com.amwolfstein.sistrum.player.media3.LimiterState
import com.amwolfstein.sistrum.player.normalizeEqGain

@Composable
internal fun PlaybackSettingsContent(
    showFullscreenQualityBadge: Boolean,
    onShowFullscreenQualityBadgeChanged: (Boolean) -> Unit,
    onSongTransitionSelected: () -> Unit,
    onVolumeNormalizationSelected: () -> Unit,
    onEqualizerSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionList(
            items = listOf(
                ActionListItem(
                    labelRes = R.string.song_transition_title,
                    onClick = onSongTransitionSelected,
                ),
                ActionListItem(
                    labelRes = R.string.playback_volume_normalization,
                    onClick = onVolumeNormalizationSelected,
                ),
                ActionListItem(
                    labelRes = R.string.equalizer_title,
                    onClick = onEqualizerSelected,
                ),
                ActionListItem(
                    labelRes = R.string.playback_show_quality_badge,
                    trailingContent = {
                        Switch(
                            checked = showFullscreenQualityBadge,
                            onCheckedChange = onShowFullscreenQualityBadgeChanged,
                        )
                    },
                    onClick = { onShowFullscreenQualityBadgeChanged(!showFullscreenQualityBadge) },
                ),
            ),
            containerStyle = ActionListContainerStyle.Card,
            dividerStyle = ActionListDividerStyle.FullWidth,
        )
    }
}

@Composable
internal fun EqualizerContent(
    settings: EqualizerSettings,
    onEnabledChanged: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledLabel = stringResource(R.string.equalizer_enable)
    val colors = LocalSistrumColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        com.amwolfstein.sistrum.ui.components.Card {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.equalizer_enable,
                        trailingContent = { Switch(checked = settings.enabled, onCheckedChange = onEnabledChanged) },
                        onClick = { onEnabledChanged(!settings.enabled) },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
            )
            ActionListDivider(style = ActionListDividerStyle.FullWidth)
            Selection(
                labelRes = R.string.equalizer_preset,
                options = settings.profiles.map { SelectionOption(it.key, label = it.name) },
                selectedValue = settings.presetKey,
                onValueSelected = onPresetSelected,
            )
        }
        LabeledCard(label = stringResource(R.string.equalizer_bands)) {
            Column(
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                    settings.gainsDb.forEachIndexed { index, gain ->
                        val frequency = EqualizerFrequenciesHz[index]
                        val frequencyLabel = if (frequency < 1_000) {
                            stringResource(R.string.equalizer_frequency_hz, frequency)
                        } else {
                            stringResource(R.string.equalizer_frequency_khz, frequency / 1_000)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = frequencyLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                            Text(
                                text = stringResource(R.string.equalizer_gain_value, gain),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textMuted,
                            )
                        }
                        SistrumTrackSlider(
                            value = gain,
                            valueRange = -12f..12f,
                            onValueChange = { onBandChanged(index, normalizeEqGain(it)) },
                            modifier = Modifier.semantics { contentDescription = "$enabledLabel $frequencyLabel" },
                            trackHeight = 6.dp,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.buttonSecondary,
                            trackAlignment = Alignment.TopCenter,
                        )
                    }
                }
        }
    }
}

/**
 * The note shown under "Prevent clipping", or null when there is nothing to report: no session has
 * reported yet, or the limiter is available and under our control.
 */
@StringRes
internal fun limiterNoteRes(state: LimiterState?): Int? = when {
    state == null -> null
    !state.available -> R.string.playback_limiter_unavailable_note
    !state.controlled -> R.string.playback_limiter_not_controlled_note
    else -> null
}

@Composable
internal fun VolumeNormalizationContent(
    normalizationAvailable: Boolean,
    normalization: com.amwolfstein.sistrum.player.NormalizationSettings,
    limiterState: LimiterState? = null,
    onNormalizationChanged: (com.amwolfstein.sistrum.player.NormalizationSettings) -> Unit,
    modifier: Modifier = Modifier,
    analysisProgress: AnalysisProgress? = null,
    nowRunning: Boolean = false,
    onAnalyzeNow: () -> Unit = {},
    onStopAnalysis: () -> Unit = {},
) {
    val preampLabel = stringResource(R.string.playback_normalization_untagged_preamp)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        com.amwolfstein.sistrum.ui.components.Card {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.playback_volume_normalization,
                        trailingContent = { Switch(checked = normalization.enabled && normalizationAvailable, enabled = normalizationAvailable, onCheckedChange = { onNormalizationChanged(normalization.copy(enabled = it)) }) },
                        onClick = { if (normalizationAvailable) onNormalizationChanged(normalization.copy(enabled = !normalization.enabled)) },
                    ),
                    ActionListItem(
                        labelRes = R.string.playback_normalization_prevent_clip,
                        trailingContent = { Switch(checked = normalization.preventClip, enabled = normalizationAvailable && normalization.enabled, onCheckedChange = { onNormalizationChanged(normalization.copy(preventClip = it)) }) },
                        onClick = { if (normalizationAvailable && normalization.enabled) onNormalizationChanged(normalization.copy(preventClip = !normalization.preventClip)) },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
                dividerStyle = ActionListDividerStyle.FullWidth,
            )
            if (normalization.enabled && normalizationAvailable) {
                ActionListDivider(style = ActionListDividerStyle.FullWidth)
                Selection(
                    labelRes = R.string.playback_normalization_mode,
                    options = listOf(
                        SelectionOption(com.amwolfstein.sistrum.player.NormalizationMode.Auto, R.string.playback_normalization_mode_auto),
                        SelectionOption(com.amwolfstein.sistrum.player.NormalizationMode.Track, R.string.playback_normalization_mode_track),
                        SelectionOption(com.amwolfstein.sistrum.player.NormalizationMode.Album, R.string.playback_normalization_mode_album),
                    ),
                    selectedValue = normalization.mode,
                    onValueSelected = { onNormalizationChanged(normalization.copy(mode = it)) },
                )
            }
        }
        if (!normalizationAvailable) {
            Text(
                stringResource(R.string.playback_normalization_requires_media3),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = LocalSistrumColors.current.textMuted,
            )
        } else {
            if (analysisProgress != null && analysisProgress.total > 0) {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        pluralStringResource(R.plurals.playback_loudness_analysis_progress,
                            analysisProgress.total, analysisProgress.analyzed, analysisProgress.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSistrumColors.current.textMuted,
                    )
                    Text(
                        stringResource(R.string.playback_loudness_analysis_charging),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalSistrumColors.current.textMuted,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Button(
                    onClick = { if (nowRunning) onStopAnalysis() else onAnalyzeNow() },
                    enabled = nowRunning || analysisProgress == null || analysisProgress.analyzed != analysisProgress.total,
                ) {
                    Text(stringResource(if (nowRunning) R.string.playback_loudness_analysis_stop else R.string.playback_loudness_analysis_now))
                }
                if (!nowRunning) Text(
                    stringResource(R.string.playback_loudness_analysis_battery),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSistrumColors.current.textMuted,
                )
            }
        }
        limiterNoteRes(limiterState)?.let { noteRes ->
            Text(
                text = stringResource(noteRes),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = com.amwolfstein.sistrum.ui.theme.LocalSistrumColors.current.textMuted,
            )
        }
        AnimatedVisibility(
            visible = normalization.enabled && normalizationAvailable,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledCard(label = stringResource(R.string.playback_normalization_target)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(text = stringResource(R.string.playback_normalization_target_value, normalization.targetLufs.toInt()), style = MaterialTheme.typography.labelMedium, color = com.amwolfstein.sistrum.ui.theme.LocalSistrumColors.current.textMuted)
                        LufsTargetSlider(
                            targetLufs = normalization.targetLufs,
                            enabled = true,
                            onTargetChanged = { onNormalizationChanged(normalization.copy(targetLufs = it)) },
                        )
                    }
                }
                LabeledCard(label = stringResource(R.string.playback_normalization_untagged_preamp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(R.string.playback_normalization_preamp_value, formatDisplay("%+.1f", normalization.untaggedPreampDb)),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalSistrumColors.current.textMuted,
                        )
                        UntaggedPreampSlider(
                            preampDb = normalization.untaggedPreampDb,
                            enabled = true,
                            onPreampChanged = { onNormalizationChanged(normalization.copy(untaggedPreampDb = it)) },
                            modifier = Modifier.semantics { contentDescription = preampLabel },
                        )
                        Text(
                            stringResource(R.string.playback_normalization_untagged_caption),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalSistrumColors.current.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SongTransitionContent(
    crossfadeSeconds: Int,
    lastEnabledCrossfadeSeconds: Int,
    onCrossfadeSecondsChanged: (Int) -> Unit,
    blendArtworkDuringCrossfade: Boolean,
    onBlendArtworkDuringCrossfadeChanged: (Boolean) -> Unit,
    repeatOneCrossfade: Boolean,
    onRepeatOneCrossfadeChanged: (Boolean) -> Unit,
    crossfadeOnSkip: Boolean,
    onCrossfadeOnSkipChanged: (Boolean) -> Unit,
    normalizationAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabled = crossfadeSeconds > 0
    val displayedSeconds = if (enabled) crossfadeSeconds else lastEnabledCrossfadeSeconds
    val crossfadeLabel = stringResource(R.string.playback_crossfade)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.amwolfstein.sistrum.ui.components.Card {
            ActionList(
                items = listOf(
                    ActionListItem(
                        labelRes = R.string.playback_crossfade,
                        trailingContent = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    onCrossfadeSecondsChanged(if (checked) lastEnabledCrossfadeSeconds else 0)
                                },
                            )
                        },
                        onClick = {
                            onCrossfadeSecondsChanged(if (enabled) 0 else lastEnabledCrossfadeSeconds)
                        },
                    ),
                ),
                containerStyle = ActionListContainerStyle.Plain,
            )
        }
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledCard(label = stringResource(R.string.playback_crossfade_duration)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.playback_crossfade_duration_value, displayedSeconds, displayedSeconds),
                            style = MaterialTheme.typography.labelMedium,
                            color = com.amwolfstein.sistrum.ui.theme.LocalSistrumColors.current.textMuted,
                        )
                        CrossfadeDurationSlider(
                            seconds = displayedSeconds,
                            onSecondsChanged = onCrossfadeSecondsChanged,
                            enabled = enabled,
                            modifier = Modifier.semantics { contentDescription = crossfadeLabel },
                        )
                    }
                }
            }
        }
        com.amwolfstein.sistrum.ui.components.Card {
            CrossfadeOptionRow(
                labelRes = R.string.playback_crossfade_repeat_one,
                checked = repeatOneCrossfade,
                enabled = enabled,
                onCheckedChange = onRepeatOneCrossfadeChanged,
            )
            ActionListDivider(style = ActionListDividerStyle.FullWidth)
            CrossfadeOptionRow(
                labelRes = R.string.playback_crossfade_on_skip,
                subtitleRes = R.string.playback_crossfade_on_skip_description,
                checked = crossfadeOnSkip,
                enabled = enabled && normalizationAvailable,
                onCheckedChange = onCrossfadeOnSkipChanged,
            )
            if (!normalizationAvailable) {
                Text(
                    stringResource(R.string.playback_normalization_requires_media3),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalSistrumColors.current.textMuted,
                )
            }
        }
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                com.amwolfstein.sistrum.ui.components.Card {
                    ActionList(
                        items = listOf(
                            ActionListItem(
                                labelRes = R.string.playback_blend_artwork_during_crossfade,
                                trailingContent = {
                                    Switch(
                                        checked = blendArtworkDuringCrossfade,
                                        onCheckedChange = onBlendArtworkDuringCrossfadeChanged,
                                    )
                                },
                                onClick = {
                                    onBlendArtworkDuringCrossfadeChanged(!blendArtworkDuringCrossfade)
                                },
                            ),
                        ),
                        containerStyle = ActionListContainerStyle.Plain,
                    )
                }
            }
        }
    }
}

@Composable
private fun CrossfadeOptionRow(
    @StringRes labelRes: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @StringRes subtitleRes: Int? = null,
) {
    val colors = LocalSistrumColors.current
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.textMain else colors.textMuted,
            )
            subtitleRes?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/** Theme-aware half-dB pre-amp slider, following the target slider RTL behavior. */
@Composable
private fun UntaggedPreampSlider(preampDb: Float, enabled: Boolean, onPreampChanged: (Float) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSistrumColors.current
    // The slider fills from the layout's start edge, so it runs right to left in RTL.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val range = -15f..15f
    val value = preampDb.coerceIn(range.start, range.endInclusive)
    fun updateAt(x: Float, width: Float) {
        val startX = if (isRtl) width - x else x
        if (enabled && width > 0f) onPreampChanged(((range.start + (range.endInclusive - range.start) * (startX / width).coerceIn(0f, 1f)) * 2f).roundToInt() / 2f)
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(48.dp).semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, 59)
                if (enabled) setProgress { requested -> onPreampChanged(((requested * 2f).roundToInt() / 2f).coerceIn(range.start, range.endInclusive)); true }
            }.pointerInput(enabled, isRtl) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateAt(down.position.x, size.width.toFloat())
                    horizontalDrag(down.id) { change -> change.consume(); updateAt(change.position.x, size.width.toFloat()) }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(48.dp)) { scale(scaleX = if (isRtl) -1f else 1f, scaleY = 1f) {
                val fraction = (value - range.start) / (range.endInclusive - range.start)
                val height = 6.dp.toPx(); val centerY = 24.dp.toPx(); val radius = height / 2f
                drawRoundRect(colors.buttonSecondary, Offset(0f, centerY - radius), Size(size.width, height), CornerRadius(radius, radius))
                val thumbX = size.width * fraction
                drawRoundRect(colors.primary, Offset(0f, centerY - radius), Size(thumbX, height), CornerRadius(radius, radius))
                drawCircle(colors.primary, radius = 10.dp.toPx(), center = Offset(thumbX, centerY))
            } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.playback_normalization_preamp_minimum), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text(stringResource(R.string.playback_normalization_preamp_maximum), style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
    }
}
