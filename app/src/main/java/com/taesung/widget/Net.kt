package com.taesung.widget

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * ERP 서버 통신 + 세션 쿠키 영속(SharedPreferences).
 * BASE_URL 만 환경에 맞게 바꾸면 됩니다.
 */
object Net {
    const val BASE_URL = "https://taesung-urban.duckdns.org"
    private const val PREF = "taesung_widget"
    private const val KEY_COOKIES = "cookies"
    private const val KEY_EMP = "employee_id"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** SharedPreferences 에 쿠키를 저장/복원하는 단순 CookieJar */
    private class PersistentCookieJar(val ctx: Context) : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val set = prefs(ctx).getStringSet(KEY_COOKIES, emptySet())!!.toMutableSet()
            cookies.forEach { set.add(it.toString()) }
            prefs(ctx).edit().putStringSet(KEY_COOKIES, set).apply()
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return prefs(ctx).getStringSet(KEY_COOKIES, emptySet())!!
                .mapNotNull { Cookie.parse(url, it) }
                .filter { it.matches(url) }
        }
    }

    private fun client(ctx: Context): OkHttpClient =
        OkHttpClient.Builder().cookieJar(PersistentCookieJar(ctx)).build()

    fun isLoggedIn(ctx: Context): Boolean =
        prefs(ctx).getStringSet(KEY_COOKIES, emptySet())!!.isNotEmpty()

    fun logout(ctx: Context) {
        prefs(ctx).edit().remove(KEY_COOKIES).remove(KEY_EMP).apply()
    }

    /** 로그인 → 성공 시 세션 쿠키 + employee_id 저장. 성공 여부 반환. */
    fun login(ctx: Context, username: String, password: String): Boolean {
        val body = JSONObject().put("username", username).put("password", password)
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$BASE_URL/api/auth/login").post(body).build()
        client(ctx).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            // 로그인 응답이 사용자 객체(employee_id 포함)면 저장
            try {
                val u = JSONObject(resp.body!!.string())
                if (!u.isNull("employee_id")) {
                    prefs(ctx).edit().putInt(KEY_EMP, u.getInt("employee_id")).apply()
                }
            } catch (_: Exception) { /* 응답 형식이 달라도 쿠키는 저장됨 */ }
            return true
        }
    }

    /** FCM 기기 토큰을 서버에 등록(로그인 세션 쿠키 사용). 성공 여부 반환. */
    fun registerFcmToken(ctx: Context, token: String): Boolean {
        if (!isLoggedIn(ctx)) return false
        return try {
            val body = JSONObject().put("token", token)
                .toString().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url("$BASE_URL/api/push/fcm-register").post(body).build()
            client(ctx).newCall(req).execute().use { resp -> resp.isSuccessful }
        } catch (e: Exception) { false }
    }

    /** 오늘 내 일정 라인 목록 반환. 인증 실패 시 null(재로그인 필요). */
    fun fetchTodayLines(ctx: Context): List<String>? {
        val empId = prefs(ctx).getInt(KEY_EMP, -1)
        if (empId < 0) return null  // employee_id 미저장 → 재로그인 권장

        val kst = TimeZone.getTimeZone("Asia/Seoul")
        val cal = Calendar.getInstance(kst).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = kst }
        val start = iso.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = iso.format(cal.time)

        val url = "$BASE_URL/api/events?start=$start&end=$end"
        val req = Request.Builder().url(url).get().build()
        client(ctx).newCall(req).execute().use { resp ->
            if (resp.code == 401) return null
            if (!resp.isSuccessful) return null
            val arr = JSONArray(resp.body!!.string())
            val timeFmt = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = kst }
            val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val lines = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val ev = arr.getJSONObject(i)
                if (ev.optInt("assigned_to_id", -1) != empId) continue
                val starts = ev.optString("starts_at")
                val hhmm = try {
                    val d = parse.parse(starts.substring(0, 19))
                    timeFmt.format(d!!)
                } catch (e: Exception) { "" }
                lines.add("$hhmm  ${ev.optString("title")}".trim())
            }
            return lines
        }
    }
}
