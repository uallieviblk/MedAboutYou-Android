// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.uallsi.medaboutyou.AppContainer
import com.uallsi.medaboutyou.R
import com.uallsi.medaboutyou.domain.Now
import com.uallsi.medaboutyou.model.ScheduleEngine
import kotlinx.coroutines.runBlocking

/** Supplies today's dose rows to the [TodayWidgetProvider] ListView. */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TodayRemoteViewsFactory(applicationContext)
}

private class TodayRemoteViewsFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private data class Row(val time: String, val name: String, val status: String, val statusColor: Int)

    private var rows: List<Row> = emptyList()

    override fun onCreate() {}
    override fun onDestroy() { rows = emptyList() }
    override fun getCount() = rows.size
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = true
    override fun getLoadingView(): RemoteViews? = null

    override fun onDataSetChanged() {
        // Runs on a binder thread, so blocking on the DB read is fine.
        rows = runBlocking {
            val container = AppContainer(context)
            val now = Now.local()
            container.schedules.snapshot()
                .occurrencesOn(now.year, now.month, now.day)
                .map { occ ->
                    val past = ScheduleEngine.isPastDateTime(occ.year, occ.month, occ.day, occ.hour, occ.minute)
                    val (resId, color) = when {
                        occ.status == "taken" -> R.string.agenda_status_taken to context.getColor(R.color.widget_accent)
                        past -> R.string.agenda_status_missed to MISSED_COLOR
                        else -> R.string.agenda_status_upcoming to context.getColor(R.color.widget_text_dim)
                    }
                    Row(occ.timeLabel(), occ.medName, context.getString(resId), color)
                }
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows[position]
        return RemoteViews(context.packageName, R.layout.widget_item).apply {
            setTextViewText(R.id.item_time, row.time)
            setTextViewText(R.id.item_name, row.name)
            setTextViewText(R.id.item_status, row.status)
            setTextColor(R.id.item_status, row.statusColor)
            setOnClickFillInIntent(R.id.item_root, Intent())  // taps bubble to the template
        }
    }

    private companion object {
        const val MISSED_COLOR = 0xFFC5221F.toInt()   // red, readable in light + dark
    }
}
