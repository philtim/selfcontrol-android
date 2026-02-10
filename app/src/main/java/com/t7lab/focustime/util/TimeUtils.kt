package com.t7lab.focustime.util

import android.content.Context
import androidx.annotation.StringRes
import com.t7lab.focustime.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(millis: Long): String {
    if (millis <= 0) return "0:00"

    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}

fun formatEndTime(endTimeMs: Long, context: Context): String {
    if (endTimeMs <= 0) return ""
    val format = SimpleDateFormat("h:mm a", Locale.getDefault())
    return context.getString(R.string.until_time_format, format.format(Date(endTimeMs)))
}

fun formatDurationShort(millis: Long, context: Context): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

    return when {
        hours > 0 && minutes > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
        hours > 0 -> context.getString(R.string.duration_hours_only, hours)
        else -> context.getString(R.string.duration_minutes_only, minutes)
    }
}

data class DurationOption(
    @StringRes val labelRes: Int,
    val durationMs: Long
)

val DURATION_OPTIONS = listOf(
    DurationOption(R.string.duration_30_min, TimeUnit.MINUTES.toMillis(30)),
    DurationOption(R.string.duration_1_hour, TimeUnit.HOURS.toMillis(1)),
    DurationOption(R.string.duration_2_hours, TimeUnit.HOURS.toMillis(2)),
    DurationOption(R.string.duration_4_hours, TimeUnit.HOURS.toMillis(4)),
    DurationOption(R.string.duration_6_hours, TimeUnit.HOURS.toMillis(6)),
    DurationOption(R.string.duration_8_hours, TimeUnit.HOURS.toMillis(8)),
)

fun isValidDomain(input: String): Boolean {
    val trimmed = input.trim().lowercase()
    if (trimmed.isEmpty()) return false

    // Allow wildcard prefix
    val domain = if (trimmed.startsWith("*.")) trimmed.removePrefix("*.") else trimmed

    // Basic domain validation
    val domainRegex = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}$")
    return domainRegex.matches(domain)
}
