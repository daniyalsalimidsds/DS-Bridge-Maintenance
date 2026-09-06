#!/usr/bin/env bash
set -euo pipefail

adb wait-for-device

wait_for_android_runtime() {
  local deadline=$((SECONDS + 240))
  while (( SECONDS < deadline )); do
    if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] \
      && adb shell service check package 2>/dev/null | grep -q 'found' \
      && adb shell pm path android >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  adb shell getprop 2>/dev/null | tail -n 80 || true
  echo "Android runtime did not expose a healthy package service within 240 seconds." >&2
  return 1
}

APP_APK="app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

if [[ ! -s "$APP_APK" || ! -s "$TEST_APK" ]]; then
  chmod +x gradlew
  ./gradlew --no-daemon assembleDebug assembleDebugAndroidTest 2>&1 | tee instrumentation-build.log
fi

test -s "$APP_APK"
test -s "$TEST_APK"

wait_for_android_runtime
adb install --no-streaming -r "$APP_APK"
wait_for_android_runtime
adb install --no-streaming -r "$TEST_APK"

set +e
adb shell am instrument -w ir.bridge.maintenance.test/androidx.test.runner.AndroidJUnitRunner > instrumentation.log 2>&1
status=$?
set -e

cat instrumentation.log

test "$status" -eq 0
if grep -q 'FAILURES!!!' instrumentation.log; then
  exit 1
fi
grep -Eq 'OK \([1-9][0-9]* tests?\)' instrumentation.log
