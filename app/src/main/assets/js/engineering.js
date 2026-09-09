(function () {
  'use strict';
  const S=window.BridgeScoring;
  const el=id=>document.getElementById(id);
  const number=n=>n==null?'محاسبه نشده':faNum(Number(n).toLocaleString('en-US',{maximumFractionDigits:1}));
  const REFERENCES=[
    ['FHWA — SNBI، مارس ۲۰۲۲، بخش ۷ و جدول ۲۰','https://www.fhwa.dot.gov/bridge/snbi/snbi_march_2022_publication.pdf'],
    ['FHWA — تعریف طبقه‌بندی Good / Fair / Poor','https://www.fhwa.dot.gov/bridge/britab.cfm'],
    ['Caltrans — Bridge Element Inspection Manual','https://dot.ca.gov/-/media/dot-media/programs/maintenance/documents/f0009170-elem-man-a11y.pdf'],
    ['FHWA — Bridge Preservation Guide','https://www.fhwa.dot.gov/bridge/preservation/guide/guide.pdf'],
    ['AASHTOWare — بازرسی میدانی موبایل','https://www.aashtoware.org/products/bridge/bridge-mobile-inspection/'],
    ['Bentley — AssetWise Inspections','https://docs.bentley.com/LiveContent/web/AssetWise%20Inspections-vlatest/Help/en/topics/3091755/GUID-8DDBF8CC-C169-4935-B31E-B0168E2D7EA6.html'],
    ['FRA — ضوابط ایمنی پل راه‌آهن، 49 CFR Part 237','https://www.ecfr.gov/current/title-49/subtitle-B/chapter-II/part-237'],
  ];
  const LESSONS=[
    ['start','پیش از حرکت','آمادگی و ایمنی بازدید',[
      'شناسنامه، نقشه، بازدید قبلی، تعمیرات و محدودیت‌های موجود پل را بخوانید. تعداد دهانه، جنس اعضا و راه دسترسی را با سرپرست کنترل کنید.',
      'مجوز کار، طرح کنترل ترافیک یا هماهنگی راه‌آهن، جلیقه، کلاه، کفش، ارتباط و همراه مناسب فراهم شود. ورود به ارتفاع، آب، فضای بسته یا محدوده برق بدون آموزش و تمهیدات مجاز نیست.',
      'ابزار ساده: دوربین، چراغ، متر، خط‌کش ترک، دفتر ثبت و وسیله مکان‌یابی. وسیله اندازه‌گیری را همراه واحد و دقت آن ثبت کنید. پیش از خروج، شارژ و فضای خالی گوشی را بررسی کنید.',
      'این جزوه به ثبت درست مشاهده کمک می‌کند. تصمیم باربری، انسداد، تعمیر سازه‌ای و بازرسی ویژه به مسئول فنی دارای صلاحیت ارجاع می‌شود.'
    ]],
    ['route','در محل پل','یک مسیر ثابت برای بازرسی',[
      'ابتدا نمای کلی و مسیر ایمن را ببینید. نام پل، کد، جهت نگاه و ترتیب شماره‌گذاری پایه‌ها و دهانه‌ها را مشخص کنید؛ همین ترتیب را در بازدیدهای بعد حفظ کنید.',
      'به ترتیب مسیر دسترسی و خاکریز، روی عرشه، درزها، نرده و زهکش، زیر عرشه و تیرها، تکیه‌گاه، پایه و کوله و سپس آبراهه را بررسی کنید. هر دو سمت عضو و نقاط پوشیده را در نظر بگیرید.',
      'کاربری و مصالح شناسنامه، چک‌لیست را تعیین می‌کند. اگر جزء واقعی در فرم نیست، شناسنامه را اصلاح کنید. نبود یک جزء الزامی مثل حفاظ ایمنی را با گزینه «کاربرد ندارد» پنهان نکنید.'
    ]],
    ['observe','ثبت مشاهده','شدت، گستره و علت احتمالی',[
      'مشاهده را از تشخیص علت جدا بنویسید: «ترک مورب در جان تیر نزدیک تکیه‌گاه» یک مشاهده است؛ «کمبود مقاومت برشی» نیاز به بررسی مهندسی دارد.',
      'نام عضو، دهانه، سمت، فاصله از تکیه‌گاه، طول، عرض و عمق قابل اندازه‌گیری و تعداد نقاط مشابه را ثبت کنید. مثلاً: دهانه ۲، تیر شرقی، ۰٫۸ متر از پایه، ترک به طول ۳۰ سانتی‌متر.',
      'شدت می‌گوید آسیب چقدر جدی است؛ گستره می‌گوید چه مقدار از عضو را درگیر کرده. یک آسیب موضعی مهم با تعداد زیادی نشانه سطحی خفیف یکسان نیست.',
      'برای یک عیب در چند محل، «＋ رخداد» را بزنید. هر رخداد عکس و GPS مستقل دارد. کد رخداد پس از حذف موارد دیگر تغییر نمی‌کند.'
    ]],
    ['severity','پنج وضعیت','ندارد، کم، متوسط، اضطراری و ع.ا.ب',[
      '«ندارد» یعنی این مورد واقعاً بررسی شده و نشانه‌ای از همان آسیب دیده نشده است. بازکردن فرم یا بازنکردن گروه، تأیید سالم‌بودن نیست.',
      '«کم» برای آسیب محدود با اثر خفیف؛ «متوسط» برای آسیب قابل توجه و نیازمند برنامه بررسی یا اقدام؛ «اضطراری» برای نشانه خطر فوری و لزوم اطلاع‌رسانی بی‌درنگ به مسئول بهره‌برداری است. حدود جزئی هر عیب به جنس، محل و دستورالعمل سازمان بستگی دارد.',
      '«ع.ا.ب» یعنی عدم امکان بازرسی: ارتفاع، پوشیدگی، نبود مجوز، آب یا دسترسی ناایمن. علت، بخش دیده‌نشده و روش لازم برای بازدید تکمیلی را بنویسید. این وضعیت نه سالم است و نه اثبات آسیب.',
      '«کاربرد ندارد» وضعیت کاربردپذیری است و جزء شدت‌ها نیست. فقط با علت روشن برای موردی استفاده شود که در دامنه واقعی این پل قرار ندارد.'
    ]],
    ['municipal','امتیاز شهرداری','مبنای عدد منفی و ضریب اهمیت',[
      'منبع، فهرست آسیب‌های VELAYAT.xlsx در برگه Ins. Form است. عنوان سند 621-8-6 روی همان فرم آمده؛ اصالت یا ویرایش مستقل این سند از روی فایل احراز نمی‌شود. ۷۸ ردیف اصلی و یک آسیب تکمیلی پرشده استخراج شده‌اند؛ چهار جای خالی آسیب وارد فهرست نشده‌اند.',
      'امتیاز پایه: ندارد ۰، کم ۱۰−، متوسط ۳۰−، اضطراری ۱۰۰− و ع.ا.ب ۱۰−. امتیاز هر کلید ارزیابی برابر امتیاز پایه ضرب در ضریب اهمیت ۱ تا ۵ است. جمع امتیازها، نمره منفی کلی است؛ عدد منفی‌تر یعنی کسری بیشتر در این روش.',
      'اگر یک ردیف مرجع در برنامه به چند آسیب یا چند رخداد شکسته شده باشد، شدیدترین امتیاز آن مجموعه فقط یک بار در جمع منظور می‌شود. این قاعده تجمیع، تصمیم صریح نسخه ۱.۶ برای جلوگیری از چندبارشماری است؛ خود اکسل فرمولی برای رخدادهای تکراری ندارد.',
      'در نمونه ارسالی: روکش با ضریب ۵ و سفیدک با ضریب ۳ در سطح کم، جمعاً ۸۰−؛ پنج مورد تکیه‌گاهی با جمع ضرایب ۱۶ در ع.ا.ب، ۱۶۰−؛ جمع کل ۲۴۰−. مقدار ۲۴۰ در A118 قدر مطلق این جمع است.',
      'سلول A115 برابر ۳۰۲، جمع ضرایب همراه چهار جای خالی است؛ درصد سلامت یا ظرفیت باربری نیست. برای آسیب‌های فقط موجود در برنامه، ضریب پیشنهادی با توضیح قیاس تعیین شده و جدا از سهم مرجع گزارش می‌شود.'
    ]],
    ['interpret','تفسیر نتیجه','پوشش بازرسی و مقایسه درست',[
      'پوشش، درصد رخدادهای قابل اعمالی است که مشاهده شده‌اند؛ ع.ا.ب و بررسی‌نشده از صورت کسر حذف می‌شوند. این درصد پوشش سطح فیزیکی عضو نیست.',
      'کسری وزنی نرمال‌شده = قدر مطلق جمع منفی تقسیم بر مجموع ضرایب کلیدهای قابل اعمال. دامنه آن ۰ تا ۱۰۰ است و صرفاً شاخص سفارشی نرم‌افزار است؛ درصد سلامت، احتمال خرابی یا عمر باقیمانده نیست.',
      'فایل مرجع مرز عددی خوب، متوسط و بد برای جمع منفی ندارد؛ برنامه چنین مرزهایی را به شهرداری نسبت نمی‌دهد. آسیب اضطراری همیشه مستقل از مجموع نمایش داده می‌شود.',
      'مقایسه زمانی فقط با دامنه یکسان، نسخه روش یکسان و پوشش مشابه معتبر است. تغییر جنس، تعداد موارد، کاربردپذیری یا روش ارزیابی می‌تواند عدد را تغییر دهد بدون آنکه خود پل تغییر کرده باشد.',
      'سوابق قدیمی با نام‌ها و مشاهداتشان حفظ می‌شوند. چون نسخه قدیمی «ندارد» پیش‌فرض داشت، پوشش آن قابل اثبات نیست؛ هر محاسبه جدید برای آن با برچسب بازتحلیل نمایش داده می‌شود.'
    ]],
    ['snbi','ارزیابی بین‌المللی','چارچوب FHWA / SNBI',[
      'یک امتیاز واحد که در همه کشورهای جهان و همه پل‌های جاده‌ای و ریلی الزام‌آور باشد وجود ندارد. این برنامه چارچوب شناخته‌شده FHWA/SNBI آمریکا را برای ارزیابی مقایسه‌ای جداگانه ارائه می‌کند.',
      'وضعیت اجزای اصلی را ارزیاب با درنظرگرفتن نوع، محل، شدت، گستره و اثر آسیب بر عملکرد تعیین می‌کند. جمع منفی شهرداری به‌صورت خودکار به نمره SNBI تبدیل نمی‌شود.',
      'نمره‌های ۰ تا ۹ برای هر جزء همراه با دلیل و نام ارزیاب ثبت می‌شوند. N فقط به معنی نبود جزء است؛ U در این برنامه یعنی ارزیابی‌نشده و کد رسمی SNBI نیست.',
      'جمع‌بندی پل از کمترین نمره قابل اعمال عرشه، روسازه، زیرسازه و کالورت به دست می‌آید: ۷ تا ۹ خوب، ۵ و ۶ متوسط، ۰ تا ۴ ضعیف. میانگین‌گیری مجاز نیست، چون می‌تواند جزء ضعیف را پنهان کند.',
      'تا اجزای اصلی قابل اعمال با دلیل و نام ارزیاب تکمیل نشده‌اند، نتیجه کلی «ناقص» می‌ماند. نمره به تنهایی مجوز بهره‌برداری یا نتیجه ارزیابی ظرفیت باربری نیست. برای پل راه‌آهن باید مقررات مالک خط و ارزیابی مهندس پل ریلی نیز رعایت شود.'
    ]],
    ['element','ارزیابی کمی اجزا','چهار حالت و مقدار هر حالت',[
      'در ارزیابی عنصری، مقدار کل عضو با واحد واقعی ثبت می‌شود؛ مانند متر طول تیر یا مترمربع دال. مقدار هر قسمت را بر اساس راهنمای همان نوع عنصر به حالت‌های CS1 تا CS4 تخصیص دهید.',
      'CS1 خوب، CS2 متوسط، CS3 ضعیف و CS4 شدید است. این‌ها ترجمه مستقیم پنج وضعیت چک‌لیست شهرداری نیستند. تعریف دقیق آسیب و حدود هر حالت به عنصر و مرجع انتخابی بستگی دارد.',
      'جمع مقدار چهار حالت باید برابر مقدار کل باشد. هر قسمت فقط در یک حالت شمرده می‌شود؛ در آسیب‌های هم‌پوشان، از تکرار مقدار جلوگیری کنید و حالت حاکم را طبق راهنمای عنصر ثبت کنید.',
      'برنامه درصد هر حالت و سهم CS3+CS4 را محاسبه می‌کند. این توزیع مقدار، امتیاز رسمی سلامت کل پل نیست. شماره عنصر و مرجع تعریف حالت‌ها باید در توضیحات باقی بماند.'
    ]],
    ['materials','نشانه‌های رایج','بتن، فولاد، تکیه‌گاه و آبراهه',[
      'بتن: ترک و جهت آن، جداشدگی پوشش، قلوه‌کن‌شدگی، خوردگی نمایان و مسیر رطوبت را ثبت کنید. سفیدک به‌تنهایی اثبات کاهش مقاومت نیست. مشاهده ظاهری، کربناته‌شدن یا واکنش شیمیایی را قطعی نمی‌کند.',
      'فولاد: زنگ سطحی را از کاهش مقطع جدا کنید؛ محل ترک، جوش، اتصال، تغییرشکل و پیچ مفقود را دقیق مستند کنید. مشکوک‌بودن به ترک خستگی یا پارگی مسیر بار باید سریع ارجاع شود.',
      'تکیه‌گاه: موقعیت، سطح تماس، تغییرشکل، لایه‌های نئوپرن، مهار و حرکت را بررسی کنید. دما و وضعیت طراحی در تفسیر جابه‌جایی مؤثر است. از ارتفاع نامطمئن درباره سلامت عضو پنهان نتیجه نگیرید.',
      'آبراهه و پی: افت بستر، گودال اطراف پی، تجمع نخاله، تغییر جهت جریان و آثار سیلاب را با سوابق مقایسه کنید. عمیق‌بودن یا ناپایداری آب‌شستگی به ارزیابی هیدرولیکی و سازه‌ای نیاز دارد.'
    ]],
    ['urgent','نشانه‌های فوری','وقتی خطر محتمل است',[
      'ریزش قطعه، جابه‌جایی ناگهانی، عضو باربر شکسته، خروج تکیه‌گاه، تغییرشکل غیرعادی جدید، پی زیرشسته یا خطر برق را فوری به مسئول بهره‌برداری گزارش کنید؛ برای کامل‌کردن فرم در محدوده خطر نمانید.',
      'زمان، محل، عکس از فاصله ایمن، شرح مشاهده، فرد مطلع‌شده و اقدام انجام‌شده را ثبت کنید. دستور انسداد یا محدودیت عبور را مرجع مجاز صادر می‌کند؛ بازرس تازه‌کار آن را از یک امتیاز عددی استنتاج نمی‌کند.',
      'برای آسیب اضطراری، یادداشت شاهد و اقدام اطلاع‌رسانی لازم است. وجود ع.ا.ب در یک عضو مهم می‌تواند نیاز به بازدید ویژه ایجاد کند حتی اگر مجموع منفی کوچک باشد.'
    ]],
    ['photos','عکس و مستندات','چگونه عکس قابل استفاده بگیریم',[
      'یک عکس عمومی برای محل عضو، یک عکس نزدیک برای آسیب و در صورت ایمن‌بودن یک مقیاس اندازه‌گیری بگیرید. نور، وضوح و جهت مشاهده را کنترل کنید.',
      'GPS محل گوشی است؛ لزوماً مختصات دقیق ترک یا عضو بالای سر نیست. خطای دقت و موقعیت نسبی عضو در یادداشت مکمل آن است.',
      'در ZIP عکس‌ها به رخدادها پیوند دارند؛ بسته را کامل استخراج کنید تا لینک نسبی Excel کار کند. فایل Excel به‌تنهایی تصاویر پوشه همراه را حمل نمی‌کند.'
    ]],
    ['followup','پس از بازدید','بازبینی، خروجی و پیگیری',[
      'پیش از امضا، پوشش، ع.ا.ب‌ها، عکس‌ها، توضیحات و موارد اضطراری را بازبینی کنید. ثبت نهایی، امتیاز و نسخه روش را همراه همان بازدید نگه می‌دارد.',
      'مسئول، اقدام، مهلت و مدارک تعمیر را در گردش کار ثبت کنید. تعمیر انجام‌شده را بدون مدرک و بازبینی، تأیید نهایی نکنید. بازدید جدید، سابقه قدیمی را جایگزین نمی‌کند.',
      'دوره بازرسی را مالک پل و مسئول فنی بر اساس وضعیت، نوع پل و خطر تعیین می‌کنند؛ یک فاصله ثابت برای همه پل‌ها معتبر نیست. پس از سیلاب، زلزله، برخورد یا آتش، نیاز به بازدید ویژه بررسی شود.',
      'از منوی پشتیبان‌گیری، ZIP کامل تهیه و بازیابی آن را روی نسخه جداگانه آزمایش کنید. خروجی گزارش جایگزین فایل پشتیبان پایگاه داده نیست.',
      'PDF صفحه‌بندی و متن چندخطی دارد؛ Excel عرض ستون و ارتفاع ردیف متناسب با متن می‌گیرد. CSV فقط متن و عدد است و امکان ذخیره عرض، ارتفاع، فونت یا رنگ سلول را ندارد.'
    ]],
  ];
  function summary(record) {
    const result=record.scores || S.snapshot(record), m=result.municipal, f=result.international;
    return '<div class="engineering-summary"><div class="score-grid">'+
      '<div class="score-tile"><small>نمره منفی کلی پل</small><strong dir="ltr">'+number(m.total)+'</strong><span>مرجع '+number(m.referenceTotal)+' | تکمیلی '+number(m.supplementalTotal)+'</span></div>'+
      '<div class="score-tile"><small>پوشش مشاهده</small><strong>'+number(m.coverage)+'٪</strong><span>'+number(m.uninspectableCount)+' ع.ا.ب • '+number(m.pendingCount)+' بررسی‌نشده</span></div>'+
      '<div class="score-tile"><small>FHWA / SNBI</small><strong>'+esc(f.complete?f.condition:'ارزیابی ناقص')+'</strong><span>کمترین نمره ثبت‌شده: '+number(f.minimum)+' از ۹</span></div></div>'+
      (m.emergencyCount || f.critical?'<div class="callout urgent"><b>نیاز به توجه فوری</b> '+(m.emergencyCount?number(m.emergencyCount)+' رخداد اضطراری ثبت شده است. ':'')+(f.critical?'نمره بحرانی در ارزیابی اجزا ثبت شده است. ':'')+'نتیجه عددی جای پیگیری فوری را نمی‌گیرد.</div>':'')+
      (!m.complete?'<div class="callout caution">پوشش کامل نیست؛ عدد گزارش‌شده فقط بر مبنای اطلاعات ثبت‌شده است.</div>':'')+
      (m.legacy?'<div class="callout">بازتحلیل سابقه قدیمی؛ تأیید بررسی موارد پیش‌فرض «ندارد» در دسترس نیست.</div>':'')+'</div>';
  }
  function renderAssessment() {
    const host=el('internationalForm'), record=window.currentInspection;
    if(!host || !record)return;
    const state=record.internationalAssessment || {}, values=state.components || {};
    host.innerHTML='<div class="form-grid"><label class="field">نوع سازه برای جمع‌بندی<select id="internationalMode"><option value="bridge">پل</option><option value="culvert">کالورت / آبرو</option><option value="mixed">پل و کالورت ترکیبی</option></select></label><label class="field">نام ارزیاب / بازبین فنی<input id="internationalReviewer" maxlength="100" value="'+esc(state.reviewer||'')+'"></label></div>'+
      '<p class="muted">نمره‌ها را پس از مشاهده و با دلیل ثبت کنید. بدون اطلاعات کافی، «ارزیابی نشده» را نگه دارید.</p>'+
      S.COMPONENTS.map(c=>'<details class="component-rating"><summary><span>'+esc(c.name)+'</span><b dir="ltr">'+c.code+'</b></summary><div class="component-body"><p>'+esc(c.hint)+'</p><label class="field">نمره وضعیت<select data-component-rating="'+c.id+'"><option value="U">ارزیابی نشده / اطلاعات ناکافی</option><option value="N">N — این جزء وجود ندارد</option>'+[...S.RATINGS].reverse().map(r=>'<option value="'+r[0]+'">'+faNum(r[0])+' — '+esc(r[1])+'</option>').join('')+'</select></label><p data-rating-help="'+c.id+'" class="callout"></p><label class="field">شواهد، گستره و دلیل نمره<textarea rows="3" data-component-note="'+c.id+'">'+esc(values[c.id]?.note||'')+'</textarea></label></div></details>').join('');
    el('internationalMode').value=state.mode || 'bridge';
    host.querySelectorAll('[data-component-rating]').forEach(control=>{control.value=String(values[control.dataset.componentRating]?.rating ?? 'U');updateRatingHelp(control);});
    renderElements();
  }
  function updateRatingHelp(control) {
    const help=document.querySelector('[data-rating-help="'+control.dataset.componentRating+'"]');
    const definition=S.RATINGS.find(row=>row[0]===control.value);
    const c=S.COMPONENTS.find(c=>c.id===control.dataset.componentRating);
    if(help)help.textContent=c?.descriptions?.[control.value] || definition?.[2] || (control.value==='N'?'علت نبود جزء را بنویسید؛ ندیدن عضو به معنی نبود آن نیست.':'اطلاعات کافی برای نمره‌دادن وجود ندارد.');
  }
  function capture() {
    const record=window.currentInspection;
    if(!record || !el('internationalMode'))return;
    const components={};
    document.querySelectorAll('[data-component-rating]').forEach(control=>{
      components[control.dataset.componentRating]={rating:control.value,note:document.querySelector('[data-component-note="'+control.dataset.componentRating+'"]')?.value.trim()||''};
    });
    record.internationalAssessment={mode:el('internationalMode').value,reviewer:el('internationalReviewer').value.trim(),components};
    record.elementAssessments=[...document.querySelectorAll('.element-assessment')].map(row=>{
      const object={id:row.dataset.element};
      row.querySelectorAll('[data-element-field]').forEach(c=>object[c.dataset.elementField]=c.value.trim());return object;
    });
  }
  function renderElements() {
    const host=el('elementAssessments');if(!host)return;
    host.innerHTML=(window.currentInspection?.elementAssessments || []).map(e=>'<div class="element-assessment" data-element="'+esc(e.id)+'"><div class="form-grid">'+[['name','نام و کد عنصر'],['unit','واحد مقدار'],['total','مقدار کل'],['cs1','CS1 — خوب'],['cs2','CS2 — متوسط'],['cs3','CS3 — ضعیف'],['cs4','CS4 — شدید'],['reference','مرجع تعریف حالت‌ها / شواهد']].map(([key,label])=>'<label class="field">'+label+'<input data-element-field="'+key+'" '+(/^(total|cs[1-4])$/.test(key)?'type="number" min="0" step="any" inputmode="decimal"':'type="text"')+' value="'+esc(e[key]??'')+'"></label>').join('')+'</div><button type="button" class="btn danger" onclick="removeElementAssessment(this)">حذف این عنصر</button><p class="element-result"></p></div>').join('');
    refreshElementResults();
  }
  function refreshElementResults() {
    document.querySelectorAll('.element-assessment').forEach(row=>{
      const e={};row.querySelectorAll('[data-element-field]').forEach(c=>e[c.dataset.elementField]=c.value);
      const result=S.elementCondition(e), host=row.querySelector('.element-result');
      host.textContent=result.valid?'سهم CS3+CS4: '+number(result.cs34Percent)+'٪ از مقدار عنصر':'مقادیر چهار حالت باید نامنفی و جمع آن‌ها دقیقاً برابر مقدار کل باشد.';
      host.classList.toggle('invalid',!result.valid);
    });
  }
  function validate(items) {
    if(!items.length){toast('چک‌لیست قابل ارزیابی وجود ندارد؛ شناسنامه پل را تکمیل کنید.');return false;}
    const pending=items.find(i=>i.applicable!==false && i.assessed!==true);
    const missingNote=items.find(i=>(i.applicable===false || i.statusId==='uninspectable' || i.statusId==='emergency') && !String(i.note||'').trim());
    if(pending || missingNote){
      const item=pending || missingNote;
      toast(pending?'بررسی همه موارد قابل اعمال را تأیید کنید؛ موارد دیده‌نشده ع.ا.ب هستند.':'برای ع.ا.ب، اضطراری و خارج از دامنه، توضیح لازم است.');
      el('inspectionValidation').textContent=(pending?'بررسی نشده: ':'توضیح لازم دارد: ')+(item.itemName || item.itemId);
      el('inspectionValidation').hidden=false;
      el('inspectionValidation').focus();
      return false;
    }
    for(const e of window.currentInspection?.elementAssessments || []){
      if(!e.name || !e.unit || !e.reference || !S.elementCondition(e).valid){toast('نام، واحد، مرجع و مجموع مقادیر ارزیابی عنصری را اصلاح کنید.');return false;}
    }
    el('inspectionValidation').hidden=true;
    return true;
  }
  let previewTimer=0;
  function preview() {
    if(!window.currentInspection || !el('scorePreview') || document.querySelector('.page.active')?.id!=='inspectionForm')return;
    capture();
    const record={...window.currentInspection,items:window.collectInspectionItems(),scores:null};
    el('scorePreview').innerHTML=summary(record);
    refreshElementResults();
  }
  function schedule(){clearTimeout(previewTimer);previewTimer=setTimeout(preview,180);}
  function setApplicability(control) {
    const row=control.closest('.item-occurrence');row.dataset.applicable=String(!control.checked);
    row.querySelector('.assessment-state').textContent=control.checked?'خارج از دامنه؛ علت را بنویسید':'نیاز به تأیید بررسی';
    schedule();window.autosaveCurrentInspection?.();
  }
  function markHealthy() {
    const category=document.querySelector('#checklistHost .checkcat:not(.collapsed)');
    if(!category){toast('ابتدا گروه بررسی‌شده را باز کنید.');return;}
    if(!confirm('فقط موارد انتخاب‌نشده این گروه را واقعاً بررسی کرده‌اید و آسیب ندارند؟'))return;
    category.querySelectorAll('.item-occurrence[data-assessed="false"][data-applicable="true"]').forEach(row=>{
      const button=row.querySelector('[data-sev="none"]');if(button)chooseSeverity(button,row.dataset.item,'none');
    });
    toast('موارد بررسی‌شده سالم تأیید شدند.');schedule();
  }
  function renderAcademy() {
    const query=normalizeFaSearch(el('academySearch')?.value || '');
    const host=el('academyLessons');if(!host)return;
    const lessons=LESSONS.filter(lesson=>!query || normalizeFaSearch(JSON.stringify(lesson)).includes(query));
    host.innerHTML=lessons.map(([id,kicker,title,paragraphs])=>'<details class="lesson"><summary><span><small>'+esc(kicker)+'</small><b>'+esc(title)+'</b></span><span aria-hidden="true">＋</span></summary><div class="lesson-body">'+paragraphs.map(p=>'<p>'+esc(p)+'</p>').join('')+(id==='snbi'?'<div class="rating-scale">'+[...S.RATINGS].reverse().map(r=>'<div><b>'+faNum(r[0])+' • '+r[1]+'</b><p>'+r[2]+'</p></div>').join('')+'</div>':'')+'</div></details>').join('') || '<div class="empty">موضوعی پیدا نشد.</div>';
    el('academyReferences').innerHTML=REFERENCES.map(([name,url])=>'<li><span>'+esc(name)+'</span><br><span class="reference-url" dir="ltr">'+esc(url)+'</span></li>').join('');
  }
  function init() {
    const form=el('inspectionForm');
    form?.addEventListener('input',schedule);
    form?.addEventListener('change',event=>{if(event.target.matches('[data-component-rating]'))updateRatingHelp(event.target);schedule();});
    renderAcademy();
    // Associate visible field labels for TalkBack without relying on placeholders.
    document.querySelectorAll('.field').forEach((field,index)=>{
      const label=field.querySelector('label'),control=field.querySelector('input,select,textarea,button');
      if(label && control){if(!control.id)control.id='field-control-'+index;label.htmlFor=control.id;}
    });
    document.querySelectorAll('.modal').forEach(m=>{m.setAttribute('role','dialog');m.setAttribute('aria-modal','true');});
    el('toast')?.setAttribute('role','status');
  }
  Object.assign(window,{engineeringSummaryHtml:summary,renderEngineeringAssessment:renderAssessment,captureEngineeringAssessment:capture,
    validateEngineeringFinalization:validate,scheduleScorePreview:schedule,setOccurrenceApplicability:setApplicability,
    markCurrentCategoryHealthy:markHealthy,renderAcademy,
    addElementAssessment:()=>{capture();const r=window.currentInspection;if(!r)return;r.elementAssessments=r.elementAssessments || [];r.elementAssessments.push({id:id('element')});renderElements();window.autosaveCurrentInspection?.();},
    removeElementAssessment:button=>{capture();const row=button.closest('.element-assessment');window.currentInspection.elementAssessments=window.currentInspection.elementAssessments.filter(e=>e.id!==row.dataset.element);renderElements();window.autosaveCurrentInspection?.();}
  });
  window.onRailReady(init);
})();
