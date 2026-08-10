package com.matedroid.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matedroid.data.local.entity.SmartPlace
import com.matedroid.domain.SmartPlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartPlacesViewModel @Inject constructor(
    private val repository: SmartPlacesRepository
) : ViewModel() {
    val places = repository.observePlaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(place: SmartPlace) = viewModelScope.launch { repository.savePlace(place) }
    fun delete(place: SmartPlace) = viewModelScope.launch { repository.deletePlace(place) }
}
