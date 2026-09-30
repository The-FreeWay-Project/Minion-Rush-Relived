# MRR Patcher

> **MRR Patcher** is our own standalone Android launcher/updater app. It
> checks the MRR server for a newer patcher version, downloads declared
> patch files into its own private workspace, verifies every file with
> SHA-256, and applies them there — with a full state machine and progress
> display. It does **not** modify the original Minion Rush app, does **not**
> patch, re-sign or repack any third-party APK, and does **not**
> circumvent signatures, DRM, TLS pinning or any other protection mechanism.

## Versioning

Patcher versions follow `A<major>.<minor>.<patch>` (example: `A1.0.0`).

- The **installed** version comes from the APK's `versionName`
  (`BuildConfig.VERSION_NAME`).
- The **latest** version is advertised by the server manifest.
- Comparison is numeric per component (`A1.10.0` > `A1.9.0`); the leading `A`
  is optional and case-insensitive on both sides.

## Endpoints

### `GET /api/v1/patch/manifest?installed=A1.0.0`

Public — no `Authorization` header (same policy as `GET /api/v1/health`).

| Query parameter | Required | Meaning |
|---|---|---|
| `installed` | no | Patcher version on the device; only influences `message`. Unparsable/missing values fall back to the default message. |

```json
{
  "version": "A1.0.0",
  "channel": "stable",
  "platform": "android",
  "serverVersion": "0.3.0",
  "message": "MRR Patcher is up to date.",
  "files": []
}
```

| Field | Type | Meaning |
|---|---|---|
| `version` | string | Latest patcher version on this channel (`A1.0.0`) |
| `channel` | string | Release channel (`stable`) |
| `platform` | string | Target platform (`android`) |
| `serverVersion` | string | Version of the mrr-server that served the manifest (`0.3.0`, from Spring Boot build info; `unknown` if unavailable) |
| `message` | string | Human-readable result of the installed-vs-latest comparison |
| `files` | array | Downloadable payload entries (see below); empty until the server populates them |

`message` values (server-side): equal/missing/unparsable →
`MRR Patcher is up to date.`, older → `Update to A1.0.0 available.`, newer →
`Installed patcher version is ahead of the stable channel.` The client
nevertheless performs its **own** numeric comparison — the status shown in
the UI never depends on parsing the message text.

### `GET /api/v1/patch/files/{path}`

Streams one file from the server's configured patch directory
(`mrr.patch-dir`, default `./patch-files`) as
`application/octet-stream`. `{path}` is a catch-all path variable, so
nested paths work (`/api/v1/patch/files/packs/demo/core.bin`). Public; any
path that is unsafe (`.`, `..`, backslash, absolute), unknown or a directory
returns `404 Not Found`. Nothing outside the patch directory is ever
readable through this endpoint.

### Manifest `files` entries

Each entry names a workspace-relative target, the download URL, the byte
size and the expected hash:

```json
{
  "path": "packs/demo/core.bin",
  "url": "files/core.bin",
  "size": 17592,
  "sha256": "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
}
```

| Field | Meaning |
|---|---|
| `path` | Target path inside the patch workspace; must be relative and `..`-free (validated on **both** sides) |
| `url` | Absolute `http(s)://…` URL, or a path relative to the configured base URL |
| `size` | Exact payload size in bytes; `0` = unknown (progress bar goes indeterminate) |
| `sha256` | Expected lowercase/uppercase hex SHA-256; verified **before** anything is applied |

The client downloads each file to `incoming/<path>.part`, verifies
`sha256` (streaming, `MessageDigest.isEqual`), moves it to
`verified/<path>`, applies it to `applied/<path>` (previous version kept as
`backup/<path>.bak`) and finally re-hashes `applied/<path>` to confirm the
result. A mismatch at any point aborts the run, deletes all partial
downloads and lands in `FAILED`.

## Pipeline & state machine

Every visible transition is validated against an explicit edge list
(`PatchStateMachine`); an illegal edge throws instead of silently corrupting
the flow:

```
IDLE → CHECKING → { UP_TO_DATE | UPDATE_AVAILABLE | FAILED }
UP_TO_DATE → CHECKING | VERIFYING (verify-only pass) | FAILED
UPDATE_AVAILABLE → CHECKING | DOWNLOADING | FAILED
DOWNLOADING → VERIFYING | FAILED
VERIFYING → READY_TO_PATCH | VERIFYING_PATCH | FAILED
READY_TO_PATCH → PATCHING | FAILED
PATCHING → VERIFYING_PATCH | FAILED
VERIFYING_PATCH → SUCCESS | FAILED
SUCCESS → CHECKING | VERIFYING
FAILED → CHECKING | IDLE
```

| State | Label in the UI |
|---|---|
| `IDLE` | Standby |
| `CHECKING` | Connecting to server … |
| `UP_TO_DATE` | Up to date |
| `UPDATE_AVAILABLE` | Update available |
| `DOWNLOADING` | Downloading … (+ `Downloading 72%`, `12.3 MB / 17.2 MB`) |
| `VERIFYING` | Verifying checksums … |
| `READY_TO_PATCH` | Ready to patch |
| `PATCHING` | Applying patch … |
| `VERIFYING_PATCH` | Verifying installation … |
| `SUCCESS` | Done |
| `FAILED` | Error + exception message |

Failures (network, HTTP, decoding, unsafe path, hash mismatch, missing
file) all end in `FAILED` and always clear `incoming/`.

## Android architecture

Separate Gradle module `android/patcher/` (application id
`de.freeway.mrr.patcher`, `versionName` `A1.0.0`) with its own APK — it sits
next to the main app (`:app`) and shares nothing with it but the technology
choices.

```
patcher/src/main/java/de/freeway/mrr/patcher/
├── MainActivity.kt              # composition root: BuildConfig → repository
│                                #   → PatchWorkspace/PatchApplier/PatchManager → ViewModel
├── api/
│   ├── PatchApi.kt              # Retrofit interface + PatchClient (base URL injected once)
│   └── PatchModels.kt           # @Serializable PatchManifest / PatchFile (path/url/size/sha256)
├── data/
│   └── PatchRepository.kt       # PatchRepository + DefaultPatchRepository + PatchApiException
├── version/
│   └── PatcherVersion.kt        # parse/compare A1.0.0 + compareVersions() → UpdateStatus
├── pipeline/
│   ├── PatchState.kt            # PatchState enum + PatchStateMachine edge list + PatchProgress
│   ├── PatchWorkspace.kt        # PatchPaths (strict validation) + incoming/verified/applied/backup
│   ├── PatchVerifier.kt         # streaming SHA-256 (sha256Hex / verify)
│   ├── PatchDownloader.kt       # PatchDownloader interface + HttpPatchDownloader (OkHttp)
│   ├── PatchApplier.kt          # verified → applied, previous version → backup
│   └── PatchManager.kt          # PatchManagerState + check()/runPatch() driver
└── ui/
    ├── PatcherViewModel.kt      # PatcherUiState (status/progress/…) + check/startPatch/onPrimaryAction
    ├── PatcherScreen.kt         # dark gaming UI: header, server/version rows, button, progress
    └── theme/Theme.kt           # PatcherTheme (darkColorScheme) + PatcherColors
```

Layering mirrors the main app: the ViewModel only maps manager state onto
`PatcherUiState` and translates button presses; the `PatchManager` owns the
state machine; the repository translates HTTP/decoding failures into typed
`PatchApiException`s; controllers/URLs are never referenced from the UI.

### Patch workspace (the only thing ever written)

```
filesDir/patch-workspace/
├── incoming/   # <path>.part while downloading — cleared on any failure
├── verified/   # passed SHA-256, waiting to be applied
├── applied/    # final position after a patch run
└── backup/     # <path>.bak — previous applied version (REPLACE)
```

All four directories are created up front (`PatchWorkspace.ensure()`).
`PatchPaths.isSafe/requireSafe` rejects blank paths, `.`/`..`/empty
segments, backslashes, NUL and absolute paths — on the workspace handle
**and** before every apply.

### Downloading

`HttpPatchDownloader` streams with 8 KiB buffers over OkHttp (HTTP or
HTTPS — no pinning, no trust-all shortcuts), reports
`(bytesDone, bytesTotal)` per chunk, honours coroutine cancellation
(`ensureActive()`) and **always deletes the partial target** on any failure.
HTTP errors become `PatchApiException.Server(code)`, transport errors and
invalid URLs become `PatchApiException.Network(cause)`.

### UI

Dark gaming layout (`darkColorScheme`, near-black surfaces, Minion-yellow
accent `#F9A825`): `MRR` header + `MINION RUSH RELIVED` letterspacing, a
card with `Server: ONLINE/OFFLINE` (pulsing dot), `Installed:`, `Available:`,
one full-width action button (`CHECK` / `PATCH` / `UPDATE` / `PATCHING…` /
`RETRY`), an animated progress block (progress bar, current file,
percentage, `12.3 MB / 17.2 MB`) and a coloured status block
(green/yellow/red). The column is vertically scrollable and capped at
560 dp, so it scales from phones to tablets. The first frame triggers an
automatic check (`onUiReady()`), never from the constructor.

### Server URL configuration (single point)

| Where | What |
|---|---|
| `patcher/build.gradle.kts` | `mrr.patcher.baseUrl` Gradle property, default `http://192.168.188.105:8080/` → `BuildConfig.MRR_BASE_URL` |
| `android/gradle.properties` | documents the override |
| Code | reads `BuildConfig.MRR_BASE_URL` in exactly one place (`MainActivity`) |

Override per build:

```sh
./gradlew :patcher:assembleDebug -Pmrr.patcher.baseUrl=http://192.168.1.10:8080/
```

## Server side

| Piece | Location |
|---|---|
| Manifest endpoint | `server/java/.../api/PatchController` (`GET /api/v1/patch/manifest`) |
| Files endpoint | same controller (`GET /api/v1/patch/files/{*path}`) → `service/PatchFileService` |
| Version/compare logic | `service/PatchService` (`A1.0.0`, channel `stable`, platform `android`, `serverVersion` from Spring build info) |
| Version parsing | `util/PatchVersion` (`^A?(\d+)\.(\d+)\.(\d+)$`, case-insensitive) |
| Patch directory | `mrr.patch-dir` (default `./patch-files`, overridden in tests) |

`PatchFileService` only ever resolves paths inside `mrr.patch-dir`
(normalize + prefix check); unsafe, missing and directory paths all yield
an empty `Optional` → `404`.

## Build & test

```sh
# Android: unit tests (both apps) and the patcher APK
cd android
./gradlew test
./gradlew :patcher:assembleDebug
# → patcher/build/outputs/apk/debug/patcher-debug.apk (~9.9 MB)

# install on a connected device/emulator
adb install -r patcher/build/outputs/apk/debug/patcher-debug.apk

# server side (manifest + files endpoints, version comparison)
cd server/java
./gradlew test
./gradlew bootRun            # http://<host>:8080/api/v1/patch/...
```

Tests: **68 patcher unit tests per build variant** (state machine, SHA-256
verifier, workspace/path rules, applier, downloader with MockWebServer,
manager end-to-end runs, ViewModel actions) plus **69 server tests**
(API suite, manifest endpoint, `PatchVersion`, `PatchFileService`,
files endpoint).

## Planned later

1. **Populate `files[]`** on the server from the patch directory.
2. **Channels** — `stable` / `beta` selection with per-channel manifests.
3. **Resumable downloads**, retries with backoff, changelog field.
4. **Manifest authenticity** (signing) before trusting content.

**Non-goals (explicit):** no modification of the original Minion Rush app,
no APK patching/re-signing, no circumvention of signature checks, DRM,
TLS pinning or other protections, no execution of remote code.

## Security notes

- Downloads and writes are confined to `filesDir/patch-workspace` and the
  server's `mrr.patch-dir`; no privileged permissions — only `INTERNET`.
- SHA-256 is verified before a file is applied **and** again after applying.
- Path traversal is rejected on the server, in the workspace handles and in
  the manifest before any I/O.
- Cleartext HTTP is enabled for local development only; production builds
  must use HTTPS.
- All inputs are display-only strings; the status is derived from the local
  numeric comparison, never from message text.
