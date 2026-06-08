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
        val ok = client(ctx).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use false
            // 로그인 응답이 사용자 객체(employee_id 포함)면 저장
            try {
                val u = JSONObject(resp.body!!.string())
                if (!u.isNull("employee_id")) {
                    prefs(ctx).edit().putInt(KEY_EMP, u.getInt("employee_id")).apply()
                }
            } catch (_: Exception) { /* 응답 형식이 달라도 쿠키는 저장됨 */ }
            true
        }
        // 로그인 응답에 employee_id가 없었으면 /me로 한 번 더 보강
        if (ok && prefs(ctx).getInt(KEY_EMP, -1) < 0) refreshEmployeeId(ctx)
        return ok
    }

    /** /api/auth/me 로 employee_id 보강. 반환: >=0 직원id(저장됨), -1 직원연결없음, -2 인증실패. */
    private fun refreshEmployeeId(ctx: Context): Int {
        return try {
            val req = Request.Builder().url("$BASE_URL/api/auth/me").get().build()
            client(ctx).newCall(req).execute().use { resp ->
                when {
                    resp.code == 401 -> -2
                    !resp.isSuccessful -> -1
                    else -> {
                        val u = JSONObject(resp.body!!.string())
                        if (u.isNull("employee_id")) -1
                        else {
                            val id = u.getInt("employee_id")
                            prefs(ctx).edit().putInt(KEY_EMP, id).apply()
                            id
                        }
                    }
                }
            }
        } catch (e: Exception) { -1 }
    }

    /** 이번 달 전체 일정 조회. 인증 실패 시 null. (직원 연결 불필요 — 모든 일정 표시) */
    fun fetchMonthEvents(ctx: Context): MonthData? {
        val kst = TimeZone.getTimeZone("Asia/Seoul")
        val now = Calendar.getInstance(kst)
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val today = now.get(Calendar.DAY_OF_MONTH)

        val mc = Calendar.getInstance(kst).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply { timeZone = kst }
        val start = iso.format(mc.time)
        mc.add(Calendar.MONTH, 1)
        val end = iso.format(mc.time)
        val s = java.net.URLEncoder.encode(start, "UTF-8")
        val e = java.net.URLEncoder.encode(end, "UTF-8")

        val req = Request.Builder().url("$BASE_URL/api/events?start=$s&end=$e").get().build()
        client(ctx).newCall(req).execute().use { resp ->
            if (resp.code == 401) return null
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val arr = JSONArray(resp.body!!.string())
            val parseUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
            // day -> (정렬키 시각, 담당자색키, 제목)
            val map = HashMap<Int, MutableList<Triple<Long, Int, String>>>()
            for (i in 0 until arr.length()) {
                val ev = arr.getJSONObject(i)
                val starts = ev.optString("starts_at")
                try {
                    val d = parseUtc.parse(starts.substring(0, 19)) ?: continue
                    val c = Calendar.getInstance(kst).apply { time = d }
                    if (c.get(Calendar.YEAR) != year || c.get(Calendar.MONTH) + 1 != month) continue
                    val day = c.get(Calendar.DAY_OF_MONTH)
                    val colorKey = ev.optInt("assigned_to_id", -1)
                    val title = ev.optString("title")
                    map.getOrPut(day) { mutableListOf() }.add(Triple(d.time, colorKey, title))
                } catch (_: Exception) { /* 형식 불량 스킵 */ }
            }
            val byDay = map.mapValues { (_, list) ->
                list.sortedBy { it.first }.map { EvtChip(it.third, it.second) }
            }
            return MonthData(year, month, today, byDay)
        }
    }

    /** 위젯에 표시할 오늘 일정 텍스트 — 상태별 안내 메시지까지 한 번에 처리. */
    fun todayWidgetText(ctx: Context): String {
        if (!isLoggedIn(ctx)) return "로그인이 필요합니다. 위젯을 눌러 로그인하세요."
        var empId = prefs(ctx).getInt(KEY_EMP, -1)
        if (empId < 0) {
            empId = refreshEmployeeId(ctx)
            if (empId == -2) return "세션이 만료됐어요. 위젯을 눌러 다시 로그인하세요."
            if (empId < 0) return "이 계정은 직원과 연결돼 있지 않아 개인 일정을 표시할 수 없어요.\n관리자에게 ‘팀원’ 연결을 요청하세요."
        }
        return try {
            val lines = fetchTodayLines(ctx)
            when {
                lines == null -> "세션이 만료됐어요. 위젯을 눌러 다시 로그인하세요."
                lines.isEmpty() -> "오늘 일정이 없습니다."
                else -> lines.joinToString("\n")
            }
        } catch (e: Exception) { "네트워크 오류 — 새로고침을 눌러 주세요." }
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

        // ⚠️ start/end 의 '+09:00' 오프셋을 그대로 URL에 넣으면 서버가 '+'를 공백으로
        // 해석해 날짜 파싱이 실패한다(422). 반드시 URL 인코딩한다.
        val s = java.net.URLEncoder.encode(start, "UTF-8")
        val e = java.net.URLEncoder.encode(end, "UTF-8")
        val url = "$BASE_URL/api/events?start=$s&end=$e"
        val req = Request.Builder().url(url).get().build()
        client(ctx).newCall(req).execute().use { resp ->
            if (resp.code == 401) return null  // 인증 실패만 null(→재로그인 안내)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")  // 그 외는 네트워크 오류로
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
