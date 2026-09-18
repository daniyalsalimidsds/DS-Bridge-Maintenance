# وابستگی‌ها

وابستگی‌های runtime محدود به AndroidX Activity, Core, WebKit و ExifInterface هستند. کتابخانه‌های AndroidX تحت Apache License 2.0 منتشر می‌شوند. تست‌ها از JUnit 4، AndroidX Test، Espresso و UIAutomator استفاده می‌کنند.

برنامه SDK تبلیغات، analytics، نقشه آنلاین، پایگاه ابری یا JavaScript framework راه‌دور ندارد. افزودن یا ارتقای dependency باید همراه با بازبینی مجوز، حریم داده و گزارش lint انجام شود.

## تفکیک مجوز پروژه و وابستگی‌ها

کد و مستندات تألیفی DS-Bridge-Maintenance تحت [MIT](../LICENSE) منتشر می‌شوند. این مجوز، شرایط مستقل وابستگی‌ها را تغییر نمی‌دهد. در بازتوزیع، اعلان‌های Apache 2.0 و SIL OFL موجود در [پوشهٔ مجوزهای همراه برنامه](../app/src/main/assets/licenses/) را نیز حفظ کنید. ابزار Gradle و کتابخانه‌های آزمون تابع مجوزهای اصلی خود هستند.
