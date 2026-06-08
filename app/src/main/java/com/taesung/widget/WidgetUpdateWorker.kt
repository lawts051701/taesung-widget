package com.taesung.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 백그라운드에서 오늘 일정을 가져와 위젯 캐시에 저장하고 다시 그린다.
 * onUpdate / 새로고침 탭 / WorkManager 주기 작업에서 트리거됨.
 */
class WidgetUpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val text: String = Net.todayWidgetText(ctx)
        TodayScheduleWidget.cache(ctx, text)
        TodayScheduleWidget.renderAll(ctx)
        return Result.success()
    }
}
