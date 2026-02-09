package com.t7lab.focustime.ui.apppicker

data class AppInfo(
    val packageName: String,
    val displayName: String,
    val isSelected: Boolean,
    val isCuratedDistraction: Boolean = false
)

data class AppPickerUiState(
    val searchQuery: String = "",
    val allApps: List<AppInfo> = emptyList(),
    val filteredApps: List<AppInfo> = emptyList(),
    val curatedApps: List<AppInfo> = emptyList(),
    val frequentlyUsedApps: List<AppInfo> = emptyList(),
    val isLoading: Boolean = true
)
