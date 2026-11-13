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
  1. 🌸 **Nursultan Alpha:** Неоновый циан (`#00F5FF`) $\to$ Розово-фиолетовый (`#FF2A85`).
  2. ⚡ **Catlavan Acid:** Кислотно-салатовый (`#39FF14`) $\to$ Электрик-циан (`#00E5FF`).
  3. 💜 **Expensive Purple:** Насыщенный пурпур (`#A855F7`) $\to$ Глубокий индиго (`#6366F1`).
  4. 🩸 **Rockstar Crimson:** Рубиново-алый (`#EF4444`) $\to$ Огненный оранж (`#F97316`).
  5. ❄️ **Wexside Ice:** Ледяной лазурный (`#38BDF8`) $\to$ Мятный арктик (`#2DD4BF`).
  6. 🤖 **Delta Cyber:** Неоновый лайм (`#84CC16`) $\to$ Бирюзовый киберпанк (`#06B6D4`).
  7. 🌌 **Celestial Neon:** Электрический ультрамарин (`#3B82F6`) $\to$ Неоновый фиолет (`#8B5CF6`).
  8. ⚡ **ThunderHack:** Тёмный фиолетовый грозовой (`#7C3AED`) $\to$ Золотая молния (`#FBBF24`).
  9. 🌅 **Astolfo Sunset:** Закатный персиковый (`#F43F5E`) $\to$ Тёплый янтарь (`#FB923C`).
  10. 🌊 **Rise Ocean:** Глубокий сапфировый (`#0284C7`) $\to$ Морская волна (`#14B8A6`).
  11. 🟢 **DeadCode Matrix:** Матричный изумруд (`#10B981`) $\to$ Цифровой лайм (`#22C55E`).
  12. 👻 **Vape Ghost:** Призрачный мятный фантом (`#2DD4BF`) $\to$ Серебристо-дымчатый (`#94A3B8`).

---

## 🖥️ 2. ClickGUI — Главное Меню Клиента

* **Режим по умолчанию:** Единое окно с сайдбаром (`Sidebar Window Mode`, размер: `660 x 430` px, центрировано).
* **Левый Сайдбар (155 px):**
  * **Шапка:** Мини-логотип молнии (`TextureAssets.MINI_LOGO`) + градиентный текст `"OPTIMUS"` и бейдж версии `"PRIME 26.3"`.
  * **Список категорий (с анимированной скользящей пилюлей):**
    * ⚔️ `Combat` (Aura, ShieldBreaker, TriggerBot, AutoTotem)
    * 👁️ `Visuals` (TargetESP, Crosshair, TargetHUD, ChinaHat, Trails, JumpCircles, Particles)
    * 🚀 `Optimization` (EntityCulling, FastMath, ChunkAnimator, MemoryCleaner)
    * 🖥️ `HUD` (Watermark, ArrayList, ArmorHUD, Cooldowns, Radar, StaffList, KeyBinds)
    * ⚡ `Movement` (Sprint, NoSlow, Velocity)
    * 👥 `Friends` (FriendManager)
  * **Нижняя панель сайдбара:**
    * Кнопка быстрой смены темы (`ThemeSelector` с 2D-превью цвета).
    * Карточка профиля игрока: сглаженная голова скина (`PlayerFaceExtractor`), ник, ранг `#0001 | Prime` и индикатор FPS.
* **Правая Рабочая Зона (Контент модулей):**
  * **Верхняя панель:** Название категории, переключатель `[View: Sidebar / Panels]`, фильтр `[Bind Only]`, строка живого поиска (`Search modules...`).
  * **Сетка карточек (Masonry Grid, 2 колонки):**
    * Карточки модулей размером `234 x 42` px (свернутые).
    * Внутри карточки: название модуля, краткое описание (мутный серый шрифт), бейдж текущего бинда клавиши (`[-]`), кнопка-тумблер со скользящей неоновой пилюлей.
    * По правому клику (ПКМ) карточка раскрывается вниз, отображая вложенные компоненты: слайдеры с заполнением, выпадающие списки (Mode), чипы-пилюли (MultiBox) и расширенный 2D HSB+Alpha колор-пикер.

---

## 📊 3. HUD (Оверлей Виджетов в Игре)

Все 16 виджетов свободно перемещаются мышью при открытии чата (`ChatScreen`) с умными направляющими прилипания (`Smart Snap Lines`):

1. **Watermark / Dynamic Island (Сверху):** Стеклянная капсула с логотипом, именем клиента, ником, FPS, пингом, скоростью (BPS), системными часами.
2. **ArrayList (Справа сверху):** Раскрывающийся список активных модулей, отсортированный по ширине текста с неоновыми боковыми маркерами и бейджами режимов.
3. **TargetHUD (Рядом с прицелом):**
   * Стили: *Nursultan*, *Catlavan*, *ThunderHack*.
   * Пружинная анимация появления (`EaseOutBack`), 2D-голова цели с эффектом сжатия и покраснения при уроне, разлетающиеся частицы крови/сердец, двойной бар HP (основной + запаздывающий след урона), золотой бар абсорбции, броня и оружие в руках, бейдж превосходства (`WIN / LOSE`).
4. **ArmorHUD:** Горизонтальные или вертикальные слоты брони + предметы в руках с полосками прочности и процентом износа.
5. **Cooldowns:** Иконки эндер-жемчуга, золотых яблок, хоруса и винд-чарджа с полосками перезарядки.
6. **KeyBinds:** Панель активных забиндженных клавиш и их текущий статус (`[TOGGLE]`).
7. **StaffList:** Список обнаруженных модераторов/админов сервера (парсинг таб-листа и дистанции).
8. **2D Radar:** Круговой радар с точками союзников (синие), врагов (красные) и мобов с дистанционными кольцами.
9. **Speedometer & BPS Graph:** Сглаженный график скорости игрока в блоках в секунду.
10. **Totem & GApple Counter:** Компактная плашка с количеством тотемов, чарок и перлов в инвентаре.
11. **PotionHUD:** Список активных эффектов зелий с таймерами и цветными иконками.
12. **Keystrokes:** Стеклянные интерактивные клавиши WASD + кнопки мыши (LMB/RMB) со счётчиком CPS.
13. **Coordinates:** Координаты XYZ и сторона света.
14. **Notifications:** Всплывающие уведомления в стиле Dynamic Island справа снизу.

---

## 🔮 4. 3D-Визуалы и Эффекты

* **TargetESP:**
  * Режим *Ghosts*: 3 светящиеся траектории частиц (`firefly.png`), вращающиеся вокруг цели со смещением по синусоиде.
  * Режим *Jello Ring*: плавающее пульсирующее кольцо у ног цели с градиентным затуханием.
  * Режим *Nurik Quad*: 4 неоновые вертикальные стойки с угловыми засечками.
  * Режим *Spiral*: восходящая спираль частиц вокруг тела моба.
* **Crosshair (Прицел):**
  * Стили: *Nursultan Circle* (плавное кольцо с радиальным индикатором кулдауна удара и точкой), *Catlavan Cross* (динамический крестик с разлетом при ходьбе), *Expensive Modern* (кольцо + 4 засечки), *Dot* (минималистичная точка).
* **JumpCircles:** Неоновые круги на земле в месте прыжка, плавно расширяющиеся и затухающие.
* **ChinaHat:** Неоновая коническая шляпа над головой игрока с вращающимися ободами.
* **Trails:** Градиентная светящаяся лента за спиной игрока при движении.
* **HitBubbles & Particles:** Вылетающие анимированные пузыри урона и частицы при ударах по сущностям.
* **Arrows:** Круговые стрелки вокруг прицела, указывающие на игроков вне зоны видимости (FOV).

---

## 📁 5. Структура Репозитория и Карта Файлов

```text
c:\Users\User\Desktop\Projects\OPTIMUSPRIME\
├── AGENTS.md                          # Главные правила и инструкции для ИИ-агентов
├── VISUAL_DATABASE.md                 # Данный документ (визуальная база и архитектура)
├── compile_check_26.py                # Скрипт быстрой проверки компиляции (Java 25, 0 ошибок)
├── test_suite_26.py                   # Комплексный тест-сьют (7/7 проверок)
├── deploy_26.py                       # Сборка jar и деплой в Optimus Prime Test
├── src_26/main/                       # АКТИВНЫЙ ИСХОДНЫЙ КОД ПОД MINECRAFT 26.3
│   ├── resources/
│   │   ├── fabric.mod.json            # Манифест мода (id: optimusprime, name: OpticumeClient)
│   │   ├── optimusprime.mixins.json   # Конфигурация 11 миксинов под Java 25
│   │   └── assets/optimusprime/textures/ # Ассеты (firefly, circle, hitbubble, mini_logo и др.)
│   └── java/dev/optimusprime/
│       ├── OptimusPrimeClient.java    # Точка входа Fabric Client ModInitializer
│       ├── client/
│       │   ├── friend/                # Менеджер друзей (FriendManager)
│       │   ├── gui/                   # ClickGUI, ModuleCard, выпадающие панели и компоненты
│       │   │   ├── ClickGuiScreen.java
│       │   │   ├── ModuleCard.java
│       │   │   └── component/         # Boolean, Slider, Mode, MultiBox, Color, Bind
│       │   ├── hud/                   # HudManager, DraggableWidget, InfoHud, ArmorHud, PotionHud, KeystrokesHud
│       │   ├── modules/
│       │   │   ├── Module.java / ModuleManager.java / Category.java
│       │   │   ├── combat/            # KillAura, ShieldBreaker, TriggerBot, AutoTotem
│       │   │   ├── movement/          # Sprint, NoSlow, Velocity
│       │   │   ├── optimization/      # EntityCulling, FastMath, MemoryCleaner, ChunkAnimator
│       │   │   └── visuals/           # TargetEsp, Crosshair, TargetHud, ChinaHat, Trails, JumpCircles, Particles...
│       │   ├── render/                # Render2DHelper, Render3DHelper, TextureAssets, ThemeManager, ColorUtils, Animation, Easing
│       │   └── setting/               # Setting, BooleanSetting, NumberSetting, ModeSetting, ColorSetting, MultiBoxSetting
│       └── mixin/                     # 11 миксинов к ванильному клиенту MC 26.3
│           ├── MouseHandlerMixin.java (фикс кликов и Drag&Drop в ChatScreen)
│           ├── KeyboardHandlerMixin.java (глобальные бинды)
│           ├── EntityRenderDispatcherMixin.java (куллинг мобов)
│           ├── MthMixin.java (FastMath синусы)
│           ├── GameRendererMixin.java (3D-рендер мира)
│           ├── InGameHudMixin.java (2D-рендер HUD)
│           └── ClientPlayerEntityMixin, EntityMixin, LivingEntityRendererMixin, PlayerEntityMixin, HeldItemRendererMixin
└── reference_sources/                 # Эталонные исходники для подглядывания архитектуры
    ├── thunderhack/                   # ThunderHack Recode (шейдеры, 3D Engine, партиклы)
    └── cis_visuals/                   # Expensive 2.0 / 3.1 / Upgrade Fabric (GUI, RenderEngine, JumpCircles)
```
