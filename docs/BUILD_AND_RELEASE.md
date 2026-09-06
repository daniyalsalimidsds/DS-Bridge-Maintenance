# ساخت، آزمون و انتشار

## پیش‌نیازها

- JDK 17
- Android SDK 35 و Build Tools 35.0.0
- Node.js 20 یا جدیدتر
- Python 3.11+ با `openpyxl` فقط برای بازتولید شِما

## بازتولید شِمای اکسل

```bash
python3 tools/generate_bridge_schema.py \
  reference/شناسنامه_فنی_پل_نشریه_367.xlsx \
  app/src/main/assets/data/bridge-profile-schema.js
```

## کنترل و ساخت

```bash
python3 tools/check_bridge_source.py
node --test tests/js/*.test.js
./gradlew testDebugUnitTest lintRelease assembleDebugAndroidTest assembleRelease
```

APK بدون امضا در `app/build/outputs/apk/release/app-release-unsigned.apk` ساخته می‌شود. CI هویت `ir.bridge.maintenance`، نسخه `1.5.0` و `versionCode 10500` را با `aapt` کنترل می‌کند.

## امضا

کلید خصوصی نباید commit شود. Gradle فقط وقتی چهار property زیر ارائه شوند release را امضا می‌کند:

- `BRIDGE_STORE_FILE`
- `BRIDGE_STORE_PASSWORD`
- `BRIDGE_KEY_ALIAS`
- `BRIDGE_KEY_PASSWORD`

پس از امضا این کنترل‌ها انجام شوند:

```bash
apksigner verify --verbose --print-certs Bridge_Maintenance_v1.5.0.apk
zipalign -c -p 4 Bridge_Maintenance_v1.5.0.apk
sha256sum Bridge_Maintenance_v1.5.0.apk
```

فایل credential تحویلی باید جدا از APK نگهداری شود و دسترسی آن محدود باشد. از یک کلید ثابت برای تمام به‌روزرسانی‌های بعدی همین applicationId استفاده کنید؛ گم‌شدن کلید، به‌روزرسانی مستقیم نسخه نصب‌شده را مختل می‌کند.
