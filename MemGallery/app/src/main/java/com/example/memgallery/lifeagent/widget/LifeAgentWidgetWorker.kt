package com.example.memgallery.lifeagent.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Keeps a placed widget's "today" honest when time passes and nothing else happens. No network. */
@HiltWorker
class LifeAgentWidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { LifeAgentWidget().updateAll(applicationContext) }
        return Result.success()
    }
}
