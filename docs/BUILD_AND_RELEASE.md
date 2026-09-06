# ساخت و تحویل ۱.۶.۰

## پیش‌نیاز و ساخت

JDK 17، Android SDK 35، Build Tools 35.0.0، Node.js 20+ و Python 3.11+ لازم‌اند. `openpyxl` فقط برای بازتولید استخراج اکسل لازم است. وابستگی جدیدی به runtime برنامه افزوده نشده است.

```bash
node --test tests/js/*.test.js
python3 tools/check_bridge_source.py
./gradlew --no-daemon testDebugUnitTest lintRelease assembleDebug assembleDebugAndroidTest assembleRelease
```

خروجی انتشار: `app/build/outputs/apk/release/app-release-unsigned.apk`. هویت قابل انتظار: `ir.bridge.maintenance`، `versionName=1.6.0`، `versionCode=10600`. CI هویت و zip alignment را بررسی می‌کند.

## آزمون اندروید

پس از روشن‌کردن شبیه‌ساز یا اتصال دستگاه مجاز:

```bash
bash tools/run_direct_instrumentation.sh
```

آزمون‌ها شامل پایگاه داده، مهاجرت، حذف، پشتیبان/بازیابی، گزارش، امضا و تعامل فرم هستند. آزمون‌های ۱.۶.۰ خروجی‌های فارسی بسیار بلند و تصاویر حالت روز/شب و متن بزرگ را در `qa-artifacts/` ذخیره می‌کنند. نتیجه موفق ساخت به‌تنهایی نتیجه موفق آزمون اندروید نیست؛ هر دو job در CI باید موفق باشند.

## بازتولید داده مرجع

```bash
python3 tools/generate_bridge_schema.py reference/شناسنامه_فنی_پل_نشریه_367.xlsx app/src/main/assets/data/bridge-profile-schema.js
python3 tools/import_municipal_catalog.py /path/to/VELAYAT.xlsx
node tools/audit_scoring_catalog.js
```

فایل خام VELAYAT حاوی عکس و اطلاعات پل است و به مخزن اضافه نشده؛ کاتالوگ استخراج‌شده، نشانی سلول‌ها و SHA-256 آن ثبت شده‌اند و برای ساخت برنامه کافی‌اند. ابزار استخراج ورودی را تغییر نمی‌دهد. ممیزی تولیدشده را پس از هر تغییر نگاشت بازبینی کنید.

## امضا و نصب ارتقا

کلید ثابت نسخه ۱.۵.۰ را بیرون مخزن نگه دارید. Gradle در صورت وجود چهار property زیر release را امضا می‌کند. مقدار رمزها را در فایل خصوصی Gradle با دسترسی محدود قرار دهید؛ آن‌ها را در فرمان قابل ثبت در تاریخچه ننویسید.

```properties
BRIDGE_STORE_FILE=/private/path/release.jks
BRIDGE_STORE_PASSWORD=YOUR_PRIVATE_VALUE
BRIDGE_KEY_ALIAS=YOUR_EXISTING_ALIAS
BRIDGE_KEY_PASSWORD=YOUR_PRIVATE_VALUE
```

برای امضای APK از قبل ساخته‌شده، ابتدا zipalign و سپس apksigner اجرا شود. پس از امضا:

```bash
apksigner verify --verbose --print-certs Bridge_Maintenance_v1.6.0.apk
zipalign -c -p 4 Bridge_Maintenance_v1.6.0.apk
sha256sum Bridge_Maintenance_v1.6.0.apk
```

اثر انگشت گواهی را با APK نسخه ۱.۵.۰ مقایسه کنید. پیش از نصب ارتقا از داخل برنامه پشتیبان کامل بگیرید و APK جدید را روی نسخه موجود نصب کنید. حذف برنامه اطلاعات محلی آن را حذف می‌کند؛ برای ارتقا نیازی به حذف نیست. آزمون عملی دوربین، GPS واقعی، اشتراک‌گذاری و نصب روی دستگاه مقصد بخشی از پذیرش میدانی است.
