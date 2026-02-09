package com.t7lab.focustime.util

import com.t7lab.focustime.data.db.BlockedItem

/**
 * Checks if a given hostname matches any of the blocked URL rules.
 *
 * Rules:
 * - "youtube.com" blocks "youtube.com" AND "www.youtube.com" (main domain + www)
 * - "*.youtube.com" blocks "youtube.com" AND all subdomains like "music.youtube.com"
 */
fun isHostBlocked(hostname: String, blockedUrls: List<BlockedItem>): Boolean {
    val normalizedHost = hostname.lowercase().trim()

    for (item in blockedUrls) {
        val pattern = item.value.lowercase().trim()

        if (item.isWildcard) {
            // *.youtube.com -> blocks youtube.com + all subdomains
            val baseDomain = pattern.removePrefix("*.")
            if (normalizedHost == baseDomain || normalizedHost.endsWith(".$baseDomain")) {
                return true
            }
        } else {
            // youtube.com -> blocks youtube.com AND www.youtube.com
            if (normalizedHost == pattern || normalizedHost == "www.$pattern") {
                return true
            }
        }
    }
    return false
}
