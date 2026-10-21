# تدقيق أداء وحدة `:core` — تطبيق «واحة»

> **النطاق:** تدقيق أولي، ثم سجل تنفيذ المرحلتين 1 و2 (الأقسام 7 و8).
> **التاريخ:** 20–21 أكتوبر 2026
> **المدخلات التي فُحصت:** `core/src/main` بالكامل (24 ملف Kotlin، ~1,200 سطر)، ومواضع استهلاكها في `:mobile` و`:tv` لأن سلوك الاستدعاء هو ما يحدد التكلفة الفعلية.

---

## 1. الملخص التنفيذي

أثقل ثلاثة أسباب في `:core` ليست في الشبكة نفسها، بل في **شكل** استهلاكها:

1. **جلب المكتبة كاملة في كل إقلاع، بدون أي كاش محلي.** `VideoRepository.getVideos()` يلفّ صفحات 1,000 صفاً ويجمعها كلها في الذاكرة، ويُستدعى من `init` في الـ ViewModel. لا يوجد مصدر حقيقة محلي، ولا عرض تدريجي، ولا إعادة استخدام بين عمليتي الإقلاع أو بين شاشتي الهاتف والتلفاز.
2. **كل هذا العمل يجري على الـ Main thread.** `viewModelScope.launch` افتراضياً `Dispatchers.Main.immediate`، وفك ترميز JSON + `map { toVideoItem() }` + `shuffled()` + `groupBy` كلها داخله. القياس المرجعي: 18–38 مللي ثانية لـ 5k–20k صف على JVM مكتبي مُسخّن، ورقم أعلى بكثير على ART بارد.
3. **محرك التوصيات يُعاد بناؤه بالكامل عند فتح المشغل، وبعد كل حفظ تقدّم.** تعقيده O(n log n) مع Tokenization بـ Regex لكل عنصر في كل مرة، وخطوة التنويع O(n²). القياس: **163 مللي ثانية لـ 5k فيديو و504 مللي ثانية لـ 20k** على JVM مكتبي — أي بثوانٍ محتملة على جهاز متوسط عند لحظة فتح المشغل.

---

## 2. المنهجية وحدود القياس

- **مراجعة كود ثابتة** لكل ملفات `:core` + تتبع مواضع الاستدعاء في الوحدتين.
- **قياس مصغّر (microbenchmark)** لمحرك التوصيات ومسار التحويل، أُعيد تنفيذ الكود الحقيقي لـ `VideoSuggestionsEngine` (نُسخ كما هو) على JVM/OpenJDK 25، بمكتبات مُولّدة صناعياً (1k/5k/20k عنواناً عربياً وإنجليزياً، 6 تصنيفات). الأرقام **نسبية**: ART على جهاز متوسط أبطأ عادة 3–8× في عمل النصوص/Regex، وليست بديلاً عن Perfetto/Macrobenchmark على جهاز حقيقي.
- **لم يُقس:** زمن الشبكة الفعلي، عدد صفوف المكتبة في Supabase، وجود فهارس في قاعدة البيانات، زمن الإقلاع البارد على جهاز، واستهلاك heap الحقيقي. هذه كلها مطلوبة في «المرحلة 0» أدناه قبل الأخذ بأرقام التحسين النهائية.

### الأرقام المرجعية المسجّلة

| السيناريو | 1,000 فيديو | 5,000 فيديو | 20,000 فيديو |
|---|---|---|---|
| `VideoSuggestionsEngine.build()` الحالي | 53 ms | 163 ms | 504 ms |
| نفس المنطق مع فهرسة مسبقة (tokens + episode) وتنويع خطّي | 7 ms | 17 ms | 39 ms |
| مكسب السرعة | 7.6× | 9.6× | 12.9× |
| `map + shuffled + groupBy` (تحويل المكتبة) | — | 18 ms | 38 ms |
| `seriesTokens()` وحدها × 20,000 عنوان | — | — | 142 ms (~7µs/عنوان) |

---

## 3. جدول النتائج حسب الأولوية

| # | الخلل | الموضع | التأثير | الصلة |
|---|---|---|---|---|
| P1 | جلب كامل المكتبة بلا كاش ولا عرض تدريجي | `Videorepository.kt` | **High** | الإقلاع + البيانات + الذاكرة |
| P2 | فك الترميز والتحويل والتجميع على الـ Main thread | `Videorepository.kt`, `Wahahomeviewmodel.kt`, `TvHomeViewModel.kt` | **High** | الإقلاع (jank/ANR) |
| P3 | محرك التوصيات يُعاد حسابه كاملاً وفي كل تغيير للسجل | `VideoSuggestionsEngine.kt` | **High** | فتح المشغل |
| P4 | قراءة التفضيلات من القرص بشكل متزامن على المسار الحرج | `Themepreferencestore.kt`, `PlayerPreferencesStore.kt` | **Medium** | الإقلاع |
| P5 | سجل المشاهدة: نسخ خرائط + كتابة كاملة + إعادة تركيب | `Watchhistorystore.kt` | **Medium–High** | المشغل |
| P6 | تخزين محلي موزّع على 3 DataStore + كتابة غير مُجمّعة | `Savedvideosstore.kt`, `Recentsearchesstore.kt` | **Low–Medium** | الإقلاع + الكتابة |
| P7 | حل رابط التشغيل متسلسل بمهل طويلة وبلا كاش | `PlaybackResolver.kt`, `Youtubeextractor.kt`, `Pipedapi.kt` | **High** (بدء الفيديو) | المشغل |
| P8 | ترقيم صفحات بـ OFFSET وفهرس قاعدة بيانات غير مؤكد | `Videorepository.kt` | **Medium–High** (خادم) | جلب البيانات |

---

## 4. تحليل مفصّل

### P1 — المكتبة كاملة في كل إقلاع، بلا كاش ولا عرض تدريجي

**أين:** [Videorepository.kt](core/src/main/java/com/waha/data/Videorepository.kt) — `getVideos()` في السطور 24–38، و`collectVideoPages` في 69–85. الاستدعاء من [Wahahomeviewmodel.kt:46](mobile/src/main/java/com/waha/ui/screens/Wahahomeviewmodel.kt#L46) و[TvHomeViewModel.kt:39](tv/src/main/java/com/waha/tv/ui/screens/TvHomeViewModel.kt#L39).

**ما يحدث فعلاً:**
- كل إقلاع بارد = طلب واحد على الأقل، وقد يصل إلى عشرات الطلبات (سقف `PAGE_SIZE = 1000`، `MAX_PAGES = 50`).
- `collectVideoPages` لا يُصدر شيئاً حتى تنتهي كل الصفحات: زمن أول محتوى = زمن المكتبة كاملة. شاشة `Loading` تبقى طوال المدة.
- لا كاش على القرص ولا في الذاكرة، ولا `stale-while-revalidate`، ولا حالة «آخر مزامنة». إعادة فتح التطبيق بعد دقيقة تعيد نفس التحميل حرفياً.
- لا يوجد **single-flight**: الهاتف والتلفاز ينشئان `VideoRepository()` مستقلين لكل ViewModel، وسحب-للتحديث أثناء تحميل أولي يطلق طلباً ثانياً متوازياً.
- الذاكرة أثناء الجلب تبلغ ذروتها لأن هذه النسخ تتعايش: نص JSON للصفحة + `List<VideoRow>` المتراكم + `List<VideoItem>` بعد التحويل + نسخة `shuffled()` + خرائط `groupBy`. تقدير لـ 20k صف: ارتفاع مؤقت في heap بحدود 15–30MB فوق الأساس — مؤثر على أجهزة التلفاز ذات heap صغير.

**الحل المقترح:**
1. **Room كمصدر الحقيقة الوحيد** (جدول `videos` + جدول مزامنة)، والـ UI يقرأ `Flow` من قاعدة البيانات. الشبكة تكتب في Room فقط.
2. **عرض تدريجي:** `Paging3` مع `RemoteMediator` (أو تدفق صفحات يدوي) بحجم صفحة 100–200 بدل 1000، حتى تظهر أول شاشة من الكاش في أول إطار.
3. **مزامنة تفاضلية:** عمود `updated_at` + استعلام `updated_at > lastSyncAt` (و/أو `If-None-Match`/ETag)، بدل إعادة تنزيل المكتبة كاملة.
4. **Single-flight + كاش في الذاكرة** داخل `:core` (`Mutex`/`Deferred` مشترك) حتى لا يتكرر الجلب من الوحدتين.
5. إبقاء `getVideos()` كواجهة متوافقة مؤقتاً (لكي لا تنكسر الاختبارات الحالية) مع إضافة `observeVideos(): Flow<...>`.

**الأثر:** **High** — زمن أول محتوى من ثوانٍ إلى <300ms في الإقلاع الدافئ، وإلغاء طلبات متكررة غير ضرورية، وخفض ذروة الذاكرة إلى صفحة واحدة.

---

### P2 — كل عمل التحويل على الـ Main thread

**أين:** `viewModelScope.launch` في [Wahahomeviewmodel.kt:39](mobile/src/main/java/com/waha/ui/screens/Wahahomeviewmodel.kt#L39) و[TvHomeViewModel.kt:37](tv/src/main/java/com/waha/tv/ui/screens/TvHomeViewModel.kt#L37)، مع `decodeList<VideoRow>()` داخل الحلقة، ثم `.map { it.toVideoItem() }.shuffled()` و`groupBy`.

**التفصيل:** `viewModelScope` يستخدم `Dispatchers.Main.immediate`. ktor لا يحجب الخيط أثناء انتظار الشبكة، لكن **فك ترميز 1000 صف** يتم في سياق المستدعي (Main)، وكذلك:
- `toVideoItem()` لكل صف، بما فيه `toVideoDurationText()` الذي يستخدم `String.format(Locale.US, ...)` — القياس: ~94ms لكل 20,000 صف على JVM مكتبي، أي ~5ms لـ 1000 صف (أعلى على ART).
- `shuffled()` = تخصيص قائمة كاملة إضافية + `Random`، في كل تحميل.
- `groupBy` = تخصيص قائمة لكل تصنيف + خريطة.
- `TvHomeViewModel` يكرر نفس المسار بلا أي فرق.

**الحل المقترح:**
- تغليف الجلب والتحويل في `withContext(Dispatchers.Default)` داخل `:core` (أو `flowOn` إذا تحول إلى Flow).
- إزالة `shuffled()` من طبقة البيانات: الخلط مظهر عرض، ويُفضّل خلط الفهرس مرة واحدة عند التحميل أو استخدام ترتيب شبه-عشوائي ثابت (seed) لتفادي إعادة تخصيص القائمة كل تحميل.
- حساب `durationText` أثناء التحويل من JSON (أو عند الكتابة في Room) مرة واحدة، وعدم إعادة تنسيقه عند كل عرض.
- إضافة `@Immutable` على `VideoItem` لضمان استقراره أمام Compose.

**الأثر:** **High** — إزالة أهم مصدر jank عند الإقلاع، ومنع إطارات >16ms على أجهزة ضعيفة.

---

### P3 — محرك التوصيات: O(n log n) مع Tokenization متكرر وO(n²) تنويع

**أين:** [VideoSuggestionsEngine.kt](core/src/main/java/com/waha/domain/VideoSuggestionsEngine.kt) — `build()` 147–185، `suggestionScore()` 97–118، `seriesTokens()` 77–86، `diversifyByCategory()` 124–140. يُستدعى من [VideoPlayerModal.kt:147](mobile/src/main/java/com/waha/ui/screens/VideoPlayerModal.kt#L147) و[TvPlayerScreen.kt:199](tv/src/main/java/com/waha/tv/ui/screens/TvPlayerScreen.kt#L199).

**التفصيل:**
- `seriesTokens(current.title)` يُحسب **داخل** `suggestionScore` لكل مرشح، ثم مرة أخرى في `build`، ثم `seriesTokens(candidate.title)` لكل مرشح — أي O(n) تحليلاً نصياً بـ Regex + `split`.
- `build` يُستدعى داخل composition مع `remember(video.id, allVideos, watchedIds)`، و`watchedIds` مشتق من `recentIds` الذي يتغير عند كل `record()`/`updateProgress()` → **حفظ التقدّم عند الإيقاف المؤقت يعيد بناء التوصيات كاملة**.
- `diversifyByCategory` مسح عكسي داخل حلقة + `indexOfFirst` على `ArrayDeque` لكل اختيار → O(n²) في أسوأ الحالات.
- القياس أعلاه: 163ms لـ 5k و504ms لـ 20k على JVM مكتبي مُسخّن؛ الأثر يقع في أشد اللحظات حساسية (فتح المشغل / الإيقاف المؤقت).

**الحل المقترح:**
1. **فهرسة مسبقة:** حساب `tokens: Set<String>` و`episodeNumber` و`categoryKey` مرة واحدة لكل فيديو عند التحميل (أو تخزين `tokens` في Room كنص مفصول)، ووضعها في `VideoItem` أو `PreparedVideo` داخلي.
2. **مرور واحد للتنقيط:** إعادة استخدام رموز الفيديو الحالي مرة واحدة، وحساب التقاطع بحلقة على المجموعة الأصغر.
3. **تنويع خطّي:** صناديق (buckets) لكل تصنيف مع `ArrayDeque` بدل المسح العكسي → O(n).
4. **كاش نتيجة** بمفتاح `(videoId, revision)` مع `revision` يزيد فقط عند تغيّر السجل فعلياً، وتشغيل البناء على `Dispatchers.Default` مع `produceState`.
5. الحد الأقصى للمرشحين: أخذ أفضل ~200 مرشح بالتنقيط قبل خطوة التنويع (كافٍ لـ 20 توصية) بدل ترتيب المكتبة كاملة.

**الأثر:** **High** داخل المشغل — من 163–504ms إلى 7–39ms (قياس)، أي إزالة jank فتح المشغل بالكامل تقريباً. والكاش يمنع إعادة الحساب عند كل حفظ تقدّم.

---

### P4 — قراءة التفضيلات من القرص على المسار الحرج للإقلاع

**أين:** `ThemePreferenceStore.init()` و`PlayerPreferencesStore.init()` يُستدعيان في [MainActivity.kt:18-19](mobile/src/main/java/com/waha/MainActivity.kt#L18-L19) و[TvApp/MainActivity](tv/src/main/java/com/waha/tv/MainActivity.kt#L21-L24) قبل `setContent`.

**التفصيل:** `getSharedPreferences(...)` أول استدعاء يفتح الملف ويحلّل XML بشكل متزامن على الـ Main thread. هذا عمل قرص على المسار الحرج، وانتهاك StrictMode، ويضيف عشرات المللي ثانية على تخزين بارد (خصوصاً على أجهزة التلفاز).

**الحل المقترح:**
- الانتقال إلى DataStore (غير متزامن بطبيعته) مع قيمة افتراضية فورية، أو إبقاء SharedPreferences مع `init` غير متزامن (`Dispatchers.IO`) وقراءة القيمة في `LaunchedEffect`.
- توحيد `waha_theme_prefs` و`waha_player_prefs` في DataStore واحد.
- تجنّب `apply()` في كل تغيير متكرر (ضبط الجودة نادر، فلا مشكلة، لكن توحيد المصدر يبسّط).

**الأثر:** **Medium** — إزالة 10–40ms تقريباً من الإقلاع البارد وإزالة انتهاك StrictMode.

---

### P5 — سجل المشاهدة: نسخ خرائط + كتابة كاملة + إعادة تركيب متسلسلة

**أين:** [Watchhistorystore.kt](core/src/main/java/com/waha/data/Watchhistorystore.kt) — `record()` 70–89، `updateProgress()` 91–99، `persist()` 147–158.

**التفصيل:**
- كل `record()` ينسخ `watchedAtMs`، وأحياناً خرائط أخرى، ثم يعدّل `recentIds` (وهي `mutableStateListOf` مقروءة في composition) — فيُبطل `remember` في المشغل ويعيد بناء التوصيات (P3).
- `updateProgress` ينسخ خريطتين ثم يستدعي `record` الذي ينسخ ثالثة، و`record` يغيّر ترتيب السجل بمجرد حفظ تقدّم (فتظهر إعادة تركيب غير ضرورية).
- `persist()` يبني 4 نصوص (`encodeIds` + 3 × `encodeMillis`) على خيط المستدعي (Main) قبل الانتقال إلى IO، ثم يكتب 4 مفاتيح في DataStore. لا debounce ولا دمج: إيقاف/تشغيل متكرر = كتابة ملف كامل لكل حدث.
- واجهة `items()` تعيد بناء 30 كائناً عند كل نداء، و`continueWatching()` يفرز في كل مرة.

**الحل المقترح:**
- حالة واحدة غير قابلة للتغيير: `data class HistoryState(entries: List<WatchHistoryItem>)` داخل `mutableStateOf`/`StateFlow` واحد، بدل 4 خرائط + قائمة.
- فصل «آخر فتح» عن «آخر تحديث تقدّم»: تحديث الموضع لا يعيد ترتيب السجل ولا يعيد حساب التوصيات.
- **Throttle** للتقدّم: كتابة عند الإيقاف/الخروج، وعلى الأكثر كل 10–15 ثانية أثناء التشغيل، مع تجاهل الفروق < 5 ثوانٍ.
- Debounce/Conflate لكتابة DataStore (مثلاً 1 ثانية) وكتابة حِزمة واحدة (JSON/protobuf صغير) بدل 4 مفاتيح، وتنفيذ الترميز داخل IO لا على Main.

**الأثر:** **Medium–High** داخل المشغل، **Low** على الإقلاع. يقلل الكتابة إلى القرص بأكثر من 90% في الجلسات الطويلة.

---

### P6 — ثلاث وحدات DataStore مستقلة + نمط حالة Compose غير مُحسَّن

**أين:** `Savedvideosstore.kt` (22–46)، `Recentsearchesstore.kt` (23–64)، `Watchhistorystore.kt` (48, 147)، ومستهلكوها في الواجهتين.

- ثلاث `preferencesDataStore` = ثلاثة ملفات + ثلاثة scopes، كلها تُقرأ عند الإقلاع؛ كل تعديل يعيد كتابة الملف كاملاً.
- `savedIds` و`recentIds` قائمتا حالة عامّتان؛ `contains()` داخل كل بطاقة O(n)، و`toSet()` داخل composition عند كل إعادة تركيب في المشغل.

**الحل المقترح:** DataStore واحد لمفاتيح المستخدم (المفضلة، البحث، السجل، الثيم، الجودة) + واجهات `StateFlow` غير قابلة للتعديل + `Set` موازٍ للاستعلام O(1) + `derivedStateOf` للقوائم المشتقة.

**الأثر:** **Low–Medium** (إقلاع + كتابة + إعادة تركيب).

---

### P7 — حل رابط التشغيل: تسلسل طويل بلا كاش

**أين:** [PlaybackResolver.kt:48-52](core/src/main/java/com/waha/domain/PlaybackResolver.kt#L48-L52) (18s للاستخراج ثم 6s لـ Piped)، [Youtubeextractor.kt:55](core/src/main/java/com/waha/data/Youtubeextractor.kt#L55)، [Pipedapi.kt:85-113](core/src/main/java/com/waha/data/Pipedapi.kt#L85-L113) (5 حالات معروفة + اكتشاف، كل واحدة بمهلة 8s وتُجرَّب تسلسلياً، وبلا إزالة تكرار بين المعروفة والمكتشفة).

**التفصيل:**
- في `fetchStreams` يُبنى `instanceBaseUrls + discoverInstances()` — أي أن طلب **اكتشاف الحالات يُرسل دائماً وقبل تجربة أي حالة معروفة**، مضيفاً دورة شبكة كاملة (حتى 8s) إلى كل حلّ رابط، وهو عمل زائد تماماً في الحالة الشائعة.
- تجربة الحالات تسلسلية، فأسوأ حالة قبل الفشل قد تتجاوز 30–40 ثانية؛ وفي النجاح العادي تُدفع ضريبة الاستخراج الكامل (حتى 18s) قبل كل تشغيل.
- لا كاش لروابط البث (صلاحيتها عادة ~6 ساعات)، ولا pre-resolve لأول عنصر في الشاشة، ولا ترتيب للحالات حسب زمن استجابة تاريخي.

**الحل المقترح:**
- **Hedging:** بدء استخراج الجهاز وPiped بالتوازي مع تأخير 1.5–2s، والفوز بأول نتيجة صالحة.
- تقصير المهل (استخراج 8–10s، Piped 4s لكل حالة) وتجربة الحالات **بالتوازي** مع `awaitAll` وأول نجاح.
- إزالة تكرار قائمة الحالات، وتصفية حسب صحة/كمون مُخزَّن، وتحديث الترتيب بعد كل محاولة.
- كاش LRU في الذاكرة (وعلى القرص اختيارياً) بمفتاح `(videoId, maxHeight)` مع TTL 30–60 دقيقة و`expiry` من الاستجابة.
- إعادة استخدام `HttpClient` الواحد (موجود) وإغلاقه عند الحاجة فقط (لا يوجد ما يغلقه الآن — مقبول لعملية التطبيق).

**الأثر:** **High** على زمن بدء التشغيل (لا على إقلاع التطبيق)، **Medium** على استهلاك الشبكة والبطارية.

---

### P8 — ترقيم صفحات بـ OFFSET واعتماد على فهرس غير مؤكد

- `.range(from, to)` يُترجم إلى `OFFSET`/`LIMIT`؛ في Postgres كل صفحة تعيد مسح/عدّ الصفوف السابقة، فالصفحة رقم 20 أغلى بكثير من الأولى.
- الترتيب `created_at DESC, id ASC` مع فلتر `status = 'approved'` يحتاج فهرساً مركّباً مطابقاً `(status, created_at DESC, id)` وإلا فكل صفحة = scan + sort.

**الحل المقترح:** Keyset pagination على `(created_at, id)` (مع توحيد اتجاه الترتيب)، وتأكيد وجود الفهرس المركّب في مشروع Supabase، والتحقق بـ `EXPLAIN ANALYZE`. إضافة `count` مرة واحدة عند المزامنة الكاملة فقط.

**الأثر:** **Medium–High** على زمن جلب البيانات من الخادم (وتقلّ أهميته بعد إدخال الكاش في P1).

---

## 5. خارطة الطريق المقترحة (بعد موافقتك)

**المرحلة 0 — قياس مرجعي (نصف يوم):** Macrobenchmark للإقلاع البارد/الدافئ، Perfetto trace لفتح المشغل، StrictMode في debug، وتحديد عدد صفوف المكتبة وزمن `getVideos()` الحقيقي. بلا هذه الأرقام لا يمكن إثبات أي تحسين لاحقاً.

**المرحلة 1 — البيانات والإقلاع (P1 + P2 + P8):** Room + RemoteMediator + مزامنة تفاضلية + نقل كل العمل خارج Main + فهرس القاعدة. الهدف: أول محتوى < 300ms من الكاش، صفر عمل ثقيل على Main، ذروة ذاكرة عند الجلب ≈ صفحة واحدة.

**المرحلة 2 — المشغل (P3 + P5 + P6):** فهرسة التوصيات مسبقاً + تنويع خطّي + كاش النتيجة + إصلاح كتابات السجل. الهدف: بناء التوصيات < 10ms، صفر كتابة قرص على Main، وإلغاء حلقات إعادة التركيب المتسلسلة.

**المرحلة 3 — بدء الفيديو (P7):** Hedging + كاش الروابط + ترتيب الحالات حسب الصحة. الهدف: تقليل زمن أول إطار في المشغل بنسبة ≥50%.

**معايير القبول المقترحة:**

| المقياس | الحالي (تقديري) | الهدف |
|---|---|---|
| زمن أول محتوى (إقلاع دافئ) | ثوانٍ (شبكة كاملة) | < 300 ms |
| عمل ثقيل على Main عند الإقلاع | JSON + mapping + shuffle | 0 (Default/IO فقط) |
| `VideoSuggestionsEngine.build` (5k) | ~163 ms (JVM) | < 15 ms |
| إطارات > 16ms أثناء التحميل | غير مقيس | < 1% |
| كتابات DataStore لكل دقيقة تشغيل | ~عدة كتابات كاملة | ≤ 1 (مُجمّعة) |

---

## 6. ملاحظات جانبية (ليست أداءً، لكنها ذات صلة)

- `FlexibleDurationSerializer` يعلن `PrimitiveKind.STRING` بينما `serialize` يكتب `encodeLong`. لا يؤثر على القراءة الحالية، لكنه فخّ لو أُعيد استخدام المُسلسِل للكتابة. يستحق التسجيل في قائمة الديون التقنية.
- `PipedApi.discoverInstances()` تُستدعى في **كل** عملية حلّ وقبل تجربة الحالات المعروفة؛ الأفضل إبقاء قائمة محدثة ومخزّنة (TTL ساعات) وتجربة المعروفة أولاً.
- ~~`WatchHistoryStore.record()` يستدعي `persist()` داخل `updateProgress()`، لذا أي حفظ تقدّم = إعادة ترتيب السجل + كتابة قرص~~ — عولج في المرحلة 2 (القسم 8): حفظ التقدّم لم يبقَ يعيد الترتيب أو يحدّث «آخر مشاهدة».

---

**الخطوة التالية:** بانتظار موافقتك على نطاق المرحلة 1 (أو ترتيب مختلف). لن يُعدّل أي كود قبل ذلك.

---

## 7. المرحلة 1 — ما نُفِّذ فعلياً

| البند | ما تم | الملف |
|---|---|---|
| P1 — Room كمصدر حقيقة | `VideoEntity` + `SyncMetadataEntity` + `VideoDao.observeVideos(): Flow<List<VideoEntity>>`، وقاعدة `waha-library.db` بإصدار 1 ومخطط محفوظ في `core/schemas` | [Videodatabase.kt](../core/src/main/java/com/waha/data/Videodatabase.kt) |
| P1 — القراءة من الكاش | `observeVideos()` / `observeLibrary()` من Room، والهاتف والتلفاز يجمعان الـ Flow بدل جلب كامل عند كل فتح | [Videorepository.kt](../core/src/main/java/com/waha/data/Videorepository.kt) |
| P1 — Single-flight | `SingleFlight` بمُشغِّل مستقل عن الشاشة: طلبان متزامنان يشتركان في نفس المشي، وفشل أحدهما لا يسمّم التالي | [Librarysync.kt](../core/src/main/java/com/waha/data/Librarysync.kt) |
| P8 — Keyset | `(created_at, id) < (cursor)` مترجمة إلى `or=(...lt...,and(...eq...,...lt...))`، مع `limit` بدل `range`، وأعمدة سبعة فقط | [Videorepository.kt](../core/src/main/java/com/waha/data/Videorepository.kt) |
| P8 — مزامنة تفاضلية | توقف عند أول صفحة مكتملة محلياً؛ مشي كامل (يُنظّف المحذوف بـ `syncGeneration`) كل 24 ساعة أو عند السحب للتحديث | [Videorepository.kt](../core/src/main/java/com/waha/data/Librarysync.kt) |
| P2 — خارج Main | فك الترميز والطلب على `Dispatchers.IO`، والتحويل والتنسيق والتجميع على `Dispatchers.Default` | [Videorepository.kt](../core/src/main/java/com/waha/data/Videorepository.kt) |
| P2 — `durationText` مرة واحدة | يُنسَّق عند الكتابة إلى Room ويُقرأ كحقل جاهز | [Librarysync.kt](../core/src/main/java/com/waha/data/Librarysync.kt) |
| P2 — لا `shuffled()` | أُزيل من مسار البيانات؛ الترتيب الآن `created_at DESC, id DESC` ثابت | [Wahahomeviewmodel.kt](../mobile/src/main/java/com/waha/ui/screens/Wahahomeviewmodel.kt) |

**ملاحظة على نطاق Single-flight:** يعمل داخل العملية الواحدة (شاشة + مشغل + سحب للتحديث). الهاتف والتلفاز تطبيقان منفصلان، فلا يمكن تقاسم الذاكرة بينهما؛ إلغاء التكرار بينهما يحتاج طبقة مشتركة (خادم أو جدول «آخر مزامنة» مشترك)، وهو خارج نطاق هذه المرحلة.

**التحقق:** 55 اختبار وحدة في `:core` ناجح، بناء `:mobile` و`:tv` ناجح، ومشي keyset جُرِّب فعلياً على مشروع Supabase الحقيقي: 2542 صفاً، 3 صفحات، 2542 معرّفاً فريداً بلا تكرار أو فقد.

---

## 8. المرحلة 2 — ما نُفِّذ فعلياً (P3 + P5 + P6)

| البند | ما تم | الملف |
|---|---|---|
| P3 — فهرسة مسبقة | `TitleIndex` (رموز السلسلة + رقم الحلقة) يُحسب مرة لكل عنوان ويُخزَّن في مذكرة محدودة (10k عنوان)، بدل تحليل العنوان عدة مرات لكل مرشح | [VideoSuggestionsEngine.kt](../core/src/main/java/com/waha/domain/VideoSuggestionsEngine.kt) |
| P3 — تنقيط بمرور واحد | `sharedTokenCount` يمشي المجموعة الأصغر مرة واحدة، ويُعاد استخدام رموز الفيديو الحالي ورقم حلقته لكل المرشحين | [VideoSuggestionsEngine.kt](../core/src/main/java/com/waha/domain/VideoSuggestionsEngine.kt) |
| P3 — تنويع خطّي | صناديق `ArrayDeque` لكل تصنيف + مؤشر واحد للمرتبة، بدل `indexOfFirst` داخل حلقة؛ ويتوقف عند امتلاء الرف | [VideoSuggestionsEngine.kt](../core/src/main/java/com/waha/domain/VideoSuggestionsEngine.kt) |
| P3 — كاش النتيجة | مفتاح `(videoId, title, category, count, watchedIds, library, size)`؛ إضافة الفيديو الحالي إلى السجل لا تُبطل الكاش لأنه مستثنى أصلاً | [VideoSuggestionsEngine.kt](../core/src/main/java/com/waha/domain/VideoSuggestionsEngine.kt) |
| P3 — خارج Main | بناء التوصيات داخل `produceState` على `Dispatchers.Default` في المشغلين | [VideoPlayerModal.kt](../mobile/src/main/java/com/waha/ui/screens/VideoPlayerModal.kt)، [TvPlayerScreen.kt](../tv/src/main/java/com/waha/tv/ui/screens/TvPlayerScreen.kt) |
| P5 — حالة واحدة | `HistorySnapshot` غير قابلة للتعديل + مخفضات نقية (`record`/`updateProgress`/`remove`) ومعرّف `watchedIds` بـ O(1) | [Historysnapshot.kt](../core/src/main/java/com/waha/data/Historysnapshot.kt) |
| P5 — فصل التقدّم عن الترتيب | `updateProgress` لا يعيد ترتيب السجل ولا يحدّث `watchedAtMs`، ويتجاهل الحركات < 5 ثوانٍ ويعيد نفس النسخة | [Historysnapshot.kt](../core/src/main/java/com/waha/data/Historysnapshot.kt) |
| P5 — كتابة واحدة مُجمَّعة | حِزمة واحدة (`encodeState`) تُبنى داخل IO بعد debounce ‏800ms، بدل 4 نصوص على خيط المستدعي و4 مفاتيح لكل حدث | [Watchhistorystore.kt](../core/src/main/java/com/waha/data/Watchhistorystore.kt)، [WatchHistoryCodec.kt](../core/src/main/java/com/waha/data/WatchHistoryCodec.kt) |
| P5 — ترحيل | المفاتيح القديمة تُقرأ مرة واحدة وتُكتب بالصيغة الجديدة، فلا يضيع سجل موجود | [Watchhistorystore.kt](../core/src/main/java/com/waha/data/Watchhistorystore.kt) |
| P6 — حالة غير قابلة للتعديل | `savedIds: Set<String>` و`recentQueries: List<String>` عبر `mutableStateOf`، و`isSaved()` بحث هاش بدل مسح لكل بطاقة | [Savedvideosstore.kt](../core/src/main/java/com/waha/data/Savedvideosstore.kt)، [Recentsearchesstore.kt](../core/src/main/java/com/waha/data/Recentsearchesstore.kt) |
| P6 — كتابة مُجمَّعة | تعديلات متتالية (حفظ عدة بطاقات، عدة عمليات بحث) تُدمج في كتابة واحدة بعد 400ms | نفس الملفين |

**قياس محرّك التوصيات** (JVM مكتبي، أفضل 5 محاولات بعد تسخين الفهرس، مكتبة بعناوين فريدة):

| السيناريو | قبل | بعد |
|---|---|---|
| بناء 5,000 فيديو (فهرس دافئ) | 163 ms | **4–8 ms** |
| بناء 20,000 فيديو (فهرس دافئ) | 504 ms | **55–71 ms** |
| أول بناء بعد الإقلاع (5k، يشمل فهرسة العناوين) | 163 ms | 68–79 ms |

**التحقق:** 70 اختبار وحدة في `:core` (منها 15 جديداً: كاش النتيجة، ثبات الرف عند تسجيل الفيديو الحالي، تنويع مكتبة 10k، حدود زمنية، مخفضات السجل، وترميز/فك ترميز الحزمة)، و`1` في `:mobile` و`53` في `:tv`، وبناء APK للوحدتين، وترجمة `androidTest` للتلفاز.

**قيود هذه المرحلة:**
- حدود الزمن في الاختبارات هي حراسات انحدار على JVM وليست أرقام جهاز؛ القياس الحقيقي يحتاج Macrobenchmark/Perfetto.
- مذكرة الفهرس محفوظة في الذاكرة (~10k عنوان كحد أقصى) وتُفرَّغ كاملة عند تجاوز الحد؛ لا توجد نسخة على القرص.
- تأجيل الكتابة (800/400ms) يعني أن تعديلاً في آخر أقل من ثانية قبل قتل العملية قد يضيع.
- ثلاثة ملفات DataStore ما زالت منفصلة (لم تُدمج) لتجنّب ترحيل مفاتيح المستخدم؛ التوحيد يبقى بنداً لاحقاً.

---

## 9. المرحلة 3 — ما نُفِّذ فعلياً (P7)

| البند | ما تم | الملف |
|---|---|---|
| Hedging | `firstNonNull` يشغّل الاستخراج فوراً ويُدخل Piped بعد 1.5s، والفائز يُلغي الآخر؛ فشل مبكر للاستخراج يُدخل Piped فوراً بدل انتظار المهلة | [Hedging.kt](../core/src/main/java/com/waha/data/Hedging.kt) |
| حالات Piped بالتوازي | كل الحالات تُجرَّب معاً بمهلة 4s لكل واحدة، وأول نجاح يُلغي الباقي — بدل التجربة التسلسلية (5 × 8s) | [Pipedapi.kt](../core/src/main/java/com/waha/data/Pipedapi.kt) |
| الاكتشاف خارج المسار الحرج | قائمة الحالات العامة تُحدَّث في الخلفية بصلاحية 6 ساعات (وإعادة محاولة بعد 15 دقيقة عند الفشل) — كانت تُطلب قبل كل حلّ وتضيف دورة شبكة كاملة | نفس الملف |
| إزالة التكرار + ترتيب بالصحة | `PipedInstances.order` يوحّد القائمتين ويقدّم الأسرع الناجح ويؤخّر المتعثر (عدّاد إخفاقات مسقوف عند 5) | [Pipedinstances.kt](../core/src/main/java/com/waha/data/Pipedinstances.kt) |
| كاش الروابط | `TtlCache` (LRU 32 مدخلاً، صلاحية 45 دقيقة) بمفتاح `(youtubeId, maxHeight)` يخدم إعادة فتح نفس الفيديو أو العودة لجودة مجرَّبة فوراً | [Playbackcache.kt](../core/src/main/java/com/waha/data/Playbackcache.kt) |
| تشخيص | سطر `Log.d` لكل حلّ: `cache hit` أو `resolved … in Xms` أو `no playable source` | [PlaybackResolver.kt](../core/src/main/java/com/waha/domain/PlaybackResolver.kt) |

**قياس على جهاز (محاكي Android TV، نفس الشبكة):**

| السيناريو | النتيجة |
|---|---|
| فتح فيديو أول مرة (كاش بارد) | 4.8–8.4s للحلّ، ثم تشغيل فعلي (SurfaceView + MediaCodec) |
| إعادة فتح نفس الفيديو | **29ms من `cache hit`** (بلا شبكة إطلاقاً) |
| Piped أثناء القياس | 4 من 5 حالات معطّلة (502/DNS/301) والسادسة لا تخدم الفيديو — أي أن المسار المتسلسل القديم كان سيدفع 8s لكل حالة قبل النجاح |

**قرار مهم أثناء التحقق:** خُفِّضت مهلة الاستخراج في المسودة إلى 10s، ثم أُعيدت إلى 18s بعد أن رفض التطبيق تشغيل فيديو نجح استخراجه في 14s. المكسب يجب أن يأتي من سباق المسار البديل لا من تقويض المسار الأساسي.

**قيود:**
- الحالات المتعثرة تُجرَّب بالتوازي دائماً؛ الترتيب بالصحة يغيّر الأولوية فقط، ولا يوجد تبريد (cooldown) يستثنيها كلياً.
- كاش الروابط في الذاكرة فقط، ويُفقد مع إعادة تشغيل العملية.
- لا pre-resolve لأول عنصر في الشاشة (مقصود: لا نستهلك شبكة قبل أن يختار المشاهد).
- زمن أول إطار الفعلي يعتمد على شبكة الجهاز؛ الأرقام أعلاه من محاكٍ على شبكة المطوّر.
