#!/bin/sh
# ---------------------------------------------------------------------------
# Build libsqlitejdbc.so for OmniOS CE r151054 x86_64 (SunOS, i86pc)
#
# Builds the SQLite-JDBC *native* library from pinned, traceable sources so
# that `./gradlew test` / `bootRun` work on OmniOS with the unmodified Xerial
# driver jar (org.xerial:sqlite-jdbc:3.53.4.0).
#
#   Upstream (JNI layer + build):  xerial/sqlite-jdbc tag 3.53.4.0
#                                  tag -> commit cab7981c19ce04d691f0675f0b2586afc2bbf803
#                                  fetched as the GitHub tag *zip* archive
#                                  (not tar.gz: GitHub serves pax-format
#                                  tarballs, which Solaris tar cannot read),
#                                  pinned by SHA-256 below, extracted with
#                                  the JDK's own `jar` — no git, no GNU tar,
#                                  no extra packages
#   SQLite engine:                official amalgamation 3530400 (SQLite 3.53.4)
#                                  from https://www.sqlite.org/2026/
#   javac classpath (JNI header): slf4j-api 1.7.36 from Maven Central
#
# Every download is verified against a pinned SHA-256 below — no binaries
# from unknown sources, no jar repackaging. Output:
#
#   server/java/native/sunos/x86_64/libsqlitejdbc.so   (git-ignored)
#
# which the Gradle build picks up automatically (see build.gradle).
#
# Prerequisites on OmniOS CE r151054 (official "core" publisher, package
# names verified against https://pkg.omnios.org/r151054/core/):
#
#   sudo pkg refresh
#   sudo pkg install developer/gcc14 developer/build/gnu-make web/curl
#
# perl ships with the base system. If you prefer one big meta
# package, `sudo pkg install developer/illumos-tools` pulls in gcc 10/14,
# git and the build tools as well (documented on illumos.org).
#
# JDK 21 with JAVA_HOME set (an OpenJDK/Zulu 21 build for illumos).
# Network access to github.com, sqlite.org and repo1.maven.org — or pre-seed
# .build/dl/ with the three pinned files (see docs/omnios.md, "Offline").
#
# Usage:
#   cd server/java/tools/omnios
#   JAVA_HOME=/usr/jdk/jdk-21 ./build-sqlitejdbc.sh
# ---------------------------------------------------------------------------
set -eu

# --- pinned inputs (update tag + archive + amalgamation + hashes together) --
SQLITE_JDBC_TAG='3.53.4.0'
SQLITE_JDBC_COMMIT='cab7981c19ce04d691f0675f0b2586afc2bbf803'   # tag maps to this commit (traceability)
SQLITE_JDBC_ZIP_URL="https://github.com/xerial/sqlite-jdbc/archive/refs/tags/${SQLITE_JDBC_TAG}.zip"
SQLITE_JDBC_ZIP_SHA256='5b6977528a2ca93293dc2dae9b0b1e29d66276ff6c4c6b993146f52b8905bcb7'
SQLITE_JDBC_ZIP="sqlite-jdbc-${SQLITE_JDBC_TAG}.zip"
SQLITE_JDBC_SRC_ROOT="sqlite-jdbc-${SQLITE_JDBC_TAG}"            # top-level dir inside the zip

SQLITE_AMALGAMATION_URL='https://www.sqlite.org/2026/sqlite-amalgamation-3530400.zip'
SQLITE_AMALGAMATION_SHA256='1e71ddf93849c6a6ecf58b827c0692073d2dd7ee40196158068f7b29f422e87d'
SQLITE_AMALGAMATION_DIR='sqlite-amalgamation-3530400'

SLF4J_URL='https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar'
SLF4J_SHA256='d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0'
SLF4J_FILE='slf4j-api-1.7.36.jar'

OS_NAME='SunOS'
OS_ARCH='x86_64'

# --- helpers ---------------------------------------------------------------
log() { printf '%s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    elif command -v digest >/dev/null 2>&1; then
        digest -a sha256 "$1"
    elif command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$1" | awk '{print $1}'
    else
        die 'no sha256 tool found (need sha256sum, digest or shasum)'
    fi
}

# fetch <url> <dest> <sha256>  — cache-first: a pre-copied, hash-valid file
# in <dest> is used without any network access (offline installs).
fetch() {
    if [ -f "$2" ] && [ "$(sha256_of "$2")" = "$3" ]; then
        log "cached (sha256 ok): $2"
        return 0
    fi
    log "downloading $1"
    curl -fL --retry 3 -o "$2" "$1" || die "download failed: $1 (no network? pre-seed the file instead, see docs/omnios.md)"
    got=$(sha256_of "$2")
    [ "$got" = "$3" ] || die "sha256 mismatch for $1
  got:  $got
  want: $3"
    log "sha256 verified: $2"
}

# resolve_cc <name-or-path>... — first candidate that exists/executable
resolve_cc() {
    for c in "$@"; do
        case $c in
            */*) [ -x "$c" ] && { printf '%s\n' "$c"; return 0; } ;;
            *)   command -v "$c" >/dev/null 2>&1 && { command -v "$c"; return 0; } ;;
        esac
    done
    return 1
}

# --- locate ourselves ------------------------------------------------------
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_ROOT="$SCRIPT_DIR/.build"
SRC_DIR="$BUILD_ROOT/sqlite-jdbc"
DL_DIR="$BUILD_ROOT/dl"
OUT_DIR="$SCRIPT_DIR/../../native/sunos/x86_64"

# --- prerequisites ---------------------------------------------------------
if [ -z "${JAVA_HOME:-}" ] && command -v javac >/dev/null 2>&1; then
    _javac=$(command -v javac)
    if command -v readlink >/dev/null 2>&1; then
        _javac=$(readlink -f "$_javac" 2>/dev/null || printf '%s' "$_javac")
    fi
    JAVA_HOME=$(CDPATH= cd -- "$(dirname -- "$_javac")/.." && pwd)
    export JAVA_HOME
    log "JAVA_HOME was not set - derived from javac: $JAVA_HOME"
fi
[ -n "${JAVA_HOME:-}" ] || die 'JAVA_HOME is not set and javac is not on PATH (JDK 21 required)'
[ -x "$JAVA_HOME/bin/javac" ] || die "JAVA_HOME does not contain bin/javac: $JAVA_HOME"
"$JAVA_HOME/bin/javac" -version 2>&1 | grep -q '21' || log "warning: expected JDK 21, got: $("$JAVA_HOME/bin/javac" -version 2>&1)"

command -v curl >/dev/null 2>&1 || die 'curl not found (pkg install web/curl)'
command -v perl >/dev/null 2>&1 || die 'perl not found (base system — needed by the upstream sqlite3.c patching step)'

if command -v gmake >/dev/null 2>&1; then
    MAKE=gmake
elif [ -x /usr/gnu/bin/make ] && /usr/gnu/bin/make --version 2>/dev/null | head -1 | grep -qi 'gnu make'; then
    MAKE=/usr/gnu/bin/make
elif command -v make >/dev/null 2>&1 && make --version 2>/dev/null | head -1 | grep -qi 'gnu make'; then
    MAKE=make
else
    die 'GNU make not found (pkg install developer/build/gnu-make)'
fi
MAKE_PATH=$(command -v "$MAKE" 2>/dev/null || printf '%s' "$MAKE")

if [ -n "${CC:-}" ]; then
    CC_BIN=$(resolve_cc "$CC") || die "CC=$CC is not executable"
else
    CC_BIN=$(resolve_cc /opt/gcc-14/bin/gcc /opt/gcc-10/bin/gcc gcc) \
        || die 'C compiler not found (pkg install developer/gcc14, or set CC)'
fi

log "uname: $(uname -srm 2>/dev/null || uname -a)"
log "java:   $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
log "make:   $($MAKE --version | head -1)"
log "cc:     $($CC_BIN --version | head -1)"

# --- 1. upstream sources from the pinned tag zip ---------------------------
# Cache-first (fetch): for offline use, copy sqlite-jdbc-3.53.4.0.zip into
# .build/dl/ with the pinned SHA-256 and no network is needed.
# Extracted with the JDK's `jar`: GitHub's .tar.gz is pax-format and Solaris
# tar chokes on it (typeflag 'g'), while .zip needs nothing but the JDK.
mkdir -p "$BUILD_ROOT" "$DL_DIR"
fetch "$SQLITE_JDBC_ZIP_URL" "$DL_DIR/$SQLITE_JDBC_ZIP" "$SQLITE_JDBC_ZIP_SHA256"

if [ -f "$SRC_DIR/Makefile" ] && [ -f "$SRC_DIR/VERSION" ] && [ -f "$SRC_DIR/amalgamation_version.sh" ]; then
    log "reusing existing source tree: $SRC_DIR"
else
    log "extracting $SQLITE_JDBC_ZIP"
    rm -rf "$SRC_DIR" "$BUILD_ROOT/src-extract"
    mkdir -p "$BUILD_ROOT/src-extract"
    (cd "$BUILD_ROOT/src-extract" && "$JAVA_HOME/bin/jar" xf "$DL_DIR/$SQLITE_JDBC_ZIP") \
        || die 'failed to unpack the source zip (used jar from JAVA_HOME)'
    [ -f "$BUILD_ROOT/src-extract/$SQLITE_JDBC_SRC_ROOT/Makefile" ] \
        || die "unexpected zip layout (expected top-level dir $SQLITE_JDBC_SRC_ROOT)"
    mv "$BUILD_ROOT/src-extract/$SQLITE_JDBC_SRC_ROOT" "$SRC_DIR"
    rm -rf "$BUILD_ROOT/src-extract"
    # .zip stores no permission bits — restore what the Makefile executes
    # directly ($(shell ./amalgamation_version.sh ...) runs at parse time):
    chmod +x "$SRC_DIR/amalgamation_version.sh"
fi
log "source tree ready (tag $SQLITE_JDBC_TAG, commit $SQLITE_JDBC_COMMIT)"

# --- 2. verified downloads (slf4j jar, sqlite amalgamation) ----------------
# The upstream Makefile downloads slf4j-api itself when the file is missing;
# we place it (verified) so that unverified curl inside make never runs.
fetch "$SLF4J_URL" "$DL_DIR/$SLF4J_FILE" "$SLF4J_SHA256"
mkdir -p "$SRC_DIR/target/classpath"
if [ ! -f "$SRC_DIR/target/classpath/slf4j-api.jar" ] \
    || [ "$(sha256_of "$SRC_DIR/target/classpath/slf4j-api.jar")" != "$SLF4J_SHA256" ]; then
    cp "$DL_DIR/$SLF4J_FILE" "$SRC_DIR/target/classpath/slf4j-api.jar"
fi

fetch "$SQLITE_AMALGAMATION_URL" "$DL_DIR/sqlite-amalgamation-3530400.zip" "$SQLITE_AMALGAMATION_SHA256"
AMAL_DIR="$BUILD_ROOT/amal"
if [ ! -f "$AMAL_DIR/$SQLITE_AMALGAMATION_DIR/sqlite3.c" ]; then
    rm -rf "$AMAL_DIR"
    mkdir -p "$AMAL_DIR"
    (cd "$AMAL_DIR" && "$JAVA_HOME/bin/jar" xf "$DL_DIR/sqlite-amalgamation-3530400.zip") \
        || die 'failed to unpack amalgamation (used jar from JAVA_HOME)'
fi
SQLITE_SOURCE="$AMAL_DIR/$SQLITE_AMALGAMATION_DIR"
[ -f "$SQLITE_SOURCE/sqlite3.c" ] && [ -f "$SQLITE_SOURCE/sqlite3.h" ] && [ -f "$SQLITE_SOURCE/sqlite3ext.h" ] \
    || die "amalgamation incomplete: $SQLITE_SOURCE"
log "sqlite amalgamation ready: $SQLITE_SOURCE"

# --- 3. neutralise upstream's "build sqlite from source" dependency chain --
# Even with SQLITE_SOURCE set from outside, upstream's make graph walks
#   $(SQLITE_OUT)/sqlite3.o -> target/sqlite-unpack.log
#     -> target/sqlite-<v>-amal.zip -> target/tmp-src-<v>/sqlite-amalgamation-<enc>.zip
#     -> target/sqlite-src.log -> target/sqlite-src-<enc>.zip
# and would then download the SQLite source tree, run ./configure and a
# nested 'make' (needing zip/unzip and GNU make under the name 'make').
# None of those files is ever read in our flow — every compile input comes
# from SQLITE_SOURCE (the verified amalgamation above) — so we pre-create the
# chain as empty placeholders in dependency order; make's timestamp check
# then marks it up to date and never runs those recipes.
version=''
# shellcheck disable=SC1091
. "$SRC_DIR/VERSION"                       # upstream file: version=3.53.4
[ -n "$version" ] || die 'could not read version from upstream VERSION file'
AMAL_ENC=$(sh "$SRC_DIR/amalgamation_version.sh" "$version")   # 3.53.4 -> 3530400
mkdir -p "$SRC_DIR/target/tmp-src.$version"
: > "$SRC_DIR/target/sqlite-$version-src.zip"
: > "$SRC_DIR/target/sqlite-src.log"
: > "$SRC_DIR/target/tmp-src.$version/sqlite-amalgamation-$AMAL_ENC.zip"
: > "$SRC_DIR/target/sqlite-$version-amal.zip"
: > "$SRC_DIR/target/sqlite-unpack.log"

# Nested make invocations upstream (sqlite's own build, only reached if the
# chain above ever rebuilds) call plain 'make' — shadow it with GNU make.
mkdir -p "$BUILD_ROOT/bin"
ln -sf "$MAKE_PATH" "$BUILD_ROOT/bin/make"

# --- 4. native build (upstream Makefile, Default profile for SunOS-x86_64) -
# OS_NAME/OS_ARCH on the command line skip the upstream OS detection run.
# SunOS-x86_64 is not in upstream's known_targets, so the generic "Default"
# profile applies: gcc, -fPIC, -shared, libsqlitejdbc.so — correct for
# illumos/i86pc. SQLITE_SOURCE points at our verified amalgamation.
# The JNI header NativeDB.h is generated by the 'jni-header' prerequisite via
# 'javac -h' (JDK 21). CC on the command line overrides the profile's 'gcc'
# so /opt/gcc-14/bin/gcc is used even when it is not on PATH.
#
# -Wno-implicit-function-declaration: GCC 14 makes implicit function
# declarations a hard error by default. Upstream 3.53.4's own Makefile
# patches `rc = RegisterExtensionFunctions(db)` into sqlite3.c (perl recipe
# at `opendb_out:`), but the function is DEFINED in
# src/main/ext/extension-functions.c, which that same recipe appends to the
# END of sqlite3.c — i.e. after the call site. Every gcc <= 13 build of
# stock 3.53.4 therefore compiled this with only a warning; the symbol
# resolves within the same translation unit and the call is ABI-safe
# (int RegisterExtensionFunctions(sqlite3*)). The flag restores that exact
# pre-GCC-14 behavior without touching upstream sources, the SQLite version,
# or the JNI approach.
cd "$SRC_DIR"
# The upstream strip recipe copies to /tmp and has no error handling — a
# picky/missing strip would fail the build at the very last step for a purely
# cosmetic gain, so strip is skipped (STRIP=true is a no-op for make).
# shellcheck disable=SC2086
PATH="$BUILD_ROOT/bin:$PATH" "$MAKE" native \
    JAVA_HOME="$JAVA_HOME" \
    OS_NAME="$OS_NAME" \
    OS_ARCH="$OS_ARCH" \
    SQLITE_SOURCE="$SQLITE_SOURCE" \
    CC="$CC_BIN -Wno-implicit-function-declaration" \
    STRIP=true \
    || die 'native build failed'

BUILT=$(find "$SRC_DIR/target" -name 'libsqlitejdbc.so' -path "*-$OS_NAME-$OS_ARCH/*" 2>/dev/null | head -1)
[ -n "$BUILT" ] && [ -f "$BUILT" ] || die 'libsqlitejdbc.so not produced by the build'

# --- 5. install where the Gradle build looks for it ------------------------
mkdir -p "$OUT_DIR"
cp "$BUILT" "$OUT_DIR/libsqlitejdbc.so"

log ''
log "installed: $OUT_DIR/libsqlitejdbc.so"
log "sha256:    $(sha256_of "$OUT_DIR/libsqlitejdbc.so")"
log "size:      $(wc -c < "$OUT_DIR/libsqlitejdbc.so") bytes"
log ''
log 'Next: cd ../.. && ./gradlew test   (OmniOS: ./gradlew test now finds the lib)'
