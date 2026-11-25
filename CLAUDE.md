# CLAUDE.md — Инструкции для Claude Code и ИИ-Агентов

При работе с этим репозиторием сначала ознакомьтесь с базовыми документами проекта:
1. **[`AGENTS.md`](AGENTS.md)** — главный свод правил безопасности, архитектуры и команд сборки.
2. **[`README.md`](README.md)** — структура директорий, список всех 116 модулей и 54 миксинов.
3. **[`CHANGELOG.md`](CHANGELOG.md)** — история релизов и изменений.
4. **[`VISUAL_DATABASE.md`](VISUAL_DATABASE.md)** — детальная спецификация UI, HUD, цветовых тем и 2D/3D рендеринга.

---

## Ключевые команды (запускать из корня проекта)

* **Проверка компиляции (Java 25, авто-синхронизация `src/` и `src_26/`):**
  ```powershell
  python compile_check_26.py
  ```
* **Сборка JAR и деплой в тестовый инстанс лаунчера (`instances\OPTIMUSPRIME`):**
  ```powershell
  python deploy_26.py
  ```
* **Запуск автотестов (8/8 проверок):**
  ```powershell
  python test_suite_26.py
  ```

---

## Критические ограничения

1. **Язык общения:** Всегда отвечайте пользователю на **русском языке**.
2. **Изоляция инстансов лаунчера:** Разрешено читать и деплоить **только** в `%APPDATA%\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods`. Категорически запрещено трогать другие папки в `instances/`.
3. **Стек Minecraft 26.3:**
   * Java 25 (`--release 25`), Official Mojang Mappings (без Intermediary и без `refmap.json`).
   * Рендер совместим с **Sodium 0.9.3 + Vulkan 1.4.323 (RenderPearl)** — используйте `GuiGraphicsExtractor` и `RenderPipelines`, не вызывайте устаревший `GL11` напрямую.
   * Не вызывайте `Items.*.getDefaultInstance()` в статических инициализаторах `<clinit>` (в MC 26.3 компоненты предметов не привязаны во время `onInitializeClient()`, используйте ленивую инициализацию во время рендера/тика).
