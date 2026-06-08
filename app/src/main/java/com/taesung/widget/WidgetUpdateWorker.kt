package com.taesung.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 백그라운드에서 이번 달 일정을 가져와 달력 비트맵으로 그리고 위젯에 반영한다.
 */
class WidgetUpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        var needLogin = !Net.isLoggedIn(ctx)
        val bmp: Bitmap = try {
            if (needLogin) {
                CalendarRenderer.message("로그인이 필요합니다.\n위젯을 눌러 로그인하세요.")
            } else {
                val data = Net.fetchMonthEvents(ctx)
                if (data == null) {
                    needLogin = true
                    CalendarRenderer.message("세션이 만료됐어요.\n위젯을 눌러 다시 로그인하세요.")
                } else {
                    CalendarRenderer.calendar(data)
                }
            }
        } catch (e: Exception) {
            CalendarRenderer.message("네트워크 오류\n새로고침(↻)을 눌러주세요.")
        }
        TodayScheduleWidget.updateAll(ctx, bmp, loggedIn = !needLogin)
        return Result.success()
    }
}
