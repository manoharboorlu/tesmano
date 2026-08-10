package com.matedroid.ui.screens.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.domain.RecurringRoute
import com.matedroid.domain.RecurringRoutesSnapshot
import com.matedroid.domain.RouteTagsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecurringRoutesUiState(
    val loading: Boolean = true,
    val snapshot: RecurringRoutesSnapshot? = null,
    val selectedKey: String? = null
) {
    val selected: RecurringRoute? get() = snapshot?.routes?.firstOrNull { it.key == selectedKey } ?: snapshot?.routes?.firstOrNull()
}

@HiltViewModel
class RecurringRoutesViewModel @Inject constructor(
    private val repository: RouteTagsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecurringRoutesUiState())
    val uiState: StateFlow<RecurringRoutesUiState> = _uiState.asStateFlow()
    private var carId: Int? = null

    fun load(carId: Int) {
        if (this.carId == carId && !_uiState.value.loading) return
        this.carId = carId
        refresh()
    }

    fun select(key: String?) = _uiState.update { it.copy(selectedKey = key) }

    fun name(route: RecurringRoute, name: String?) = saveIntent(route, name, confirmed = true, dismissed = false)
    fun keepUnnamed(route: RecurringRoute) = saveIntent(route, null, confirmed = true, dismissed = false)
    fun dismiss(route: RecurringRoute) = saveIntent(route, route.metadata?.name, confirmed = false, dismissed = true)

    private fun saveIntent(route: RecurringRoute, name: String?, confirmed: Boolean, dismissed: Boolean) = viewModelScope.launch {
        repository.saveRouteIntent(route, name, confirmed, dismissed)
        refresh()
    }

    private fun refresh() {
        val id = carId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            val snapshot = repository.recurringRoutes(id)
            _uiState.update { previous -> previous.copy(loading = false, snapshot = snapshot, selectedKey = previous.selectedKey ?: snapshot.routes.firstOrNull()?.key) }
        }
    }
}
