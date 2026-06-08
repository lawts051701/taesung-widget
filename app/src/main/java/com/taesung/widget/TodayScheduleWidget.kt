package com.taesung.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 오늘 일정 위젯. 표시는 SharedPreferences 에 캐시된 텍스트를 그리고,
 * 실제 갱신(네트워크)은 WidgetUpdateWorker 가 백그라운드에서 수행.
 */
class TodayScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // 먼저 캐시로 즉시 렌더 후, 백그라운드 갱신 트리거
        ids.forEach { render(ctx, mgr, it) }
        triggerRefresh(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) {
            triggerRefresh(ctx)
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, TodayScheduleWidget::class.java))
            ids.forEach { render(ctx, mgr, it) }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.taesung.widget.REFRESH"
        private const val PREF = "taesung_widget"
        private const val KEY_TEXT = "widget_text"
        private const val KEY_UPDATED = "widget_updated"

        fun cache(ctx: Context, text: String) {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putString(KEY_TEXT, text)
                .putLong(KEY_UPDATED, System.currentTimeMillis())
                .apply()
        }

        fun triggerRefresh(ctx: Context) {
            WorkManager.getInstance(ctx).enqueue(
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            )
        }

        fun renderAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(ctx, TodayScheduleWidget::class.java))
            ids.forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val text = p.getString(KEY_TEXT, "불러오는 중...") ?: ""
            val views = RemoteViews(ctx.packageName, R.layout.widget_today)
            views.setTextViewText(R.id.widget_body, if (text.isBlank()) "오늘 일정이 없습니다." else text)

            // 새로고침 버튼 → ACTION_REFRESH 브로드캐스트
            val refresh = Intent(ctx, TodayScheduleWidget::class.java).apply { action = ACTION_REFRESH }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            views.setOnClickPendingIntent(R.id.widget_refresh,
                PendingIntent.getBroadcast(ctx, 0, refresh, flags))

            // 본문 탭 → 설정(로그인) 화면 열기
            val cfg = Intent(ctx, WidgetConfigActivity::class.java)
            views.setOnClickPendingIntent(R.id.widget_body,
                PendingIntent.getActivity(ctx, 1, cfg, flags))

            mgr.updateAppWidget(id, views)
        }
    }
}
