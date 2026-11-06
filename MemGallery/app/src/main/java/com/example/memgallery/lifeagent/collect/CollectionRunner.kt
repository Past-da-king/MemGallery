package com.example.memgallery.lifeagent.collect

import android.util.Log
import com.example.memgallery.lifeagent.data.CollectionResult
import javax.inject.Inject
import javax.inject.Singleton

/** Phase 1 stub: the real pull, filter and propose pipeline lands in the next commits. */
@Singleton
class CollectionRunner @Inject constructor() {
    suspend fun run(): CollectionResult {
        Log.i("LifeAgent", "collection stub")
        return CollectionResult("stub", "skipped", 0, 0, emptyList(), null)
    }
}
