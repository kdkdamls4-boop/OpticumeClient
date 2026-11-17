# 🎨 OpticumeClient — Полная Визуальная База Данных и Архитектура Проекта

> **Документ для ИИ и разработчиков:** визуальное описание интерфейса, рендер-системы, эффектов, структуры ассетов и карты компонентов клиента.

---

## 🖼️ 1. Дизайн-система и Визуальный Стиль

Клиент выполнен в стилистике **Nursultan Nextgen / Catlavan Rework / Expensive Upgrade**:
* **Концепция Liquid Glass (Жидкое стекло):**
  * Тёмные глубокие полупрозрачные плашки (`0xCC0D0E15` – `0xEE12141F`) с закругленными углами.
  * Тонкая неоновая обводка с градиентным свечением под цвет активной темы.
  * Внутренний зеркальный блик (Specular Sheen) сверху карточек и виджетов.
  * Мягкий внешний радиальный ореол свечения (`TextureAssets.GLOW_RADIAL`).
* **Палитра 12 Встроенных Тем (`ThemeManager.java`):**
  1. 🌸 **Nursultan Alpha:** Неоновый фиолет (`#7B61FF`) $\to$ Неоновый циан (`#00E5FF`).
  2. ⚡ **Catlavan Acid:** Кислотно-розовый (`#FF2A85`) $\to$ Электрик-циан (`#00F0FF`).
  3. 💜 **Expensive Purple:** Насыщенный пурпур (`#8A5CF6`) $\to$ Маджента (`#D946EF`).
  4. 🩸 **Rockstar Crimson:** Рубиново-алый (`#FF2E54`) $\to$ Огненный оранж (`#FF8A00`).
  5. ❄️ **Wexside Ice:** Ледяной лазурный (`#38BDF8`) $\to$ Глубокий индиго (`#6366F1`).
  6. 🤖 **Delta Cyber:** Неоновый мятный (`#00F5A0`) $\to$ Бирюзовый киберпанк (`#00D9F5`).
  7. 🌌 **Celestial Neon:** Ярко-розовый (`#FF007F`) $\to$ Неоновый фиолет (`#7928CA`).
  8. ⚡ **ThunderHack:** Грозовой аметист (`#A855F7`) $\to$ Маджента (`#EC4899`).
  9. 🌅 **Astolfo Sunset:** Закатный персиковый (`#FF6B9D`) $\to$ Бирюза (`#4ECDC4`).
  10. 🌊 **Rise Ocean:** Глубокий сапфировый (`#2563EB`) $\to$ Аква (`#06B6D4`).
  11. 🟢 **DeadCode Matrix:** Матричный изумруд (`#22C55E`) $\to$ Цифровой лайм (`#A3E635`).
  12. 👻 **Vape Ghost:** Стелс-синий (`#3B82F6`) $\to$ Светлый голубой (`#60A5FA`).

---

## 🖥️ 2. ClickGUI — Главное Меню Клиента

* **Режим 1 (по умолчанию):** Единое окно с сайдбаром (`Sidebar Window Mode`, размер: `660 x 430` px, центрировано).
* **Левый Сайдбар (155 px):**
  * **Шапка:** Мини-логотип молнии (`TextureAssets.MINI_LOGO`) + градиентный текст `"OpticumeClient"` и бейдж версии `"v1.0.0 • 26.3"`.
  * **Список категорий (с анимированной скользящей пилюлей):**
    * ⚔️ `Combat` (`OpticumeAura`, `KillAura`, `LegitAura`, `ShieldBreaker`, `TriggerBot`, `AimAssist`, `AutoTotem`, `MiddleClickPearl`)
    * 👁️ `Visuals` (`TargetESP`, `Crosshair`, `TargetHUD`, `ViewModel`, `NameTags`, `PopChams`, `Ambience`, `KillEffect`, `ItemESP`, `Tracers`, `Chams`, `ChinaHat`, `Trails`, `JumpCircles`, `Particles`, `NoRender`, `Zoom`, ...)
    * 🚀 `Optimization` (`EntityCulling`, `FastMath`, `FrameSync`, `ChunkAnimator`, `MemoryCleaner`, `LowFire`, `NoHurtCam`)
    * 🖥️ `HUD` (`InfoHud`, `KeystrokesHud`, `ArmorHud`, `PotionHud`)
    * ⚡ `Movement` (`Sprint`, `NoSlow`, `Velocity`, `NoPush`, `NoJumpDelay`)
    * 👥 `Friends` (модуль `MiddleClickFriend` + встроенный **Friends Manager**: ввод ника, список сохранённых друзей и быстрые кнопки `+ Add / ✓ Friend` для онлайн-игроков)
  * **Нижняя панель сайдбара:**
    * Кнопка **`Config: <active>`** (открывает экран `Configs & Presets` с загрузкой, сохранением, автозагрузкой `★ Auto` и удалением).
    * Кнопка **`Theme: <name>`** (`ThemeSelector` с 12 темами).
    * Карточка профиля игрока: сглаженная голова скина (`drawPlayerHead`), ник (fallback `"OpticumeClient"`), ранг `UID: #0001 | OpticumeClient` и индикатор FPS.
* **Правая Рабочая Зона (Контент модулей):**
  * **Верхняя панель:** Название категории, переключатель `Layout: Window / Panels`, фильтр `Bound Only` (или `+ Save New` в конфигах), строка живого поиска (`Search modules...`).
  * **Сетка карточек (Masonry Grid, 2 колонки):**
    * Карточки модулей шириной `233` px, шапка `48` px с 2-строчным переносом описания и плавающим стеклянным тултипом (`Floating Liquid Glass Tooltip`).
    * По правому клику (ПКМ) карточка раскрывается вниз, отображая вложенные компоненты: слайдеры с заполнением, выпадающие списки (`Mode`), чипы (`MultiBox`), настройку бинда (`Keybind`) и раскрывающийся **2D HSB + Alpha + Rainbow пикер** для `ColorSetting`.
* **Режим 2 (`Layout: Panels`):**
  * 6 перетаскиваемых колонок категорий шириной `152` px с раскрытием настроек внутри панелей, списком друзей в панели `Friends`, живым поиском в верхней панели (`barW = 580` px) и модальным оверлеем `Configs & Presets`.

---

## 📊 3. HUD (Оверлей Виджетов в Игре)

Виджеты свободно перемещаются мышью при открытии чата (`ChatScreen`) или ClickGUI с умными направляющими прилипания (`Smart Snap Lines`) и автоматическим сохранением координат в активный конфиг:

1. **Watermark:** Стеклянная капсула с логотипом, именем `"OpticumeClient"`, ником, FPS, пингом, скоростью (BPS), координатами XYZ.
2. **Dynamic Island:** Симметрично расширяющаяся капсула с эквалайзером, временем, телеметрией и алертами переключения модулей.
3. **ArrayList:** Раскрывающийся список активных модулей, отсортированный по ширине текста с неоновыми боковыми маркерами и бейджами режимов.
4. **TargetHUD:** Стили *Nursultan*, *Catlavan*, *ThunderHack*; пружинная анимация `EaseOutBack`, 2D-голова цели, частицы урона, двойной бар HP, броня, предмет в руке и бейдж `WIN / LOSE`.
5. **ArmorHUD:** Слоты брони + предметы в руках с полосками прочности.
6. **Cooldowns:** Иконки эндер-жемчуга, золотых яблок, хоруса и винд-чарджа с полосками перезарядки.
7. **KeyBinds:** Панель активных забиндженных клавиш и их текущий статус.
8. **StaffList:** Список обнаруженных модераторов/админов сервера.
9. **2D Radar:** Круговой радар с точками союзников (друзей) и врагов.
10. **Speedometer & BPS Graph:** Сглаженный график скорости игрока в блоках в секунду.
11. **Totem & GApple Counter:** Компактная плашка с количеством тотемов, яблок и перлов.
12. **PotionHUD:** Список активных эффектов зелий с таймерами.
13. **Keystrokes:** Интерактивные клавиши WASD + кнопки мыши (LMB/RMB) со счётчиком CPS.
14. **Coordinates:** Координаты XYZ и сторона света.
15. **Notifications:** Всплывающие уведомления с `EaseOutBack` / `EaseInBack` и защитой границ экрана.

---

## 🔮 4. 3D-Визуалы и Эффекты

* **TargetESP (полный порт Desire):**
  * 7 режимов отображения: `Карты`, `Квадрат` (Типы 1–2), `Кольцо` (Типы 1–3, включая желе-кольцо `JellyShape`), `Кристалики` (Типы 1–5), `Молнии` (Типы 1–3), `Пентограмма` (Типы 1–2), `Призраки` (Типы 1–8).
  * 25 динамически скрываемых настроек (`visibleWhen`), 35 SPIR-V-адаптированных шейдеров в `assets/opticumeclient/shaders/core/targetesp/`, 12 текстур в `assets/opticumeclient/targetesp/`, экранное преломление (`GlowDistortionPass`), Gaussian bloom (`EspBloomPass`) и динамическое освещение поверхностей блоков (`SurfaceLightingPass`).
* **CustomFont (MSDF Font Renderer):**
  * Полноценный MSDF-рендер текста (`OpticumeFonts` / `MsdfFontFace`) со шрифтами `Inter Medium`, `Inter Regular`, `Inter SemiBold`, `Outfit SemiBold` и SPIR-V шейдерами `msdf_text.vsh` / `msdf_text.fsh`.
* **Crosshair (Прицел):** Стили *Nursultan Circle*, *Catlavan Cross*, *Expensive Modern*, *Dot*.
* **ViewModel & OldAnimations:** Изолированная настройка смещения/масштаба/поворота правой и левой руки around кисти, анимации удара (`Nursultan Smooth`, `1.7 BlockHit`, `Exhibition`, др.).
* **Ambience:** Синхронизация неба и тумана (`Sync Sky`), независимые ползунки яркости.
* **JumpCircles, ChinaHat, Trails, HitBubbles, Particles (Glow Halo), Arrows, Zoom.**

---

## 📁 5. Структура Репозитория и Карта Файлов

```text
<PROJECT_ROOT>/
├── AGENTS.md                            # Главные правила и инструкции для ИИ-агентов
├── VISUAL_DATABASE.md                   # Данный документ (визуальная база и архитектура)
├── compile_check_26.py                  # Скрипт быстрой проверки компиляции (Java 25, 0 ошибок)
├── test_suite_26.py                     # Комплексный тест-сьют (8/8 проверок)
├── deploy_26.py                         # Сборка jar и деплой в instances\OPTIMUSPRIME\minecraft\mods
├── src_26/main/                         # АКТИВНЫЙ ИСХОДНЫЙ КОД ПОД MINECRAFT 26.3 (127 Java-файлов, 115 ресурсов, синхронизирован с src/)
│   ├── resources/
│   │   ├── fabric.mod.json              # Манифест мода (id: opticumeclient, name: OpticumeClient)
│   │   ├── opticumeclient.mixins.json   # Конфигурация 27 миксинов под Java 25
│   │   └── assets/opticumeclient/       # icon.png, lang/{en_us,ru_ru}.json, fonts/msdf, shaders/core/targetesp (35), targetesp (12), menu
│   └── java/dev/opticumeclient/
│       ├── OpticumeClient.java          # Точка входа Fabric ClientModInitializer
│       ├── client/
│       │   ├── config/                  # ConfigManager (<gameDir>/opticumeclient/, миграция из opticume/)
│       │   ├── friend/                  # FriendManager (<gameDir>/opticumeclient/friends.json)
│       │   ├── gui/                     # ClickGuiScreen (System), SystemTheme, SystemModuleCard, SystemSearchModal, SystemPages
│       │   ├── ui/menu/                 # OpticumeMainMenuScreen (Desire), OpticumeAltScreen, MenuWallpapers, OpticumeButtonStyle
│       │   ├── hud/                     # HudManager, InfoHud, ArmorHud, PotionHud, KeystrokesHud
│       │   ├── modules/
│       │   │   ├── Module.java / ModuleManager.java / Category.java
│       │   │   ├── animations/          # OldAnimationsModule
│       │   │   ├── combat/              # OpticumeAura, KillAura, LegitAura, ShieldBreaker, TriggerBot, AimAssist, AutoTotem, MiddleClickPearl, MiddleClickFriend
│       │   │   ├── movement/            # Sprint, NoSlow, Velocity, NoPush, NoJumpDelay
│       │   │   ├── optimization/        # EntityCulling, FrameSync, MemoryCleaner, LowFire, NoHurtCam
│       │   │   └── visuals/             # TargetEspModule + targetesp/ (16 классов), Crosshair, TargetHud, ViewModel, CustomFont, NoRender, Zoom, ...
│       │   ├── render/                  # Render2DHelper, Render3DHelper, font/OpticumeFonts, TextureAssets, ThemeManager, ColorUtils, Animation, Easing
│       │   └── setting/                 # Setting (с visibleWhen), BooleanSetting, NumberSetting, ModeSetting, ColorSetting, MultiBoxSetting
│       └── mixin/                       # 27 миксинов к ванильному клиенту MC 26.3
│           ├── GuiMixin.java (подмена TitleScreen на OpticumeMainMenuScreen)
│           ├── AbstractButtonMixin.java (стеклянный стиль ванильных кнопок Desire)
│           ├── AbstractSliderButtonMixin.java (стеклянный стиль ванильных слайдеров)
│           ├── GuiGraphicsExtractorMixin.java (MSDF CustomFont)
│           ├── LightmapRenderStateExtractorMixin.java (FullbrightModule в MC 26.3)
│           ├── MinecraftAccessor.java (переключение User в AltManager)
│           ├── CameraMixin.java (плавный Zoom)
│           ├── LivingEntityMixin.java (NoJumpDelay)
│           ├── ClientPacketListenerMixin.java (Velocity, PopChams, KillEffect)
│           ├── ScreenEffectRendererMixin.java (NoRender Fire/Totem + LowFire)
│           └── BossHealthOverlayMixin, ClientPlayerEntityMixin, EntityMixin, EntityRenderDispatcherMixin,
│               FogRendererMixin, GameRendererMixin, HeldItemRendererMixin, HumanoidArmorLayerMixin,
│               InGameHudMixin, KeyboardHandlerMixin, LivingEntityRendererMixin, MouseHandlerMixin,
│               MultiPlayerGameModeMixin, ParticleEngineMixin, PlayerEntityMixin,
│               SkyRendererMixin, WeatherEffectRendererMixin
└── reference_sources/                   # Эталонные исходники для справки
```
