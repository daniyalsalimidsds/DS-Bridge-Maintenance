# امنیت و حریم داده

- برنامه مجوز INTERNET ندارد، cleartext غیرفعال است و WebView درخواست شبکه را مسدود می‌کند.
- Native bridge فقط از origin محلی اصلی پیام می‌پذیرد و اندازه/نوع payload، ID و JSON را اعتبارسنجی می‌کند.
- FileProvider export نشده و فقط مسیر application-owned را در دسترس می‌گذارد.
- نام و مسیر فایل sanitize می‌شود؛ resolve رسانه canonical-path را زیر root برنامه کنترل می‌کند.
- CSV/XLSX مقدارهای آغازشونده با `=`, `+`, `-`, `@` را برای جلوگیری از formula injection خنثی می‌کند.
- Backup محدودیت تعداد/اندازه دارد، مسیر ZIP را کنترل می‌کند، SHA-256 داده و رسانه را می‌سنجد، پیش از restore پشتیبان ایمنی می‌سازد و database را تراکنشی جایگزین می‌کند.
- نقش‌های کاربری محلی، احراز هویت شبکه‌ای نیستند و در برابر دستگاه rooted/unlocked مرز امنیتی محسوب نمی‌شوند.
- هیچ JKS/P12/PFX یا رمز امضا در مخزن مجاز نیست؛ `tools/check_bridge_source.py` این قاعده را fail-closed کنترل می‌کند.
