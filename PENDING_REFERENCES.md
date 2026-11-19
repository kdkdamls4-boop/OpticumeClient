# 📌 Очередь Референсов и Отложенных Доработок (OpticumeClient)

Этот файл фиксирует статус модулей и визуальных доработок, ожидающих дополнительных референсов или решений после проверки в игре.

---

## ✅ Реализовано из очереди

| Модуль / Фича | Текущий статус |
| :--- | :--- |
| **Ambience** (небо, туман, мокрые поверхности) | Реализован в [`AmbienceModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/AmbienceModule.java) вместе с [`FogRendererMixin.java`](src_26/main/java/dev/opticumeclient/mixin/FogRendererMixin.java), [`SkyRendererMixin.java`](src_26/main/java/dev/opticumeclient/mixin/SkyRendererMixin.java) и подсистемой `visuals/ambience/*` (RenderPearl), дополнен [`AtmosphereModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/AtmosphereModule.java). |
| **BlockOverlay** (стили подсветки блоков) | Обновлён в [`BlockOverlayModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/BlockOverlayModule.java): доступны режимы `Plasma`, `Crystal`, `Fill`, плавная анимация `Smooth` и `Theme Outline`. |
| **WorldObjects / Particles** (`Cubes` / `Comets`) | Объединены и доработаны в [`ParticlesModule.java`](src_26/main/java/dev/opticumeclient/client/modules/visuals/ParticlesModule.java) (3D-физика частиц Desire для удара/бездействия/снарядов + кубики и кометы). |

---

## ⏳ Отложенные / удалённые пункты

| Модуль / Фича | Текущий статус | Примечание |
| :--- | :--- | :--- |
| **ChestStealer** | Удалён | При необходимости перенести заново под Minecraft 26.3 с адаптацией под серверную проверку слотов. |
| **HitColor** | Удалён по запросу | Восстанавливать не требуется, если пользователь не запросит обратное. |

---

## 📚 Используемые референсы стилей и подсистем

1. **Desire** — Main Menu, TargetESP (35 SPIR-V шейдеров), BlockOverlay, JumpCircles (`Desire Effect` & `Desire Circle`), NameTags, Hands, GlowESP, Server Events / Reconnect, ThemesPage.
2. **SystemDLC** — ClickGUI (`System` оболочка, сайдбар Dock, masonry-карточки), BindsHUD / InventoryHUD / CooldownsHUD.
3. **Skycore / YTsadqdex** — 3D-физика частиц, `Predictions`, визуальные эффекты мира.
4. **Nursultan / Catlavan / ThunderHack / Rockstar / Deluxe** — палитры тем, стили TargetHUD и вспомогательные утилиты.
