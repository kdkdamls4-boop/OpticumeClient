package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.services.AnarchyReconnectService;
import dev.opticumeclient.client.setting.BindSetting;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.util.ChatUtils;
import dev.opticumeclient.client.util.InventoryUtil;
import dev.opticumeclient.client.util.ServerDetection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Desire-ported server Assist module for FunTime & HolyWorld:
 * instant keybind helpers for trapka, snowball, wind charge (pitch-down), backpack,
 * FT & HW special items/potions, continuous scroll-of-exp hold mode, and auto-.rct on HW gapple.
 */
public class AssistModule extends Module {
    private static AssistModule INSTANCE;

    private static final long SCROLL_INTERVAL_MS = 50L;
    private static final List<Item> BACKPACKS = List.of(
            Items.SHULKER_BOX,
            Items.DYED_SHULKER_BOX.magenta(),
            Items.DYED_SHULKER_BOX.purple(),
            Items.DYED_SHULKER_BOX.red(),
            Items.DYED_SHULKER_BOX.pink(),
            Items.DYED_SHULKER_BOX.blue()
    );

    private enum Use {
        PRESS,
        RELEASE,
        HOLD,
        WIND,
        BACKPACK
    }

    private enum Group {
        ALL, FT, HW
    }

    private record AssistItem(String name, BindSetting bind, Predicate<ItemStack> match, Group group, Use use) {
    }

    private final ModeSetting mode = new ModeSetting("Режим", "Выбор сервера для биндов Assist", "Авто", "Авто", "FunTime", "HolyWorld");
    private final BooleanSetting autoRct = new BooleanSetting("Авто .rct после чарки (HW)", "Автоматический перезаход на анархию после чаровального яблока вне КТ", false);
    private final List<AssistItem> items = new ArrayList<>();

    private final Set<Integer> waitingRelease = new HashSet<>();
    private AssistItem holding;
    private long lastHoldUse;

    public AssistModule() {
        super("Assist", "Быстрое использование донат-предметов (FT / HW) и авто-.rct", Category.OTHER);

        autoRct.visibleWhen(() -> activeMode() == Group.HW);
        addSetting(mode);
        addSetting(autoRct);

        addItem("Трапка", this::isTrapka, Group.ALL, Use.RELEASE);
        addItem("Ком снега", item(Items.SNOWBALL), Group.ALL, Use.RELEASE);
        addItem("Заряд ветра", item(Items.WIND_CHARGE), Group.ALL, Use.WIND);
        addItem("Рюкзак", stack -> BACKPACKS.contains(stack.getItem()), Group.ALL, Use.BACKPACK);

        addItem("Дезориентация", item(Items.ENDER_EYE), Group.FT, Use.RELEASE);
        addItem("Божья аура", item(Items.PHANTOM_MEMBRANE), Group.FT, Use.RELEASE);
        addItem("Огненный смерч", item(Items.FIRE_CHARGE), Group.FT, Use.PRESS);
        addItem("Пласт", item(Items.DRIED_KELP), Group.FT, Use.RELEASE);
        addItem("Явная пыль", item(Items.SUGAR), Group.FT, Use.RELEASE);
        addItem("Хлопушка", splash("Хлопушка"), Group.FT, Use.PRESS);
        addItem("Зелье радиации", splash("Зелье Радиации"), Group.FT, Use.PRESS);
        addItem("Снотворное", splash("Снотворное"), Group.FT, Use.PRESS);
        addItem("Святая вода", splash("Святая вода"), Group.FT, Use.PRESS);
        addItem("Зелье гнева", splash("Зелье гнева"), Group.FT, Use.PRESS);
        addItem("Зелье паладина", splash("Зелье паладина"), Group.FT, Use.PRESS);
        addItem("Зелье ассасина", splash("Зелье ассасина"), Group.FT, Use.PRESS);

        addItem("Стан", item(Items.NETHER_STAR), Group.HW, Use.RELEASE);
        addItem("Взрывная штучка", item(Items.FIRE_CHARGE), Group.HW, Use.PRESS);
        addItem("Взрывная трапка", item(Items.PRISMARINE_SHARD), Group.HW, Use.RELEASE);
        addItem("Ловушка", item(Items.HEART_OF_THE_SEA), Group.HW, Use.PRESS);
        addItem("Анти полет", named(Items.FIREWORK_STAR, "анти", "полет"), Group.HW, Use.PRESS);
        addItem("Тёмный пульс", named(Items.FIREWORK_STAR, "темн", "пульс"), Group.HW, Use.PRESS);
        addItem("Свиток опыта", named(Items.FLOWER_BANNER_PATTERN, "свит", "опыт"), Group.HW, Use.HOLD);

        INSTANCE = this;
    }

    public static AssistModule getInstance() {
        return INSTANCE;
    }

    private void addItem(String name, Predicate<ItemStack> match, Group group, Use use) {
        BindSetting bind = new BindSetting(name, "Клавиша быстрого использования: " + name);
        bind.visibleWhen(() -> inMode(group));
        addSetting(bind);
        items.add(new AssistItem(name, bind, match, group, use));
    }

    private Group activeMode() {
        if (mode.is("FunTime")) return Group.FT;
        if (mode.is("HolyWorld")) return Group.HW;
        return ServerDetection.isHolyWorld() ? Group.HW : Group.FT;
    }

    private boolean inMode(Group group) {
        return group == Group.ALL || group == activeMode();
    }

    @Override
    public void onDisable() {
        waitingRelease.clear();
        holding = null;
    }

    public static void onInputKey(int keyOrButton, int action, boolean mouse) {
        AssistModule module = INSTANCE;
        if (module == null || !module.isEnabled() || action != 1) return;
        if (module.mc.player == null || (module.mc.gui != null && module.mc.gui.screen() != null)) return;
        module.handlePress(keyOrButton, mouse);
    }

    private void handlePress(int keyOrButton, boolean mouse) {
        for (AssistItem item : items) {
            if (!item.bind().matchesInput(keyOrButton, mouse) || !inMode(item.group())) continue;
            switch (item.use()) {
                case RELEASE -> waitingRelease.add(item.bind().getBindCode());
                case HOLD -> {
                    holding = item;
                    lastHoldUse = System.currentTimeMillis();
                    useItem(item);
                }
                default -> useItem(item);
            }
            return;
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;

        if (!waitingRelease.isEmpty()) {
            for (Integer code : new ArrayList<>(waitingRelease)) {
                AssistItem matched = findWaitingItem(code);
                if (matched == null) {
                    waitingRelease.remove(code);
                    continue;
                }
                if (matched.bind().isDownNow()) continue;
                waitingRelease.remove(code);
                if (mc.gui != null && mc.gui.screen() != null) continue;
                useItem(matched);
            }
        }

        if (holding != null) {
            if (!holding.bind().isDownNow() || (mc.gui != null && mc.gui.screen() != null)) {
                holding = null;
            } else if (System.currentTimeMillis() - lastHoldUse >= SCROLL_INTERVAL_MS) {
                lastHoldUse = System.currentTimeMillis();
                useItem(holding);
            }
        }
    }

    private AssistItem findWaitingItem(int bindCode) {
        for (AssistItem item : items) {
            if (item.use() == Use.RELEASE && item.bind().getBindCode() == bindCode && inMode(item.group())) {
                return item;
            }
        }
        return null;
    }

    private void useItem(AssistItem item) {
        if (mc.player == null) return;
        boolean used;
        if (item.use() == Use.WIND) {
            float pitch = mc.player.getXRot();
            mc.player.setXRot(90.0f);
            used = InventoryUtil.swapAndUseHvH(item.match());
            mc.player.setXRot(pitch);
        } else {
            used = InventoryUtil.swapAndUseHvH(item.match());
        }
        if (!used && item.use() != Use.HOLD) {
            ChatUtils.sendClientMessage("Нет предмета: " + item.name().toLowerCase(Locale.ROOT));
        }
    }

    public static void onFinishUsing(ItemStack stack) {
        AssistModule module = INSTANCE;
        if (module == null || !module.isEnabled() || !module.autoRct.isEnabled() || stack == null) return;
        if (!stack.is(Items.ENCHANTED_GOLDEN_APPLE) || !ServerDetection.isHolyWorld()) return;
        if (ServerDetection.isInPvp()) return;
        module.mc.execute(() -> AnarchyReconnectService.getInstance().start(AnarchyReconnectService.Target.AUTO, null));
    }

    private boolean isTrapka(ItemStack stack) {
        boolean chorus = activeMode() == Group.HW && !ServerDetection.addressContains("holytime");
        return stack.is(chorus ? Items.POPPED_CHORUS_FRUIT : Items.NETHERITE_SCRAP);
    }

    private static Predicate<ItemStack> item(Item item) {
        return stack -> stack.is(item);
    }

    private static Predicate<ItemStack> named(Item item, String... words) {
        return stack -> {
            if (!stack.is(item)) return false;
            String name = normalize(stack.getHoverName().getString());
            for (String word : words) {
                if (!name.contains(normalize(word))) return false;
            }
            return true;
        };
    }

    private static Predicate<ItemStack> splash(String title) {
        String lowTitle = normalize(title);
        return stack -> stack.is(Items.SPLASH_POTION) && normalize(stack.getHoverName().getString()).contains(lowTitle);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
