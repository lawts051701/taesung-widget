package com.taesung.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        // 데이터 전용 메시지 — 전·후면 모두 여기서 직접 알림 생성(탭 동작 제어용)
        val d = message.data
        val title = d["title"] ?: message.notification?.title ?: "정비사업팀"
        val body = d["body"] ?: message.notification?.body ?: ""
        val url = d["url"] ?: "/"
        val id = d["id"] ?: ""
        showNotification(title, body, url, id)
    }

    private fun showNotification(title: String, body: String, url: String, id: String) {
        ensureChannel(this)

        // 탭 → 앱(TWA)을 '?notif=<id>'로 열어 웹에서 전체 내용 팝업을 띄운다.
        val target = when {
            id.isNotEmpty() -> "${Net.BASE_URL}/?notif=$id"
            url.startsWith("http") -> url
            else -> "${Net.BASE_URL}$url"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
            setPackage(packageName)  // 우리 앱에서 열기
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val reqCode = if (id.isNotEmpty()) id.hashCode() else System.currentTimeMillis().toInt()
        val pi = PendingIntent.getActivity(this, reqCode, intent, flags)

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
