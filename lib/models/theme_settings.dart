import 'package:material_ui/material_ui.dart';

const String kThemeModeKey = 'theme_mode';
const String kMornyeAccentKey = 'mornye_accent';
const String kMornyeAccentMovedToBlueKey = 'mornye_accent_moved_to_blue';
const String kLyricsTextScaleKey = 'lyrics_text_scale';

double normalizeLyricsTextScale(num? value) =>
    value != null && value.isFinite ? value.toDouble().clamp(0.8, 1.4) : 1.0;
const String kLyricsBlurKey = 'lyrics_blur';

/// How strongly Mornye blurs the lyric lines around the current one: 0 turns
/// it off, 1 is the default.
double normalizeLyricsBlur(num? value) =>
    value != null && value.isFinite ? value.toDouble().clamp(0.0, 1.5) : 1.0;
const String kLyricsOffsetKey = 'lyrics_offset_ms';
const int kMaxLyricsOffsetMs = 2000;

/// How much later synced lyrics follow playback, in milliseconds, so they
/// match audio that reaches the ears late (such as over Bluetooth). Negative
/// values show them earlier.
int normalizeLyricsOffsetMs(num? value) => value != null && value.isFinite
    ? value.round().clamp(-kMaxLyricsOffsetMs, kMaxLyricsOffsetMs)
    : 0;
const String kMornyeGlassOutlinesKey = 'mornye_glass_outlines';

/// The user's selected glass mode. [MornyeGlassLevel] names are stored.
const String kMornyeGlassModeKey = 'mornye_glass_mode';
const MornyeGlassLevel kDefaultMornyeGlassLevel = MornyeGlassLevel.liquid;

/// The retired continuous clarity (0 to 1). Read only by the one-time mode
/// migration, then removed.
const String kLegacyMornyeGlassClarityKey = 'mornye_glass_clarity';
const String kMornyeGlassModeMigratedKey = 'mornye_glass_mode_migrated';

/// Glass the user selected. Device fallback is applied at render time and
/// never changes this value. Two modes: Liquid and None.
enum MornyeGlassLevel {
  /// None: solid surfaces with no backdrop sampling or glass shaders.
  flat,

  /// Liquid Glass: native optical lens over a batched backdrop.
  liquid,
}

/// The retired Frosted mode, as stored by older builds. It reads as Liquid.
const String kRetiredFrostedGlassName = 'frosted';

MornyeGlassLevel mornyeGlassLevelFromString(String? value) {
  if (value == kRetiredFrostedGlassName) return MornyeGlassLevel.liquid;
  return MornyeGlassLevel.values.firstWhere(
    (level) => level.name == value,
    orElse: () => kDefaultMornyeGlassLevel,
  );
}

/// The mode the retired clarity slider implied: exactly 0 was None, and any
/// value above 0 was glass (Liquid). Missing or non-finite values had the
/// default clarity of 0.75, so they map to Liquid.
MornyeGlassLevel mornyeGlassLevelFromLegacyClarity(num? clarity) {
  final value = clarity != null && clarity.isFinite
      ? clarity.toDouble().clamp(0.0, 1.0)
      : 0.75;
  return value == 0 ? MornyeGlassLevel.flat : MornyeGlassLevel.liquid;
}

enum MornyeAccent { red, orange, green, teal, blue, purple, pink }

MornyeAccent mornyeAccentFromString(String? value) =>
    MornyeAccent.values.firstWhere(
      (accent) => accent.name == value,
      orElse: () => MornyeAccent.blue,
    );

/// Preference keys of the removed Material style, dynamic color, seed color
/// and AMOLED. Nothing reads them now; the theme provider removes them once.
const String kRetiredUseDynamicColorKey = 'use_dynamic_color';
const String kRetiredSeedColorKey = 'seed_color';
const String kRetiredUseAmoledKey = 'use_amoled';
const String kRetiredThemeStyleKey = 'theme_style';
const String kRetiredThemePrefsRemovedKey = 'retired_theme_prefs_removed';

/// Preference keys of the removed custom and system font settings.
const String kRetiredUseSystemFontKey = 'use_system_font';
const String kRetiredCustomFontIdKey = 'custom_font_id';
const String kRetiredCustomFontNameKey = 'custom_font_name';
const String kRetiredFontPrefsRemovedKey = 'retired_font_prefs_removed';

class ThemeSettings {
  final ThemeMode themeMode;
  final MornyeAccent mornyeAccent;
  final double lyricsTextScale;
  final double lyricsBlur;
  final int lyricsOffsetMs;
  final MornyeGlassLevel mornyeGlassLevel;
  final bool mornyeGlassOutlines;

  const ThemeSettings({
    this.themeMode = ThemeMode.system,
    this.mornyeAccent = MornyeAccent.blue,
    this.lyricsTextScale = 1,
    this.lyricsBlur = 1,
    this.lyricsOffsetMs = 0,
    this.mornyeGlassLevel = kDefaultMornyeGlassLevel,
    this.mornyeGlassOutlines = true,
  });

  ThemeSettings copyWith({
    ThemeMode? themeMode,
    MornyeAccent? mornyeAccent,
    double? lyricsTextScale,
    double? lyricsBlur,
    int? lyricsOffsetMs,
    MornyeGlassLevel? mornyeGlassLevel,
    bool? mornyeGlassOutlines,
  }) {
    return ThemeSettings(
      themeMode: themeMode ?? this.themeMode,
      mornyeAccent: mornyeAccent ?? this.mornyeAccent,
      lyricsTextScale: normalizeLyricsTextScale(
        lyricsTextScale ?? this.lyricsTextScale,
      ),
      lyricsBlur: normalizeLyricsBlur(lyricsBlur ?? this.lyricsBlur),
      lyricsOffsetMs: normalizeLyricsOffsetMs(
        lyricsOffsetMs ?? this.lyricsOffsetMs,
      ),
      mornyeGlassLevel: mornyeGlassLevel ?? this.mornyeGlassLevel,
      mornyeGlassOutlines: mornyeGlassOutlines ?? this.mornyeGlassOutlines,
    );
  }

  Map<String, dynamic> toJson() => {
    kThemeModeKey: themeMode.name,
    kMornyeAccentKey: mornyeAccent.name,
    kLyricsTextScaleKey: lyricsTextScale,
    kLyricsBlurKey: lyricsBlur,
    kLyricsOffsetKey: lyricsOffsetMs,
    kMornyeGlassModeKey: mornyeGlassLevel.name,
    kMornyeGlassOutlinesKey: mornyeGlassOutlines,
  };

  factory ThemeSettings.fromJson(Map<String, dynamic> json) {
    return ThemeSettings(
      themeMode: themeModeFromString(json[kThemeModeKey] as String?),
      mornyeAccent: mornyeAccentFromString(json[kMornyeAccentKey] as String?),
      lyricsTextScale: normalizeLyricsTextScale(
        json[kLyricsTextScaleKey] as num?,
      ),
      lyricsBlur: normalizeLyricsBlur(json[kLyricsBlurKey] as num?),
      lyricsOffsetMs: normalizeLyricsOffsetMs(json[kLyricsOffsetKey] as num?),
      mornyeGlassLevel: mornyeGlassLevelFromString(
        json[kMornyeGlassModeKey] as String?,
      ),
      mornyeGlassOutlines: json[kMornyeGlassOutlinesKey] as bool? ?? true,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is ThemeSettings &&
        other.themeMode == themeMode &&
        other.mornyeAccent == mornyeAccent &&
        other.lyricsTextScale == lyricsTextScale &&
        other.lyricsBlur == lyricsBlur &&
        other.lyricsOffsetMs == lyricsOffsetMs &&
        other.mornyeGlassLevel == mornyeGlassLevel &&
        other.mornyeGlassOutlines == mornyeGlassOutlines;
  }

  @override
  int get hashCode =>
      themeMode.hashCode ^
      mornyeAccent.hashCode ^
      lyricsTextScale.hashCode ^
      lyricsBlur.hashCode ^
      lyricsOffsetMs.hashCode ^
      mornyeGlassLevel.hashCode ^
      mornyeGlassOutlines.hashCode;
}

ThemeMode themeModeFromString(String? value) {
  if (value == null) return ThemeMode.system;
  return ThemeMode.values.firstWhere(
    (e) => e.name == value,
    orElse: () => ThemeMode.system,
  );
}
