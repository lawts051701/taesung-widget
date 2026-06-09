package com.taesung.widget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessaging
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

        // 알림 채널 + (안드로이드13+) 알림 권한 요청
        FcmService.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
            )
        }

        if (Net.isLoggedIn(this)) {
            status.text = "로그인됨 — 다시 로그인하려면 입력 후 버튼을 누르세요."
            registerPush()  // 이미 로그인 상태면 토큰 재등록
        }

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
                        status.text = "로그인 성공! 위젯·알림을 설정합니다."
                        scheduleUpdates()
                        registerPush()  // FCM 토큰을 이 사용자와 연결
                        TodayScheduleWidget.triggerRefresh(this)
                        finish()
                    } else {
                        status.text = "로그인 실패 — 아이디/비밀번호를 확인하세요."
                    }
                }
            }.start()
        }
    }

    /** FCM 토큰을 받아 서버(로그인 세션)에 등록 — 이 사용자에게 알림이 오도록 연결. */
    private fun registerPush() {
        FcmService.ensureChannel(this)
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                Thread { try { Net.registerFcmToken(this, token) } catch (_: Exception) {} }.start()
            }
        } catch (_: Exception) { /* 파이어베이스 미초기화 등 — 무시 */ }
    }

    private fun scheduleUpdates() {
        val work = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "today_widget_refresh", ExistingPeriodicWorkPolicy.UPDATE, work
        )
    }
}
