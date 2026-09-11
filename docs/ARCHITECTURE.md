# معماری نرم‌افزار

برنامه یک پوسته Android/Java با رابط HTML/CSS/JavaScript آفلاین است. WebView فقط محتوای محلی را از origin امن `https://appassets.androidplatform.net` بارگذاری می‌کند و شبکه در تنظیمات WebView مسدود است.

## مسیر داده

1. `bridge-profile-schema.js` تعریف ۲۰ بخش و ۶۰ فیلد شناسنامه را نگه می‌دارد.
2. `bridges.js` شناسنامه و موقعیت هر پل را در entity نوع `bridges` ذخیره می‌کند.
3. `bridge-checklists.js` از مقادیر شناسنامه tagهای مصالح، سیستم، اجزا و کاربری می‌سازد.
4. `app-core.js` تنها دسته‌های سازگار با tagهای پل انتخاب‌شده را در فرم بازدید نمایش می‌دهد.
5. پیش‌نویس در SQLite ذخیره خودکار می‌شود؛ `InspectionGovernance` ارسال و قفل snapshot، QC مستقل، ابطال/اصلاح، برنامه و پرونده بحرانی را در transactionهای بومی انجام می‌دهد.
6. `reports.js` payload را می‌سازد؛ لایه بومی فقط رکورد تأییدشده، ثبت‌شده، باطل‌نشده و منطبق با hash و Critical Findingهای SQLite را می‌پذیرد؛ `ReportExporter.java` زنجیره هویت/QC را به خروجی می‌افزاید.

## entityها

entityهای دامنه عبارت‌اند از `users`, `bridges`, `standards`, `inspections`, `defects`, `settings`, `reminders`, `reviews`, `criticalFindings`, `inspectionVoids`, `reportRevisions`, `inspectionPrograms`. رویدادهای `audit` در جدول بومی append-only نگهداری می‌شوند. جدول‌های بومی `credentials`, `report_sequences` و `report_registry` نیز داده‌های امنیتی و یکتایی را از JSON عمومی جدا می‌کنند.

هر بازدید علاوه بر `bridgeId` یک `bridgeSnapshot` دارد تا گزارش تاریخی پس از ویرایش شناسنامه پل همچنان قابل بازتولید باشد.

## رسانه

تصاویر در `BridgeMaintenance/Images/<date>/` و امضاها در `BridgeMaintenance/Signatures/<date>/` در scoped storage نگهداری می‌شوند. SQLite فقط شناسه، مسیر نسبی امن، MIME، ابعاد، SHA-256 و زمان را ذخیره می‌کند. نام تصویر شامل نام/کد پل و بخش بازرسی است.

## گردش رسمی ۱.۷.۰

- `draft` قابل ویرایش میدانی است. در `returned` فقط محتوای اصلاحی قابل ویرایش است و شناسه بازرس اولیه، اطلاعات ارسال، hash قبلی و پیوندهای حاکمیتی از مسیر عمومی قابل بازنویسی نیستند.
- `submitted` دارای `fieldHash` و snapshot نسخه‌ای و برای ویرایش میدانی قفل است.
- بازبین واجد صلاحیت و مستقل، رکورد را `returned` یا `approved` می‌کند.
- ارسال دوباره رکورد `returned` به امضای تازه نیاز دارد و تغییر بازرس فقط با دلیل ثبت‌شده در audit انجام می‌شود.
- `approved` تنها حالت قابل صدور رسمی است و شماره آن در registry یکتا ثبت می‌شود.
- گزارش اصلی حذف/بازنویسی نمی‌شود؛ `inspectionVoids` ابطال و یک draft دارای `supersedesInspectionId` اصلاح را ثبت می‌کند.

## مقیاس آسیب

| ID | برچسب | رتبه |
|---|---|---:|
| `none` | ندارد | 0 |
| `low` | کم | 1 |
| `medium` | متوسط | 2 |
| `emergency` | اضطراری | 3 |
| `uninspectable` | عدم امکان بازرسی (ع.ا.ب) | -1 |

`unknown` حالت fail-safe داخلی و غیرقابل ارسال است، نه یک سطح رسمی. مقدار منبع قدیمی در `legacySeverityValue` حفظ می‌شود و بدون تصمیم مهندسی به یک شدت رسمی تبدیل نمی‌شود.
