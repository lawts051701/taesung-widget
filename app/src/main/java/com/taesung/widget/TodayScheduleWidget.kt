package com.taesung.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.RemoteViews
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 월 일정 달력 위젯. 실제 갱신(네트워크+그리기)은 WidgetUpdateWorker가 수행하고,
 * 결과 비트맵을 updateAll()로 위젯에 반영한다.
 */
class TodayScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        // 기존 비트맵은 그대로 두고 백그라운드 갱신만(주기 갱신 시 깜빡임 방지)
        triggerRefresh(ctx)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_REFRESH) triggerRefresh(ctx)
    }

    companion object {
        const val ACTION_REFRESH = "com.taesung.widget.REFRESH"

        fun triggerRefresh(ctx: Context) {
            WorkManager.getInstance(ctx).enqueue(
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            )
        }

        /** 워커가 호출 — 비트맵으로 모든 위젯 인스턴스를 갱신. */
        fun updateAll(ctx: Context, bmp: Bitmap, loggedIn: Boolean) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, TodayScheduleWidget::class.java))
            if (ids.isEmpty()) return

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val refresh = Intent(ctx, TodayScheduleWidget::class.java).apply { action = ACTION_REFRESH }
            val refreshPi = PendingIntent.getBroadcast(ctx, 0, refresh, flags)

            // 탭: 로그인 상태면 웹 캘린더(/events) 열기, 아니면 로그인 화면
            val tapPi = if (loggedIn) {
                val view = Intent(Intent.ACTION_VIEW, Uri.parse("${Net.BASE_URL}/events")).apply {
                    setPackage(ctx.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PendingIntent.getActivity(ctx, 1, view, flags)
            } else {
                PendingIntent.getActivity(
                    ctx, 1, Intent(ctx, WidgetConfigActivity::class.java), flags
                )
            }

            for (id in ids) {
                val views = RemoteViews(ctx.packageName, R.layout.widget_today)
                views.setImageViewBitmap(R.id.widget_cal, bmp)
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshPi)
                views.setOnClickPendingIntent(R.id.widget_cal, tapPi)
                mgr.updateAppWidget(id, views)
            }
        }
    }
}
