package com.taesung.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Calendar

/** 월 일정 데이터: 해당 월 + 일(day)별 일정 제목 목록. */
data class MonthData(
    val year: Int,
    val month: Int,        // 1~12
    val today: Int,        // 오늘 일자(이번 달이 아니면 -1)
    val byDay: Map<Int, List<String>>,
)

/** 월 달력을 비트맵으로 그린다(웹 캘린더 형식). */
object CalendarRenderer {

    private const val W = 600  // 폭(px). RGB_565 사용으로 메모리 절반.

    fun calendar(data: MonthData): Bitmap {
        val cal = Calendar.getInstance().apply {
            clear(); set(data.year, data.month - 1, 1)
        }
        val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1  // 0=일
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = Math.ceil((firstWeekday + daysInMonth) / 7.0).toInt().coerceAtLeast(5)

        val headerH = 74
        val weekHdrH = 40
        val cellH = 118
        val H = headerH + weekHdrH + rows * cellH + 8

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellW = W / 7f

        // 제목
        p.color = Color.parseColor("#1F2937"); p.textSize = 40f
        p.textAlign = Paint.Align.LEFT; p.isFakeBoldText = true
        c.drawText("${data.year}년 ${data.month}월", 18f, 50f, p)
        p.isFakeBoldText = false

        // 요일 헤더
        val names = arrayOf("일", "월", "화", "수", "목", "금", "토")
        p.textSize = 24f; p.textAlign = Paint.Align.CENTER
        for (i in 0..6) {
            p.color = when (i) {
                0 -> Color.parseColor("#DC2626")
                6 -> Color.parseColor("#2563EB")
                else -> Color.parseColor("#6B7280")
            }
            c.drawText(names[i], cellW * i + cellW / 2, headerH + 28f, p)
        }

        // 그리드 셀
        val gridTop = (headerH + weekHdrH).toFloat()
        var day = 1
        for (r in 0 until rows) {
            for (col in 0..6) {
                val idx = r * 7 + col
                if (idx < firstWeekday || day > daysInMonth) continue
                val x = cellW * col
                val y = gridTop + r * cellH

                // 오늘 강조
                if (day == data.today) {
                    p.color = Color.parseColor("#EEF2FF")
                    c.drawRect(x + 1.5f, y + 1.5f, x + cellW - 1.5f, y + cellH - 1.5f, p)
                }

                // 날짜 숫자
                p.textAlign = Paint.Align.LEFT; p.textSize = 26f
                p.color = when (col) {
                    0 -> Color.parseColor("#DC2626")
                    6 -> Color.parseColor("#2563EB")
                    else -> Color.parseColor("#374151")
                }
                p.isFakeBoldText = (day == data.today)
                c.drawText(day.toString(), x + 8f, y + 30f, p)
                p.isFakeBoldText = false

                // 일정 제목들
                val titles = data.byDay[day]
                if (!titles.isNullOrEmpty()) {
                    p.textSize = 18f
                    val maxLines = ((cellH - 38) / 22).coerceIn(1, 3)
                    val show = titles.take(maxLines)
                    show.forEachIndexed { li, t ->
                        p.color = Color.parseColor("#4B5563")
                        c.drawText(ellipsize(t, p, cellW - 12), x + 6f, y + 52f + li * 22, p)
                    }
                    if (titles.size > maxLines) {
                        p.color = Color.parseColor("#9CA3AF")
                        c.drawText("+${titles.size - maxLines}", x + 6f, y + 52f + maxLines * 22, p)
                    }
                }
                day++
            }
        }

        // 그리드 선
        val line = Paint().apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 1f }
        val gridBottom = gridTop + rows * cellH
        for (col in 0..7) c.drawLine(cellW * col, gridTop, cellW * col, gridBottom, line)
        for (r in 0..rows) c.drawLine(0f, gridTop + r * cellH, W.toFloat(), gridTop + r * cellH, line)

        return bmp
    }

    /** 안내 메시지 비트맵(미로그인·오류 등). */
    fun message(msg: String): Bitmap {
        val H = 260
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#374151"); textSize = 28f; textAlign = Paint.Align.CENTER
        }
        val lines = msg.split("\n")
        var y = H / 2f - (lines.size - 1) * 20
        for (ln in lines) { c.drawText(ln, W / 2f, y, p); y += 40 }
        return bmp
    }

    private fun ellipsize(s: String, p: Paint, maxW: Float): String {
        if (p.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }
}
