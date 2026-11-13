# 🚀 OpticumeClient — Документация и Инструкции для ИИ-Агентов

> **Статус проекта:** Все майлстоуны **M1 – M5 полностью завершены и верифицированы** (`VICTORY CONFIRMED`).  
> Проект собран под **Minecraft 26.3 / Java 25 / Sodium 0.9.3 + Vulkan 1.4.323** (86 исходных файлов, 42 модуля, 15 миксинов, 0 ошибок компиляции, 7/7 тестов пройдено).

---

## 📌 1. Основная информация и пути

* **Директория проекта:** `c:\Users\User\Desktop\Projects\OPTIMUSPRIME`
* **Навигация по документации:**
  - [`README.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/README.md) — человекочитаемый обзор проекта, таблицы модулей и структура папок.
  - [`AGENTS.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/AGENTS.md) — инструкции и правила для ИИ-агентов (Antigravity, Codex, Windsurf, Copilot).
  - [`TASK_LIST_NEXT.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/TASK_LIST_NEXT.md) — актуальный технический план следующих задач (M6) с точной диагностикой багов и строк кода.
  - [`CLAUDE.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/CLAUDE.md) и [`.cursorrules`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/.cursorrules) — точки входа для Claude Code и Cursor IDE.
  - [`VISUAL_DATABASE.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/VISUAL_DATABASE.md) — полная база данных визуальной архитектуры, цветов и рендер-пайплайна.
* **Исходный код:** `src/` и `src_26/` (86 Java-файлов под Minecraft 26.3; автоматически двусторонне синхронизируются скриптом `compile_check_26.py`, поэтому редактировать можно любую из двух папок).
* **Целевая версия игры:** Minecraft **26.3** (Game Drop 2026).
* **Рантайм Java:** Java **25.0.1** (Microsoft OpenJDK 25.0.1+8-LTS):
  `C:\Users\User\AppData\Roaming\ElyPrismLauncher\java\java-runtime-epsilon\bin\javac.exe`
* **Графический стек:** Fabric Loader `0.20.1`, Fabric API `0.165.2+26.3`, Sodium `0.9.3-beta.1+mc26.3` + **Vulkan 1.4.323** (RenderPearl / Blaze3D).
* **Маппинги:** Официальные Mojang-имена (**Official Mappings**, без Intermediary и без TinyRemapper).
* **Единый чистый бэкап:** `c:\Users\User\Desktop\Projects\OPTIMUSPRIME\backups\OpticumeClient_backup.zip`

---

## ⚠️ 2. СТРОГИЕ ПРАВИЛА БЕЗОПАСНОСТИ

1. **Изоляция инстансов лаунчера:**
   * Работать и деплоить разрешено **СТРОГО И ИСКЛЮЧИТЕЛЬНО** в тестовый инстанс:
     `C:\Users\User\AppData\Roaming\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods`
     *(В лаунчере называется `Optimus Prime Test`)*.
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

Все команды запускать из корня проекта `c:\Users\User\Desktop\Projects\OPTIMUSPRIME`:

* **Проверка компиляции (`javac --release 25`):**
  ```powershell
  python compile_check_26.py
  ```
* **Запуск автоматических тестов (7/7 проверок):**
  ```powershell
  python test_suite_26.py
  ```
* **Сборка jar и деплой в лаунчер:**
  ```powershell
  python deploy_26.py
  ```

---

## ✅ 4. Полный список реализованных подсистем (M1 – M5)

1. **M1 — Управление, ввод и ClickGUI:**
   * Нормализация кнопок мыши SDL3 (`1=LMB, 2=MMB, 3=RMB` $\to$ `0=LMB, 1=RMB, 2=MMB`) в [`ClickGuiScreen.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/gui/ClickGuiScreen.java).
   * Drag & Drop виджетов HUD в `ChatScreen` через [`MouseHandlerMixin.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/mixin/MouseHandlerMixin.java).
   * Глобальная обработка биндов через [`KeyboardHandlerMixin.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/mixin/KeyboardHandlerMixin.java).
   * Единое окно с сайдбаром и карточками (`Sidebar Window` 660x430) по умолчанию.
2. **M2 — Оптимизация рендеринга и FPS (350+ FPS):**
   * Удалён `System.gc()` из [`MemoryCleanerModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/optimization/MemoryCleanerModule.java).
   * Подключены [`EntityRenderDispatcherMixin.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/mixin/EntityRenderDispatcherMixin.java) и [`MthMixin.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/mixin/MthMixin.java).
   * Оптимизированы [`Render2DHelper.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/render/Render2DHelper.java) (`TextureAssets.GLOW_RADIAL`, блочные скругления без попиксельных циклов) и [`Render3DHelper.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/render/Render3DHelper.java) (линии квадами без 2px-дробления).
   * Кеширование данных HUD в `onTick()` (20 TPS) с нулевыми аллокациями в `onRender2D`.
3. **M3 — 3D-проекция, HD-прицел и 8 визуальных модулей 4-го этапа:**
   * В `Render3DHelper.worldToScreen` внедрена точная матрица проекции камеры и динамический FOV, устранено съезжание Призрака в `TargetEspModule`; очищен чёрный фон `firefly.png`.
   * Процедурный HD-прицел `CROSSHAIR_RING_HD` (128x128) со стилями *Nursultan Circle*, *Catlavan Cross*, *Expensive Modern*, *Dot* и отрисовка предметов HUD в масштабе `1.0x`.
   * Реализованы и зарегистрированы 8 новых визуальных модулей (всего **42 модуля** и **15 миксинов**):
     - [`ViewModelModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/ViewModelModule.java)
     - [`NameTagsModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/NameTagsModule.java)
     - [`PopChamsModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/PopChamsModule.java)
     - [`AmbienceModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/AmbienceModule.java)
     - [`KillEffectModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/KillEffectModule.java)
     - [`ItemEspModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/ItemEspModule.java)
     - [`TracersModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/TracersModule.java)
     - [`ChamsModule.java`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/src/main/java/dev/optimusprime/client/modules/visuals/ChamsModule.java)
4. **M4 – M5 — Ребрендинг в `OpticumeClient`, консолидация и бэкап:**
   * Выполнен ребрендинг в `"OpticumeClient"` во всех точках отображения (`fabric.mod.json`, ClickGUI, Watermark, Dynamic Island, `MANIFEST.MF`).
   * Папка `src/` синхронизирована с `src_26/`, временные папки и старые скрипты удалены, создан единый архив [`backups/OpticumeClient_backup.zip`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/backups/OpticumeClient_backup.zip).
