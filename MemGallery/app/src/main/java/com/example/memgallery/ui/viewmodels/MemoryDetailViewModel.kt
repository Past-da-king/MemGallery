package com.example.memgallery.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.memgallery.data.local.entity.MemoryEntity
import com.example.memgallery.data.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _memory = MutableStateFlow<MemoryEntity?>(null)
    val memory: StateFlow<MemoryEntity?> = _memory.asStateFlow()

    fun loadMemory(id: Int) {
        viewModelScope.launch {
            memoryRepository.getMemory(id).collect {
                _memory.value = it
            }
        }
    }
}
