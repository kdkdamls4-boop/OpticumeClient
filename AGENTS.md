# 🚀 OpticumeClient — Документация и Инструкции для ИИ-Агентов

> **Статус проекта:** Полный ребрендинг в **OpticumeClient**, главное меню в стиле **Desire**, глобальный стеклянный стиль кнопок/слайдеров, оффлайн **AltManager** и **ClickGUI в стиле System** завершены.  
> Проект собран под **Minecraft 26.3 / Java 25 / Sodium 0.9.3 + Vulkan 1.4.323** (`115` исходных Java-файлов, `49` модулей, `26` миксинов, `0` ошибок компиляции, `8/8` тестов пройдено).

---

## 📌 1. Основная информация и пути

* **Корень репозитория:** текущая рабочая директория проекта (`./`)
* **Навигация по документации:**
  - [`README.md`](README.md) — человекочитаемый обзор проекта, таблицы модулей и структура папок.
  - [`AGENTS.md`](AGENTS.md) — инструкции и правила для ИИ-Агентов (Antigravity, Codex, Windsurf, Copilot).
  - [`TASK_LIST_NEXT.md`](TASK_LIST_NEXT.md) — архив технического плана M6 (все 8 задач выполнены).
  - [`CLAUDE.md`](CLAUDE.md) и [`.cursorrules`](.cursorrules) — точки входа для Claude Code и Cursor IDE.
  - [`VISUAL_DATABASE.md`](VISUAL_DATABASE.md) — полная база данных визуальной архитектуры, цветов и рендер-пайплайна.
* **Исходный код:** `src/` и `src_26/` (`115` Java-файлов и `62` ресурса под Minecraft 26.3 в пакете `dev.opticumeclient.*`; автоматически двусторонне синхронизируются скриптом `compile_check_26.py`, при переименовании/удалении файлов удаляйте старые копии в обеих папках).
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

## ✅ 4. Полный список реализованных подсистем (M1 – M8)

1. **M1 — Управление, ввод и Input:**
   * Нормализация кнопок мыши SDL3 (`1=LMB, 2=MMB, 3=RMB` $\to$ `0=LMB, 1=RMB, 2=MMB`) в [`ClickGuiScreen.java`](src_26/main/java/dev/opticumeclient/client/gui/ClickGuiScreen.java).
   * Drag & Drop виджетов HUD в `ChatScreen` и `ClickGuiScreen` через [`HudManager.java`](src_26/main/java/dev/opticumeclient/client/hud/HudManager.java) с автосохранением позиций в конфиг.
   * Глобальная обработка биндов через [`KeyboardHandlerMixin.java`](src_26/main/java/dev/opticumeclient/mixin/KeyboardHandlerMixin.java).
2. **M2 — Оптимизация рендеринга и FPS:**
   * Умный `MemoryCleanerModule` без фризов на главном потоке, [`EntityRenderDispatcherMixin.java`](src_26/main/java/dev/opticumeclient/mixin/EntityRenderDispatcherMixin.java), [`MthMixin.java`](src_26/main/java/dev/opticumeclient/mixin/MthMixin.java), [`FrameSyncModule.java`](src_26/main/java/dev/opticumeclient/client/modules/optimization/FrameSyncModule.java), [`NoRenderModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/NoRenderModule.java) и [`LowFireModule.java`](src_26/main/java/dev/opticumeclient/client/modules/optimization/LowFireModule.java) через [`ScreenEffectRendererMixin.java`](src_26/main/java/dev/opticumeclient/mixin/ScreenEffectRendererMixin.java).
3. **M3 – M6 — 3D-проекция, визуалы, ViewModel, Ambience:**
   * Точная матрица проекции в [`Render3DHelper.java`](src_26/main/java/dev/opticumeclient/client/render/Render3DHelper.java), HD-прицел, серверные пресеты (`Default`, `Dexland`, `FunTime`, `HolyWorld`, `MaxFPS`) в [`ConfigManager.java`](src_26/main/java/dev/opticumeclient/client/config/ConfigManager.java) с авто-миграцией данных в `<gameDir>/opticumeclient/`.
4. **M7 — Ребрендинг в `OpticumeClient` и +5 модулей:**
   * Пакет `dev.opticumeclient.*`, [`OpticumeClient.java`](src_26/main/java/dev/opticumeclient/OpticumeClient.java), [`OpticumeAuraModule.java`](src_26/main/java/dev/opticumeclient/client/modules/combat/OpticumeAuraModule.java), `opticumeclient.mixins.json`, ресурсы `assets/opticumeclient/` (`icon.png`), JAR `opticumeclient-26.3.jar`.
   * Добавлены модули: [`VelocityModule.java`](src_26/main/java/dev/opticumeclient/client/modules/movement/VelocityModule.java), [`NoPushModule.java`](src_26/main/java/dev/opticumeclient/client/modules/movement/NoPushModule.java), [`NoJumpDelayModule.java`](src_26/main/java/dev/opticumeclient/client/modules/movement/NoJumpDelayModule.java), [`MiddleClickPearlModule.java`](src_26/main/java/dev/opticumeclient/client/modules/combat/MiddleClickPearlModule.java), [`ZoomModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/ZoomModule.java).
5. **M8 — Main Menu (Desire), глобальные кнопки, AltManager и ClickGUI (System):**
   * Главное меню [`OpticumeMainMenuScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeMainMenuScreen.java) + [`GuiMixin.java`](src_26/main/java/dev/opticumeclient/mixin/GuiMixin.java) + [`MenuWallpapers.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuWallpapers.java) (живая панорама по умолчанию + `Gallery` с 8 обоями).
   * Единый стеклянный стиль ванильных кнопок и слайдеров [`OpticumeButtonStyle.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeButtonStyle.java) через [`AbstractButtonMixin.java`](src_26/main/java/dev/opticumeclient/mixin/AbstractButtonMixin.java) и [`AbstractSliderButtonMixin.java`](src_26/main/java/dev/opticumeclient/mixin/AbstractSliderButtonMixin.java).
   * Оффлайн [`OpticumeAltScreen.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/OpticumeAltScreen.java) через [`MinecraftAccessor.java`](src_26/main/java/dev/opticumeclient/mixin/MinecraftAccessor.java) и [`MenuConfigStore.java`](src_26/main/java/dev/opticumeclient/client/ui/menu/MenuConfigStore.java).
   * Новый ClickGUI в стиле **System**: [`ClickGuiScreen.java`](src_26/main/java/dev/opticumeclient/client/gui/ClickGuiScreen.java), [`SystemTheme.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemTheme.java), [`SystemModuleCard.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemModuleCard.java), [`SystemSearchModal.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemSearchModal.java), [`SystemPages.java`](src_26/main/java/dev/opticumeclient/client/gui/SystemPages.java).
