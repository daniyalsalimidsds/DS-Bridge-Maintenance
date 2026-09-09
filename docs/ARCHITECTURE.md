# معماری نرم‌افزار

برنامه یک پوسته Android/Java با رابط HTML/CSS/JavaScript آفلاین است. WebView فقط محتوای محلی را از origin امن `https://appassets.androidplatform.net` بارگذاری می‌کند و شبکه در تنظیمات WebView مسدود است.

## مسیر داده

1. `bridge-profile-schema.js` تعریف ۲۰ بخش و ۶۰ فیلد شناسنامه را نگه می‌دارد.
2. `bridges.js` شناسنامه و موقعیت هر پل را در entity نوع `bridges` ذخیره می‌کند.
3. `bridge-checklists.js` از مقادیر شناسنامه tagهای مصالح، سیستم، اجزا و کاربری می‌سازد.
4. `app-core.js` تنها دسته‌های سازگار با tagهای پل انتخاب‌شده را در فرم بازدید نمایش می‌دهد.
5. پیش‌نویس در SQLite ذخیره خودکار می‌شود؛ نهایی‌سازی بازدید، آسیب‌ها، یادآورها و audit را در یک transaction ثبت می‌کند.
6. `reports.js` ردیف‌های کامل CSV/XLSX و ردیف‌های فشرده PDF را می‌سازد؛ `ReportExporter.java` فایل‌های بومی را می‌نویسد.

## entityها

`users`, `bridges`, `standards`, `inspections`, `defects`, `audit`, `settings`, `reminders`

هر بازدید علاوه بر `bridgeId` یک `bridgeSnapshot` دارد تا گزارش تاریخی پس از ویرایش شناسنامه پل همچنان قابل بازتولید باشد.

## رسانه

تصاویر در `BridgeMaintenance/Images/<date>/` و امضاها در `BridgeMaintenance/Signatures/<date>/` در scoped storage نگهداری می‌شوند. SQLite فقط شناسه، مسیر نسبی امن، MIME، ابعاد، SHA-256 و زمان را ذخیره می‌کند. نام تصویر شامل نام/کد پل و بخش بازرسی است.

## مقیاس آسیب

| ID | برچسب | رتبه |
|---|---|---:|
| `none` | ندارد | 0 |
| `low` | کم | 1 |
| `medium` | متوسط | 2 |
| `emergency` | اضطراری | 3 |

مقادیر نسخه قبلی فقط هنگام مهاجرت به چهار ID بالا نگاشت می‌شوند و گزینه اضافی در رابط یا خروجی ایجاد نمی‌کنند.
