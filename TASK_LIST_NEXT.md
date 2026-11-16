# 📋 OpticumeClient (MC 26.3 / Java 25) — Детальный План Задач и Техническая Спецификация (Recovery & Handoff)

> **Статус сборки перед переключением ИИ:** `0 ошибок компиляции`, `7/7 тестов пройдено`, 87 исходных файлов (`src/` и `src_26/` синхронизированы), архив восстановления сохранён в [`backups/OpticumeClient_backup.zip`](backups/OpticumeClient_backup.zip).

---

## 🔍 Детальный разбор всех 9 задач (Root-Cause Analysis & Решение)

### Задача 1. Оптимизация индикатора `Speed` (BPS Graph) в `InfoHud.java`
* **Симптом (скриншот 1):** Кажется, что из-за виджета `Speed` (`renderBpsGraph`) происходят микрофризы при ходьбе, а иконка спидометра имеет чёрный квадратный фон.
* **Точная причина в коде ([`InfoHud.java:1031-1089`](src_26/main/java/dev/optimusprime/client/hud/InfoHud.java#L1031-L1089)):**
  1. В `renderBpsGraph` каждый кадр в цикле (`for (int i = 0; i < BPS_SAMPLES - 1; i += 2)`) выполняется **60 отдельных вызовов `drawGradientRect` + `drawRect`** (по 30 сегментов кривой), а также вызов `Render2DHelper.getTextWidth(speedText, 0.74f)` прямо в `onRender2D`.
  2. Текстура `assets/optimusprime/textures/speedometer.png` имеет непрозрачный чёрный фон `RGB(0,0,0)`.
* **Что нужно сделать:**
  1. В `renderBpsGraph` уменьшить число сегментов графика до `12–15` (шаг `step = 4`) или заменить на плавную горизонтальную шкалу скорости + 12 баров без 60-кратного `fillGradient`, а ширину `speedText` использовать из закешированного в `onTick()` поля `cachedBpsW`.
  2. Очистить чёрный фон у `speedometer.png` (и других иконок HUD: `fps.png`, `ping.png`, `coords.png`, `tps.png`) или рисовать векторную иконку/процедурную дугу без чёрного квадрата.

---

### Задача 2. Анимация появления `Notifications`, защита от выхода за экран и анти-спам переполнения
* **Симптом:** Уведомления (`renderNotifications` в [`InfoHud.java:1217-1253`](src_26/main/java/dev/optimusprime/client/hud/InfoHud.java#L1217-L1253)) не имеют плавной slide-анимации выезда/исчезновения по координатам X/Y, могут выходить за пределы маленького окна Minecraft, а при быстром переключении модулей накапливаются.
* **Точная причина в коде:**
  - В `renderNotifications` переменная `anim = alert.slideAnim.getValue()` применяется только к `alpha` карточки, но **не сдвигает координаты `x` / `y`** при появлении и исчезновении (`slide-in` / `slide-out`).
  - Нет проверки нижней и правой границ экрана (`screenW = mc.getWindow().getGuiScaledWidth()`, `screenH = mc.getWindow().getGuiScaledHeight()`).
* **Что нужно сделать:**
  1. Добавить в `ToggleAlert` полноценную `in/out` анимацию: при появлении (`age < 0.25s`) и перед исчезновением (`age > 1.8s`) плавно интерполировать `offsetX = (1.0f - anim) * (w + 20.0f)` и высоту слота `slotH = (cardH + 4.0f) * anim`.
  2. Ограничить координаты `x = Math.clamp(notificationsWidget.getX(), 4.0f, Math.max(4.0f, screenW - w - 4.0f))` и `y`.
  3. Вычислять динамический лимит уведомлений под текущий размер окна (`maxVisible = Math.max(1, Math.min(4, (int)((screenH - startY - 24.0f) / (cardH + 4.0f))))`). Если новое уведомление не помещается в экран или превышает `maxVisible`, старые уведомления мгновенно переводятся в фазу быстрого исчезновения (`fastEvict`) или удаляются (0 лагов даже в маленьком окне).

---

### Задача 3. Исправление `MemoryCleaner` + Новые модули реальной оптимизации (`NoRender`, `FrameSync` / Render Optimizer)
* **Симптом:** `MemoryCleaner` сейчас ничего не делает (пустой `if` на строке 28 [`MemoryCleanerModule.java`](src_26/main/java/dev/optimusprime/client/modules/optimization/MemoryCleanerModule.java#L28-L31)), а `FastMath` реально дал `+10..25 FPS` и стабильный фреймтайм. Нужен рабочий `MemoryCleaner`, полноценный модуль **`NoRender`** и оптимизатор рендера/синхронизации кадров (**`FrameSync` / `GpuOptimizer`**).
* **Что нужно сделать:**
  1. **`MemoryCleanerModule.java`:**
     - Добавить настройки: `Mode` (`Smart Trim`, `Aggressive GC`), `Interval (sec)`, `Clean Render Caches` (очистка неиспользуемых буферов/кэшей коллекций клиента без фриза на главном потоке — вызывать фоновый `CompletableFuture.runAsync(System::gc)` только при превышении порога памяти или при открытии инвентаря/паузы, плюс сбрасывать пустые кэши партиклов/трейлов).
  2. **Новый модуль `NoRenderModule.java` (категория `VISUALS` или `OPTIMIZATION`):**
     - Настройки (`MultiBoxSetting` или `BooleanSetting`):
       - `Fire Overlay` (убирает огонь на экране)
       - `Weather` (отключает рендер дождя и снега)
       - `Totem Animation` (отключает перекрывающую экран анимацию тотема)
       - `Explosions` (убирает тяжёлые частицы взрывов кристаллов/ТНТ — огромный буст FPS в Crystal/Anchor PvP)
       - `BossBar` (скрывает боссбары)
       - `Bad Effects` (убирает слепоту, тошноту, тряску портала и тьму)
       - `Armor` / `Falling Blocks` / `ArmorStands` (отключает рендер лишних полигонов в замесах и на спавне)
       - `HurtCam` / `Pumpkin Blur` / `Scoreboard`
  3. **Новый модуль `FrameSyncModule.java` (категория `OPTIMIZATION` — аналог TikTok Frame Sync / ImmediatelyFast / Dynamic FPS буста):**
     - Настройки:
       - `Disable VSync Overhead` (принудительное отключение лимита ожидания swapchain `options.enableVsync().set(false)` и снятие лимита FPS `framerateLimit = 260` в бою).
       - `Particle Cap` (жёсткий лимит ванильных частиц в мире до 64/128, чтобы взрывы и спавн не сажали FPS в 3 раза).
       - `Dynamic Entity Distance` (динамическое снижение дальности рендера мелких сущностей и блок-энтити, когда FPS падает ниже порога).
       - `Skip Unfocused Load` (снижение нагрузки при свёрнутом окне).

---

### Задача 4. Модуль `Particles`: улучшение видимости, больше настроек и увеличение лимитов
* **Симптом:** В [`ParticlesModule.java`](src_26/main/java/dev/optimusprime/client/modules/visuals/ParticlesModule.java) частицы плохо видно: маленький лимит размера (`max = 0.65`), малый лимит количества (`max = 45`, общий пул жёстко зажат `< 90` на строках 96 и 174), всего `14` светлячков в мире (`targetAmbient = 14`), и нет яркого свечения (`Glow`).
* **Что нужно сделать в [`ParticlesModule.java`](src_26/main/java/dev/optimusprime/client/modules/visuals/ParticlesModule.java):**
  1. Увеличить лимиты ползунков:
     - `Hit Count`: default `30`, min `5`, **max `150`**.
     - `Ambient Count` (новый ползунок): default `45`, min `10`, **max `200`** (вместо зашитого `targetAmbient = 14`).
     - `Size`: default `0.45`, min `0.15`, **max `2.0`** (вместо `0.65`).
     - `Speed` (новый ползунок): `0.2 .. 3.0`.
     - `LifeTime (sec)` (новый ползунок): `1.0 .. 10.0`.
     - `Glow Halo` (`BooleanSetting`, default `true`): рисовать за каждой частицей мягкое неоновое гало `TextureAssets.GLOW_RADIAL` в `1.8x` размера частицы + яркое белое ядро, чтобы частицы светились как в Nursultan / ThunderHack!
     - `Color Mode` (`ModeSetting`: `Theme`, `Rainbow`, `Sync`, `Custom` + `ColorSetting`).
     - Поднять общий лимит пула частиц с `90` до `450`.

---

### Задача 5. Полная починка `ViewModel`: изоляция правой/левой руки, `MainScale`, вращение `RotX/Y/Z` и удар `Nursultan Smooth`
* **Симптом:**
  1. При движении ползунков `MainX/Y/Z` двигаются **сразу обе руки** (и правая, и левая), тогда как `OffX/Y/Z` двигает только левую.
  2. Ползунок `MainScale` работает некорректно (смещает руку вместо масштабирования предмета вокруг кисти).
  3. Режим `Nursultan Smooth` бьёт не в ту сторону (вбок вместо плавного рубящего удара к центру экрана).
* **Точная техническая причина (найдена через `javap -c FirstPersonHandsAndItemsRenderer`):**
  1. В Minecraft 26.3 метод `submitHandsWithItems` вызывает `submitArmWithItem` сначала для `MAIN_HAND`, а затем для `OFF_HAND`, передавая **один и тот же `PoseStack matrices`**.
  2. Внутри `submitArmWithItem` вызов `matrices.pushPose()` происходит на **96-м байте байткода** (после проверок `isScoping` и `avatarRenderState`).
  3. В [`HeldItemRendererMixin.java:28`](src_26/main/java/dev/optimusprime/mixin/HeldItemRendererMixin.java#L28) инъекция стояла в `@At("HEAD")` — то есть **ДО `matrices.pushPose()`**! Из-за этого трансформация `MAIN_HAND` записывалась в родительскую матрицу `PoseStack`, не сбрасывалась в `popPose()`, и полностью наследовалась левой рукой (`OFF_HAND`)!
  4. Кроме того, вызов `matrices.scale(...)` до `applyItemArmTransform` масштабировал базовое смещение плеча `(0.56, -0.52, -0.72)`, уводя руку за экран.
* **Что нужно сделать:**
  1. В [`HeldItemRendererMixin.java`](src_26/main/java/dev/optimusprime/mixin/HeldItemRendererMixin.java) перенести инъекцию трансформации позиции/вращения/масштаба внутрь `submitArmWithItem` **ПОСЛЕ `PoseStack.pushPose()`** (`@At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER)`) для `translate`, а `scale` и `rotate` применять в `@Inject(method = "applyItemArmTransform", at = @At("TAIL"))` (когда матрица уже находится в локальном пивоте кисти `HumanoidArm.RIGHT` / `HumanoidArm.LEFT`!).
  2. Добавить в [`ViewModelModule.java`](src_26/main/java/dev/optimusprime/client/modules/visuals/ViewModelModule.java) детальные настройки как в лучших open-source ViewModel модах (Marlow's ViewModel / ThunderHack ViewModel):
     - Правая рука: `MainX`, `MainY`, `MainZ`, `MainScale`, `MainRotX`, `MainRotY`, `MainRotZ`.
     - Левая рука: `OffX`, `OffY`, `OffZ`, `OffScale`, `OffRotX`, `OffRotY`, `OffRotZ`.
     - `No Equip Reset` (`BooleanSetting` — убирает проваливание меча вниз при переключении слотов / ударе).
  3. Исправить формулу `Nursultan Smooth` в `applyCustomSwing`:
     - В ванильном `applyItemArmAttackTransform` меч сначала поворачивается на `Axis.YP (side * 45.0f)`, затем бьёт по `Axis.XP (-75.0f * g)` и `Axis.ZP (side * -20.0f * g)`, и возвращается `Axis.YP (side * -45.0f)`. В текущей версии были пропущены пред-поворот `45°` и возврат `-45°`, из-за чего меч бил вбок.

---

### Задача 6. Отрисовка настроек модулей в режиме `Layout: Panels` (Dropdown Panels)
* **Симптом (скриншот 2):** При переключении ClickGUI в режим `Layout: Panels` и нажатии ПКМ по модулю (например, `ViewModel`) карточка растягивается вниз, но внутри неё пусто — настройки не рисуются и не кликаются.
* **Точная причина в коде ([`ClickGuiScreen.java:1076-1140`](src_26/main/java/dev/optimusprime/client/gui/ClickGuiScreen.java#L1076-L1140)):**
  - В `DropdownPanel.render` высота карточки считается через `float mH = m.isExpanded() ? calculateCardHeight(m) : 24.0f;`, но метод `renderCompactModuleCard` рисует только заголовок (`cx + 5.0f, cy + 7.0f`) и переключатель, **вообще не вызывая `renderExpandedSettings`**! А `DropdownPanel.mouseClicked` проверяет только `button == 0` (toggle) и `button == 1` (expand) по всей высоте `mH`.
* **Что нужно сделать:**
  1. Увеличить `PANEL_WIDTH` с `116.0f` до `152.0f` (или адаптировать `renderExpandedSettings` под любую ширину `cw`), вызывать `renderExpandedSettings(context, m, cx, cy, cw, ch, expandVal, mouseX, mouseY)` внутри `renderCompactModuleCard`, когда `m.isExpanded()`.
  2. В `DropdownPanel.mouseClicked` обрабатывать клики по заголовку (`mouseY <= curY + 24.0f`) отдельно от кликов по раскрытым настройкам (`mouseY > curY + 24.0f`), вызывая общий метод `handleSettingsClick(m, cx, curY + 24.0f, cw, mouseX, mouseY, button)`.

---

### Задача 7. Гармонизация цветов неба и тумана в `AmbienceModule`
* **Симптом (скриншот 3):** При включении `Ambience` цвет неба и цвет тумана резко отличаются (жёсткая граница на горизонте), а хочется либо идеального слияния, либо независимой гибкой настройки (например, оставить красивый цвет неба, но отключить или смягчить туман).
* **Точная причина в коде ([`AmbienceModule.java`](src_26/main/java/dev/optimusprime/client/modules/visuals/AmbienceModule.java), [`SkyRendererMixin.java`](src_26/main/java/dev/optimusprime/mixin/SkyRendererMixin.java), [`FogRendererMixin.java`](src_26/main/java/dev/optimusprime/mixin/FogRendererMixin.java)):**
  - По умолчанию `skyMode = "Vanilla"`, а `fogMode = "Theme"`.
  - Даже когда оба стоят в `"Theme"`, `SkyRendererMixin` умножает цвет темы на `0.45f`, а `FogRendererMixin` берёт `1.0f` без затемнения!
* **Что нужно сделать:**
  1. В `AmbienceModule`:
     - Поставить по умолчанию `skyMode = "Theme"` и `fogMode = "Sync Sky"` (новые режимы `FogMode`: `"Sync Sky"`, `"Theme"`, `"Theme Secondary"`, `"Custom"`, `"Vanilla"`).
     - Добавить ползунок `SkyBrightness` (`0.2 .. 1.0`, по умолчанию `0.65`) и `FogBrightness` (`0.2 .. 1.0`, по умолчанию `0.65`).
     - Когда `FogMode = "Sync Sky"`, цвет тумана автоматически равен текущему итоговому цвету неба (с учётом `SkyBrightness`), давая бесшовный градиент горизонта, а если игрок хочет свой туман или без тумана — он просто выключает `CustomFog` или выбирает `"Vanilla"` / `"Custom"`.

---

### Задача 8. Полное отображение описаний модулей и настроек в ClickGUI (`ClickGuiScreen.java`)
* **Симптом (скриншоты 4 и 5):** На карточках всех модулей (не только `Arrows`) описание обрезается многоточием (`Круговые OOF-стрелочки радара вокруг прице...`), а у самих настроек внутри карточки описания вообще не показываются.
* **Точная причина в коде ([`ClickGuiScreen.java:688-694`](src_26/main/java/dev/optimusprime/client/gui/ClickGuiScreen.java#L688-L694)):**
  - Метод `getTruncatedDescription` обрезает описание модуля в одну короткую строку до правого переключателя, и нигде не рисует ни 2-ю строку, ни всплывающий тултип (Tooltip) при наведении курсора на модуль или настройку!
* **Что нужно сделать:**
  1. Увеличить высоту шапки карточки `CARD_HEADER_HEIGHT` с `42.0f` до `48.0f` и выводить описание модуля **в 2 строки** (перенос по словам `wrapText(desc, maxDescW, 0.64f)`) без обрезки смысла!
  2. Добавить **плавающий стеклянный тултип (Floating Liquid Glass Tooltip)** поверх курсора при наведении на любую карточку модуля или любую настройку (`Setting.getDescription()`), чтобы полное описание каждого модуля и каждого ползунка/переключателя всегда было идеально читаемым как в Window, так и в Panels режиме!

---

## 🛠️ Команды проверки и сборки для следующей ИИ-системы

```powershell
# 1. Проверка компиляции (автоматически синхронизирует src/ и src_26/):
python compile_check_26.py

# 2. Сборка JAR и деплой СТРОГО в тестовый инстанс OPTIMUSPRIME:
python deploy_26.py

# 3. Запуск автотестов (7/7):
python test_suite_26.py
```
