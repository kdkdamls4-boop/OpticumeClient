# Changelog

Все заметные изменения в **OpticumeClient** (Minecraft 26.3 / Java 25 / Fabric + Sodium / RenderPearl Vulkan) фиксируются в этом файле.

---

## [1.2.0-mc26.3] — 2026-11-19 (текущие изменения к релизу)

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
