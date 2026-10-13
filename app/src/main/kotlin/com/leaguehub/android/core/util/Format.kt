package com.leaguehub.android.core.util

import com.leaguehub.android.domain.schedule.PARIS
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Dates affichées en heure de Paris, quel que soit le fuseau de l'appareil (portage de `format.ts`).

private fun zoned(ms: Long) = Instant.ofEpochMilli(ms).atZone(PARIS)

fun formatTime(ms: Long): String = DateTimeFormatter.ofPattern("HH:mm").format(zoned(ms))

fun formatDay(day: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day).replaceFirstChar { it.titlecase(locale) }

fun formatDay(ms: Long, locale: Locale = Locale.getDefault()): String = formatDay(zoned(ms).toLocalDate(), locale)

fun formatShortDate(ms: Long, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("d MMM", locale).format(zoned(ms))

fun formatDateTime(ms: Long, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).format(zoned(ms))

fun formatMonth(ms: Long, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(zoned(ms)).replaceFirstChar { it.titlecase(locale) }

fun formatGoalDiff(diff: Int): String = if (diff > 0) "+$diff" else diff.toString()

fun parisDay(ms: Long): LocalDate = zoned(ms).toLocalDate()
