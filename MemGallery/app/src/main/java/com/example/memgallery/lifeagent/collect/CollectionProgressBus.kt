package com.example.memgallery.lifeagent.collect

import com.example.memgallery.lifeagent.data.CollectionProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Live progress of the collection run, shared by the runner (writes) and the collecting screen (reads). */
@Singleton
class CollectionProgressBus @Inject constructor() {
    private val state = MutableStateFlow(CollectionProgress.IDLE)
    val progress: StateFlow<CollectionProgress> = state
    fun update(p: CollectionProgress) { state.value = p }
    fun reset() { state.value = CollectionProgress.IDLE }
}
