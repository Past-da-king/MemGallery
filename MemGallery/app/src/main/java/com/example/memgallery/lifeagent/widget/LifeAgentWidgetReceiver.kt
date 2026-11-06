package com.example.memgallery.lifeagent.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The home screen entry point. Glance draws the widget itself (its own onUpdate), so nothing extra is queued here;
 * the layout picks 2x2, 4x2 or 4x4 from the size the launcher hands it.
 */
class LifeAgentWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeAgentWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        LifeAgentWidgetRefresh.enqueue(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        LifeAgentWidgetRefresh.enqueue(context)
    }
}
