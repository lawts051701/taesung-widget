package com.taesung.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 월 일정 달력 위젯. 실제 갱신(네트워크)은 WidgetUpdateWorker가 수행하고,
 * 결과 월 데이터를 RemoteViews 그리드로 반영한다.
 */
class TodayScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // 기존 표시 상태는 그대로 두고 백그라운드 갱신만 수행(주기 갱신 시 깜빡임 방지)
        triggerRefresh(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) triggerRefresh(ctx)
    }

    override fun onAppWidgetOptionsChanged(
        ctx: Context,
        mgr: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(ctx, mgr, appWidgetId, newOptions)
        val cached = Net.loadCachedMonth(ctx)
        if (cached != null && Net.isLoggedIn(ctx)) updateAll(ctx, cached, loggedIn = true)
        else triggerRefresh(ctx)
    }

    companion object {
        const val ACTION_REFRESH = "com.taesung.widget.REFRESH"
        private const val MAX_ROWS = 6
        private const val DAYS_IN_WEEK = 7
        private val DEFAULT_ACCENT = 0xFF2563EB.toInt()

        private data class WidgetPalette(
            val rootBg: Int,
            val cellBg: Int,
            val emptyBg: Int,
            val todayBg: Int,
            val text: Int,
            val todayText: Int,
            val subText: Int,
            val messageText: Int,
            val sundayText: Int,
            val saturdayText: Int,
            val chipText: Int,
            val chipBlendTarget: Int,
            val chipBlendWeight: Float,
        )

        fun triggerRefresh(ctx: Context) {
            val req = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }

        /** 워커가 호출 — 안내 문구로 모든 위젯 인스턴스를 갱신. */
        fun updateMessage(ctx: Context, message: String, loggedIn: Boolean) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayScheduleWidget::class.java))
            if (ids.isEmpty()) return

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val refresh = Intent(ctx, TodayScheduleWidget::class.java).apply { action = ACTION_REFRESH }
            val refreshPi = PendingIntent.getBroadcast(ctx, 0, refresh, flags)
            val tapPi = if (loggedIn) openEventsPi(ctx, flags) else configPi(ctx, flags)

            for (id in ids) {
                val palette = widgetPalette(ctx)
                val views = RemoteViews(ctx.packageName, R.layout.widget_today)
                applyPalette(views, palette)
                views.setViewVisibility(R.id.widget_calendar_content, View.GONE)
                views.setViewVisibility(R.id.widget_message, View.VISIBLE)
                views.setTextViewText(R.id.widget_message, message)
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
                views.setOnClickPendingIntent(R.id.widget_message, tapPi)
                mgr.updateAppWidget(id, views)
            }
        }

        /** 워커가 호출 — 월 일정 데이터로 모든 위젯 인스턴스를 갱신. */
        fun updateAll(ctx: Context, data: MonthData, loggedIn: Boolean) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayScheduleWidget::class.java))
            if (ids.isEmpty()) return

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val refresh = Intent(ctx, TodayScheduleWidget::class.java).apply { action = ACTION_REFRESH }
            val refreshPi = PendingIntent.getBroadcast(ctx, 0, refresh, flags)

            val cal = Calendar.getInstance().apply {
                clear()
                set(data.year, data.month - 1, 1)
            }
            val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

            for (id in ids) {
                val palette = widgetPalette(ctx)
                val maxEvents = maxEventsForWidget(mgr, id)
                val views = RemoteViews(ctx.packageName, R.layout.widget_today)
                applyPalette(views, palette)
                views.setViewVisibility(R.id.widget_calendar_content, View.VISIBLE)
                views.setViewVisibility(R.id.widget_message, View.GONE)
                views.setTextViewText(R.id.widget_month, "%d.%02d".format(data.year, data.month))
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
                views.removeAllViews(R.id.widget_days_container)

                for (row in 0 until MAX_ROWS) {
                    val rowView = RemoteViews(ctx.packageName, R.layout.widget_calendar_row)
                    for (col in 0 until DAYS_IN_WEEK) {
                        val cellIndex = row * DAYS_IN_WEEK + col
                        val day = cellIndex - firstWeekday + 1
                        val cell = RemoteViews(ctx.packageName, R.layout.widget_day_cell)
                        if (day in 1..daysInMonth) {
                            val chips = data.byDay[day].orEmpty()
                            bindDayCell(cell, palette, data, day, col, chips, maxEvents)
                            cell.setOnClickPendingIntent(
                                R.id.day_cell_root,
                                dayPopupPi(ctx, data, day, chips, flags)
                            )
                        } else {
                            bindEmptyCell(cell, palette)
                        }
                        rowView.addView(R.id.widget_row_container, cell)
                    }
                    views.addView(R.id.widget_days_container, rowView)
                }
                mgr.updateAppWidget(id, views)
            }
        }

        private fun bindDayCell(
            cell: RemoteViews,
            palette: WidgetPalette,
            data: MonthData,
            day: Int,
            col: Int,
            chips: List<EvtChip>,
            maxEvents: Int,
        ) {
            val isToday = day == data.today
            cell.setInt(R.id.day_cell_root, "setBackgroundColor", if (isToday) palette.todayBg else palette.cellBg)
            cell.setTextViewText(R.id.day_num, day.toString())
            cell.setTextColor(
                R.id.day_num,
                when {
                    isToday -> palette.todayText
                    col == 0 -> palette.sundayText
                    col == 6 -> palette.saturdayText
                    else -> palette.text
                }
            )

            val eventViews = intArrayOf(
                R.id.day_event_1,
                R.id.day_event_2,
                R.id.day_event_3,
                R.id.day_event_4,
                R.id.day_event_5,
            )
            val showLimit = maxEvents.coerceIn(1, eventViews.size)
            eventViews.forEachIndexed { idx, viewId ->
                val chip = if (idx < showLimit) chips.getOrNull(idx) else null
                if (chip == null) {
                    cell.setViewVisibility(viewId, View.GONE)
                } else {
                    cell.setViewVisibility(viewId, View.VISIBLE)
                    cell.setTextViewText(viewId, chip.display())
                    cell.setTextColor(viewId, palette.chipText)
                    cell.setInt(viewId, "setBackgroundColor", chipBg(chip.color, palette))
                }
            }
            if (chips.size > showLimit) {
                cell.setViewVisibility(R.id.day_more, View.VISIBLE)
                cell.setTextViewText(R.id.day_more, "+${chips.size - showLimit}개 더")
                cell.setTextColor(R.id.day_more, palette.subText)
            } else {
                cell.setViewVisibility(R.id.day_more, View.GONE)
            }
        }

        private fun bindEmptyCell(cell: RemoteViews, palette: WidgetPalette) {
            cell.setInt(R.id.day_cell_root, "setBackgroundColor", palette.emptyBg)
            cell.setTextViewText(R.id.day_num, "")
            cell.setViewVisibility(R.id.day_event_1, View.GONE)
            cell.setViewVisibility(R.id.day_event_2, View.GONE)
            cell.setViewVisibility(R.id.day_event_3, View.GONE)
            cell.setViewVisibility(R.id.day_event_4, View.GONE)
            cell.setViewVisibility(R.id.day_event_5, View.GONE)
            cell.setViewVisibility(R.id.day_more, View.GONE)
        }

        private fun maxEventsForWidget(mgr: AppWidgetManager, id: Int): Int {
            val options = mgr.getAppWidgetOptions(id)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 460)
            return when {
                minHeight >= 620 -> 5
                minHeight >= 520 -> 4
                minHeight >= 360 -> 3
                minHeight >= 280 -> 2
                else -> 1
            }
        }

        fun redrawFromCacheOrRefresh(ctx: Context) {
            val cached = Net.loadCachedMonth(ctx)
            if (cached != null && Net.isLoggedIn(ctx)) updateAll(ctx, cached, loggedIn = true)
            else triggerRefresh(ctx)
        }

        private fun dayPopupPi(
            ctx: Context,
            data: MonthData,
            day: Int,
            chips: List<EvtChip>,
            flags: Int,
        ): PendingIntent {
            val date = "%d-%02d-%02d".format(data.year, data.month, day)
            val intent = Intent(ctx, DaySchedulePopupActivity::class.java).apply {
                putExtra("date", date)
                putExtra("events", chipsToJson(chips))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val requestCode = data.year * 10000 + data.month * 100 + day
            return PendingIntent.getActivity(ctx, requestCode, intent, flags)
        }

        private fun openEventsPi(ctx: Context, flags: Int): PendingIntent {
            val view = Intent(Intent.ACTION_VIEW, Uri.parse("${Net.BASE_URL}/events")).apply {
                setPackage(ctx.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(ctx, 1, view, flags)
        }

        private fun configPi(ctx: Context, flags: Int): PendingIntent =
            PendingIntent.getActivity(ctx, 1, Intent(ctx, WidgetConfigActivity::class.java), flags)

        private fun chipsToJson(chips: List<EvtChip>): String {
            val arr = JSONArray()
            chips.forEach {
                arr.put(
                    JSONObject()
                        .put("text", it.text)
                        .put("id", it.id ?: JSONObject.NULL)
                        .put("color", it.color ?: "")
                        .put("time", it.time ?: "")
                        .put("attendees", it.attendees ?: "")
                        .put("location", it.location ?: "")
                )
            }
            return arr.toString()
        }

        private fun parseColorOr(hex: String?, def: Int): Int {
            if (hex.isNullOrBlank()) return def
            return try { Color.parseColor(hex.trim()) } catch (_: Exception) { def }
        }

        private fun widgetPalette(ctx: Context): WidgetPalette =
            if (ThemePrefs.isDark(ctx)) {
                WidgetPalette(
                    rootBg = 0xFF0F172A.toInt(),
                    cellBg = 0xFF111827.toInt(),
                    emptyBg = 0xFF0B1120.toInt(),
                    todayBg = 0xFF1E293B.toInt(),
                    text = 0xFFE5E7EB.toInt(),
                    todayText = 0xFFFFFFFF.toInt(),
                    subText = 0xFFAAB3C2.toInt(),
                    messageText = 0xFFE5E7EB.toInt(),
                    sundayText = 0xFFFCA5A5.toInt(),
                    saturdayText = 0xFF93C5FD.toInt(),
                    chipText = 0xFFF8FAFC.toInt(),
                    chipBlendTarget = 0xFF111827.toInt(),
                    chipBlendWeight = 0.56f,
                )
            } else {
                WidgetPalette(
                    rootBg = 0xFFFFFFFF.toInt(),
                    cellBg = 0xFFFFFFFF.toInt(),
                    emptyBg = 0xFFF8FAFC.toInt(),
                    todayBg = 0xFFEFF6FF.toInt(),
                    text = 0xFF111827.toInt(),
                    todayText = 0xFF1D4ED8.toInt(),
                    subText = 0xFF64748B.toInt(),
                    messageText = 0xFF334155.toInt(),
                    sundayText = 0xFFEF4444.toInt(),
                    saturdayText = 0xFF2563EB.toInt(),
                    chipText = 0xFF1F2937.toInt(),
                    chipBlendTarget = Color.WHITE,
                    chipBlendWeight = 0.84f,
                )
            }

        private fun applyPalette(views: RemoteViews, palette: WidgetPalette) {
            views.setInt(R.id.widget_root, "setBackgroundColor", palette.rootBg)
            views.setTextColor(R.id.widget_month, palette.text)
            views.setTextColor(R.id.widget_message, palette.messageText)
            views.setTextColor(R.id.widget_refresh, palette.subText)
        }

        private fun chipBg(hex: String?, palette: WidgetPalette): Int =
            blendColor(parseColorOr(hex, DEFAULT_ACCENT), palette.chipBlendTarget, palette.chipBlendWeight)

        private fun blendColor(color: Int, target: Int, targetWeight: Float): Int {
            val w = targetWeight.coerceIn(0f, 1f)
            val base = 1f - w
            return Color.argb(
                (Color.alpha(color) * base + Color.alpha(target) * w).toInt(),
                (Color.red(color) * base + Color.red(target) * w).toInt(),
                (Color.green(color) * base + Color.green(target) * w).toInt(),
                (Color.blue(color) * base + Color.blue(target) * w).toInt(),
            )
        }

        private fun EvtChip.display(): String =
            if (time.isNullOrBlank()) text else "$time $text"
    }
}
