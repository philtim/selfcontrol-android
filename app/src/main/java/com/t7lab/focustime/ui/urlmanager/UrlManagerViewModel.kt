package com.t7lab.focustime.ui.urlmanager

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.t7lab.focustime.R
import com.t7lab.focustime.data.db.BlockedItem
import com.t7lab.focustime.data.repository.BlocklistRepository
import com.t7lab.focustime.util.isValidDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UrlManagerUiState(
    val urls: List<BlockedItem> = emptyList(),
    val inputText: String = "",
    @StringRes val errorMessageRes: Int? = null
)

@HiltViewModel
class UrlManagerViewModel @Inject constructor(
    private val blocklistRepository: BlocklistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UrlManagerUiState())
    val uiState: StateFlow<UrlManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            blocklistRepository.getUrls().collect { urls ->
                _uiState.value = _uiState.value.copy(urls = urls)
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text, errorMessageRes = null)
    }

    fun addUrl() {
        val input = _uiState.value.inputText.trim()

        if (input.isEmpty()) return

        // Strip protocol prefix if user entered it
        val cleaned = input
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')

        if (!isValidDomain(cleaned)) {
            _uiState.value = _uiState.value.copy(
                errorMessageRes = R.string.invalid_domain_format
            )
            return
        }

        viewModelScope.launch {
            blocklistRepository.addUrl(cleaned)
            _uiState.value = _uiState.value.copy(inputText = "", errorMessageRes = null)
        }
    }

    fun removeUrl(item: BlockedItem) {
        viewModelScope.launch {
            blocklistRepository.removeItem(item)
        }
    }
}
