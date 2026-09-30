# MRR Java Server on OmniOS CE (SunOS/x86_64)

> **Experimental.** How to run `server/java` (MRR v0.3) on **OmniOS CE
> r151054 x86_64 (SunOS, i86pc)** — including a reproducible build of the
> SQLite-JDBC native library.

## The problem

`server/java` uses `org.xerial:sqlite-jdbc:3.53.4.0`. That jar bundles
prebuilt native libraries only for a fixed set of platforms:

```
org/sqlite/native/Linux/{x86_64,aarch64,x86,arm,armv6,armv7,ppc64,riscv64}/libsqlitejdbc.so
org/sqlite/native/Linux-Musl/{x86_64,aarch64,x86}/libsqlitejdbc.so
org/sqlite/native/FreeBSD/{x86_64,aarch64,x86}/libsqlitejdbc.so
org/sqlite/native/Mac/{x86_64,aarch64}/libsqlitejdbc.dylib
org/sqlite/native/Windows/{x86_64,aarch64,armv7,x86}/sqlitejdbc.dll
```

There is **no `org/sqlite/native/SunOS/...` entry**. The driver's loader
(`org.sqlite.SQLiteJDBCLoader.loadSQLiteNativeLibrary()`, v3.53.4.0) tries, in
order:

1. system property `org.sqlite.lib.path` + `org.sqlite.lib.name` → `System.load(...)`
2. the jar resource `org/sqlite/native/<os.name>/<os.arch>/<libname>`
3. every directory in `java.library.path`
4. `System.loadLibrary("sqlitejdbc")`

On OmniOS step 1 is unset, step 2 finds nothing (`os.name=SunOS`), steps 3/4
find nothing → `NativeLibraryNotFoundException` surfaces as
`java.lang.UnsatisfiedLinkError` from `org.sqlite.core.NativeDB`, and the
Spring context (tests, `bootRun`) fails on the first SQLite connection.

## The solution (no jar, no code changes)

Build **one** `libsqlitejdbc.so` from pinned sources on the OmniOS machine and
let the driver load it via **step 1** (`org.sqlite.lib.path`). The Xerial JDBC
layer (jar, Java code, driver registration, Hibernate dialect) stays exactly
as it is; the existing MRR JPA/SQLite code is untouched.

```
server/java/
├── native/sunos/x86_64/libsqlitejdbc.so   ← built locally, git-ignored
├── tools/omnios/build-sqlitejdbc.sh       ← reproducible build script
└── build.gradle                           ← auto-detects the library
```

`build.gradle` configures `test` and `bootRun` with
`-Dorg.sqlite.lib.path=<dir> -Dorg.sqlite.lib.name=libsqlitejdbc.so` when the
file exists. Lookup order:

1. env `MRR_SQLITE_JDBC_LIB` (absolute path to any `libsqlitejdbc.so`/`.dll`)
2. `-Dorg.sqlite.lib.path` / `-Dorg.sqlite.lib.name` on the gradle command line
3. `server/java/native/sunos/x86_64/libsqlitejdbc.so` (script output)

On **SunOS without** a configured library, `./gradlew test` and `bootRun` fail
fast with a pointer to this document instead of the raw `UnsatisfiedLinkError`.
Tests are never skipped or disabled — they run fully once the library exists.

## Reproducible build

### Prerequisites (OmniOS packages)

All three packages exist in the official **r151054 core** repository
(`https://pkg.omnios.org/r151054/core/`, verified against the signed IPS
catalog — note the *exact* names; `pkg search` misses them if the local
catalog is stale):

```bash
sudo pkg refresh
sudo pkg install developer/gcc14 developer/build/gnu-make web/curl
 # perl ships with the base system.
# JDK 21 — JAVA_HOME is optional, the script derives it from javac when unset:
export JAVA_HOME=/usr/jdk/jdk-21   # only needed if javac is not on PATH
```

- `developer/gcc14` → `gcc` in `/opt/gcc-14/bin` (the script finds it
  automatically; `/opt/gcc-10` and a PATH `gcc` are tried as fallbacks)
- `developer/build/gnu-make` → GNU make (`gmake`; `/usr/gnu/bin/make` and a
  GNU `make` on PATH are tried as fallbacks)
- `web/curl` → downloads (or pre-seed `.build/dl/`, see **Offline** below)

If you prefer one meta package instead: `sudo pkg install
developer/illumos-tools` (the package documented on illumos.org for building
illumos) pulls in gcc 10/14, git and the build tools in one go. If `pkg`
reports an unknown package, run `sudo pkg refresh` first.

### Build

```bash
cd server/java/tools/omnios
JAVA_HOME=$JAVA_HOME ./build-sqlitejdbc.sh
```

The script builds `libsqlitejdbc.so` and installs it to
`server/java/native/sunos/x86_64/` (git-ignored, never committed).

### Offline (no internet on the OmniOS box)

Every download is cache-first: if the pinned file already exists in
`server/java/tools/omnios/.build/dl/` with the correct SHA-256, no network
access is used. Copy these three files from another machine into
`.build/dl/`:

| File in `.build/dl/` | SHA-256 |
|---|---|
| `sqlite-jdbc-3.53.4.0.zip` | `5b6977528a2ca93293dc2dae9b0b1e29d66276ff6c4c6b993146f52b8905bcb7` |
| `sqlite-amalgamation-3530400.zip` | `1e71ddf93849c6a6ecf58b827c0692073d2dd7ee40196158068f7b29f422e87d` |
| `slf4j-api-1.7.36.jar` | `d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0` |

(`pkg install` still needs the repositories reachable once, for gcc/make/curl.)

### What it builds — every input pinned

| Input | Pin | Source |
|---|---|---|
| JNI + build system | `xerial/sqlite-jdbc` tag **3.53.4.0** (tag maps to commit `cab7981c19ce04d691f0675f0b2586afc2bbf803`), GitHub tag zip `sqlite-jdbc-3.53.4.0.zip`, SHA-256 `5b6977528a2ca93293dc2dae9b0b1e29d66276ff6c4c6b993146f52b8905bcb7` | github.com/xerial/sqlite-jdbc (zip archive — **no git, no GNU tar required**) |
| SQLite engine | official amalgamation **`sqlite-amalgamation-3530400.zip`** (SQLite 3.53.4, matches the driver version), SHA-256 `1e71ddf93849c6a6ecf58b827c0692073d2dd7ee40196158068f7b29f422e87d` | `https://www.sqlite.org/2026/` |
| javac classpath for `javac -h` (JNI header) | `slf4j-api-1.7.36.jar`, SHA-256 `d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0` | `repo1.maven.org` (Maven Central) |

Every download is verified against the pinned SHA-256; the upstream Makefile's
own unverified curl for slf4j never runs because the script pre-places the
verified jar. No binaries from unknown sources are used, downloaded or
committed — the only artifact (`libsqlitejdbc.so`) is produced locally from
these sources.

The compile itself uses upstream's own Makefile:

```text
gmake native JAVA_HOME=... OS_NAME=SunOS OS_ARCH=x86_64 SQLITE_SOURCE=<amalgamation> CC=gcc
```

Notes on the platform:

- `SunOS-x86_64` is not in upstream's `known_targets`, so their generic
  **`Default` profile** applies: `gcc`, `-Os -fPIC -fvisibility=hidden`,
  `-shared -static-libgcc -pthread -lm`, output `libsqlitejdbc.so` — correct
  for illumos/i86pc (64-bit).
- `jni.h` / `jni_md.h` are located automatically by the Makefile via
  `find -L "$JAVA_HOME"` — works regardless of the JDK's include layout.
- `OS_NAME`/`OS_ARCH` are passed explicitly, skipping upstream's OS-detection
  helper (no effect on `os.arch`/`uname` quirks: `uname -m` = `i86pc`,
  `isainfo -b` = 64 on OmniOS — the library path bypasses Java's OSInfo
  entirely through `org.sqlite.lib.path`).
- The build uses the upstream SQLite compile flags (JDBC extensions,
  `SQLITE_ENABLE_UPDATE_DELETE_LIMIT`, `SQLITE_THREADSAFE=1`, …), so behavior
  matches the official jar for other platforms.
- Upstream's make graph, if left alone, would additionally download the
  SQLite *source* tree, run `./configure --update-limit` and a nested `make`.
  The script short-circuits that chain with timestamp-checked placeholders —
  none of those files is read, because all compile inputs come from the
  verified amalgamation passed as `SQLITE_SOURCE`.
- **GCC 14**: implicit function declarations are a hard error by default in
  GCC 14. Upstream 3.53.4 patches `rc = RegisterExtensionFunctions(db)` into
  `sqlite3.c` (their `opendb_out:` perl recipe) while the definition lives in
  `src/main/ext/extension-functions.c`, which that recipe appends to the *end*
  of `sqlite3.c` — i.e. after the call site. Every gcc ≤ 13 build of stock
  3.53.4 warned here; the symbol resolves in the same translation unit and
  the call is ABI-safe (`int RegisterExtensionFunctions(sqlite3 *)`). The
  script therefore compiles with `-Wno-implicit-function-declaration`
  (passed via `CC`), restoring the pre-GCC-14 behavior without touching
  upstream sources, the SQLite version, or the JNI approach.

### Verify

```bash
cd server/java
./gradlew test          # all tests run, SQLite works via the local library
```

The script prints the SHA-256 of the produced library — record it if you want
to detect rebuild differences (timestamps are embedded in binaries, so exact
bit-reproducibility across machines is not guaranteed; the *inputs* are
pinned).

## Running the server on OmniOS

```bash
cd server/java
./gradlew bootRun                       # picks up native/sunos/x86_64/ automatically
# or an explicit location:
MRR_SQLITE_JDBC_LIB=/opt/mrr/lib/libsqlitejdbc.so ./gradlew bootRun
# packaged jar:
java -Dorg.sqlite.lib.path=/opt/mrr/lib -Dorg.sqlite.lib.name=libsqlitejdbc.so -jar app.jar
```

Note on the Gradle daemon: environment variables (`MRR_SQLITE_JDBC_LIB`) are
read by the Gradle daemon. After **changing** the variable, run
`./gradlew --stop` once (or use `--no-daemon`) so a fresh daemon picks it up.
The default `native/sunos/x86_64/` path is a file-system lookup and is not
affected by this.

## Updating the driver later

When bumping `org.xerial:sqlite-jdbc` in `build.gradle`, update the pins in
`tools/omnios/build-sqlitejdbc.sh` **together**: tag + tag-zip URL +
SHA-256, amalgamation URL + SHA-256 (encoding rule: SQLite `3.X.Y` →
`3XXYY00`), slf4j URL + SHA-256. The driver's Java code and the native
library must come from the same version — mismatched JNI signatures cause
`UnsatisfiedLinkError` on method lookup.

## Explicit non-goals

Same as the rest of MRR: no Minion Rush protocol compatibility, no game
assets, no secrets in the repo — local/synthetic test data only.
