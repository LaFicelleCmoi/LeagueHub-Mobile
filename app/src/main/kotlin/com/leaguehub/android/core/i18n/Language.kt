package com.leaguehub.android.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.leaguehub.android.domain.repository.AppLanguage

// Portage du système `data-i18n` du site (clé `ldc_lang`) : les textes vivent dans
// res/values (FR, langue par défaut) et res/values-en (EN) ; le choix de l'utilisateur
// est appliqué à l'app entière par AppCompat (et mémorisé par Android 13+).

object Language {
    fun apply(language: AppLanguage) {
        val wanted = if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(language.tag)
        if (AppCompatDelegate.getApplicationLocales() != wanted) AppCompatDelegate.setApplicationLocales(wanted)
    }
}
