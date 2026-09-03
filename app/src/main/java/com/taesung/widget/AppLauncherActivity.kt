package com.taesung.widget

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.androidbrowserhelper.trusted.LauncherActivity
import com.google.firebase.messaging.FirebaseMessaging

/**
 * 앱 실행 시 알림 권한과 현재 FCM 토큰을 준비한 뒤 ERP TWA를 연다.
 * 토큰은 로그인된 웹 세션에서 현재 사용자에게 귀속되도록 쿼리로 한 번 전달한다.
 */
class AppLauncherActivity : AppCompatActivity() {
    companion object {
        private const val REQ_NOTIFICATIONS = 2001
        private const val TOKEN_QUERY = "app_fcm_token"
    }

    private var launched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePrefs.apply(this)
        super.onCreate(savedInstanceState)
        FcmService.ensureChannel(this)

        if (openNotificationPopup()) return

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIFICATIONS,
            )
        } else {
            prepareLaunch()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATIONS) prepareLaunch()
    }

    private fun prepareLaunch() {
        val cachedToken = FcmService.cachedToken(this)
        Handler(Looper.getMainLooper()).postDelayed({ launchTwa(cachedToken) }, 4000)
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val token = if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null
                if (token != null) FcmService.rememberToken(this, token)
                launchTwa(token ?: cachedToken)
            }
        } catch (_: Exception) {
            launchTwa(cachedToken)
        }
    }

    private fun openNotificationPopup(): Boolean {
        if (intent?.action != FcmService.OPEN_NOTIFICATION_ACTION) return false
        startActivity(Intent(this, NotificationPopupActivity::class.java).apply {
            putExtra("title", intent.getStringExtra("title") ?: "정비사업팀")
            putExtra("body", intent.getStringExtra("body") ?: "")
            putExtra("url", intent.getStringExtra("url") ?: "/")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
        return true
    }

    @Synchronized
    private fun launchTwa(token: String?) {
        if (launched) return
        launched = true

        val incoming = intent?.data?.takeIf {
            it.scheme == "https" && it.host == Uri.parse(Net.BASE_URL).host
        }
        val builder = (incoming ?: Uri.parse(Net.BASE_URL)).buildUpon()
        if (!token.isNullOrBlank()) builder.appendQueryParameter(TOKEN_QUERY, token)

        startActivity(Intent(this, LauncherActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = builder.build()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        finish()
    }
}
