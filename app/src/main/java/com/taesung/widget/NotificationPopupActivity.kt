package com.taesung.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 푸시 알림을 탭하면 앱 전체가 아니라 이 작은 팝업(다이얼로그)만 떠서 내용을 보여준다.
 * '이동하기'를 누를 때만 앱(웹)으로 이동한다.
 */
class NotificationPopupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_notification)
        setFinishOnTouchOutside(true)

        val title = intent.getStringExtra("title") ?: "정비사업팀"
        val body = intent.getStringExtra("body") ?: ""
        val url = intent.getStringExtra("url") ?: "/"

        findViewById<TextView>(R.id.np_title).text = title
        findViewById<TextView>(R.id.np_body).text = if (body.isBlank()) "(내용 없음)" else body

        findViewById<Button>(R.id.np_close).setOnClickListener { finish() }
        findViewById<Button>(R.id.np_go).setOnClickListener {
            val target = if (url.startsWith("http")) url else "${Net.BASE_URL}$url"
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                        setPackage(packageName)  // 우리 앱(TWA)에서 열기
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (_: Exception) { /* 무시 */ }
            finish()
        }
    }
}
