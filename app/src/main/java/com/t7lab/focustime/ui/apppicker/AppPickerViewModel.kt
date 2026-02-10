package com.t7lab.focustime.ui.apppicker

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t7lab.focustime.data.repository.BlocklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blocklistRepository: BlocklistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _uiState.asStateFlow()

    companion object {
        private const val FREQUENTLY_USED_COUNT = 5
        private const val USAGE_STATS_DAYS = 30
    }

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val (allAppInfos, frequentlyUsed) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isLauncher(pm, it.packageName) }
                    .filter { it.packageName != context.packageName }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

                val selectedPackages = blocklistRepository.getBlockedApps()
                    .map { it.value }
                    .toSet()

                val appInfos = apps.map { appInfo ->
                    AppInfo(
                        packageName = appInfo.packageName,
                        displayName = pm.getApplicationLabel(appInfo).toString(),
                        isSelected = appInfo.packageName in selectedPackages
                    )
                }

                // Get frequently used apps from UsageStatsManager
                val frequentPackages = getFrequentlyUsedPackages()
                val frequentApps = frequentPackages
                    .mapNotNull { pkg -> appInfos.find { it.packageName == pkg } }
                    .take(FREQUENTLY_USED_COUNT)

                // Filter out frequently used apps from the main list
                val frequentPackageSet = frequentApps.map { it.packageName }.toSet()
                val remainingApps = appInfos.filter { it.packageName !in frequentPackageSet }

                Pair(remainingApps, frequentApps)
            }

            _uiState.update {
                AppPickerUiState(
                    allApps = allAppInfos,
                    filteredApps = allAppInfos,
                    frequentlyUsedApps = frequentlyUsed,
                    isLoading = false
                )
            }
        }
    }

    private fun getFrequentlyUsedPackages(): List<String> {
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - (USAGE_STATS_DAYS.toLong() * 24 * 60 * 60 * 1000)

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            usageStats
                ?.groupBy { it.packageName }
                ?.mapValues { (_, stats) -> stats.sumOf { it.totalTimeInForeground } }
                ?.entries
                ?.sortedByDescending { it.value }
                ?.filter { it.value > 0 }
                ?.map { it.key }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isLauncher(pm: PackageManager, packageName: String): Boolean {
        return pm.getLaunchIntentForPackage(packageName) != null
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredApps = if (query.isBlank()) {
                    state.allApps
                } else {
                    state.allApps.filter {
                        it.displayName.contains(query, ignoreCase = true) ||
                                it.packageName.contains(query, ignoreCase = true)
                    }
                }
            )
        }
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            // Check both lists since frequent apps are not in allApps
            val app = _uiState.value.allApps.find { it.packageName == packageName }
                ?: _uiState.value.frequentlyUsedApps.find { it.packageName == packageName }
                ?: return@launch

            if (app.isSelected) {
                // Remove from blocklist
                val items = blocklistRepository.getBlockedApps()
                items.find { it.value == packageName }?.let {
                    blocklistRepository.removeItem(it)
                }
            } else {
                // Add to blocklist
                blocklistRepository.addApp(packageName, app.displayName)
            }

            // Update local state atomically
            _uiState.update { state ->
                state.copy(
                    allApps = state.allApps.map {
                        if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
                    },
                    filteredApps = state.filteredApps.map {
                        if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
                    },
                    frequentlyUsedApps = state.frequentlyUsedApps.map {
                        if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
                    }
                )
            }
        }
    }
}
