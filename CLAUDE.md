# CLAUDE.md — Инструкции для Claude Code и ИИ-Агентов

При работе с этим репозиторием сначала ознакомьтесь с базовыми документами проекта:
1. **[`AGENTS.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/AGENTS.md)** — главный свод правил безопасности, архитектуры и команд сборки.
2. **[`README.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/README.md)** — структура директорий, список всех 42 модулей и 15 миксинов.
3. **[`VISUAL_DATABASE.md`](file:///c:/Users/User/Desktop/Projects/OPTIMUSPRIME/VISUAL_DATABASE.md)** — детальная спецификация UI, HUD, цветовых тем и 2D/3D рендеринга.

---

## Ключевые команды (запускать из корня проекта)

* **Проверка компиляции (Java 25, авто-синхронизация `src/` и `src_26/`):**
  ```bash
  python compile_check_26.py
  ```
* **Сборка JAR и деплой в тестовый инстанс лаунчера (`Optimus Prime Test`):**
  ```bash
  python deploy_26.py
  ```
* **Запуск автотестов (7/7 проверок):**
  ```bash
  python test_suite_26.py
  ```

---

## Критические ограничения

1. **Язык общения:** Всегда отвечайте пользователю на **русском языке**.
2. **Изоляция инстансов лаунчера:** Разрешено читать и деплоить **только** в `C:\Users\User\AppData\Roaming\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods`. Категорически запрещено трогать другие папки в `instances/`.
3. **Стек Minecraft 26.3:**
   * Java 25 (`--release 25`), Official Mojang Mappings (без Intermediary и без `refmap.json`).
   * Рендер совместим с **Sodium 0.9.3 + Vulkan 1.4.323 (RenderPearl)** — используйте `GuiGraphicsExtractor` и `RenderPipelines`, не вызывайте устаревший `GL11` напрямую.
   * Не вызывайте `Items.*.getDefaultInstance()` в статических инициализаторах `<clinit>` (в MC 26.3 компоненты предметов не привязаны во время `onInitializeClient()`, используйте ленивую инициализацию во время рендера/тика).
