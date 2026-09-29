package me.misa198.airmedy.ui.components

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/*
 * Numbers are always shown with Western (Latin) digits. In Arabic locales the platform would
 * otherwise format resource placeholders and String.format output with Arabic-Indic digits
 * while literal and metadata numbers stay Western, so one screen mixed both systems.
 *
 * Two entry points keep this in one place: the activity's resources carry the `nu-latn`
 * Unicode extension (covering stringResource and plural placeholders), and code that formats
 * outside resources uses [formatDisplay] / [displayLocale].
 */

/** [this] locale with the Latin numbering system; language, region and plural rules are unchanged. */
internal fun Locale.withLatinDigits(): Locale =
    runCatching { Locale.Builder().setLocale(this).setUnicodeLocaleKeyword("nu", "latn").build() }.getOrDefault(this)

/** The locale to format user-visible numbers with. */
internal fun displayLocale(): Locale = Locale.getDefault().withLatinDigits()

/** `String.format` for user-visible text, with Latin digits. */
internal fun formatDisplay(format: String, vararg args: Any?): String = String.format(displayLocale(), format, *args)

/** A context whose resources format numbers with Latin digits. */
internal fun Context.withLatinDigits(): Context {
    val current = resources.configuration
    val locales = current.locales
    val latin = LocaleList(*Array(locales.size()) { index -> locales[index].withLatinDigits() })
    if (latin == locales) return this
    return createConfigurationContext(Configuration(current).apply { setLocales(latin) })
}
