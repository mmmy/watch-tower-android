package com.watchtower.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.RemoteViews
import com.watchtower.android.MainActivity
import com.watchtower.android.R
import com.watchtower.android.SharedPreferencesConfigStorage
import com.watchtower.android.SignalSide
import com.watchtower.android.WatchTowerConfigStore
import com.watchtower.android.WatchTowerWidgetState
import com.watchtower.android.WidgetSignalSnapshotStore
import com.watchtower.android.toWidgetState
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
            val config = WatchTowerConfigStore(SharedPreferencesConfigStorage(context)).load()
            val snapshot = WidgetSignalSnapshotStore(context).load()
            val widgetState = config.toWidgetState(snapshot.alerts)
            val lastUpdated = if (snapshot.updatedAtMillis > 0L) {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snapshot.updatedAtMillis))
            } else {
                "尚未同步"
            }

            views.setTextViewText(R.id.widgetTitle, widgetState.titleText)
            views.setTextViewText(R.id.widgetSubtitle, widgetState.syncText(lastUpdated))
            views.setTextViewText(R.id.widgetStatus, widgetState.toFlowSpannable())
            views.setOnClickPendingIntent(R.id.widgetRoot, openAppPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun WatchTowerWidgetState.toFlowSpannable(): CharSequence {
            if (rows.isEmpty()) return flowText

            val builder = SpannableStringBuilder()
            rows.take(6).forEachIndexed { index, row ->
                if (index > 0) builder.append("  ")

                val periodStart = builder.length
                builder.append(row.periodText)
                builder.setSpan(
                    ForegroundColorSpan(if (row.unread) UNREAD_PERIOD_COLOR else DEFAULT_PERIOD_COLOR),
                    periodStart,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                builder.append(" ")

                val barsStart = builder.length
                builder.append(row.barsAgoText)
                builder.setSpan(
                    ForegroundColorSpan(row.side.barsColor),
                    barsStart,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                builder.setSpan(
                    RelativeSizeSpan(BARS_TEXT_SIZE_RATIO),
                    barsStart,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            return builder
        }

        private val SignalSide.barsColor: Int
            get() = when (this) {
                SignalSide.Bullish -> BULLISH_BARS_COLOR
                SignalSide.Bearish -> BEARISH_BARS_COLOR
            }

        private const val DEFAULT_PERIOD_COLOR = Color.WHITE
        private const val UNREAD_PERIOD_COLOR = 0xFFFFD54F.toInt()
        private const val BULLISH_BARS_COLOR = 0xFF34D399.toInt()
        private const val BEARISH_BARS_COLOR = 0xFFF87171.toInt()
        private const val BARS_TEXT_SIZE_RATIO = 0.82f
    }
}
