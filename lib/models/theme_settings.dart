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
const String kMornyeGlassClarityKey = 'mornye_glass_clarity';
const String kMornyeGlassOutlinesKey = 'mornye_glass_outlines';
const double kDefaultMornyeGlassClarity = 0.75;

double normalizeMornyeGlassClarity(num? value) =>
    value != null && value.isFinite
    ? value.toDouble().clamp(0.0, 1.0)
    : kDefaultMornyeGlassClarity;

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
  final double mornyeGlassClarity;
  final bool mornyeGlassOutlines;

  const ThemeSettings({
    this.themeMode = ThemeMode.system,
    this.mornyeAccent = MornyeAccent.blue,
    this.lyricsTextScale = 1,
    this.lyricsBlur = 1,
    this.lyricsOffsetMs = 0,
    this.mornyeGlassClarity = kDefaultMornyeGlassClarity,
    this.mornyeGlassOutlines = true,
  });

  ThemeSettings copyWith({
    ThemeMode? themeMode,
    MornyeAccent? mornyeAccent,
    double? lyricsTextScale,
    double? lyricsBlur,
    int? lyricsOffsetMs,
    double? mornyeGlassClarity,
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
      mornyeGlassClarity: normalizeMornyeGlassClarity(
        mornyeGlassClarity ?? this.mornyeGlassClarity,
      ),
      mornyeGlassOutlines: mornyeGlassOutlines ?? this.mornyeGlassOutlines,
    );
  }

  Map<String, dynamic> toJson() => {
    kThemeModeKey: themeMode.name,
    kMornyeAccentKey: mornyeAccent.name,
    kLyricsTextScaleKey: lyricsTextScale,
    kLyricsBlurKey: lyricsBlur,
    kLyricsOffsetKey: lyricsOffsetMs,
    kMornyeGlassClarityKey: mornyeGlassClarity,
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
      mornyeGlassClarity: normalizeMornyeGlassClarity(
        json[kMornyeGlassClarityKey] as num?,
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
        other.mornyeGlassClarity == mornyeGlassClarity &&
        other.mornyeGlassOutlines == mornyeGlassOutlines;
  }

  @override
  int get hashCode =>
      themeMode.hashCode ^
      mornyeAccent.hashCode ^
      lyricsTextScale.hashCode ^
      lyricsBlur.hashCode ^
      lyricsOffsetMs.hashCode ^
      mornyeGlassClarity.hashCode ^
      mornyeGlassOutlines.hashCode;
}

ThemeMode themeModeFromString(String? value) {
  if (value == null) return ThemeMode.system;
  return ThemeMode.values.firstWhere(
    (e) => e.name == value,
    orElse: () => ThemeMode.system,
  );
}
