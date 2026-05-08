package com.example.memgallery.ui.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetRefreshManager {
    fun refreshWidget(context: Context) {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.memgallery.worker.WidgetUpdateWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
    }
}
