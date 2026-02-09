package com.t7lab.focustime.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val message: String? = null,
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

    fun setPassword(newPassword: String, confirmPassword: String) {
        if (_uiState.value.isSessionActive) {
            _uiState.value = _uiState.value.copy(
                message = "Cannot change password during an active session",
                isError = true
            )
            return
        }

        if (newPassword.length < 4) {
            _uiState.value = _uiState.value.copy(
                message = "Password must be at least 4 characters",
                isError = true
            )
            return
        }

        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(
                message = "Passwords don't match",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            preferencesManager.setPassword(newPassword)
            _uiState.value = _uiState.value.copy(
                message = "Password set successfully",
                isError = false
            )
        }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if (_uiState.value.isSessionActive) {
            _uiState.value = _uiState.value.copy(
                message = "Cannot change password during an active session",
                isError = true
            )
            return
        }

        if (newPassword.length < 4) {
            _uiState.value = _uiState.value.copy(
                message = "Password must be at least 4 characters",
                isError = true
            )
            return
        }

        if (newPassword != confirmPassword) {
            _uiState.value = _uiState.value.copy(
                message = "Passwords don't match",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            if (!preferencesManager.verifyPassword(currentPassword)) {
                _uiState.value = _uiState.value.copy(
                    message = "Current password is incorrect",
                    isError = true
                )
                return@launch
            }

            preferencesManager.setPassword(newPassword)
            _uiState.value = _uiState.value.copy(
                message = "Password changed successfully",
                isError = false
            )
        }
    }

    fun removePassword(currentPassword: String) {
        if (_uiState.value.isSessionActive) {
            _uiState.value = _uiState.value.copy(
                message = "Cannot remove password during an active session",
                isError = true
            )
            return
        }

        viewModelScope.launch {
            if (!preferencesManager.verifyPassword(currentPassword)) {
                _uiState.value = _uiState.value.copy(
                    message = "Incorrect password",
                    isError = true
                )
                return@launch
            }

            preferencesManager.removePassword()
            _uiState.value = _uiState.value.copy(
                message = "Password removed",
                isError = false
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, isError = false)
    }
}
