# MRR Android Client

> **Experimental.** The Android app is MRR's **v0.3** reference client for
> [protocol.md](protocol.md). It is our own application — **not** a modified
> Minion Rush client. It talks only to MRR's own API.

## Technology

| Concern | Choice |
|---|---|
| Language | Kotlin 2.0.21 |
| Build | Gradle 8.9 wrapper + Android Gradle Plugin 8.7.3 |
| UI | Jetpack Compose (Material 3), single-activity, state-driven screens |
| Architecture | One `MrrViewModel` exposing a `StateFlow<MrrUiState>` |
| Async | Kotlin coroutines + `viewModelScope` |
| HTTP | Retrofit 2.11 + OkHttp 4.12 + kotlinx-serialization converter |
| Token storage | `EncryptedSharedPreferences` (fallback: private prefs) |
| Tests | JUnit 4 + MockWebServer + coroutines-test (41 tests) |

## Layout

```
android/
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── gradlew, gradlew.bat
└── app/src/
    ├── main/java/de/freeway/mrr/android/
    │   ├── MrrApp.kt            # Application + AppContainer (composition root)
    │   ├── MainActivity.kt      # setContent + screen switching + BackHandler
    │   ├── api/                 # MrrApi (Retrofit), Models, ApiClient, MrrApiException
    │   ├── data/                # TokenStore, MrrRepository (HTTP error mapping)
    │   └── ui/                  # MrrViewModel, screens/, theme/
    └── test/java/...            # ApiModelsTest, MrrRepositoryTest, MrrViewModelTest
```

Screens: **Login → Register → Main (profile + players + save state entry) →
State (experience/level/coins)**. Navigation is a `screen` field in the UI
state; the system back button maps to `viewModel.back()`.

## Building

```bash
cd android
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 41 unit tests
```

Requires an Android SDK (`android/local.properties`, `sdk.dir=...`).
`buildToolsVersion 35.0.1` is pinned because 34.0.0 in this SDK install is
corrupted (missing `aapt.exe`).

## Server base URL (important)

The app builds against `BuildConfig.MRR_BASE_URL`, from the Gradle property
`mrr.baseUrl` (default `http://10.0.2.2:8080/`):

| Where the server runs | Base URL |
|---|---|
| Android emulator (host machine) | `http://10.0.2.2:8080/` (default) |
| Device on same LAN | `http://<your-LAN-IP>:8080/` |
| Emulator → Java server on another port | `http://10.0.2.2:8085/` |

```bash
./gradlew assembleDebug -Pmrr.baseUrl=http://192.168.1.10:8085/
```

Notes:

- `127.0.0.1` **inside the emulator is the emulator itself**, not the host —
  use `10.0.2.2`.
- Cleartext HTTP is enabled (`android:usesCleartextTraffic="true"`) because
  local dev servers are plain HTTP. **A production build must switch to
  HTTPS** and drop cleartext.

## Behavior

- Registration/login store the bearer token via `TokenStore`; only the token
  is persisted — never passwords, never logged.
- Every API call sends `Authorization: Bearer <token>` when present.
- Any `401` clears the stored token and routes back to the login screen
  (expired session → "Session expired" notice).
- Save state writes validate `experience >= 0`, `level >= 1`,
  `coins >= 0` client-side and still honor the server's `422`.
- Player create/delete assumes the **Java** server semantics (authenticated,
  ownership-scoped) — see [protocol.md](protocol.md) for how the Python
  reference differs.

## Tests

`./gradlew testDebugUnitTest` runs three suites:

- **ApiModelsTest** — JSON ↔ model mapping (snake_case, unknown fields,
  nullable `player`).
- **MrrRepositoryTest** — MockWebServer: token storage + `Bearer` header,
  `401/404/409/422/500` mapping, token clearing on 401 and logout, request
  bodies, network-failure mapping.
- **MrrViewModelTest** — fake repository: login/register/logout flows,
  401 → login routing, state form prefill/validation/save, player CRUD.

Instrumented/emulator UI tests are **not** part of v0.3; the app is verified
by unit tests plus manual runs.

## Explicit non-goals

No game assets, no Minion Rush protocol, no DRM/pinning/signature bypass, no
telemetry, no real user data — local/synthetic MRR test data only.
