# Changelog

Все заметные изменения в **OpticumeClient** (Minecraft 26.3 / Java 25 / Fabric + Sodium / RenderPearl Vulkan) фиксируются в этом файле.

---

## [1.5.5-mc26.3] — 2026-11-24 (Combat Rotation 2.0: приоритет ReallyWorld / Matrix / Dexland + фикс центрирования `BetterTab`)

### Добавлено (Added)
- **Pre-Tick конвейер ([`Module.java`](src_26/main/java/dev/opticumeclient/client/modules/Module.java), [`ModuleManager.java`](src_26/main/java/dev/opticumeclient/client/modules/ModuleManager.java), [`OpticumeClient.java`](src_26/main/java/dev/opticumeclient/OpticumeClient.java)):**
  - Зарегистрирован `ClientTickEvents.START_CLIENT_TICK`, вызывающий `ModuleManager.onPreTick()` и `RotationManager.onPreTick()` строго **до** `LocalPlayer.applyInput()`, `aiStep()` и `sendPosition()` — устранён 1-тиковый десинхрон Silent-ротации, `MoveFix` и `W-Tap`.
  - Сохранена обработка поздних `END_CLIENT_TICK` запросов в `RotationManager.onPostTick()` для совместимости с остальными боевыми и утилити-модулями.
- **Движок [`CombatRotationEngine.java`](src_26/main/java/dev/opticumeclient/client/combat/CombatRotationEngine.java):**
  - Введено 1-тиковое упреждение скорости (`VelocityPredict`) с безопасным прицеливанием по ближайшей точке внутри хитбокса (`inset 14%`), смещением после попадания и резервным перебором видимых точек через `ClipContext.Block.COLLIDER`.
  - Реализованы режимы: **`Matrix`** (переменная скорость, S-дуга по pitch, post-hit сдвиг — флагман для ReallyWorld / Matrix), **`Snap`** (расслабленное сопровождение вне окна удара + быстрый burst-снап в пре-тике), **`Organic`** (пружинно-демпферная баллистика 2-го порядка + `LazyAim` — флагман для Dexland / HolyWorld / Grim), улучшенные **`Multipoint`** и **`Neuro (GRU)`** с лимитом углового ускорения, а также коррелированный дрейф Орнштейна–Уленбека.
  - Строгая проверка луча `raycastTarget(...)` через `ClipContext` (стены) и `ProjectileUtil.getEntityHitResult` (перекрывающие сущности).
- **Встроенный серверный пресет `ReallyWorld`** в [`ConfigManager.java`](src_26/main/java/dev/opticumeclient/client/config/ConfigManager.java).

### Исправлено (Fixed)
- **[`RotationManager.java`](src_26/main/java/dev/opticumeclient/client/combat/RotationManager.java), [`ClientPlayerEntityMixin.java`](src_26/main/java/dev/opticumeclient/mixin/ClientPlayerEntityMixin.java), [`SprintModule.java`](src_26/main/java/dev/opticumeclient/client/modules/movement/SprintModule.java):**
  - Битово-точная квантизация шага мыши `(float) (px * h) * 0.15f` как для Silent, так и для высокого FPS Client-режима (`quantizeClientDelta` с персистентным остатком), защита от нулевого pitch при крупных разворотах и корректный краевой кламп на `±90°`.
  - Реализован пре-тиковый `Legit W-Tap` (`requestWTap`) и автоматическая блокировка спринта при обратном/боковом `FREE MoveFix` (`shouldBlockSprint`).
- **[`OpticumeAuraModule.java`](src_26/main/java/dev/opticumeclient/client/modules/combat/OpticumeAuraModule.java), [`LegitAuraModule.java`](src_26/main/java/dev/opticumeclient/client/modules/combat/LegitAuraModule.java), [`AimAssistModule.java`](src_26/main/java/dev/opticumeclient/client/modules/combat/AimAssistModule.java):**
  - Переведены на Pre-Tick ротацию, `CombatRotationEngine`, `quantizeClientDelta`, сниженный `HitboxMargin` и обновлённые пресеты (`ReallyWorld`, `Matrix`, `Dexland`, `HolyWorld`, `FunTime`, `SpookyTime`, `Grim 1.21`).
- **Центрирование [`BetterTabModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/BetterTabModule.java) и миграция [`ConfigManager.java`](src_26/main/java/dev/opticumeclient/client/config/ConfigManager.java) (`1.5.5-26.3`):**
  - Добавлена настройка `Auto Center` (по умолчанию `true`) и якорение `PosX` по центру виджета (`centerX`) — устранено смещение `dx=+80px` при переходе между узким превью 1 колонки в чате и широким 6-колоночным Tab-листом.
  - Миграция `migratePre155Configs` автоматически обновляет старые режимы (`Smooth` → `Organic`, `SnapOnHit` → `Snap`, `Standard` → `Organic`), снижает завышенный `HitboxMargin` и сбрасывает `BetterTab.Auto Center = true`.

---

## [1.5.4-mc26.3] — 2026-11-24 (Старт с `ERR=0`, чистая караоке-лирика, вёрстка HUD и оптимизация `Ambience` для Iris Xe)

Пакет исправлений по 12 диагностическим отчётам `OpticumeReporter` (`v1.5.3` A/B-замеры на Intel Iris Xe).

### Исправлено (Fixed)
- **Устранение `ERR=40` при запуске ([`OpticumeClient.java`](src_26/main/java/dev/opticumeclient/OpticumeClient.java)):**
  - Прогрев пайплайнов `OpticumeFonts.prewarm()`, `RoundedRectRenderState.prewarm()` и `BlurredRectRenderState.prewarm()` перенесён из `CLIENT_STARTED` в `END_CLIENT_TICK` после `client.isGameLoadFinished() && client.gui.overlay() == null`, что полностью убирает 40 ранних `FileNotFoundException` до загрузки ресурсов.
- **Двоение караоке-лирики ([`DynamicIslandHud.java`](src_26/main/java/dev/opticumeclient/client/hud/DynamicIslandHud.java), [`MediaPlayerHud.java`](src_26/main/java/dev/opticumeclient/client/hud/MediaPlayerHud.java)):**
  - Активный цветной слой караоке переведён на тот же шрифт `mediumFont` / `KEY_HUD_MEDIUM`, что и серая подложка и измерение ширины, устраняя горизонтальный сдвиг и раздвоение букв.
- **Вёрстка HUD, `ArrayList`, `BetterTab` и миграция `1.5.4-26.3` ([`ArrayListHud.java`](src_26/main/java/dev/opticumeclient/client/hud/ArrayListHud.java), [`InventoryHud.java`](src_26/main/java/dev/opticumeclient/client/hud/InventoryHud.java), [`BetterTabModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/BetterTabModule.java), [`ConfigManager.java`](src_26/main/java/dev/opticumeclient/client/config/ConfigManager.java)):**
  - В `ArrayListHud` включён `Hide HUD = true` по умолчанию, добавлена настройка `Max Rows` (`16`) и динамическое ограничение по высоте экрана с компактной плашкой `+N more`.
  - Позиция по умолчанию `InventoryHUD` перенесена в правый нижний угол `(760, 415)` с мягким клампом вьюпорта.
  - В `BetterTabModule` добавлена обратная синхронизация clamped-позиции `dragWidget.syncExternalPosition(px, py)` при `!dragWidget.isDragging()`.
  - Обновлён формат конфигов до `1.5.4-26.3` с миграцией `migratePre154Configs` (разведение `ArmorHUD <-> PotionHUD`, перенос `InventoryHUD`, `ArrayList.Hide HUD = true`, `Ambience.Shader Quality = Balanced`) и автоматическим апгрейдом устаревших встроенных пресетов на диске.

### Оптимизировано (Performance)
- **Оптимизация GPU-пайплайна `Ambience` под Intel Iris Xe ([`AmbienceModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/AmbienceModule.java), [`SkyPipelines.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/ambience/SkyPipelines.java), `sky_blit.fsh`, [`AtmosphereRenderer.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/atmosphere/AtmosphereRenderer.java), `atmosphere_fog.fsh`, [`LevelRendererMixin.java`](src_26/main/java/dev/opticumeclient/mixin/LevelRendererMixin.java)):**
  - Добавлена настройка `Shader Quality` (`Fast` / `Balanced` *(по умолчанию)* / `Ultra`).
  - В `Fast`/`Balanced` все 9 процедурных шейдеров неба рендерятся в half-res `TextureTarget` (`0.5x`, в 4 раза меньше пикселей) с билинейным апскейлом `sky_blit.fsh` и без per-frame аллокации `Matrix4f`.
  - В `atmosphere_fog.fsh` и `AtmosphereRenderer` качество `fogNoise`, `God Rays` (`8/12/20` шагов), `DDA Particles` (`10/14/26` шагов) и `Fog Softness` адаптированы по `quality`, вычисление мировой нормали `dFdx/dFdy` перенесено строго внутрь `RippleParams.x > 0`, а `Saturation` объединена в один проход с `Atmosphere` через `WindParams.w` (минус лишнее полноэкранное копирование `AmbienceSaturationProgram`).

---

## [1.5.2-mc26.3] — 2026-11-23 (Главное меню в стиле osu! Lazer + фикс сдвига текста в боковых режимах)

Добавлен новый официальный режим главного меню **osu! Lazer** (по архитектуре `ppy/osu`), исправлено матричное смещение текста и иконок в боковых режимах, а все предыдущие стили оставлены доступными через переключатель `Style:` (`340` исходных Java-файлов, `269` ресурсов, `609` файлов в `src/` и `src_26/`, `0` ошибок компиляции, `8/8` автотестов).

### Добавлено (Added)
- **Официальный режим `osu! Lazer` ([`OsuLazerMenuView.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OsuLazerMenuView.java), [`MenuLayoutPreferences.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuLayoutPreferences.java), [`OpticumeMainMenuScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeMainMenuScreen.java)):**
  - Новый режим **`OSU_LAZER` (`"osu! Lazer"` / `"osu!"`)** по умолчанию с одноразовой миграцией `menu_layout_v2_migrated` и сохранением всех 4 прежних режимов (`Side Right`, `Side Left`, `Split Dock`, `Desire Center`) в переключателе `Style:`.
  - Горизонтальная лента `ButtonArea` с наклонными Liquid Glass параллелограммами (`Settings | Logo | Singleplayer · Multiplayer · AltManager · Gallery · Quit`) с отрицательным стыком `-WEDGE`, цветовой кодировкой акцентов `osu!`, пружинным расширением при наведении (`1.45x`, сдвигающим соседние кнопки), вспышкой при клике и покачиванием иконок в такт музыке.
  - Интерактивный `OsuLogo` с состояниями `Initial` ↔ `TopLevel` (опция `"osu! Start Open"` в мини-меню `≡`), `360°` радиальным аудио-визуалайзером, дрейфующими фигурами внутри круга, кольцом `Impact` и упругим перетягиванием (`rubber-band drag`).
  - Скрывающаяся верхняя панель `Toolbar` (`Settings`, `Gallery`, `Proxy` с индикатором состояния, профиль `AltManager`, управление треком `SmtcBridge` и часы `HH:mm`), а также мягкие боковые вспышки экрана `MenuSideFlashes` по ритму трека.

### Исправлено (Fixed)
- **Смещение текста и иконок в `Side Left` / `Side Right` ([`OpticumeMainMenuScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeMainMenuScreen.java)):**
  - Матричный проход фона со скосом (`Matrix3x2f` shear) и проход не-скошенной отрисовки иконок/меток/индикаторов разделены в отдельные `pushMatrix()/popMatrix()` блоки вокруг центра кнопки `(cx, cy)` — устранён прогрессирующий сдвиг `-slant * cy`, выталкивавший текст нижних кнопок влево.
  - Расчёт геометрии боковых кнопок вынесен в единый метод `computeButtonRect(...)` для синхронизации отрисовки и хит-теста в `mouseClicked`.

---

## [1.5.1-mc26.3] — 2026-11-23 (Исправления UX/UI, Новая Галерея Фонов, RenderPearl-шейдеры и аудит стабильности)

Полировка релиза `1.5.0-mc26.3` + перенос фонов из `wyvern-dlc` + новый экран галереи и мульти-раскладка главного меню в стиле `osu!` (`339` исходных Java-файлов, `269` ресурсов, `116` модулей, `54` миксина, `0` ошибок компиляции, `8/8` автотестов).

### Добавлено (Added)
- **Динамичное главное меню (`osu! Right` / `osu! Left` / `Split Dock` / `Desire Center`) ([`OpticumeMainMenuScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeMainMenuScreen.java), [`MenuLayoutPreferences.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuLayoutPreferences.java), [`MenuMusicWidget.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuMusicWidget.java)):**
  - Переключение между 4 раскладками по клику на пилюлю `Style: ...` в правом нижнем углу и мини-меню `≡` с тумблерами (`Slant Buttons`, `Pulse Logo`, `Music SMTC`, `Per-Wallpaper`) и сохранением в `menu.json`.
  - Пружинный выезд кнопок при наведении в стиле `osu!`, акцентная полоса и шеврон `>`, 2D-скос плашек без legacy OpenGL, фоновый подсчёт миров/серверов через `CompletableFuture`, `26` плавающих частиц, интерактивный пульсирующий круг `Opticume` (`EternalHeaven`) и компактный мини-плеер `SmtcBridge` + `CoverArtManager`.
- **Отдельная галерея фонов в стиле Liquid Glass ([`OpticumeGalleryScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeGalleryScreen.java), [`CustomWallpaperStore.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/CustomWallpaperStore.java), [`MenuWallpapers.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuWallpapers.java)):**
  - Перенесено `12` фонов из `wyvern-dlc` в оригинальном полном качестве (`2752×1536` и `1920×1072` PNG `wallpaper_9..20.png` + `512×512` LANCZOS-превью) в дополнение к `Vanilla Panorama` и `wallpaper_1..8` (итого `21` встроенный фон).
  - Добавлен SDF-шейдер сглаженного скругления текстур с билинейной фильтрацией и 4×4 гауссовым анти-мерцанием по `dFdx`/`dFdy` (`RoundedTextureRenderState`, `ui_rounded_texture.vsh/.fsh`, `MenuBackdrop` float-параллакс), устраняющий выпирающие углы, пикселизацию и рябь при движении мыши.
  - Левый сайдбар фильтров (`All Wallpapers`, `Favorites ★`, `Minecraft`, `Sky & Abstract`, `Anime`, `Custom`) со счётчиками и кнопкой быстрого сброса в панораму.
  - Прокручиваемая сетка **3 карточки по горизонтали**, поиск по названию (`SDL3 TextInputManager`, `Ctrl+V`, `RU→EN`), избранное `★` (сохраняется в `menu.json`) и плавное живое переключение фона с Kawase-размытием.
  - Загрузка пользовательских фонов (`PNG/JPG/BMP/GIF`) через нативный системный диалог **SDL3 `SDLDialog.SDL_ShowOpenFileDialog`** (`+ Upload`) и **Drag & Drop** в окно (`onFilesDrop`), поддержка до `3840px` по ширине, хранение в `<gameDir>/opticumeclient/wallpapers/` и удаление (`×`).

### Исправлено (Fixed)
- **Friends & Ввод текста под SDL3 (`SystemPages`, `ClickGuiScreen`, `OpticumeAltScreen`, `ProxyScreen`, `ScreenBots`):**
  - Включён нативный `Minecraft.textInputManager().startTextInput(...)` / `stopTextInput(...)` при фокусе кастомных текстовых полей (`charTyped` под SDL3 в MC 26.3), добавлена вставка `Ctrl+V`, `Ctrl+Backspace` и автотранслитерация `RU→EN` для ников.
  - Разделена прокрутка левой колонки `Saved Friends` (`friendScroll`) и правой колонки `Online Players` (`onlineScroll`) с роутингом колеса мыши по X-координате и скроллбарами.
  - Добавлены расширенные тултипы при наведении на `MMB Friend` и `Add Friend`.
- **ClickGUI — Сайдбар (`MacOS Dock ↔ Expanded`) и Верхняя панель (`ClickGuiScreen`):**
  - Единая плавно морфирующая стеклянная капсула вокруг навигации в обоих режимах (свёрнутый Dock и развёрнутый сайдбар), кроссфейд иконок/меток, корректный баланс `enableScissor` / `disableScissor` во всех ветках `renderSideBar` через `try/finally`.
  - Единообразный размер шрифта (`0.72f`) и центровка кнопок верхней панели (`Compact`, `Theme Preset`, `Bound`, `Collapse`, смена светлой/тёмной темы), SDF-сглаженный индикатор темы и тултип для режима привязки `Bound`.
- **AltManager, ProxyScreen, Gallery & Глобальные кнопки (`OpticumeAltScreen`, `ProxyScreen`, `OpticumeGalleryScreen`, `ScreenBots`, `OpticumeButtonStyle`, `KawaseBlurRenderer`):**
  - Исправлен пропуск `context.nextStratum()` перед `context.blurBeforeThisStratum()` в `OpticumeAltScreen` и `ProxyScreen` (устранён чёрный непрозрачный фон подложки), ослаблено Kawase-размытие меню до лёгкого матового (`1` проход × `0.75f`–`0.85f`), добавлен плавный параллакс, светлый прозрачный стиль Liquid Glass в стиле `MainMenu` (`0x100A0E18` + `0x10FFFFFF`), тонкие контуры и MSDF-шрифт `Inter` (`OpticumeButtonStyle.drawUiText`), а также осветлены глобальные ванильные кнопки/слайдеры, `Gallery` и `ScreenBots`.
  - Добавлен переиспользуемый каллиграфический рендерер заголовков `OpticumeButtonStyle.drawScriptTitle(...)` на базе полного 95-символьного MSDF-шрифта `EternalHeaven` (в стиле `Welcome!` главного меню) для заголовков `AltManager`, `Proxy Manager`, `Wallpaper Gallery` и будущих экранов.
- **BetterTab (`BetterTabModule`, `PlayerTabOverlayMixin`):**
  - Переведён на перетаскиваемую и масштабируемую панель `HudManager` (`DraggableWidget("TabList")`, позиция по умолчанию по центру ниже DynamicIsland), заменён непрозрачный фон на `HudPanelRenderer.drawSurface(...)` (Kawase Blur + Liquid Glass), добавлена плавная анимация открытия/закрытия на `Tab` и адаптивная сетка под вьюпорт (включая постраничный показ при до 400 игроках).
- **Ambience Sky (`CloudRendererMixin`, `SkyRendererMixin`, `sky_*.fsh`):**
  - Добавлен миксин [`CloudRendererMixin.java`](src_26/main/java/dev/opticumeclient/mixin/CloudRendererMixin.java), скрывающий ванильные объёмные облака при активном кастомном небе `Ambience`, подавлена вспышка Энда (`renderEndFlash`).
  - Устранены артефакты/пустоты во всех 9 фрагментных шейдерах неба (`clipZ = 0.0` для Reverse-Z RenderPearl, убраны инвертированные `smoothstep(edge0 > edge1)` и полярные провалы в `sky_aurora2.fsh`, `sky_northern.fsh`, `sky_night.fsh`, `sky_summer.fsh`, `sky_sakura.fsh`).
- **ChinaHat & Trails & 3D `BufferPool` (`ChinaHatModule`, `TrailsModule`, `GameRendererMixin`, `BlockESPModule`, `JumpCirclesModule`, `PopChamsModule`, `KillEffectModule`, `DeathPiece`):**
  - Частицы `ChinaHat` (`Призраки 3`) унифицированы с палитрой и текстурой `Particles` (`DESIRE_PARTICLE_HEX_ORBIZ`, пропорции `core/halo`, билбординг через `camera.rotation()`).
  - В `TrailsModule` добавлено присоединение живой интерполированной вершины к текущим координатам игрока без отставания и переведён `GameRendererMixin` на `getGameTimeDeltaPartialTick(true)`.
  - Устранено повреждение вершинного буфера (вертикальные «стержни»-артефакты): `TrailsModule`, `BlockESPModule`, `JumpCirclesModule`, `PopChamsModule` и `KillEffectModule`/`DeathPiece` переведены на строго последовательные `begin → emit → draw/discard` проходы для единого `BufferPool`.
- **Надёжность и безопасность по итогам аудита CodeRabbit (`AtomicFileWriter`, `ScriptManager`, `SmtcBridge`, `DiscordRpcService`):**
  - Вынесен общий помощник атомарной записи [`AtomicFileWriter.java`](src_26/main/java/dev/opticumeclient/client/util/AtomicFileWriter.java) (временный файл + атомарное перемещение) для `CommandDataStore`, `NeuroDatasetRecorder` и `BanTrackerStore`.
  - В `ScriptManager` добавлена блокировка выполнения правил `.oc` во время активного `PanicModule`.
  - В `SmtcBridge` чтение `stdin` переведено на асинхронный `ReadLineAsync()` внутри основного PowerShell Runspace + добавлен счётчик поколений `AtomicInteger generation`; в `DiscordRpcService` чтение Win32 Named Pipe переведено на фоновую очередь `PipeSession` без вызова `RandomAccessFile.length()`.

---

## [1.5.0-mc26.3] — 2026-11-21 (Волна 3: M16 + M17 — HUD 2.0 & SMTC Media/Karaoke, ProxyManager, Microsoft OAuth & BanTracker, Discord RPC, Neuro GRU, Bezier Editor, Scripts `.oc` и ProfilerHud)

Завершающий релиз плана `M12–M17` (`116` модулей в `7` категориях, `53` миксина, `19` команд, `332` Java-файла, `243` ресурса, `0` ошибок компиляции, `8/8` тестов пройдено).

### Добавлено (Added)
- **M16 — HUD 2.0 и подсистема Media/SMTC (`hud` + `services/media`):**
  - Индивидуальный масштаб (`scale` `0.5x..2.0x`) каждого HUD-виджета через скролл мыши / ПКМ-меню в `HudManager` (`MouseHandlerMixin`) и сохранение в `ConfigManager`.
  - `ServerKeybindsHud` (список активных предметных биндов `AssistModule`), `SchedulesHud` (ближайшие события `ServerEventsService`) и `CustomHotbarHud` (стеклянный хотбар с плавным выбором слота, HP/бронёй и заменой ванильного хотбара через `InGameHudMixin`).
  - `SmtcBridge` (единый лёгкий фоновый WinRT-процесс PowerShell с `stdin`-командами `PLAYPAUSE`/`NEXT`/`PREV` и авто-паузой), `LyricsService` (`lrclib.net` синхронизированная лирика `.lrc`), `CoverArtManager` (`DynamicTexture` с конвертацией в PNG через `ImageIO` и освобождением `TextureManager.release`), `MediaPlayerHud` и `Music Mode` + 2-слойное `enableScissor` караоке в `DynamicIslandHud`.
- **M16 — Сеть, авторизация, трекер банов и Discord RPC:**
  - `ProxyManager` + `ProxyScreen` + `ClientConnectionMixin`: pure-Netty клиент `SOCKS5` (без пароля / Username+Password) / `SOCKS4` / `HTTP CONNECT` в пайплайне `Connection` с потокобезопасной передачей целевого адреса в `Netty EventLoop`.
  - `MicrosoftOAuthService` (Device Code Flow $\to$ Xbox Live $\to$ XSTS $\to$ Minecraft Session + `User` 5-arg constructor) и `BanTrackerStore` в `OpticumeAltScreen` (добавление и таймеры активных банов по серверам).
  - `DiscordRpcService` (прямой Win32 Named Pipe `\\.\pipe\discord-ipc-0..9` без нативных DLL) и модуль `DiscordRpcModule`.
- **M17 — Нейро-ротации, интерактивный Bezier Editor, скрипты `.oc` и ProfilerHud:**
  - Pure-Java GRU-модель ротаций (`GruRotationModel`, `NeuroDatasetRecorder`, команда `.neuro record/save/train/reset`) с режимом `"Neuro (GRU)"` в `OpticumeAuraModule` и `LegitAuraModule`.
  - `ViewModelEditorScreen`: интерактивный экран настройки позиций `MainHand`/`OffHand` и перетаскиваемого графика кривой Безье (`P1`/`P2` контроль) с кнопками быстрого запуска из `ViewModelModule` и `SwingAnimationsModule`.
  - `ScriptManager` и команда `.script` (`reload`/`list`/`run`): hot-reload клиентский скриптовый движок `.oc` (`on_tick`, `on_enable`, `on_disable`, `bind`, команды `toggle`, `chat`, `notify`).
  - `ProfilerHud`: визуальный HUD-оверлей нагрузки модулей из `ModuleProfiler`.

---

## [1.4.0-mc26.3] — 2026-11-21 (Волна 2: M14 + M15 — Privacy, 9 команд + CommandFix/RU→EN, Atmosphere 2.0, KillEffect 3D и мировые визуалы)

Крупный релиз приватности, утилит, 3D-эффектов мира и модернизации RenderPearl-шейдеров (`110` модулей в `7` категориях, `53` миксина, `312` Java-файлов, `243` ресурса, `0` ошибок компиляции, `8/8` тестов пройдено).

### Добавлено (Added)
- **M14 — Приватность и защита (`other`):**
  - `NameProtectModule`: подмена своего ника и ников друзей из `FriendManager` в чате (`ChatCompactMixin`), Tab-листе (`PlayerTabOverlayMixin`), `NameTagsModule`, `TargetHudModule` и `StaffListHud`.
  - `PanicModule`: экстренное тихое отключение всех модулей/уведомлений в памяти с закрытием `ClickGuiScreen` и опциональным возвратом состояния при выключении.
  - `CaptureBypassModule` + `Win32WindowBridge`: исключение окна SDL3 из захвата OBS/Discord/BitBlt через `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` поверх встроенного **JNA 5.17.0**.
- **M14 — Команды, `CommandFix` и `RU→EN` поиск:**
  - Конфигурируемый префикс команд (`.prefix <char|reset>`), персистентное хранилище `CommandDataStore` (`<gameDir>/opticumeclient/commands.json`) и `CommandFix` (авто-исправление русской раскладки `.рудз` / `юрудз` $\to$ `.help`).
  - `9` новых команд: `.prefix`, `.macro` (клавишные макросы в `KeyboardHandlerMixin`), `.staff` (добавление модераторов в `StaffListHud`), `.way` (мульти-вейпоинты на экране `GpsManager`), `.vclip`, `.hclip`, `.fakeplayer` (`RemotePlayer` через `FakePlayerManager`), `.blockesp`, `.profiler` (`ModuleProfiler` для tick/render2D/render3D).
  - Двусторонняя `RU→EN`/`EN→RU` транслитерация и словарь русских синонимов в `SystemSearchModal`.
- **M15.3 — Новые мировые визуалы и таймеры (`+9 модулей`):**
  - `ObjectInfoModule` — 3D-радиусы и 2D-таймеры трапок, пластов, дезориентаций, смерчей, сфер/станов.
  - `BlockESPModule` — бюджетное послоевое сканирование сундуков, эндер-сундуков, шалкеров, спавнеров, древних обломков, алмазной руды и кастомных ID из `.blockesp`.
  - `TntTimerModule` и `PotsTimerModule` — таймеры взрыва `PrimedTnt`/`MinecartTNT` и облаков `AreaEffectCloud` с 3D-кругами радиуса.
  - `FireworkESPModule` — 3D-подсветка и траектория летящих фейерверков.
  - `ShulkerPreviewModule` — сетка содержимого шалкеров (`DataComponents.CONTAINER`) и мешков (`DataComponents.BUNDLE_CONTENTS`) в тултипе (`HandledScreenMixin`).
  - `ItemPhysicsModule` — реалистичное плоское лежание выброшенных предметов на земле без подпрыгивания (`ItemEntityOptMixin`).
  - `MotionBlurModule` — репроекционное размытие движения камеры на RenderPearl `TextureTarget` (`motion_blur.fsh`) строго на 3D-мире до GUI (`LevelRendererMixin`).
  - `BetterTabModule` — прозрачный стеклянный Tab-лист с головами игроков, пингом в `мс`, подсветкой друзей/стаффа и расширением лимита сверх 80 (`PlayerTabOverlayMixin`).

### Улучшено (Changed)
- **`M15.1 — Ambience -> Atmosphere 2.0` (`atmosphere_fog.fsh` + `AtmosphereRenderer` UBO 368 байт):**
  - Добавлены 20-шаговые экранные `God Rays` (Reverse-Z safe), 26-шаговые 3D DDA-частицы мировых ячеек (`Dust`, `Snow`, `Embers`) с окклюзией по глубине и плавным затуханием в помещениях (`LightLayer.SKY`), edge-aware `Fog Softness`, `Post Grade`, виньетка, зимний иней и пресет `Inferno Embers`.
- **`M15.2 — KillEffect 3D Death Animations` (`KillEffectModule` + `DeathPiece` + `DeathPoseFactory`):**
  - Встроены 4 3D-анимации смерти поверх `WorldPrimitiveRenderer`: `Shatter` (48 физических 3D-осколков с отскоком от земли), `Dissolve`, `Soul` и `Ash`, а также `Hide Corpse` в `EntityRenderDispatcherMixin` и перевод `activeBloomParticles` на `depthTest = true`.

---

## [1.3.0-mc26.3] — 2026-11-20 (Волна 1: M12 + M13 — Silent Rotation Core, Combat 2.0, Movement, QoL, Bundles/Shulkers и AuctionHelper)

Крупное расширение функциональности по результатам конкурентного анализа (`98` модулей в `7` категориях, `52` миксина, `282` Java-файла, `242` ресурса, `0` ошибок компиляции, `8/8` тестов пройдено).

### Добавлено (Added)
- **Централизованный движок ротаций (`RotationManager`):**
  - Приоритетная очередь запросов (`RotationRequest`) между боевыми и вспомогательными модулями.
  - Точная GCD-квантизация по чувствительности мыши с сохранением остатка (residual carry), ограничение скорости поворота и плавный возврат к углам камеры без snap-back.
  - Полный `MoveFix` (`Off` / `Focus` / `Free` с 8-направленной дискретизацией WASD в `ClientPlayerEntityMixin`, `EntityMixin.moveRelative` и `LivingEntityMixin.jumpFromGround`).
  - Синхронизация визуальной головы/наклона игрока от 3-го лица в `LivingEntityRendererMixin`.
- **Новые боевые модули M12 (+8 модулей):**
  - `AutoCrystalModule` + `CrystalDamageUtil` (расчёт урона с raycast-экспозицией, бронёй, Blast Protection и Resistance, `Silent` ротация, анти-суицид, 3D-подсветка).
  - `AutoAnchorModule` (установка якоря, зарядка светокамнем и безопасный подрыв вне Незера).
  - `MaceHelperModule` (`MaceAura`, `BreachSwap`, `WindCombo` с авто-подбросом).
  - `BackTrackModule` (буферизация `ClientboundMoveEntityPacket` / `ClientboundEntityPositionSyncPacket` через `VecDeltaCodec` и `PositionPath` с 3D-боксом реальной позиции сервера).
  - `HitBoxesModule` (расширение `getPickRadius` для игроков и мобов с игнором себя, друзей и ботов).
  - `AutoPotionModule` (авто-бросок взрывных баффов и хила под ноги с Silent `pitch = 88.5°`).
  - `TargetPearlModule` (симуляция траектории вражеского `ThrownEnderpearl` и баллистический ответный бросок).
  - `BowAimBotModule` (баллистическое упреждение с учётом гравитации и скорости цели для лука, арбалета и трезубца + 3D-маркер).
- **Новые модули движения, QoL и аукциона M13 (+13 модулей):**
  - **Movement:** `JesusModule` (`Solid` через `LiquidBlockMixin`, `Dolphin`, `Bounce`), `SpiderModule` (`Vanilla`, `Block Place`, `Grim Step`), `NoFallModule` (`Packet Ground`, `Water Bucket` Silent MLG, `Cancel`), `AirStuckModule`, `SpeedModule` (`Entity Collision`, `Strafe Hop`, `Vanilla Boost`), `SuperFireworkModule`.
  - **Visuals / Camera:** `FreeCamModule` и `FreeLookModule` (через `CameraMixin` и `MouseHandlerMixin`).
  - **Other / QoL:** `AutoAcceptModule` (авто-`/tpaccept` с фильтром друзей и задержкой), `LockSlotModule` (защита слотов 1–9 от Q и выброса в GUI), `NoInteractModule` (защита от случайных кликов по сундукам/печкам/калиткам/рамкам), `ServerRPSpoofModule` (пропуск серверных ресурспаков на основной сессии), `AuctionHelperModule` (подсветка Топ-3 самых дешёвых лотов за 1 ед. в `/ah`, бейдж цены за шт., математический калькулятор `/ah sell 12*64` / `500k*3`, бинд `/ah search` по предмету в руке и ручной `AutoSell` со скидкой).
- **Поддержка Мешков (Bundles) и Шалкеров (`InventoryUtil` + `AssistModule`):**
  - Поиск и мгновенное извлечение предметов из `DataComponents.BUNDLE_CONTENTS` через `ServerboundSelectBundleItemPacket`, а также поиск/подсчёт в шалкерах (`DataComponents.CONTAINER`).

### Улучшено (Changed)
- **`OpticumeAuraModule` и `LegitAuraModule`:** добавлены `Rotation (Silent / Client)` и `MoveFix (Free / Focus / Off)`, Silent-рейкаст через `RotationManager.getViewVector()`.
- **`AutoTotemModule`:** добавлены триггеры `TNT`, `Anchors`, `Mace`, `Trident`, приоритет незачарованных тотемов (`SaveEnchanted`) и возврат прежнего предмета во 2-ю руку (`SwapBack`).
- **`AutoSwapModule`:** добавлен режим 3-слотового радиального колеса `Wheel` (`AutoSwapWheelScreen`) и `Head Swap` (Шлем ↔ Голова/Сфера в слоте 5).

---

## [1.2.1-mc26.3] — 2026-11-19 (M11 — исправление багов, дедупликация и очистка)

Комплексное обновление по итогам двойного независимого аудита (`77` модулей в `7` категориях, `51` миксин, `257` Java-файлов, `242` ресурса, размер JAR снижен на ~5 МБ): объединение `Atmosphere` внутрь `Ambience`, 4 новые чат-команды, устранение всех дублирующихся функций и исправление недоделанных режимов.

### Добавлено (Added)
- **Новые чат-команды (`CommandManager`):**
  - `.friend` / `.f` (`FriendCommand`): `add <ник>`, `remove/del <ник>`, `list`, `clear`.
  - `.config` / `.cfg` (`ConfigCommand`): `load <имя>`, `save <имя>`, `delete <имя>`, `auto <имя>`, `list`.
  - `.bind` (`BindCommand`): `.bind <модуль> <клавиша|none>` и `.bind list`.
  - `.toggle` / `.t` (`ToggleCommand`): быстрое переключение любого модуля из чата с автосохранением.
- **Точные границы контейнеров (`IContainerScreenBounds` + `HandledScreenMixin`):** передача реальных `leftPos` и `topPos` из `AbstractContainerScreen` в `ItemScrollerModule` для точной работы в двойных сундуках (`176×222`), шалкерах и кастомных меню.
- **Миграция конфигов `1.2.1-26.3` (`ConfigManager`):** автоматический перенос настроек удалённых `NoHurtCam` (в `NoRender → HurtCam`) и `Atmosphere` (в `Ambience → Atmosphere`) при загрузке конфигов `1.2.0-26.3` и более ранних.

### Изменено и объединено (Changed)
- **Полное объединение `Atmosphere` внутрь `AmbienceModule`:** объёмный туман, нижняя дымка, шейдерные осадки, круги на воде и молнии интегрированы в единый модуль `Ambience` с автоматическим подавлением двойного рендера ряби при включённом `Wet World → Rain Ripples`.
- **`TargetHudModule` перенесён в категорию `HUD`:** зарегистрирован в `HudManager` (все 18 HUD-модулей теперь находятся во вкладке `HUD`) и читает живую цель `LegitAuraModule.getTarget()` напрямую.
- **Разведение `ViewModelModule` и `SwingAnimationsModule`:** из `ViewModel` убраны дублирующиеся 6 стилей взмаха и `SwingSpeed` (все 23 режима взмаха централизованы в `SwingAnimationsModule`).
- **Разведение `LowFireModule` и `NoRenderModule`:** в `NoRender` дефолт `Fire` изменён на `false`, чтобы включённый по умолчанию `LowFire` работал из коробки.
- **Разведение `VideoOptimizerModule` и `FrameSyncModule`:** управление VSync централизовано в `FrameSyncModule` с сохранением и восстановлением исходных `enableVsync` и `framerateLimit` при выключении.
- **`NameTagsModule`:** опция `"Предметы"` отключена по умолчанию, чтобы не дублировать подписи `ItemESP`.

### Исправлено (Fixed)
- **`GlowEspModule` и `EspBloomPass`:** исправлена упаковка uniform-вектора `Params` (`Params.z = saturation = 1.0f`, `Params.w = innerGlow`), устранено 65%-ное обесцвечивание свечения и заработал ползунок `Подсветка модели`.
- **`NoWebModule`:** реализована физика режима `Grim / FastFall` в `onTick()` (быстрое опускание и безопасная горизонтальная компенсация в паутине).
- **`OpticumeAuraModule`:** реализован равномерный векторный шаг наводки `Linear` (отдельно от нелинейного `Smooth`).
- **`GuiMoveModule`:** разделена логика `Grim / FunTime` (блокировка спринта только внутри контейнерных экранов `AbstractContainerScreen`) и `Vanilla`.
- **`AutoBuyEngine` и `PriceAutoParser`:** подключён периодический авто-запуск парсера цен по настройке `Авто-парсинг цен` (`autoParse`, `parseIntervalSeconds`) и резервная цена `defaultPriceIfMissing` без обнуления ручных цен при отсутствии лотов.
- **`BotsModule` и `BotSessionManager`:** настройки `Макс. ботов` и `Чат ботов в чат` читаются напрямую на лету без необходимости повторного включения модуля.
- **`SwingAnimationsModule`:** масштабирование `Размер предмета` больше не применяется к левой руке и режиму `Обычный`.
- **`KillEffectModule` и `ParticleEngineMixin`:** локальные взрывы `KillEffect` больше не блокируются настройкой `NoRender → Explosions`.
- **`ClickGuiScreen`, `SystemPages` и `ConfigManager`:** исправлена опечатка `"StopOnEat"` в серверных пресетах, учтён фокус ввода `AutoBuyPage.isTyping()` и `SystemPages.hasFocusedInput()`, закрыта перезапись встроенных пресетов (`Dexland`, `FunTime`, `HolyWorld`, `MaxFPS`) кнопкой `Save`.

### Удалено (Removed)
- Дублирующиеся модули `NoHurtCamModule`, `KillAuraModule`, `AtmosphereModule` и дублирующая настройка `NoSlow → Cobweb`.
- Неиспользуемые классы `EntityParticleEmitter` и `ParticleEmitter`, 24 неиспользуемые константы `TextureAssets`, мёртвые поля `refreshRandomizationPoint0..3` и `161` неиспользуемая PNG-текстура (~5 МБ, включая `logo_desire.png` и `cosmetics/*`).

---

## [1.2.0-mc26.3] — 2026-11-19

Крупное обновление функциональности (`80` модулей в `7` категориях, `51` миксин, `257` Java-файлов, `403` ресурса): добавлены подсистемы AutoBuy, Multi-Session Bots, чат-команды, GPS, живые серверные ивенты, шейдерные модули Ambience / Atmosphere / Hands / GlowESP / JumpCircles, менеджер тем и модульная архитектура HUD.

### Добавлено (Added)
- **AutoBuy и экономика аукциона (`client/autobuy/`, `AutoBuyModule`, `AutoBuyPage`, `AutoBuyReportHud`):**
  - Механизм автоматического поиска, фильтрации и покупки лотов на аукционе (`AutoBuyEngine`, `ItemDatabase`, `autobuy-items.json`, `ItemMatcher`, `PriceParser`, `PriceAutoParser`, `AutoBuyConfig`, `AutoBuyPresets`).
  - Автоперепродажа, управление слотами и автооплата (`AutoSeller`, `RelistManager`, `AutoPay`, `AuctionSortMode`, `AutoBuyOverlay`, `AutoBuyReport`).
  - Вкладка `AutoBuy` в ClickGUI и отдельный HUD-виджет `AutoBuyReportHUD`.
- **Менеджер ботов и мульти-сессий (`client/bots/`, `client/ui/bots/`, `BotsModule`):**
  - Фоновые клиентские боты с переключением активной сессии и авто-переподключением (`BotSessionManager`, `BotSession`, `BotContext`, `SessionContext`, `ScreenBots`, `BotMiniMap`).
  - Изоляция сетевых/рендер/звуковых вызовов через миксины: `BotChatComponentMixin`, `BotLevelExtractorMixin`, `BotLocalPlayerMixin`, `BotSoundManagerMixin`, `ClientCommonPacketListenerMixin`, `ClientConfigurationPacketListenerMixin`, `ClientConnectionMixin`, `HandledScreenMixin`.
- **Чат-команды и GPS-навигация (`client/command/`, `client/gps/`):**
  - Диспетчер `.`-команд Brigadier (`CommandManager`): `.help` (`HelpCommand`), `.gps` (`GpsCommand`), `.rct` (`RctCommand`), `.autobuy` (`AutoBuyCommand`).
  - Метки и кликабельные координаты в чате (`GpsManager`, `GpsChatLinks`, `ScreenGpsClickMixin`, `ChatCoordsMixin`, `ChatCompactMixin`).
- **Мониторинг ивентов и авто-перезаход (`client/services/`, `EventsPage`):**
  - Асинхронный парсер живых ивентов FunTime и HolyWorld (`ServerEventsService`) и страница `EventsPage` в ClickGUI.
  - Сервис быстрого перезахода `AnarchyReconnectService` с поддержкой FunTime, HolyWorld, SpookyTime и ReallyWorld.
- **Новые визуальные и шейдерные модули (RenderPearl):**
  - `AmbienceModule` и подсистема `visuals/ambience/*` (`SkyPipelines`, `FogBlurRenderer`, `WetShaderRenderer`, `AmbienceSaturationProgram`, `WorldRenderBridge`).
  - `AtmosphereModule` (`visuals/atmosphere/*`: `AtmosphereRenderer`, `StormState`) — объёмный туман, дымка, осадки и молнии.
  - `HandsModule` (`visuals/hands/HandsEffectRenderer` + перехват таргета рук в `GameRendererMixin`) — шейдеры, зеркало, свечение и шлейф рук.
  - `GlowEspModule` (`LevelRendererMixin`, `MinecraftMixin`) — bloom-свечение и контур сущностей сквозь стены.
  - `SwingAnimationsModule` (16 режимов анимации удара/предмета в стиле Desire + HMI), `BlockOverlayModule`, `DamageTintModule`, `PredictionsModule`, `AspectRatioModule`, `InterpolateF5Module`, `SeeInvisiblesModule`, `ToggleSoundsModule` (12 звуков `.ogg` и `sounds.json`).
  - Подсистема 3D-частиц (`visuals/particles/*`) и универсальный рендер мировых примитивов `WorldPrimitiveRenderer`.
- **Новые модули Combat / Movement / Other / Optimization:**
  - `Combat`: `AntiBotModule`, `AutoSwapModule`, `NoFriendDamageModule`.
  - `Movement`: `AutoToolModule`, `ElytraHelperModule`, `GuiMoveModule`, `ItemScrollerModule`, `NoWebModule`.
  - `Other`: `AssistModule`, `AutoBuyModule`, `BotsModule`.
  - `Optimization`: `VideoOptimizerModule`, `OptimizationCoordinator`, `IRenderStatePool` и набор пулинг/оптимизационных миксинов (`BlockEntityRenderDispatcherMixin`, `EntityRendererPoolMixin`, `FramerateLimitTrackerMixin`, `ItemEntityOptMixin`, `ItemEntityTickMixin`, `LevelExtractorOptMixin`, `ParticleOptMixin`, `RenderStatePoolMixin`, `ClientClockInstanceMixin`).
- **Модульная система HUD (17 модулей в категории `HUD`):**
  - Единая настройка стекла/скругления `InterfaceModule`, панель рендеринга `HudPanelRenderer`, Kawase-блюр `KawaseBlurRenderer` и шина уведомлений `HudAlertBus`.
  - Выделены самостоятельные модули `WatermarkHud`, `DynamicIslandHud`, `ArrayListHud`, `StaffListHud`, `RadarHud`, `BpsGraphHud`, `TotemCounterHud`, `CoordinatesHud`, `NotificationsHud`, а также `BindsHud`, `CooldownsHud`, `InventoryHud`, `AutoBuyReportHud`.
- **Менеджер тем и настройки (`ThemesPage`, `BindSetting`, `RunSetting`, `TextSetting`):**
  - Страница `ThemesPage` с 15 пресетами Desire + 12 пресетами Opticume и встроенным HSB+Alpha редактором пользовательских тем.
  - Дополнительные MSDF-шрифты `fonts/desire/`, UI-шейдеры (`ui_blur_gaussian`, `ui_liquid_glass`, `ui_rounded_rect`) и текстуры `textures/desire/`.

### Изменено и улучшено (Changed)
- **`JumpCirclesModule`:** портирован полноэкранный шейдер ударной волны `Desire Effect` (`shaders/core/post/jump_effect.fsh`) с реконструкцией глубины для OpenGL и Vulkan Reverse-Z, хроматической аберрацией и настройками `Amplitude` / `Aberration`; добавлен 3D-текстурный режим `Desire Circle` (`glow_fat.png`, `lean.png`, `jump.png`).
- **`DynamicIslandHud`:** переработана архитектура геометрии — горизонтальное расширение и сжатие строго симметричны в обе стороны вокруг инвариантного центра (`Auto Center` или `PosX`), добавлено экспоненциальное сглаживание, MSDF-текст `OpticumeFonts`, scissor-обрезка при кроссфейде, внутренние отступы прогресс-бара и опция `Suppress Toasts` (синхронизация с `NotificationsHud` через `HudAlertBus`).
- **`PopChamsModule`:** переписан на 3D-голограмму игрока (6 частей тела с плавным всплытием и расширением) и аддитивный взрыв частиц Desire через `WorldPrimitiveRenderer`.
- **`TrailsModule`:** расширен режимами `"Ribbon"`, `"Walk Particles"` и `"Both"` с настройками `Particle Count`, `Particle Size` и `Sparks`.
- **`ArrowsModule` и `NameTagsModule`:** по умолчанию включена опция `"Голых"` (игроки без брони больше не скрываются в лобби/одиночке); в `ArrowsModule` добавлен фильтр `"Мобов"` и цвет `MobColor`.
- **`ThemeManager` и `ThemesPage`:** добавлена поддержка удаления встроенных пресетов (список сохраняется в `<gameDir>/opticumeclient/themes/deleted_presets.json`, последняя оставшаяся тема защищена от удаления) и восстановления кнопкой `Restore built-in themes (N)`.
- **`ConfigManager`:** формат конфигурации обновлён до `1.2.0-26.3`; подавлены ложные тосты и звуки модулей при загрузке конфига; добавлена автоматическая миграция старых конфигов (разделение `InfoHUD` на 9 модулей HUD, включение `"Голых"` в `Arrows`/`NameTags`, обновление стиля `JumpCircles` на `Desire Effect`).
- **`OpticumeMainMenuScreen` и `ClickGuiScreen`:** обновлены шрифты (`ETERNAL_HEAVEN`, `DREAMLINE_SF`), добавлена сворачиваемая боковая панель Dock, пресеты размера окна и новые страницы навигации.

### Исправлено (Fixed)
- Одностороннее смещение вправо при раскрытии `DynamicIsland` и вылет прогресс-бара за скруглённые углы.
- Отсутствие индикаторов `Arrows` и `NameTags` на голых игроках; удалён дублирующий проход размытия в `NameTagsRender`.
- Конфликт регистрации виджетов `Cooldowns` и `InventoryHUD` в `HudManager` после удаления монолитного `InfoHud`.
- Перезапись профиля `Default.json` при автосохранении другого активного встроенного пресета (`Dexland`, `FunTime`, `HolyWorld`, `MaxFPS`), защита от перезаписи отклонённого `Default.json`, сохранение HUD-правок встроенных пресетов в `<Preset> Custom` (`saveHudChangeQuietly`) и строгая валидация формата версий конфига в `ConfigManager`.
- Коллизия имён файлов пользовательских тем при санитизации (`A/B` vs `A?B`), резервирование `deleted_presets.json` и правильный порядок применения удалений перед загрузкой пользовательских тем при рестарте/восстановлении в `ThemeManager` / `ThemesPage`.
- Утечка статических `activeBindingSetting` и `focusedTextSetting` в `ClickGuiScreen`, очистка фокуса при переключении/скрытии вкладок, адаптивное ограничение размера окна под малые экраны и отсечение кликов по скрытым настройкам во время сворачивания карточки в `SystemModuleCard`.
- Невозможность нажать на кнопки главного меню (`Quit`, `Gallery`) в `OpticumeMainMenuScreen` до появления их анимации входа.
- Центрирование прицела по размерам фреймбуфера (`getWidth()`/`getHeight()`) в `CrosshairModule` и конвертация координат мыши через `MouseHandler.getScaledXPos/YPos` в `HudManager` для HiDPI-экранов.
- Сохранение §-цветовых кодов (`§0`-`§f`, `§r`) в градиентном проходе MSDF-рендера `MsdfFontFace`.
- Изоляция матрицы `PoseStack` (`pushPose`/`popPose`) вокруг вызовов `renderPlayerArm` в `HeldItemRendererMixin`.
- Сохранение ванильного тумана в воде/лаве/рыхлом снеге и под эффектами `Blindness` / `Darkness` в `FogRendererMixin`.
- Безопасная проверка `instanceof IBotListener` в `ClientPacketListenerMixin` и проверка `isEnabled()` в `FrameSyncModule.isSkipUnfocusedEnabled()`.
- Централизованное освобождение GPU-ресурсов RenderPearl (шейдерные таргеты, UBO и `BufferPool`) в `GameRendererMixin.resetData` и `MinecraftMixin.close`.

### Удалено (Removed)
- `InfoHud` — разделён на 9 самостоятельных модулей категории `HUD`.
- Дублирующие эффекты поп-тотема и ходьбы в `ParticlesModule`, `ClientPacketListenerMixin` и `EntityBurstEmitter` (оставлены только в `PopChamsModule` и `TrailsModule`).
- Функция и настройка `NameTags` из `PlayerEspModule`.
- Устаревшие/дублирующие модули: `OldAnimationsModule` (заменён на `SwingAnimationsModule`), `ChamsModule`, `HitColorModule`.

---

## [1.1.0-mc26.3] — 2026-11-17 (`f44aa74`)

### Добавлено и улучшено
- Полный порт **TargetESP из Desire** (`dev.opticumeclient.client.modules.visuals.targetesp.*`, `16` классов, `35` SPIR-V шейдеров, `12` текстур, `7` режимов, `25` настроек).
- **MSDF-рендер шрифтов** (`OpticumeFonts`, `MsdfFontFace`, `msdf_text.vsh/.fsh`, интеграция в `CustomFontModule` и `GuiGraphicsExtractorMixin`).
- Тултипы и предикаты видимости `visibleWhen` в `SystemModuleCard`, исправление `FullbrightModule` через `LightmapRenderStateExtractorMixin`.
- Удалены плацебо-модули `ChunkAnimatorModule` и `FastMathModule`.

---

## [1.0.1-mc26.3] — 2026-11-16 (`4306ccd`, `a2c2c08`)

### Добавлено
- Главное меню в стиле **Desire** поверх живой панорамы Minecraft + `Gallery` (8 обоев).
- Глобальный полупрозрачный стеклянный стиль кнопок и слайдеров (`OpticumeButtonStyle`, `AbstractButtonMixin`, `AbstractSliderButtonMixin`).
- Оффлайн `AltManager` (`OpticumeAltScreen`, `MinecraftAccessor`, `MenuConfigStore`).
- Новый **System ClickGUI** (`ClickGuiScreen`, `SystemTheme`, `SystemModuleCard`, `SystemSearchModal`, `SystemPages`).
- Настройка Linux/Cloud окружения сборки под JDK 25 и Minecraft 26.3 (`setup_env_26.py`).

---

## [1.0.0-mc26.3] — 2026-11-13 (`3f6eac2`)

### Добавлено
- Первый релиз **OpticumeClient** под Minecraft 26.3 / Java 25 (Fabric Loader + Sodium + Vulkan RenderPearl, Official Mojang Mappings).
- Базовая система модулей, миксинов, `ConfigManager`, `HudManager`, `FriendManager` и скрипты пайплайна сборки (`compile_check_26.py`, `deploy_26.py`, `test_suite_26.py`).
