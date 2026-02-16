package com.t7lab.focustime.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t7lab.focustime.R
import com.t7lab.focustime.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val hasPassword: Boolean = false,
    val isSessionActive: Boolean = false,
    @StringRes val messageRes: Int? = null,
    val isError: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        combine(
            preferencesManager.hasPassword,
            preferencesManager.isSessionActive
        ) { hasPassword, isSessionActive ->
            _uiState.value.copy(
                hasPassword = hasPassword,
                isSessionActive = isSessionActive
            )
        }.onEach { _uiState.value = it }
            .launchIn(viewModelScope)
    }

    fun setPassword(newPassword: String) {
        if (_uiState.value.isSessionActive) {
            _uiState.value = _uiState.value.copy(
                messageRes = R.string.cannot_change_during_session,
                isError = true
            )
            return
        }

        if (newPassword.length < 4) {
            _uiState.value = _uiState.value.copy(
                messageRes = R.string.password_too_short,
                isError = true
            )
            return
        }

        viewModelScope.launch {
            preferencesManager.setPassword(newPassword)
            _uiState.value = _uiState.value.copy(
                messageRes = R.string.password_set_success,
                isError = false
            )
        }
    }

    fun removePassword() {
        if (_uiState.value.isSessionActive) {
            _uiState.value = _uiState.value.copy(
                messageRes = R.string.cannot_remove_during_session,
                isError = true
            )
            return
        }

        viewModelScope.launch {
            preferencesManager.removePassword()
            _uiState.value = _uiState.value.copy(
                messageRes = R.string.password_removed_success,
                isError = false
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(messageRes = null, isError = false)
    }
}
