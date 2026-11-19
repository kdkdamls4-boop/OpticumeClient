# 🌌 OpticumeClient (Minecraft 26.3 / Java 25 / Vulkan)

**OpticumeClient** — кастомный визуальный и вспомогательный клиентский мод для **Minecraft 26.3** (Game Drop 2026), разработанный под **Java 25**, **Fabric Loader 0.20.1**, **Fabric API 0.165.2+26.3** и графический стек **Sodium 0.9.3 + Vulkan 1.4.323 (RenderPearl / Blaze3D)**.

---

## ⚡ Быстрый старт

Все команды выполняются из корня репозитория:

```powershell
# 1. Проверить компиляцию (Java 25, 257 исходных файлов, авто-синхронизация src/ <-> src_26/)
python compile_check_26.py

# 2. Собрать JAR и задеплоить в тестовый инстанс лаунчера (%APPDATA%\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods)
python deploy_26.py

# 3. Запустить полный набор автотестов (8/8 проверок)
python test_suite_26.py
```

* **Главное меню и AltManager:** кастомное меню в стиле **Desire** поверх живой панорамы Minecraft (плюс выдвижная **Gallery** для выбора обоев), рабочий оффлайн-переключатель ников **AltManager** и стеклянный стиль для всех ванильных кнопок и слайдеров игры.
* **Открыть ClickGUI в игре:** клавиша `Right Shift` (`Правый Shift`) — меню в стиле **System** со сворачиваемым сайдбаром / macOS Dock, тёмной/светлой темой, поиском `Ctrl+F`, 3-колоночной сеткой карточек, тултипами, страницами `Themes`, `Events`, `AutoBuy`, `Configs`, `Friends` и режимом `HUD Editor`.
* **Перетаскивание виджетов HUD (Drag & Drop):** откройте чат (`T`) или нажмите `HUD Editor` в ClickGUI (правый клик по виджету открывает настройки фона/стекла).

---

## 🧭 Навигация по документации

| Файл | Назначение | Кто читает |
| :--- | :--- | :--- |
| [`README.md`](README.md) | Обзор проекта, быстрый старт, структура папок, таблицы всех модулей и миксинов | Человек, GitHub, IDE, ИИ |
| [`CHANGELOG.md`](CHANGELOG.md) | Подробная история версий и исправлений по релизам | Человек, GitHub |
| [`AGENTS.md`](AGENTS.md) | Главный свод правил, ограничений безопасности, архитектуры и пайплайна сборки | Antigravity, Codex, Windsurf, Copilot |
| [`CLAUDE.md`](CLAUDE.md) | Точка входа и инструкции для агентов Claude Code | Claude Code |
| [`.cursorrules`](.cursorrules) | Системные правила для IDE Cursor | Cursor AI |
| [`VISUAL_DATABASE.md`](VISUAL_DATABASE.md) | Полная визуальная база данных: палитры, координаты UI, рендер-пайплайн | Дизайнер / Разработчик |

---

## 📂 Структура директорий

```text
OpticumeClient/
├── src/                        # Основной исходный код (257 Java-файлов + 403 ресурса, синхронизирован с src_26/)
│   └── main/
│       ├── java/dev/opticumeclient/
│       │   ├── OpticumeClient.java         # Главная точка входа мода (ClientModInitializer, MOD_ID = opticumeclient)
│       │   ├── client/
│       │   │   ├── autobuy/                # Подсистема AutoBuy (движок, база предметов, парсер цен, автоселлер)
│       │   │   ├── bots/                   # Менеджер мульти-сессионных ботов (BotSessionManager, BotContext)
│       │   │   ├── command/                # Клиентские команды Brigadier (.help, .gps, .rct, .autobuy)
│       │   │   ├── config/                 # ConfigManager (<gameDir>/opticumeclient/, формат 1.2.0-26.3)
│       │   │   ├── friend/                 # FriendManager (<gameDir>/opticumeclient/friends.json)
│       │   │   ├── gps/                    # GPS-метки и кликабельные координаты в чате
│       │   │   ├── gui/                    # System ClickGUI, SystemModuleCard, SystemSearchModal, ThemesPage, EventsPage, AutoBuyPage, SystemPages
│       │   │   ├── hud/                    # HudManager, HudAlertBus, HudPanelRenderer и 16 HUD-виджетов
│       │   │   ├── modules/                # 80 модулей по 7 категориям + ModuleManager + пакеты visuals/targetesp, ambience, atmosphere, hands, particles
│       │   │   ├── render/                 # Render2DHelper, Render3DHelper, KawaseBlurRenderer, WorldPrimitiveRenderer, font/OpticumeFonts, TextureAssets, ThemeManager
│       │   │   ├── services/               # ServerEventsService (FT/HW API) и AnarchyReconnectService (.rct)
│       │   │   ├── setting/                # Настройки модулей (Boolean, Number, Mode, MultiBox, Color, Bind, Run, Text + visibleWhen)
│       │   │   └── ui/                     # Главное меню Desire, Gallery, оффлайн AltManager, OpticumeButtonStyle, ScreenBots
│       │   └── mixin/                      # 51 Mixin-класс (Official Mojang Mappings, JAVA_25)
│       └── resources/
│           ├── fabric.mod.json             # Метаданные мода OpticumeClient (id: opticumeclient)
│           ├── opticumeclient.mixins.json  # Конфигурация 51 миксина
│           └── assets/opticumeclient/      # Иконка, локализации, шрифты MSDF, шейдеры TargetESP/UI/PostFX, звуки и текстуры Desire, обои
├── src_26/                     # Зеркало исходников для пайплайна сборки 26.3 (авто-синхронизируется с src/)
├── CHANGELOG.md                # История версий и изменений
├── compile_check_26.py         # Скрипт проверки компиляции (javac --release 25 + авто-синхронизация)
├── deploy_26.py                # Скрипт упаковки JAR и деплоя в инстанс лаунчера
└── test_suite_26.py            # Автоматический тест-сьют (8/8 проверок)
```

> [!TIP]
> **Автоматическая синхронизация `src/` и `src_26/`:**  
> При запуске `python compile_check_26.py` (или `python deploy_26.py`) обе папки автоматически синхронизируются по времени модификации файла. При удалении или переименовании файлов удаляйте старые копии в обеих директориях.

---

## 🏗️ Архитектура рендеринга и управления

```mermaid
flowchart TD
    A["Minecraft 26.3 (Java 25 + SDL3 + Vulkan 1.4.323)"] --> B["OpticumeClient (onInitializeClient)"]
    B --> C["ModuleManager (80 модулей) & CommandManager"]
    B --> D["HudManager (17 HUD-модулей + HudAlertBus)"]
    A --> E["51 Mixin (Official Mojang Mappings)"]
    E -->|"GuiMixin / AbstractButtonMixin / AbstractSliderButtonMixin"| M["OpticumeMainMenuScreen, AltManager & Стеклянные кнопки"]
    E -->|"InGameHudMixin / GuiGraphicsExtractorMixin"| F["Render2DHelper, KawaseBlur & OpticumeFonts (MSDF)"]
    E -->|"GameRendererMixin / LevelRendererMixin / CameraMixin"| G["TargetEspRenderer, JumpCircles PostFX, Ambience/Atmosphere & WorldPrimitiveRenderer"]
    E -->|"MouseHandlerMixin / KeyboardHandlerMixin"| H["System ClickGuiScreen (SDL3 Input)"]
```

---

## 🧩 Полный список модулей (80 модулей)

| Категория | Кол-во | Модули |
| :--- | :---: | :--- |
| **COMBAT** | 11 | `LegitAuraModule`, `KillAuraModule`, `OpticumeAuraModule`, `TriggerBotModule`, `AimAssistModule`, `AutoTotemModule`, `AutoSwapModule`, `ShieldBreakerModule`, `AntiBotModule`, `NoFriendDamageModule`, `MiddleClickPearlModule` |
| **FRIENDS** | 1 | `MiddleClickFriendModule` *(плюс страница `Friends` в ClickGUI)* |
| **MOVEMENT** | 10 | `SprintModule`, `NoSlowModule`, `VelocityModule`, `NoPushModule`, `NoJumpDelayModule`, `GuiMoveModule`, `AutoToolModule`, `ItemScrollerModule`, `NoWebModule`, `ElytraHelperModule` |
| **VISUALS** | 32 | `PlayerEspModule`, `TargetEspModule` *(7 режимов Desire, 25 настроек)*, `ViewModelModule`, `NameTagsModule`, `PopChamsModule`, `KillEffectModule`, `ItemEspModule`, `TracersModule`, `GlowEspModule`, `JumpCirclesModule` *(Desire Effect PostFX + 3D)*, `ParticlesModule`, `ChinaHatModule`, `HitBubblesModule`, `ArrowsModule`, `FullbrightModule`, `TrailsModule`, `CrosshairModule`, `SwingAnimationsModule`, `TargetHudModule`, `CustomFontModule` *(MSDF)*, `ToggleSoundsModule`, `NoRenderModule`, `ZoomModule`, `PredictionsModule`, `AspectRatioModule`, `InterpolateF5Module`, `BlockOverlayModule`, `DamageTintModule`, `SeeInvisiblesModule`, `HandsModule`, `AmbienceModule`, `AtmosphereModule` |
| **OPTIMIZATION** | 6 | `LowFireModule`, `NoHurtCamModule`, `MemoryCleanerModule`, `EntityCullingModule`, `FrameSyncModule`, `VideoOptimizerModule` |
| **OTHER** | 3 | `AssistModule`, `AutoBuyModule`, `BotsModule` |
| **HUD** | 17 | `InterfaceModule`, `WatermarkHud`, `DynamicIslandHud`, `ArrayListHud`, `StaffListHud`, `RadarHud`, `BpsGraphHud`, `TotemCounterHud`, `CoordinatesHud`, `NotificationsHud`, `KeystrokesHud`, `ArmorHud`, `PotionHud`, `CooldownsHud`, `BindsHud`, `InventoryHud`, `AutoBuyReportHud` |

---

## ⚠️ Важные технические особенности Minecraft 26.3

1. **Официальные маппинги (Official Mojang Mappings):**
   * В Minecraft 26.3 обфускация удалена (`GuiGraphicsExtractor`, `DeltaTracker`, `PoseStack`, `Gui.setScreen`, без Intermediary и `refmap.json`).
2. **Ввод через SDL3:**
   * В `Screen.mouseClicked(MouseButtonEvent, boolean)` используются SDL3-коды (`1=LMB, 2=MMB, 3=RMB`), а скан-коды клавиатуры задаются через `InputConstants.KEY_*`.
3. **Ленивая инициализация `ItemStack`:**
   * Все `ItemStack` для иконок HUD создаются лениво, чтобы не вызывать `NullPointerException: Components not bound yet` в `<clinit>`.
4. **Изоляция лаунчера:**
   * Деплой выполняется **только** в тестовый инстанс `%APPDATA%\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods\opticumeclient-26.3.jar`.
