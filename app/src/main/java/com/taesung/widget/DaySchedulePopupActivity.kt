package com.taesung.widget

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

/** 위젯 날짜 셀을 탭했을 때 해당 날짜의 일정을 보여주는 작은 팝업. */
class DaySchedulePopupActivity : AppCompatActivity() {
    private var selectedDate = ""
    private lateinit var events: MutableList<PopupEvent>
    private lateinit var countText: TextView
    private lateinit var listView: LinearLayout
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_day_schedule)
        setFinishOnTouchOutside(true)
        styleDialog()
        expandWindow()

        selectedDate = intent.getStringExtra("date") ?: ""
        events = parseEvents(intent.getStringExtra("events") ?: "[]").toMutableList()
        countText = findViewById(R.id.ds_count)
        listView = findViewById(R.id.ds_list)
        statusText = findViewById(R.id.ds_nl_status)

        findViewById<TextView>(R.id.ds_title).text = dateLabel(selectedDate)
        renderEvents()

        val nlInput = findViewById<EditText>(R.id.ds_nl_input)
        val nlAdd = findViewById<Button>(R.id.ds_nl_add)
        val nlStatus = statusText
        nlAdd.setOnClickListener {
            val text = nlInput.text?.toString()?.trim().orEmpty()
            if (text.length < 2) {
                nlStatus.text = "일정을 한 문장으로 입력하세요."
                return@setOnClickListener
            }
            nlAdd.isEnabled = false
            nlStatus.text = "등록 중..."
            Thread {
                try {
                    val created = Net.createNaturalEvent(applicationContext, text, selectedDate)
                    runOnUiThread {
                        nlAdd.isEnabled = true
                        if (created == -2) {
                            nlStatus.text = "세션이 만료됐어요. 다시 로그인하세요."
                            Toast.makeText(this@DaySchedulePopupActivity, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                        } else {
                            nlInput.setText("")
                            nlStatus.text = "일정 ${created}건 등록 완료"
                            Toast.makeText(this@DaySchedulePopupActivity, "일정 등록 완료", Toast.LENGTH_SHORT).show()
                            TodayScheduleWidget.triggerRefresh(applicationContext)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        nlAdd.isEnabled = true
                        nlStatus.text = "등록 실패: ${e.message ?: "네트워크 오류"}"
                    }
                }
            }.start()
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

    private fun styleDialog() {
        val dark = ThemePrefs.isDark(this)
        val bg = if (dark) 0xFF111827.toInt() else 0xFFFFFFFF.toInt()
        val text = if (dark) 0xFFF9FAFB.toInt() else 0xFF111827.toInt()
        val sub = if (dark) 0xFFD1D5DB.toInt() else 0xFF6B7280.toInt()
        val inputFill = if (dark) 0xFF1F2937.toInt() else 0xFFFFFFFF.toInt()
        val inputStroke = if (dark) 0xFF4B5563.toInt() else 0xFFD1D5DB.toInt()
        val primary = if (dark) 0xFFA5B4FC.toInt() else 0xFF4F46E5.toInt()

        window?.setBackgroundDrawable(ColorDrawable(bg))
        findViewById<LinearLayout>(R.id.ds_root).setBackgroundColor(bg)
        findViewById<TextView>(R.id.ds_title).setTextColor(text)
        findViewById<TextView>(R.id.ds_count).setTextColor(sub)
        findViewById<TextView>(R.id.ds_nl_status).setTextColor(sub)

        findViewById<EditText>(R.id.ds_nl_input).apply {
            setTextColor(text)
            setHintTextColor(if (dark) 0xFF9CA3AF.toInt() else 0xFF9CA3AF.toInt())
            background = rounded(inputFill, inputStroke, 4)
        }

        findViewById<Button>(R.id.ds_nl_add).apply {
            setTextColor(primary)
            background = rounded(
                if (dark) 0xFF312E81.toInt() else 0xFFEEF2FF.toInt(),
                if (dark) 0xFF6366F1.toInt() else 0xFFC7D2FE.toInt(),
                5,
            )
        }
        findViewById<Button>(R.id.ds_close).setTextColor(sub)
        findViewById<Button>(R.id.ds_go).setTextColor(primary)
    }

    private fun expandWindow() {
        val dm = resources.displayMetrics
        val width = (dm.widthPixels * 0.96f).toInt()
        val height = (dm.heightPixels * 0.88f).toInt()
        window?.setLayout(width, height)
    }

    private fun renderEvents() {
        countText.text = if (events.isEmpty()) "등록된 일정이 없습니다." else "일정 ${events.size}건"
        listView.removeAllViews()
        if (events.isEmpty()) {
            listView.addView(emptyText())
        } else {
            events.forEach { listView.addView(eventRow(it)) }
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
                            id = if (o.has("id") && !o.isNull("id")) o.optInt("id") else null,
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
        val dark = ThemePrefs.isDark(this)
        val label = eventLabel(ev)
        val accent = parseColorOr(ev.color, 0xFF4F46E5.toInt())
        val meta = buildList {
            if (!ev.attendees.isNullOrBlank()) add("참석자: ${ev.attendees}")
            if (!ev.location.isNullOrBlank()) add("장소: ${ev.location}")
        }.joinToString(" · ")

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = GradientDrawable().apply {
                setColor(if (dark) 0xFF1F2937.toInt() else 0xFFF8FAFC.toInt())
                setStroke(dp(1), if (dark) 0xFF374151.toInt() else 0xFFE5E7EB.toInt())
                cornerRadius = dp(9).toFloat()
            }
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
                            setTextColor(if (dark) 0xFFF9FAFB.toInt() else 0xFF111827.toInt())
                            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                        }
                    )

                    if (meta.isNotBlank()) {
                        addView(
                            TextView(this@DaySchedulePopupActivity).apply {
                                text = meta
                                textSize = 13f
                                setTextColor(if (dark) 0xFFD1D5DB.toInt() else 0xFF6B7280.toInt())
                                setPadding(0, dp(3), 0, 0)
                                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                            }
                        )
                    }
                }
            )

            if (ev.id != null) {
                addView(
                    TextView(this@DaySchedulePopupActivity).apply {
                        text = "삭제"
                        textSize = 13f
                        setTextColor(if (dark) 0xFFFCA5A5.toInt() else 0xFFDC2626.toInt())
                        setPadding(dp(10), dp(2), 0, 0)
                        setOnClickListener { confirmDelete(ev) }
                    }
                )
            }
        }
    }

    private fun confirmDelete(ev: PopupEvent) {
        if (ev.id == null) return
        val dialog = AlertDialog.Builder(this)
            .setTitle("일정 삭제")
            .setMessage("${eventLabel(ev)}\n\n이 일정을 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제") { _, _ -> deleteEvent(ev) }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFDC2626.toInt())
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            if (ThemePrefs.isDark(this)) 0xFFD1D5DB.toInt() else 0xFF6B7280.toInt()
        )
    }

    private fun deleteEvent(ev: PopupEvent) {
        val id = ev.id ?: return
        statusText.text = "삭제 중..."
        Thread {
            try {
                val result = Net.deleteEvent(applicationContext, id)
                runOnUiThread {
                    if (result == -2) {
                        statusText.text = "세션이 만료됐어요. 다시 로그인하세요."
                        Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        events.removeAll { it.id == id }
                        renderEvents()
                        statusText.text = "삭제 완료"
                        Toast.makeText(this, "일정 삭제 완료", Toast.LENGTH_SHORT).show()
                        TodayScheduleWidget.triggerRefresh(applicationContext)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "삭제 실패: ${e.message ?: "네트워크 오류"}"
                }
            }
        }.start()
    }

    private fun eventLabel(ev: PopupEvent): String =
        if (ev.time.isNullOrBlank()) ev.text else "${ev.time}  ${ev.text}"

    private fun emptyText(): View =
        TextView(this).apply {
            text = "이 날짜에는 표시할 일정이 없습니다."
            textSize = 15f
            setTextColor(if (ThemePrefs.isDark(this@DaySchedulePopupActivity)) 0xFFD1D5DB.toInt() else 0xFF6B7280.toInt())
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

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private data class PopupEvent(
        val id: Int?,
        val text: String,
        val time: String?,
        val color: String?,
        val attendees: String?,
        val location: String?,
    )
}
