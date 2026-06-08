package com.taesung.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM 수신 서비스.
 * - onNewToken: 새 토큰을 서버에 등록(로그인돼 있을 때)
 * - onMessageReceived: 알림을 네이티브로 표시(앱 이름·아이콘으로)
 */
class FcmService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "taesung_erp"
        const val CHANNEL_NAME = "정비사업팀 알림"

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                    val ch = NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
                    ).apply { description = "일정·자문 등 ERP 알림" }
                    mgr.createNotificationChannel(ch)
                }
            }
        }
    }

    override fun onNewToken(token: String) {
        // 로그인돼 있으면 즉시 서버 등록 (위젯 로그인으로 세션이 있을 때)
        Thread { try { Net.registerFcmToken(applicationContext, token) } catch (_: Exception) {} }.start()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        val title = n?.title ?: message.data["title"] ?: "정비사업팀"
        val body = n?.body ?: message.data["body"] ?: ""
        val url = message.data["url"] ?: "/"
        showNotification(title, body, url)
    }

    private fun showNotification(title: String, body: String, url: String) {
        ensureChannel(this)

        // 알림 탭 → ERP 앱(TWA 런처) 열기
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("url", url)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getActivity(this, 0, launch ?: Intent(), flags)

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notif)
        } catch (_: SecurityException) { /* 알림 권한 없음 — 무시 */ }
    }
}
