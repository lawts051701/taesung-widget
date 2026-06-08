package com.taesung.widget

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * 위젯 로그인 설정 화면. ERP 아이디/비밀번호로 로그인해 세션 쿠키를 저장한다.
 * 로그인 성공 시 30분 주기 갱신 작업을 등록하고 즉시 1회 갱신.
 */
class WidgetConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_config)

        val id = findViewById<EditText>(R.id.cfg_id)
        val pw = findViewById<EditText>(R.id.cfg_pw)
        val btn = findViewById<Button>(R.id.cfg_login)
        val status = findViewById<TextView>(R.id.cfg_status)

        if (Net.isLoggedIn(this)) status.text = "로그인됨 — 다시 로그인하려면 입력 후 버튼을 누르세요."

        btn.setOnClickListener {
            val u = id.text.toString().trim()
            val p = pw.text.toString()
            if (u.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "아이디/비밀번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            status.text = "로그인 중..."
            Thread {
                val ok = try { Net.login(this, u, p) } catch (e: Exception) { false }
                runOnUiThread {
                    if (ok) {
                        status.text = "로그인 성공! 위젯을 갱신합니다."
                        scheduleUpdates()
                        TodayScheduleWidget.triggerRefresh(this)
                        finish()
                    } else {
                        status.text = "로그인 실패 — 아이디/비밀번호를 확인하세요."
                    }
                }
            }.start()
        }
    }

    private fun scheduleUpdates() {
        val work = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "today_widget_refresh", ExistingPeriodicWorkPolicy.UPDATE, work
        )
    }
}
