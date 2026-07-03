package com.taesung.widget

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

/** 위젯 날짜 셀을 탭했을 때 해당 날짜의 일정을 보여주는 작은 팝업. */
class DaySchedulePopupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_day_schedule)
        setFinishOnTouchOutside(true)

        val date = intent.getStringExtra("date") ?: ""
        val events = parseEvents(intent.getStringExtra("events") ?: "[]")

        findViewById<TextView>(R.id.ds_title).text = dateLabel(date)
        findViewById<TextView>(R.id.ds_count).text =
            if (events.isEmpty()) "등록된 일정이 없습니다." else "일정 ${events.size}건"

        val list = findViewById<LinearLayout>(R.id.ds_list)
        list.removeAllViews()
        if (events.isEmpty()) {
            list.addView(emptyText())
        } else {
            events.forEach { list.addView(eventRow(it)) }
        }

        findViewById<Button>(R.id.ds_close).setOnClickListener { finish() }
        findViewById<Button>(R.id.ds_go).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("${Net.BASE_URL}/events")).apply {
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) { /* 무시 */ }
            finish()
        }
    }

    private fun parseEvents(raw: String): List<PopupEvent> {
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PopupEvent(
                            text = o.optString("text"),
                            time = o.optString("time").ifBlank { null },
                            color = o.optString("color").ifBlank { null },
                            attendees = o.optString("attendees").ifBlank { null },
                            location = o.optString("location").ifBlank { null },
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun eventRow(ev: PopupEvent): View {
        val label = if (ev.time.isNullOrBlank()) ev.text else "${ev.time}  ${ev.text}"
        val accent = parseColorOr(ev.color, 0xFF4F46E5.toInt())
        val meta = buildList {
            if (!ev.attendees.isNullOrBlank()) add("참석자: ${ev.attendees}")
            if (!ev.location.isNullOrBlank()) add("장소: ${ev.location}")
        }.joinToString(" · ")

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                setColor(0xFFF8FAFC.toInt())
                setStroke(dp(1), 0xFFE5E7EB.toInt())
                cornerRadius = dp(9).toFloat()
            }
            compoundDrawablePadding = dp(6)
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }

            addView(
                TextView(this@DaySchedulePopupActivity).apply {
                    text = "●"
                    textSize = 15f
                    setTextColor(accent)
                    setPadding(0, dp(1), dp(8), 0)
                    setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                }
            )

            addView(
                LinearLayout(this@DaySchedulePopupActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f,
                    )

                    addView(
                        TextView(this@DaySchedulePopupActivity).apply {
                            text = label
                            textSize = 16f
                            setTextColor(0xFF111827.toInt())
                            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                        }
                    )

                    if (meta.isNotBlank()) {
                        addView(
                            TextView(this@DaySchedulePopupActivity).apply {
                                text = meta
                                textSize = 13f
                                setTextColor(0xFF6B7280.toInt())
                                setPadding(0, dp(3), 0, 0)
                                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun emptyText(): View =
        TextView(this).apply {
            text = "이 날짜에는 표시할 일정이 없습니다."
            textSize = 15f
            setTextColor(0xFF6B7280.toInt())
            setPadding(dp(4), dp(18), dp(4), dp(18))
        }

    private fun dateLabel(date: String): String {
        val parts = date.split("-")
        return if (parts.size == 3) {
            "${parts[0]}년 ${parts[1].toIntOrNull() ?: parts[1]}월 ${parts[2].toIntOrNull() ?: parts[2]}일"
        } else {
            date.ifBlank { "선택한 날짜" }
        }
    }

    private fun parseColorOr(hex: String?, def: Int): Int {
        if (hex.isNullOrBlank()) return def
        return try { Color.parseColor(hex.trim()) } catch (_: Exception) { def }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private data class PopupEvent(
        val text: String,
        val time: String?,
        val color: String?,
        val attendees: String?,
        val location: String?,
    )
}
