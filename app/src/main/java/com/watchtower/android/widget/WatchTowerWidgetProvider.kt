package com.watchtower.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.watchtower.android.MainActivity
import com.watchtower.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WatchTowerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun refreshAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WatchTowerWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
            widgetIds.forEach { widgetId ->
                updateWidget(context, appWidgetManager, widgetId)
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_watch_tower)
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val lastUpdated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

            views.setTextViewText(R.id.widgetTitle, context.getString(R.string.widget_title))
            views.setTextViewText(
                R.id.widgetSubtitle,
                context.getString(R.string.widget_placeholder_subtitle)
            )
            views.setTextViewText(
                R.id.widgetStatus,
                context.getString(R.string.widget_placeholder_status, lastUpdated)
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

