# 🚀 OpticumeClient — Документация и Инструкции для ИИ-Агентов

> **Статус проекта:** Полный ребрендинг в **OpticumeClient**, главное меню в стиле **Desire**, глобальный стеклянный стиль кнопок/слайдеров, оффлайн **AltManager**, **ClickGUI в стиле System**, **MSDF-рендер шрифта**, порт **TargetESP / JumpCircles / Ambience / Atmosphere / Hands / GlowESP**, подсистемы **AutoBuy**, **Bots**, **GPS & Чат-команды**, **Server Events** и модульная система **HUD (17 модулей)** завершены.  
> Проект собран под **Minecraft 26.3 / Java 25 / Sodium 0.9.3 + Vulkan 1.4.323** (`257` исходных Java-файлов, `403` ресурса, `80` модулей, `51` миксин, `0` ошибок компиляции, `8/8` тестов пройдено).

---

## 📌 1. Основная информация и пути

* **Корень репозитория:** текущая рабочая директория проекта (`./`)
* **Навигация по документации:**
  - [`README.md`](README.md) — человекочитаемый обзор проекта, таблицы модулей и структура папок.
  - [`CHANGELOG.md`](CHANGELOG.md) — история версий и список всех изменений проекта.
  - [`AGENTS.md`](AGENTS.md) — инструкции и правила для ИИ-Агентов (Antigravity, Codex, Windsurf, Copilot).
  - [`TASK_LIST_NEXT.md`](TASK_LIST_NEXT.md) — архив технического плана M6 (все 8 задач выполнены).
  - [`CLAUDE.md`](CLAUDE.md) и [`.cursorrules`](.cursorrules) — точки входа для Claude Code и Cursor IDE.
  - [`VISUAL_DATABASE.md`](VISUAL_DATABASE.md) — полная база данных визуальной архитектуры, цветов и рендер-пайплайна.
* **Исходный код:** `src/` и `src_26/` (`257` Java-файлов и `403` ресурса под Minecraft 26.3 в пакете `dev.opticumeclient.*`; автоматически двусторонне синхронизируются скриптом `compile_check_26.py`, при переименовании/удалении файлов удаляйте старые копии в обеих папках).
* **Целевая версия игры:** Minecraft **26.3** (Game Drop 2026).
* **Рантайм Java:** Java **25** (Microsoft OpenJDK 25): `%APPDATA%\ElyPrismLauncher\java\java-runtime-epsilon\bin\javac.exe`
* **Графический стек:** Fabric Loader `0.20.1`, Fabric API `0.165.2+26.3`, Sodium `0.9.3-beta.1+mc26.3` + **Vulkan 1.4.323** (RenderPearl / Blaze3D).
* **Маппинги:** Официальные Mojang-имена (**Official Mappings**, без Intermediary и без TinyRemapper).

---

## ⚠️ 2. СТРОГИЕ ПРАВИЛА БЕЗОПАСНОСТИ

1. **Изоляция инстансов лаунчера:**
   * Работать и деплоить разрешено **СТРОГО И ИСКЛЮЧИТЕЛЬНО** в тестовый инстанс:
     `%APPDATA%\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods`
     *(Техническое имя тестового инстанса в ElyPrismLauncher: `OPTIMUSPRIME`)*.
   * **КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО** читать, изменять или трогать другие папки в `instances/`.
2. **Графика и RenderPearl / Vulkan:**
   * Не использовать устаревшие прямые вызовы OpenGL (`glBegin`, `glEnd`, `GL11`, `GlStateManager`).
   * Использовать `RenderPipelines` и ванильный `GuiGraphicsExtractor` / `BufferBuilder`.
3. **Модели субагентов (условие только для агентов Antigravity):**
   * При вызове субагентов (`invoke_subagent`) в среде **Antigravity** использовать модель **Gemini Flash** (`Model: "flash"`) при наличии большого лимита на Flash для экономии квоты основной модели. Для других ИИ-агентов это ограничение не применяется.
4. **Оформление Walkthrough:**
   * В конце каждого итогового отчёта (`walkthrough.md`) **всегда** писать раздел **«Следующие рекомендуемые действия»** с конкретными шагами для проверки и дальнейшего развития проекта.

---

## 🛠️ 3. Скрипты сборки, проверки и деплоя

Все команды запускать из корня репозитория:

* **Проверка компиляции (`javac --release 25`):**
  ```powershell
  python compile_check_26.py
  ```
* **Запуск автоматических тестов (8/8 проверок):**
  ```powershell
  python test_suite_26.py
  ```
* **Сборка jar и деплой в лаунчер (`opticumeclient-26.3.jar`):**
  ```powershell
  python deploy_26.py
  ```

---

## ✅ 4. Полный список реализованных подсистем (M1 – M10)

1. **M1 — Управление, ввод и Input:**
   * Нормализация кнопок мыши SDL3 (`1=LMB, 2=MMB, 3=RMB` $\to$ `0=LMB, 1=RMB, 2=MMB`) в [`ClickGuiScreen.java`](src_26/main/java/dev/opticumeclient/client/gui/ClickGuiScreen.java).
   * Drag & Drop виджетов HUD в `ChatScreen` и `ClickGuiScreen` через [`HudManager.java`](src_26/main/java/dev/opticumeclient/client/hud/HudManager.java) с автосохранением позиций в конфиг.
   * Глобальная обработка биндов через [`KeyboardHandlerMixin.java`](src_26/main/java/dev/opticumeclient/mixin/KeyboardHandlerMixin.java).
2. **M2 — Оптимизация рендеринга и FPS:**
   * Умный `MemoryCleanerModule` без фризов на главном потоке, [`EntityRenderDispatcherMixin.java`](src_26/main/java/dev/opticumeclient/mixin/EntityRenderDispatcherMixin.java), [`MthMixin.java`](src_26/main/java/dev/opticumeclient/mixin/MthMixin.java), [`FrameSyncModule.java`](src_26/main/java/dev/opticumeclient/client/modules/optimization/FrameSyncModule.java), [`NoRenderModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/NoRenderModule.java) и [`LowFireModule.java`](src_26/main/java/dev/opticumeclient/client/modules/optimization/LowFireModule.java) через [`ScreenEffectRendererMixin.java`](src_26/main/java/dev/opticumeclient/mixin/ScreenEffectRendererMixin.java).
3. **M3 – M6 — 3D-проекция, визуалы, ViewModel, Ambience:**
   * Точная матрица проекции в [`Render3DHelper.java`](src_26/main/java/dev/opticumeclient/client/render/Render3DHelper.java), HD-прицел, серверные пресеты (`Default`, `Dexland`, `FunTime`, `HolyWorld`, `MaxFPS`) в [`ConfigManager.java`](src_26/main/java/dev/opticumeclient/client/config/ConfigManager.java) с авто-миграцией данных в `<gameDir>/opticumeclient/`.
4. **M7 — Ребрендинг в `OpticumeClient` и новые модули:**
   * Пакет `dev.opticumeclient.*`, [`OpticumeClient.java`](src_26/main/java/dev/opticumeclient/OpticumeClient.java), [`OpticumeAuraModule.java`](src_26/main/java/dev/opticumeclient/client/modules/combat/OpticumeAuraModule.java), `opticumeclient.mixins.json`, ресурсы `assets/opticumeclient/` (`icon.png`), JAR `opticumeclient-26.3.jar`.
   * Добавлены модули: [`VelocityModule.java`](src_26/main/java/dev/opticumeclient/client/modules/movement/VelocityModule.java), [`NoPushModule.java`](src_26/main/java/dev/opticumeclient/client/modules/movement/NoPushModule.java), [`NoJumpDelayModule.java`](src_26/main/java/dev/opticumeclient/client/modules/movement/NoJumpDelayModule.java), [`MiddleClickPearlModule.java`](src_26/main/java/dev/opticumeclient/client/modules/combat/MiddleClickPearlModule.java), [`ZoomModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/ZoomModule.java).
5. **M8 — Main Menu (Desire), глобальные кнопки, AltManager и ClickGUI (System):**
   * Главное меню [`OpticumeMainMenuScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeMainMenuScreen.java) + [`GuiMixin.java`](src_26/main/java/dev/opticumeclient/mixin/GuiMixin.java) + [`MenuWallpapers.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuWallpapers.java) (живая панорама по умолчанию + `Gallery` с 8 обоями).
   * Единый стеклянный стиль ванильных кнопок и слайдеров [`OpticumeButtonStyle.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeButtonStyle.java) через [`AbstractButtonMixin.java`](src_26/main/java/dev/opticumeclient/mixin/AbstractButtonMixin.java) и [`AbstractSliderButtonMixin.java`](src_26/main/java/dev/opticumeclient/mixin/AbstractSliderButtonMixin.java).
   * Оффлайн [`OpticumeAltScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeAltScreen.java) через [`MinecraftAccessor.java`](src_26/main/java/dev/opticumeclient/mixin/MinecraftAccessor.java) и [`MenuConfigStore.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuConfigStore.java).
   * Новый ClickGUI в стиле **System**: [`ClickGuiScreen.java`](src_26/main/java/dev/opticumeclient/client/gui/ClickGuiScreen.java), [`SystemTheme.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemTheme.java), [`SystemModuleCard.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemModuleCard.java), [`SystemSearchModal.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemSearchModal.java), [`SystemPages.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemPages.java).
6. **M9 — Кастомный MSDF-шрифт, порт Desire TargetESP, анимации HUD и очистка плацебо-модулей:**
   * Полноценный **MSDF-рендер шрифта** [`OpticumeFonts.java`](src_26/main/java/dev/opticumeclient/client/render/font/OpticumeFonts.java) и [`MsdfFontFace.java`](src_26/main/java/dev/opticumeclient/client/render/font/MsdfFontFace.java) (`Inter Medium`, `Inter Regular`, `Inter SemiBold`, `Outfit SemiBold`) с SPIR-V шейдерами `msdf_text.vsh`/`.fsh` и интеграцией в [`CustomFontModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/CustomFontModule.java) и [`GuiGraphicsExtractorMixin.java`](src_26/main/java/dev/opticumeclient/mixin/GuiGraphicsExtractorMixin.java).
   * Полный порт **Desire TargetESP** в пакете `dev.opticumeclient.client.modules.visuals.targetesp.*` (`16` классов во главе с [`TargetEspRenderer.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/targetesp/TargetEspRenderer.java), `35` SPIR-V шейдеров, `12` PNG-текстур, `7` режимов и `25` динамически скрываемых настроек в [`TargetEspModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/TargetEspModule.java)).
   * Очистка плацебо-модулей (`ChunkAnimatorModule`, `FastMathModule`), фикс `FullbrightModule` через [`LightmapRenderStateExtractorMixin.java`](src_26/main/java/dev/opticumeclient/mixin/LightmapRenderStateExtractorMixin.java), тултипы и предикаты видимости (`visibleWhen`) в [`SystemModuleCard.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemModuleCard.java).
7. **M10 (`1.2.0-mc26.3`) — Модульный HUD, AutoBuy, Bots, Чат-команды, Server Events и RenderPearl-визуалы:**
   * **Модульный HUD (17 модулей):** единый стиль [`InterfaceModule.java`](src_26/main/java/dev/opticumeclient/client/modules/hud/InterfaceModule.java), [`HudPanelRenderer.java`](src_26/main/java/dev/opticumeclient/client/hud/HudPanelRenderer.java), [`KawaseBlurRenderer.java`](src_26/main/java/dev/opticumeclient/client/render/KawaseBlurRenderer.java) и шина [`HudAlertBus.java`](src_26/main/java/dev/opticumeclient/client/hud/HudAlertBus.java); монолитный `InfoHud` разделён на `9` самостоятельных модулей ([`WatermarkHud.java`](src_26/main/java/dev/opticumeclient/client/hud/WatermarkHud.java), симметричный [`DynamicIslandHud.java`](src_26/main/java/dev/opticumeclient/client/hud/DynamicIslandHud.java), [`ArrayListHud.java`](src_26/main/java/dev/opticumeclient/client/hud/ArrayListHud.java), [`StaffListHud.java`](src_26/main/java/dev/opticumeclient/client/hud/StaffListHud.java), [`RadarHud.java`](src_26/main/java/dev/opticumeclient/client/hud/RadarHud.java), [`BpsGraphHud.java`](src_26/main/java/dev/opticumeclient/client/hud/BpsGraphHud.java), [`TotemCounterHud.java`](src_26/main/java/dev/opticumeclient/client/hud/TotemCounterHud.java), [`CoordinatesHud.java`](src_26/main/java/dev/opticumeclient/client/hud/CoordinatesHud.java), [`NotificationsHud.java`](src_26/main/java/dev/opticumeclient/client/hud/NotificationsHud.java)), плюс `BindsHud`, `CooldownsHud`, `InventoryHud`, `AutoBuyReportHud`.
   * **AutoBuy & Bots & Commands:** подсистема `client/autobuy/*` + [`AutoBuyPage.java`](src_26/main/java/dev/opticumeclient/client/gui/AutoBuyPage.java), мульти-сессионные боты `client/bots/*` + [`ScreenBots.java`](src_26/main/java/dev/opticumeclient/client/ui/bots/ScreenBots.java), диспетчер `.`-команд (`CommandManager`: `.help`, `.gps`, `.rct`, `.autobuy`), GPS-метки `client/gps/*`, живые ивенты FunTime/HolyWorld [`ServerEventsService.java`](src_26/main/java/dev/opticumeclient/client/services/ServerEventsService.java) + [`EventsPage.java`](src_26/main/java/dev/opticumeclient/client/gui/EventsPage.java) и авто-перезаход [`AnarchyReconnectService.java`](src_26/main/java/dev/opticumeclient/client/services/AnarchyReconnectService.java).
   * **Шейдеры, визуалы и менеджер тем:** `Desire Effect` в [`JumpCirclesModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/JumpCirclesModule.java) (`jump_effect.fsh`), [`AmbienceModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/AmbienceModule.java), [`AtmosphereModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/AtmosphereModule.java), [`HandsModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/HandsModule.java), [`GlowEspModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/GlowEspModule.java), [`SwingAnimationsModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/SwingAnimationsModule.java), [`PopChamsModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/PopChamsModule.java), [`TrailsModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/TrailsModule.java), [`ThemesPage.java`](src_26/main/java/dev/opticumeclient/client/gui/ThemesPage.java) (удаление/восстановление пресетов + кастомные темы в [`ThemeManager.java`](src_26/main/java/dev/opticumeclient/client/render/ThemeManager.java)) и миграция конфигов `1.2.0-26.3`.

