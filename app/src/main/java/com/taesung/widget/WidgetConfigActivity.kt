package com.taesung.widget

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
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
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_config)

        styleScreen()
        bindThemeMode()

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
                        status.text = "로그인 성공! 일정으로 이동합니다."
                        scheduleUpdates()
                        registerPush()  // FCM 토큰을 이 사용자와 연결
                        TodayScheduleWidget.triggerRefresh(this)
                        // 로그인 후 앱 첫 화면이 아니라 '일정(/events)'으로 바로 이동
                        try {
                            startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("${Net.BASE_URL}/events")
                                ).apply {
                                    setPackage(packageName)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        } catch (_: Exception) { /* 브라우저 미설치 등 — 무시 */ }
                        finish()
                    } else {
                        status.text = "로그인 실패 — 아이디/비밀번호를 확인하세요."
                    }
                }
            }.start()
        }
    }

    private fun bindThemeMode() {
        val group = findViewById<RadioGroup>(R.id.cfg_theme_group)
        val checked = when (ThemePrefs.mode(this)) {
            ThemePrefs.MODE_DARK -> R.id.cfg_theme_dark
            ThemePrefs.MODE_SYSTEM -> R.id.cfg_theme_system
            else -> R.id.cfg_theme_light
        }
        group.check(checked)
        group.setOnCheckedChangeListener { _, id ->
            val mode = when (id) {
                R.id.cfg_theme_dark -> ThemePrefs.MODE_DARK
                R.id.cfg_theme_system -> ThemePrefs.MODE_SYSTEM
                else -> ThemePrefs.MODE_LIGHT
            }
            if (mode == ThemePrefs.mode(this)) return@setOnCheckedChangeListener
            ThemePrefs.setMode(this, mode)
            ThemePrefs.apply(this)
            recreate()
        }
    }

    private fun styleScreen() {
        val dark = ThemePrefs.isDark(this)
        findViewById<LinearLayout>(R.id.cfg_root).setBackgroundColor(if (dark) 0xFF111827.toInt() else 0xFFFFFFFF.toInt())
        val text = if (dark) 0xFFF9FAFB.toInt() else 0xFF111827.toInt()
        val sub = if (dark) 0xFFD1D5DB.toInt() else 0xFF6B7280.toInt()
        val hint = if (dark) 0xFF9CA3AF.toInt() else 0xFF9CA3AF.toInt()
        val inputFill = if (dark) 0xFF1F2937.toInt() else 0xFFFFFFFF.toInt()
        val inputStroke = if (dark) 0xFF4B5563.toInt() else 0xFFD1D5DB.toInt()
        findViewById<TextView>(R.id.cfg_title).setTextColor(text)
        findViewById<TextView>(R.id.cfg_theme_label).setTextColor(sub)
        val ids = listOf(
            R.id.cfg_id,
            R.id.cfg_pw,
        )
        ids.forEach { id ->
            findViewById<EditText>(id).apply {
                setTextColor(text)
                setHintTextColor(hint)
                background = rounded(inputFill, inputStroke, 4)
                setPadding(dp(10), paddingTop, dp(10), paddingBottom)
            }
        }
        listOf(
            R.id.cfg_theme_light,
            R.id.cfg_theme_dark,
            R.id.cfg_theme_system,
        ).forEach { id ->
            findViewById<TextView>(id).setTextColor(text)
        }
        findViewById<TextView>(R.id.cfg_status).setTextColor(sub)
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = dp(radius).toFloat()
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
