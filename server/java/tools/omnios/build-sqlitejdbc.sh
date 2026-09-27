#!/bin/sh
# ---------------------------------------------------------------------------
# Build libsqlitejdbc.so for OmniOS CE r151054 x86_64 (SunOS, i86pc)
#
# Builds the SQLite-JDBC *native* library from pinned, traceable sources so
# that `./gradlew test` / `bootRun` work on OmniOS with the unmodified Xerial
# driver jar (org.xerial:sqlite-jdbc:3.53.4.0).
#
#   Upstream (JNI layer + build):  xerial/sqlite-jdbc tag 3.53.4.0
#                                  commit cab7981c19ce04d691f0675f0b2586afc2bbf803
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
# Prerequisites on OmniOS:
#   pkg install developer/git developer/build/gnu-make developer/gcc-14
#   JDK 21 with JAVA_HOME set (e.g. an OpenJDK/Zulu 21 build for illumos)
#   curl (shipped) and network access to github.com, sqlite.org, repo1.maven.org
#
# Usage:
#   cd server/java/tools/omnios
#   JAVA_HOME=/usr/jdk/jdk-21 ./build-sqlitejdbc.sh
# ---------------------------------------------------------------------------
set -eu

# --- pinned inputs (update tag + commit + amalgamation + hashes together) --
SQLITE_JDBC_REPO='https://github.com/xerial/sqlite-jdbc.git'
SQLITE_JDBC_TAG='3.53.4.0'
SQLITE_JDBC_COMMIT='cab7981c19ce04d691f0675f0b2586afc2bbf803'

SQLITE_AMALGAMATION_URL='https://www.sqlite.org/2026/sqlite-amalgamation-3530400.zip'
SQLITE_AMALGAMATION_SHA256='1e71ddf93849c6a6ecf58b827c0692073d2dd7ee40196158068f7b29f422e87d'
SQLITE_AMALGAMATION_DIR='sqlite-amalgamation-3530400'

SLF4J_URL='https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar'
SLF4J_SHA256='d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0'

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

# fetch <url> <dest> <sha256>
fetch() {
    if [ -f "$2" ] && [ "$(sha256_of "$2")" = "$3" ]; then
        log "cached (sha256 ok): $2"
        return 0
    fi
    log "downloading $1"
    curl -fL --retry 3 -o "$2" "$1" || die "download failed: $1"
    got=$(sha256_of "$2")
    [ "$got" = "$3" ] || die "sha256 mismatch for $1
  got:  $got
  want: $3"
    log "sha256 verified: $2"
}

# --- locate ourselves ------------------------------------------------------
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_ROOT="$SCRIPT_DIR/.build"
SRC_DIR="$BUILD_ROOT/sqlite-jdbc"
DL_DIR="$BUILD_ROOT/dl"
OUT_DIR="$SCRIPT_DIR/../../native/sunos/x86_64"

# --- prerequisites ---------------------------------------------------------
[ -n "${JAVA_HOME:-}" ] || die 'JAVA_HOME is not set (JDK 21 required)'
[ -x "$JAVA_HOME/bin/javac" ] || die "JAVA_HOME does not contain bin/javac: $JAVA_HOME"
"$JAVA_HOME/bin/javac" -version 2>&1 | grep -q '21' || log "warning: expected JDK 21, got: $("$JAVA_HOME/bin/javac" -version 2>&1)"

command -v git >/dev/null 2>&1 || die 'git not found (pkg install developer/git)'
command -v curl >/dev/null 2>&1 || die 'curl not found'
command -v perl >/dev/null 2>&1 || die 'perl not found (pkg install runtime/perl-5xx) — needed by the upstream sqlite3.c patching step'

if command -v gmake >/dev/null 2>&1; then
    MAKE=gmake
elif command -v make >/dev/null 2>&1 && make --version 2>/dev/null | head -1 | grep -qi 'gnu make'; then
    MAKE=make
else
    die 'GNU make not found (pkg install developer/build/gnu-make)'
fi

CC_BIN=${CC:-gcc}
command -v "$CC_BIN" >/dev/null 2>&1 || die "C compiler '$CC_BIN' not found (pkg install developer/gcc-14, or set CC)"

log "uname: $(uname -srm 2>/dev/null || uname -a)"
log "java:   $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
log "make:   $($MAKE --version | head -1)"
log "cc:     $($CC_BIN --version | head -1)"

# --- 1. upstream sources at the pinned commit ------------------------------
mkdir -p "$BUILD_ROOT" "$DL_DIR"
if [ -d "$SRC_DIR/.git" ]; then
    log "reusing existing checkout: $SRC_DIR"
else
    log "cloning xerial/sqlite-jdbc @ $SQLITE_JDBC_TAG (depth 1)"
    git clone --depth 1 --branch "$SQLITE_JDBC_TAG" "$SQLITE_JDBC_REPO" "$SRC_DIR" \
        || die 'git clone failed'
fi
head_commit=$(git -C "$SRC_DIR" rev-parse HEAD)
[ "$head_commit" = "$SQLITE_JDBC_COMMIT" ] || die "commit mismatch for tag $SQLITE_JDBC_TAG
  got:  $head_commit
  want: $SQLITE_JDBC_COMMIT
(refuse to build from an unexpected revision)"
log "source commit verified: $head_commit"

# --- 2. verified downloads (slf4j jar, sqlite amalgamation) ----------------
# The upstream Makefile downloads slf4j-api itself when the file is missing;
# we pre-place it (verified) so that unverified curl inside make never runs.
mkdir -p "$SRC_DIR/target/classpath"
fetch "$SLF4J_URL" "$SRC_DIR/target/classpath/slf4j-api.jar" "$SLF4J_SHA256"

fetch "$SQLITE_AMALGAMATION_URL" "$DL_DIR/sqlite-amalgamation-3530400.zip" "$SQLITE_AMALGAMATION_SHA256"
AMAL_DIR="$BUILD_ROOT/amal"
if [ ! -f "$AMAL_DIR/$SQLITE_AMALGAMATION_DIR/sqlite3.c" ]; then
    rm -rf "$AMAL_DIR"
    mkdir -p "$AMAL_DIR"
    (cd "$AMAL_DIR" && "$JAVA_HOME/bin/jar" xf "$DL_DIR/sqlite-amalgamation-3530400.zip") \
        || die 'failed to unpack amalgamation (used jar from JAVA_HOME)'
fi
SQLITE_SOURCE="$AMAL_DIR/$SQLITE_AMALGAMATION_DIR"
[ -f "$SQLITE_SOURCE/sqlite3.c" ] && [ -f "$SQLITE_SOURCE/sqlite3.h" ] || die "amalgamation incomplete: $SQLITE_SOURCE"
log "sqlite amalgamation ready: $SQLITE_SOURCE"

# --- 3. native build (upstream Makefile, Default profile for SunOS-x86_64) -
# OS_NAME/OS_ARCH on the command line skip the upstream OS detection run.
# SunOS-x86_64 is not in upstream's known_targets, so the generic "Default"
# profile applies: gcc, -fPIC, -shared, libsqlitejdbc.so — correct for
# illumos/i86pc. SQLITE_SOURCE points at our verified amalgamation (the
# ifneq-guard in Makefile.common then skips the upstream zip handling).
# The JNI header NativeDB.h is generated by the 'jni-header' prerequisite via
# 'javac -h' (JDK 21).
cd "$SRC_DIR"
STRIP_ARG=''
command -v strip >/dev/null 2>&1 || STRIP_ARG='STRIP=true'

# shellcheck disable=SC2086
"$MAKE" native \
    JAVA_HOME="$JAVA_HOME" \
    OS_NAME="$OS_NAME" \
    OS_ARCH="$OS_ARCH" \
    SQLITE_SOURCE="$SQLITE_SOURCE" \
    CC="$CC_BIN" \
    $STRIP_ARG \
    || die 'native build failed'

BUILT=$(find "$SRC_DIR/target" -name 'libsqlitejdbc.so' -path "*-$OS_NAME-$OS_ARCH/*" 2>/dev/null | head -1)
[ -n "$BUILT" ] && [ -f "$BUILT" ] || die 'libsqlitejdbc.so not produced by the build'

# --- 4. install where the Gradle build looks for it ------------------------
mkdir -p "$OUT_DIR"
cp "$BUILT" "$OUT_DIR/libsqlitejdbc.so"

log ''
log "installed: $OUT_DIR/libsqlitejdbc.so"
log "sha256:    $(sha256_of "$OUT_DIR/libsqlitejdbc.so")"
log "size:      $(wc -c < "$OUT_DIR/libsqlitejdbc.so") bytes"
log ''
log 'Next: cd ../.. && ./gradlew test   (OmniOS: ./gradlew test now finds the lib)'
