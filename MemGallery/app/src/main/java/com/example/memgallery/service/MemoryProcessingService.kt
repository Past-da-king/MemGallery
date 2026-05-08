package com.example.memgallery.service

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryProcessingService @Inject constructor() {
    // This service is no longer responsible for processing memories.
    // The logic has been moved to MemoryProcessingWorker.
    // This service can be removed if it's not used for anything else.
}
