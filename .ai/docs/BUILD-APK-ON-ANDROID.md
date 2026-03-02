# Building Android Projects on Android (Termux)

A troubleshooting guide for building Android apps—including Termux itself—directly on Termux.

---

## Quick Reference

| Component | Status | Notes |
|-----------|--------|-------|
| Java/Kotlin compilation | Works | Use `openjdk-17` for Gradle 7.x projects |
| Gradle daemon | Works | Memory-constrained; use `--no-daemon` or limit heap |
| AAPT2 | Works | Must override with Termux's native binary |
| D8/R8 (dexing) | Works | Java-based, runs fine |
| NDK (ndk-build/cmake) | Fails | No aarch64 host toolchains in official NDK |
| Unit tests (JVM) | Works | Robolectric, JUnit, etc. |

---

## Prerequisites

```bash
pkg install openjdk-17 gradle aapt aapt2
```

For projects requiring Java 21:
```bash
pkg install openjdk-21
```

---

## Critical Configuration

### gradle.properties

Add these to your project's `gradle.properties` or `~/.gradle/gradle.properties`:

```properties
# REQUIRED: Use Termux's native AAPT2 (Maven's is x86-64)
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2

# Memory limits (adjust based on device RAM)
org.gradle.jvmargs=-Xmx1536m

# Suppress SDK version warnings
android.suppressUnsupportedCompileSdk=36

# Optional: Debug logging
org.gradle.logging.level=info
```

### Environment Variables

```bash
export ANDROID_HOME="$HOME/.local/android"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

---

## The NDK Problem

### Why Native Builds Fail

The Android NDK only ships with `linux-x86_64` host toolchains:

```
ndk/26.1.10909125/toolchains/llvm/prebuilt/
└── linux-x86_64/    # No linux-aarch64 directory exists
```

When `ndk-build` or CMake runs on Termux (aarch64), you get:

```
ERROR: Unknown host CPU architecture: aarch64
```

### What Works vs. What Doesn't

| Task | Works? | Reason |
|------|--------|--------|
| `:termux-shared:test` | Yes | JVM-only, no native build triggered |
| `:termux-shared:assembleDebug` | No | Triggers `ndk-build` |
| `:app:assembleDebug` | No | Triggers `ndk-build` + bootstrap download |
| `:terminal-view:assembleDebug` | Yes | No native code |
| `:terminal-emulator:assembleDebug` | Yes | No native code |

### Workarounds

1. **Skip native build tasks** (if module allows):
   ```bash
   ./gradlew :terminal-view:assembleDebug  # Java-only module
   ```

2. **Build native libs on x86 machine, copy to Termux**:
   - Build on Linux x86_64 or via CI
   - Copy `*.so` files to `src/main/jniLibs/<abi>/`
   - Disable `externalNativeBuild` in `build.gradle`

3. **Use Termux's native clang** (experimental, for simple projects):
   ```bash
   pkg install clang ndk-sysroot
   ```
   Then manually compile and place `.so` files.

4. **Cross-compile on a real Linux box** and sync artifacts.

---

## Termux-App Specific

### Project Structure

```
termux-app/
├── app/                    # Main app (has native: bootstrap installer)
├── terminal-emulator/      # Pure Java
├── terminal-view/          # Pure Java
└── termux-shared/          # Library (has native code)
```

### The `gw` Wrapper

`termux-app/gw` forces Java 17 before invoking Gradle:

```bash
#!/bin/bash
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
./gradlew "$@"
```

### Building Java-Only Modules

```bash
cd ~/p/gh/termux/termux-app

# These work:
./gw :terminal-emulator:assembleDebug
./gw :terminal-view:assembleDebug
./gw :termux-shared:test  # JVM tests only

# These fail (require NDK):
./gw :app:assembleDebug
./gw :termux-shared:assembleDebug
```

### Full Build Attempt (Will Fail at NDK)

```bash
ANDROID_SDK_ROOT="$ANDROID_HOME" \
JITPACK_NDK_VERSION=26.1.10909125 \
./gw --console=plain :termux-shared:assembleDebug --no-daemon
```

Error:
```
ERROR: Unknown host CPU architecture: aarch64
```

---

## Common Errors & Fixes

### 1. AAPT2 "Syntax error" or Binary Format Error

**Symptom:**
```
Syntax error: "(" unexpected
```

**Cause:** Gradle downloaded x86-64 AAPT2 binary.

**Fix:** Add to `gradle.properties`:
```properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

### 2. Missing android.jar

**Symptom:**
```
error: failed to load include path .../platforms/android-XX/android.jar
```

**Fix:** Install SDK platform:
```bash
sdkmanager "platforms;android-34"
```

Or download manually and place in `$ANDROID_HOME/platforms/android-34/`.

### 3. OutOfMemoryError

**Symptom:**
```
Java heap space
```

**Fix:** Reduce heap or kill other processes:
```properties
org.gradle.jvmargs=-Xmx1024m
```

Use `--no-daemon` to avoid persistent memory usage:
```bash
./gradlew --no-daemon assembleDebug
```

### 4. Native Platform Library Failure

**Symptom:**
```
NativeIntegrationLinkageException
libstdc++.so.6 not found
```

**Status:** Non-fatal. Gradle falls back to Java-based file operations.

### 5. Read-Only Filesystem

**Symptom:**
```
Read-only file system
```

**Fix:** Ensure you're not writing outside Termux's allowed paths. Use `$TMPDIR` instead of `/tmp`.

### 6. SDK License Not Accepted

**Fix:**
```bash
yes | sdkmanager --licenses
```

---

## Android SDK Setup

### Minimal Installation

```bash
mkdir -p ~/.local/android
cd ~/.local/android

# Download command-line tools (get URL from developer.android.com)
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-XXXXXX_latest.zip
unzip commandlinetools-linux-*.zip
mkdir -p cmdline-tools
mv cmdline-tools latest  # Rename extracted folder
mv latest cmdline-tools/

# Accept licenses
yes | ./cmdline-tools/latest/bin/sdkmanager --licenses

# Install essentials
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

### What You Actually Need

| Component | Required For |
|-----------|--------------|
| `platforms;android-XX` | Compilation (android.jar) |
| `build-tools;XX.0.0` | D8, zipalign (but prefer Termux's aapt2) |
| `ndk;XX.X.XXXXXX` | Native builds (won't work on aarch64) |

---

## Performance Tips

1. **Use `--no-daemon`** for one-off builds (saves memory)
2. **Keep daemon running** for iterative development (saves startup time)
3. **Disable parallel builds** if RAM-constrained:
   ```properties
   org.gradle.parallel=false
   ```
4. **Use build cache**:
   ```properties
   org.gradle.caching=true
   ```
5. **Run Gradle in Termux:Float** if your main Termux session needs to stay responsive

---

## File Paths Reference

```
Termux prefix:     $PREFIX = /data/data/com.termux/files/usr
Home:              $HOME   = /data/data/com.termux/files/home
Temp:              $TMPDIR = /data/data/com.termux/files/usr/tmp
Java 17:           $PREFIX/lib/jvm/java-17-openjdk
Java 21:           $PREFIX/lib/jvm/java-21-openjdk
AAPT2:             $PREFIX/bin/aapt2
Gradle cache:      ~/.gradle/
Android SDK:       ~/.local/android/ (user-installed)
```

---

## CI/Hybrid Workflow

Since native builds don't work on-device, consider:

1. **Develop Java/Kotlin** on Termux (edit, lint, test)
2. **Build native + APK** via GitHub Actions or local x86 machine
3. **Install APK** via `adb install` or direct copy

Example GitHub Actions excerpt for Termux-app:
```yaml
- uses: actions/setup-java@v3
  with:
    java-version: '17'
- run: ./gradlew assembleDebug
```

---

## Summary

| Goal | Possible on Termux? |
|------|---------------------|
| Edit and lint Android code | Yes |
| Run JVM unit tests | Yes |
| Compile Java/Kotlin to DEX | Yes |
| Build APK (no native code) | Yes |
| Build APK (with native code) | No (NDK limitation) |
| Build Termux-app fully | No (requires ndk-build) |

The fundamental blocker is that **Google doesn't provide aarch64 host toolchains in the NDK**. Until that changes, native Android builds on Android require external compilation.

---

## A Note of Optimism

Despite these limitations, **building Android apps on Termux is absolutely viable** for many projects. Using the tips in this guide combined with web searches for project-specific issues, we successfully built and ran two non-trivial apps entirely on-device:

- **RootlessJamesDSP** — A system-wide audio DSP app with native C/C++ components (libjamesdsp, libsamplerate, JNI wrappers). Required CMake configuration tweaks and proper NDK/AAPT2 setup.

- **Wabbitemu-Android** — A TI graphing calculator emulator with native code. Built successfully after configuring AAPT2 override and memory limits.

Both apps have complex build systems (Kotlin DSL, CMake, native libraries) and were compiled to working APKs on a phone running Termux. The key is patience, proper `gradle.properties` configuration, and occasionally pre-building native components elsewhere when the NDK host limitation bites.

**If your project's native code is already compiled** (e.g., pulled from CI artifacts or a prior build), the rest of the Android build pipeline works remarkably well on Termux.
