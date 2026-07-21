package com.taesung.widget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * 이번 달 일정을 가져와 달력 비트맵으로 그려 위젯에 반영.
 * - 네트워크/HTTP 오류: 마지막 정상 데이터(캐시)를 그대로 보여주고 재시도(에러 깜빡임 방지)
 * - 401: 재로그인 안내
 */
class WidgetUpdateWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext

        if (!Net.isLoggedIn(ctx)) {
            TodayScheduleWidget.updateMessage(
                ctx, "로그인이 필요합니다.\n위젯을 눌러 로그인하세요.", loggedIn = false
            )
            return Result.success()
        }

        FcmService.registerCurrentToken(ctx)

        val data = try {
            Net.fetchMonthEvents(ctx)  // null = 401(세션 만료)
        } catch (e: Exception) {
            // 네트워크/HTTP 오류 → 캐시 표시(없으면 안내) + 자동 재시도
            val cached = Net.loadCachedMonth(ctx)
            if (cached != null) {
                TodayScheduleWidget.updateAll(ctx, cached, loggedIn = true)
            } else {
                TodayScheduleWidget.updateMessage(
                    ctx, "연결 대기 중…\n잠시 후 자동으로 갱신됩니다.", loggedIn = true
                )
            }
            return Result.retry()
        }

        if (data == null) {
            TodayScheduleWidget.updateMessage(
                ctx, "세션이 만료됐어요.\n위젯을 눌러 다시 로그인하세요.", loggedIn = false
            )
            return Result.success()
        }

        Net.cacheMonth(ctx, data)
        TodayScheduleWidget.updateAll(ctx, data, loggedIn = true)
        return Result.success()
    }
}
