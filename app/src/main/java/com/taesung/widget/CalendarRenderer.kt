package com.taesung.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.util.Calendar

/** 일정 칩 한 개: 표시 텍스트 + 색상(hex, null이면 중립색) + 팝업용 상세 정보. */
data class EvtChip(
    val text: String,
    val color: String?,
    val time: String? = null,
    val attendees: String? = null,
    val location: String? = null,
)

/** 월 일정 데이터. */
data class MonthData(
    val year: Int,
    val month: Int,
    val today: Int,                       // 이번 달이 아니면 -1
    val byDay: Map<Int, List<EvtChip>>,
)

/** 월 달력을 비트맵으로 그린다 (다크 테마 + 색상 칩, 캘린더 앱 스타일). */
object CalendarRenderer {

    private const val W = 820
    private val BG = 0xFF1C1C1E.toInt()
    private val DEFAULT_CHIP = 0xFF3A3A3C.toInt()

    private fun parseColorOr(hex: String?, def: Int): Int {
        if (hex.isNullOrBlank()) return def
        return try { Color.parseColor(hex.trim()) } catch (e: Exception) { def }
    }

    fun calendar(data: MonthData): Bitmap {
        val cal = Calendar.getInstance().apply { clear(); set(data.year, data.month - 1, 1) }
        val firstWeekday = cal.get(Calendar.DAY_OF_WEEK) - 1  // 0=일
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val rows = Math.ceil((firstWeekday + daysInMonth) / 7.0).toInt().coerceAtLeast(5)

        val headerH = 64
        val weekHdrH = 42
        val cellH = 158
        val H = headerH + weekHdrH + rows * cellH + 6

        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(BG)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val cellW = W / 7f

        // 헤더(월)
        p.color = Color.WHITE; p.textSize = 38f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
        c.drawText("%d.%02d".format(data.year, data.month), W / 2f, 46f, p)
        p.isFakeBoldText = false

        // 요일
        val names = arrayOf("일", "월", "화", "수", "목", "금", "토")
        p.textSize = 22f
        for (i in 0..6) {
            p.color = when (i) {
                0 -> 0xFFFF6B6B.toInt()
                6 -> 0xFF6B9BFF.toInt()
                else -> 0xFF9A9A9E.toInt()
            }
            c.drawText(names[i], cellW * i + cellW / 2, (headerH + 28).toFloat(), p)
        }

        val gridTop = (headerH + weekHdrH).toFloat()
        val line = Paint().apply { color = 0xFF2E2E31.toInt(); strokeWidth = 1f }
        for (r in 0..rows) c.drawLine(0f, gridTop + r * cellH, W.toFloat(), gridTop + r * cellH, line)

        var day = 1
        for (r in 0 until rows) {
            for (col in 0..6) {
                val idx = r * 7 + col
                if (idx < firstWeekday || day > daysInMonth) continue
                val x = cellW * col
                val y = gridTop + r * cellH

                // 날짜 숫자 (오늘은 흰 원)
                p.textAlign = Paint.Align.CENTER; p.textSize = 24f
                val ncx = x + 22f
                val ncy = y + 28f
                if (day == data.today) {
                    p.color = Color.WHITE
                    c.drawCircle(ncx, ncy - 8f, 19f, p)
                    p.color = BG
                    c.drawText(day.toString(), ncx, ncy, p)
                } else {
                    p.color = when (col) {
                        0 -> 0xFFFF6B6B.toInt()
                        6 -> 0xFF6B9BFF.toInt()
                        else -> 0xFFE5E5E7.toInt()
                    }
                    c.drawText(day.toString(), ncx, ncy, p)
                }

                // 일정 칩 (일정에 지정한 색상 사용)
                val chips = data.byDay[day]
                if (!chips.isNullOrEmpty()) {
                    val chipH = 24f
                    val gap = 3f
                    val top0 = y + 42f
                    val maxChips = (((cellH - 44) / (chipH + gap)).toInt()).coerceIn(1, 4)
                    val show = chips.take(maxChips)
                    p.textAlign = Paint.Align.LEFT; p.textSize = 15f
                    show.forEachIndexed { ci, chip ->
                        val cy0 = top0 + ci * (chipH + gap)
                        p.color = parseColorOr(chip.color, DEFAULT_CHIP)
                        c.drawRoundRect(RectF(x + 3f, cy0, x + cellW - 3f, cy0 + chipH), 5f, 5f, p)
                        p.color = 0xFFF5F5F5.toInt()
                        c.drawText(ellipsize(chip.text, p, cellW - 10), x + 8f, cy0 + chipH - 7f, p)
                    }
                    if (chips.size > maxChips) {
                        p.color = 0xFF9A9A9E.toInt(); p.textSize = 14f; p.textAlign = Paint.Align.CENTER
                        c.drawText("+${chips.size - maxChips}", x + cellW / 2, top0 + maxChips * (chipH + gap) + 11f, p)
                    }
                }
                day++
            }
        }
        return bmp
    }

    /** 안내 메시지 비트맵(미로그인·오류 등). */
    fun message(msg: String): Bitmap {
        val H = 260
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565)
        val c = Canvas(bmp)
        c.drawColor(BG)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE5E5E7.toInt(); textSize = 28f; textAlign = Paint.Align.CENTER
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
