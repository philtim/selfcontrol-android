package com.t7lab.focustime.util

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

fun formatDurationShort(millis: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

data class DurationOption(
    val label: String,
    val durationMs: Long
)

val DURATION_OPTIONS = listOf(
    DurationOption("30 min", TimeUnit.MINUTES.toMillis(30)),
    DurationOption("1 hour", TimeUnit.HOURS.toMillis(1)),
    DurationOption("2 hours", TimeUnit.HOURS.toMillis(2)),
    DurationOption("4 hours", TimeUnit.HOURS.toMillis(4)),
    DurationOption("6 hours", TimeUnit.HOURS.toMillis(6)),
    DurationOption("8 hours", TimeUnit.HOURS.toMillis(8)),
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
