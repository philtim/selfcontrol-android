package com.t7lab.focustime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.db.Session
import com.t7lab.focustime.data.preferences.PreferencesManager
import com.t7lab.focustime.data.repository.BlocklistRepository
import com.t7lab.focustime.data.repository.SessionRepository
import com.t7lab.focustime.service.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val activeSession: Session? = null,
    val blockedItems: List<BlockedItem> = emptyList(),
    val selectedDurationMs: Long? = null,
    val remainingTimeMs: Long = 0L,
    val isSessionActive: Boolean = false,
    val passwordUnlockResult: PasswordUnlockResult? = null,
    val showSessionComplete: Boolean = false,
    val completedDurationMs: Long = 0L,
)

enum class PasswordUnlockResult {
    SUCCESS,
    WRONG_PASSWORD,
    NO_PASSWORD_SET
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val blocklistRepository: BlocklistRepository,
    private val sessionManager: SessionManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeSession()
        observeBlocklist()
        startCountdownTimer()
        checkPendingCelebration()
    }

    private fun observeSession() {
        viewModelScope.launch {
            sessionRepository.getActiveSession().collect { session ->
                _uiState.value = _uiState.value.copy(
                    activeSession = session,
                    isSessionActive = session != null
                )
            }
        }
    }

    private fun observeBlocklist() {
        viewModelScope.launch {
            blocklistRepository.getAllItems().collect { items ->
                _uiState.value = _uiState.value.copy(blockedItems = items)
            }
        }
    }

    private fun checkPendingCelebration() {
        viewModelScope.launch {
            val completedMs = preferencesManager.getLastCompletedDuration()
            if (completedMs > 0) {
                _uiState.value = _uiState.value.copy(
                    showSessionComplete = true,
                    completedDurationMs = completedMs
                )
            }
        }
    }

    private fun startCountdownTimer() {
        viewModelScope.launch {
            while (isActive) {
                val session = _uiState.value.activeSession
                if (session != null && session.isActive) {
                    val remaining = session.endTime - System.currentTimeMillis()
                    if (remaining <= 0) {
                        val durationMs = session.durationMs
                        sessionManager.endSession()
                        preferencesManager.setLastCompletedDuration(durationMs)
                        _uiState.value = _uiState.value.copy(
                            remainingTimeMs = 0L,
                            isSessionActive = false,
                            activeSession = null,
                            showSessionComplete = true,
                            completedDurationMs = durationMs
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(remainingTimeMs = remaining)
                    }
                }
                delay(1000)
            }
        }
    }

    fun dismissSessionComplete() {
        viewModelScope.launch {
            preferencesManager.clearLastCompletedDuration()
        }
        _uiState.value = _uiState.value.copy(
            showSessionComplete = false,
            completedDurationMs = 0L
        )
    }

    fun selectDuration(durationMs: Long) {
        _uiState.value = _uiState.value.copy(selectedDurationMs = durationMs)
    }

    fun startSession(onVpnPermissionNeeded: () -> Unit) {
        val duration = _uiState.value.selectedDurationMs ?: return
        viewModelScope.launch {
            sessionManager.startSession(duration)
        }
    }

    fun removeItem(item: BlockedItem) {
        viewModelScope.launch {
            blocklistRepository.removeItem(item)
        }
    }

    fun tryUnlockWithPassword(password: String) {
        viewModelScope.launch {
            if (!preferencesManager.hasPasswordSet()) {
                _uiState.value = _uiState.value.copy(
                    passwordUnlockResult = PasswordUnlockResult.NO_PASSWORD_SET
                )
                return@launch
            }

            if (preferencesManager.verifyPassword(password)) {
                sessionManager.endSession()
                _uiState.value = _uiState.value.copy(
                    passwordUnlockResult = PasswordUnlockResult.SUCCESS
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    passwordUnlockResult = PasswordUnlockResult.WRONG_PASSWORD
                )
            }
        }
    }

    fun clearPasswordResult() {
        _uiState.value = _uiState.value.copy(passwordUnlockResult = null)
    }
}
