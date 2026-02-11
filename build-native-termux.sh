#!/usr/bin/env sh
set -eu

PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
TMPDIR="${TMPDIR:-$PREFIX/tmp}"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

detect_abi() {
  if command -v getprop >/dev/null 2>&1; then
    abi="$(getprop ro.product.cpu.abi 2>/dev/null || true)"
    case "$abi" in
      arm64-v8a|armeabi-v7a|x86_64|x86) echo "$abi"; return 0 ;;
    esac
  fi

  arch="$(uname -m)"
  case "$arch" in
    aarch64) echo "arm64-v8a" ;;
    armv7l|armv8l|arm) echo "armeabi-v7a" ;;
    x86_64|amd64) echo "x86_64" ;;
    i?86) echo "x86" ;;
    *) echo "arm64-v8a" ;;
  esac
}

ABI="${1:-$(detect_abi)}"

case "$ABI" in
  arm64-v8a)
    TOOL_PREFIX="aarch64-linux-android"
    BOOTSTRAP_ARCH="aarch64"
    ;;
  armeabi-v7a)
    TOOL_PREFIX="arm-linux-androideabi"
    BOOTSTRAP_ARCH="arm"
    ;;
  x86_64)
    TOOL_PREFIX="x86_64-linux-android"
    BOOTSTRAP_ARCH="x86_64"
    ;;
  x86)
    TOOL_PREFIX="i686-linux-android"
    BOOTSTRAP_ARCH="i686"
    ;;
  *)
    echo "Unsupported ABI: $ABI" >&2
    exit 2
    ;;
esac

CC="$PREFIX/bin/${TOOL_PREFIX}-clang"
CXX="$PREFIX/bin/${TOOL_PREFIX}-clang++"

if [ ! -x "$CC" ]; then
  echo "Missing compiler: $CC" >&2
  exit 1
fi

mkdir -p "$TMPDIR"
WORKDIR="$(mktemp -d "$TMPDIR/termux-native.XXXXXX")"
trap 'rm -rf "$WORKDIR"' EXIT

ensure_bootstrap_zip() {
  zip_path="$PROJECT_DIR/app/src/main/cpp/bootstrap-$BOOTSTRAP_ARCH.zip"
  if [ -f "$zip_path" ]; then
    return 0
  fi

  ANDROID_HOME="${ANDROID_HOME:-$HOME/.local/android}"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

  (cd "$PROJECT_DIR" && TERMUX_PREBUILT_NATIVE=1 ./gradlewa --quiet --console=plain :app:downloadBootstraps --no-daemon)
  if [ ! -f "$zip_path" ]; then
    echo "Bootstrap zip still missing after download: $zip_path" >&2
    exit 1
  fi
}

build_termux_bootstrap() {
  ensure_bootstrap_zip

  out_dir="$PROJECT_DIR/app/src/main/jniLibs/$ABI"
  mkdir -p "$out_dir"

  (cd "$PROJECT_DIR/app/src/main/cpp" && "$CC" -fPIC -c termux-bootstrap-zip.S -o "$WORKDIR/termux-bootstrap-zip.o")
  "$CC" -fPIC -I"$PREFIX/include" -c "$PROJECT_DIR/app/src/main/cpp/termux-bootstrap.c" -o "$WORKDIR/termux-bootstrap.o"
  "$CC" -shared -o "$out_dir/libtermux-bootstrap.so" "$WORKDIR/termux-bootstrap-zip.o" "$WORKDIR/termux-bootstrap.o"
}

build_terminal_emulator() {
  out_dir="$PROJECT_DIR/terminal-emulator/src/main/jniLibs/$ABI"
  mkdir -p "$out_dir"

  "$CC" -shared -fPIC -I"$PREFIX/include" -o "$out_dir/libtermux.so" "$PROJECT_DIR/terminal-emulator/src/main/jni/termux.c"
}

build_local_socket() {
  out_dir="$PROJECT_DIR/termux-shared/src/main/jniLibs/$ABI"
  mkdir -p "$out_dir"

  "$CXX" -shared -fPIC -I"$PREFIX/include" -std=c++17 -o "$out_dir/liblocal-socket.so" \
    "$PROJECT_DIR/termux-shared/src/main/cpp/local-socket.cpp" -llog
}

build_termux_bootstrap
build_terminal_emulator
build_local_socket

echo "Built prebuilt JNI libs for $ABI"
