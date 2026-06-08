# 태성 ERP — 오늘 일정 홈 위젯 (Android 네이티브)

법무법인 태성 ERP의 **오늘 내 일정**을 안드로이드 홈 화면 위젯으로 보여주는 네이티브 모듈입니다.
TWA/PWA로는 홈 위젯을 만들 수 없어, 별도의 작은 네이티브 앱(위젯 포함)으로 구현합니다.

> ⚠️ 이 프로젝트는 **Android Studio에서 빌드**해야 합니다. 서버/웹 빌드 파이프라인과 무관합니다.
> 코드는 작성돼 있으나, APK 빌드·서명·기기 설치는 Android Studio + 안드로이드 기기에서 진행해야 합니다.

## 동작 방식 (백엔드 변경 불필요)

1. 위젯을 홈에 추가하면 **설정 화면**(`WidgetConfigActivity`)이 떠서 ERP **아이디/비밀번호**로 로그인.
2. 로그인은 기존 `POST /api/auth/login`을 호출하고, 응답의 **세션 쿠키**를 기기에 저장(`PersistentCookieJar`).
3. 위젯 갱신 워커(`WidgetUpdateWorker`)가 저장된 쿠키로 `GET /api/events?start=…&end=…`(오늘 범위)를 호출 →
   본인(`assigned_to_id`) 일정을 추려 위젯에 표시.
4. **주기 갱신**: WorkManager 30분 간격 + 위젯의 새로고침 버튼 탭 시 즉시 갱신.

> 세션 쿠키가 만료되면 위젯에 "다시 로그인 필요"가 표시됩니다. 위젯을 탭해 설정에서 재로그인하세요.
> (장기적으로는 백엔드에 위젯 전용 토큰 발급 엔드포인트를 두는 것이 더 견고합니다 — 아래 '개선' 참고.)

## 빌드 방법

1. Android Studio에서 이 `android-widget/` 폴더를 **새 프로젝트로 열기**.
2. `app/build.gradle.kts`의 의존성 동기화(Gradle Sync).
3. `Net.kt`의 `BASE_URL` 확인: 기본 `https://taesung-urban.duckdns.org`.
4. 실제 안드로이드 기기(또는 에뮬레이터)에서 Run → 앱 설치.
5. 홈 화면 → 위젯 → "태성 오늘 일정" 추가 → 로그인.
6. (배포용) `Build > Generate Signed Bundle/APK`로 서명 APK 생성 후 사내 배포.

## 파일 구성

```
android-widget/
├─ settings.gradle.kts, build.gradle.kts, gradle.properties   # Gradle 설정(샘플)
└─ app/
   ├─ build.gradle.kts
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/taesung/widget/
      │  ├─ Net.kt                 # OkHttp + 영속 쿠키 + 로그인/이벤트 조회
      │  ├─ TodayScheduleWidget.kt # AppWidgetProvider
      │  ├─ WidgetUpdateWorker.kt  # WorkManager 갱신 워커
      │  └─ WidgetConfigActivity.kt# 로그인 설정 화면
      └─ res/
         ├─ layout/widget_today.xml, widget_config.xml
         ├─ xml/widget_today_info.xml
         └─ values/strings.xml
```

## 개선(선택) — 백엔드 토큰 방식
세션 쿠키 대신 위젯 전용 장기 토큰을 쓰려면 백엔드에 다음을 추가:
- `POST /api/auth/widget-token` (로그인된 사용자 → 장기 토큰 발급, DB 저장)
- 위젯은 `Authorization: Bearer <token>` 로 `/api/events` 호출
- 이벤트 엔드포인트가 해당 토큰 헤더도 인증으로 허용하도록 보강
이렇게 하면 세션 만료와 무관하게 안정적으로 동작합니다.
