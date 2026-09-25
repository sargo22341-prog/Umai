package org.opensources.umai.core.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

/**
 * Applies the in-app language with the platform per-app language API
 * (`LocaleManager`, API 33+). No AppCompat shim is needed since Umai targets
 * Android 17 and above.
 *
 * `SYSTEM` clears the override, which makes Android fall back to the device
 * language; because the app only ships `en` and `fr` resources, any other
 * device language resolves to the default resources, i.e. English.
 */
class LocaleController(context: Context) {

    private val localeManager = context.applicationContext.getSystemService(LocaleManager::class.java)

    fun apply(language: AppLanguage) {
        val current = localeManager?.applicationLocales
        val target = language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        if (current != target) localeManager?.applicationLocales = target
    }

    /** Language tag sent in `Accept-Language`, so Mealie can localize its own strings. */
    fun acceptLanguage(): String {
        val override = localeManager?.applicationLocales?.takeUnless { it.isEmpty }?.get(0)
        return (override ?: Locale.getDefault()).toLanguageTag()
    }

    /** The language the app shows, "fr" or "en": the only two it is written in. */
    fun appLanguage(): String = if (acceptLanguage().startsWith("fr")) "fr" else "en"
}
