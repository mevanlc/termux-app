# BUILD-TERMUX-ON-TERMUX.md

A practical cookbook for building **Termux-app** on Android **inside Termux**.

This guide assumes the repo layout used here:

- `termux-app/` (the Android app project)
- `termux-api/` (not needed for the app build below)

## What Works (and Why)

Building Termux-app on-device is possible if you avoid the official Android NDK host toolchain
(`ndk-build`), which is typically `linux-x86_64` only.

This workflow uses:

- **Java 17** for Gradle 7.2
- Termux’s **native `aapt2`** (instead of Gradle’s downloaded x86_64 binary)
- Termux’s Android-targeting **clang** (`$PREFIX/bin/*-linux-android-clang`)
- Termux’s **NDK sysroot/libs** (`ndk-sysroot`, `ndk-multilib`)
- Prebuilt JNI `.so` dropped into `src/main/jniLibs/<abi>/`

## Prereqs

Install build tools (adjust as needed for your device/storage):

```sh
pkg update
pkg install -y git openjdk-17 aapt2 aapt clang ndk-sysroot ndk-multilib
```

You need an Android SDK with commandline-tools, platforms, and build-tools. This guide uses:

```sh
export ANDROID_HOME="$HOME/.local/android"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

If your SDK is already set up elsewhere, just export `ANDROID_HOME`/`ANDROID_SDK_ROOT` accordingly.

## Repo Setup

```sh
cd ~/p/gh/termux/termux-app
git pull
```

This repo includes:

- `termux-app/gw` → wrapper that forces Java 17 for Gradle
- `termux-app/build-native-termux.sh` → builds JNI `.so` via Termux clang into `jniLibs/`

## 1) Build the JNI libraries (no official NDK needed)

Build for your device ABI (recommended: `arm64-v8a` on most modern phones):

```sh
cd ~/p/gh/termux/termux-app
./build-native-termux.sh arm64-v8a
```

To ship a “universal” APK that contains multiple ABIs, run the script multiple times before building:

```sh
./build-native-termux.sh arm64-v8a
./build-native-termux.sh armeabi-v7a
# ./build-native-termux.sh x86_64
# ./build-native-termux.sh x86
```

Notes:
- The script uses `$PREFIX/tmp` via `TMPDIR` if set; it defaults to `$PREFIX/tmp`.
- Bootstrap ZIPs are downloaded into `termux-app/app/src/main/cpp/bootstrap-*.zip` if missing.

## 2) Build the APK (skip externalNativeBuild)

Set `TERMUX_PREBUILT_NATIVE=1` so Gradle doesn’t try to run `ndk-build`:

```sh
cd ~/p/gh/termux/termux-app
TERMUX_PREBUILT_NATIVE=1 ANDROID_SDK_ROOT="$ANDROID_HOME" \
  ./gw --quiet --console=plain :app:assembleDebug --no-daemon
```

APK output:

```sh
ls -1 app/build/outputs/apk/debug/*.apk
```

On this setup you should see:

- `app/build/outputs/apk/debug/termux-app_apt-android-7-debug_universal.apk`

## 3) Install

If you can install directly:

```sh
pkg install -y android-tools
adb install -r app/build/outputs/apk/debug/termux-app_apt-android-7-debug_universal.apk
```

If you’re using SAF/file manager export, copy the APK from that path.

## Troubleshooting

### AAPT2 “Syntax error: ( unexpected”

This happens when Gradle downloads an x86_64 `aapt2`. The fix is to force Termux’s native aapt2.

This repo sets:

- `termux-app/gradle.properties:1` → `android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`

### “NDK not configured… Preferred NDK version is …”

You’re configuring `externalNativeBuild` somewhere. Build with:

- `TERMUX_PREBUILT_NATIVE=1`

and make sure you are not invoking tasks that force native build.

### Gradle/Java incompatibility (`Unsupported class file major version 65`)

Use Java 17:

- Run Gradle via `termux-app/gw` (it sets `JAVA_HOME` to `$PREFIX/lib/jvm/java-17-openjdk`)

### Reducing Gradle output

Prefer:

```sh
./gw --quiet --console=plain ...
```

## Clean Rebuild

```sh
cd ~/p/gh/termux/termux-app
./gw --quiet --console=plain clean --no-daemon
rm -rf app/src/main/jniLibs terminal-emulator/src/main/jniLibs termux-shared/src/main/jniLibs
```

