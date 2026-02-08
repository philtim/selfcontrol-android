package com.t7lab.focustime.ui.apppicker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t7lab.focustime.data.db.BlockedItemType
import com.t7lab.focustime.data.repository.BlocklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    val isLoading: Boolean = true
)

@HiltViewModel
class AppPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blocklistRepository: BlocklistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppPickerUiState())
    val uiState: StateFlow<AppPickerUiState> = _uiState.asStateFlow()

    companion object {
        val CURATED_DISTRACTIONS = mapOf(
            "com.instagram.android" to "Instagram",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.google.android.youtube" to "YouTube",
            "com.twitter.android" to "X (Twitter)",
            "com.reddit.frontpage" to "Reddit",
            "com.facebook.katana" to "Facebook",
            "com.snapchat.android" to "Snapchat",
            "com.facebook.orca" to "Messenger",
            "com.whatsapp" to "WhatsApp",
            "com.discord" to "Discord",
            "com.pinterest" to "Pinterest",
            "com.linkedin.android" to "LinkedIn",
            "org.telegram.messenger" to "Telegram",
            "com.netflix.mediaclient" to "Netflix",
            "com.spotify.music" to "Spotify",
            "tv.twitch.android.app" to "Twitch",
        )
    }

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val (installedApps, selectedPackages) = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || isLauncher(pm, it.packageName) }
                    .filter { it.packageName != context.packageName }
                    .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

                val selected = blocklistRepository.getBlockedApps()
                    .map { it.value }
                    .toSet()

                Pair(apps, selected)
            }

            val pm = context.packageManager
            val allAppInfos = installedApps.map { appInfo ->
                AppInfo(
                    packageName = appInfo.packageName,
                    displayName = pm.getApplicationLabel(appInfo).toString(),
                    isSelected = appInfo.packageName in selectedPackages,
                    isCuratedDistraction = appInfo.packageName in CURATED_DISTRACTIONS
                )
            }

            val curatedApps = CURATED_DISTRACTIONS.map { (pkg, name) ->
                val installed = allAppInfos.find { it.packageName == pkg }
                AppInfo(
                    packageName = pkg,
                    displayName = installed?.displayName ?: name,
                    isSelected = pkg in selectedPackages,
                    isCuratedDistraction = true
                )
            }.filter { curated -> allAppInfos.any { it.packageName == curated.packageName } }

            _uiState.value = AppPickerUiState(
                allApps = allAppInfos,
                filteredApps = allAppInfos,
                curatedApps = curatedApps,
                isLoading = false
            )
        }
    }

    private fun isLauncher(pm: PackageManager, packageName: String): Boolean {
        return pm.getLaunchIntentForPackage(packageName) != null
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredApps = if (query.isBlank()) {
                _uiState.value.allApps
            } else {
                _uiState.value.allApps.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                            it.packageName.contains(query, ignoreCase = true)
                }
            }
        )
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            val app = _uiState.value.allApps.find { it.packageName == packageName } ?: return@launch

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

            // Update local state
            val updatedAll = _uiState.value.allApps.map {
                if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
            }
            val updatedCurated = _uiState.value.curatedApps.map {
                if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
            }
            val updatedFiltered = _uiState.value.filteredApps.map {
                if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
            }

            _uiState.value = _uiState.value.copy(
                allApps = updatedAll,
                curatedApps = updatedCurated,
                filteredApps = updatedFiltered
            )
        }
    }
}
