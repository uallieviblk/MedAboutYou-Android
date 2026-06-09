package com.uallsi.medaboutyou.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.uallsi.medaboutyou.MainActivity
import com.uallsi.medaboutyou.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Home-screen widget listing today's dose schedule. */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { bind(context, manager, it) }
    }

    companion object {
        /** Rebuild + reload every placed widget (call after dose/schedule changes). */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { bind(context, manager, it) }
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }

        private fun bind(context: Context, manager: AppWidgetManager, id: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_today)

            // Localized date subtitle (no DB access — safe on the main thread).
            val date = LocalDate.now().format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()),
            )
            views.setTextViewText(R.id.widget_date, date)

            val service = Intent(context, TodayWidgetService::class.java).apply {
                // Make the intent unique per widget id so the adapter isn't reused stale.
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, service)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Tapping the widget (title or any row) opens the app on Today.
            val open = Intent(context, MainActivity::class.java).apply {
                putExtra("open", "today")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_title, pi)
            views.setPendingIntentTemplate(R.id.widget_list, pi)

            manager.updateAppWidget(id, views)
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }
}
