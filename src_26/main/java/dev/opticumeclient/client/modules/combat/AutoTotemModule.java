package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.ModeSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Automated Totem of Undying manager with extended threat detection
 * (Health, EndCrystal, TNT/MinecartTNT, Respawn Anchor, Falling Mace, Incoming Trident, Fall Distance),
 * SaveEnchanted totem preference, and SwapBack restoration when safe.
 */
public class AutoTotemModule extends Module {
    private final ModeSetting mode = new ModeSetting("Mode", "Метод свапа тотема", "Swap", "Swap", "Paced Pickup");
    private final NumberSetting healthThreshold = new NumberSetting("Health", "Здоровье для свапа тотема", 16.0, 1.0, 20.0, 1.0);
    private final BooleanSetting checkCrystals = new BooleanSetting("Crystals", "Брать тотем при наличии рядом EndCrystal (<= 6 блоков)", true);
    private final BooleanSetting checkTnt = new BooleanSetting("TNT", "Брать тотем рядом с зажжённым ТНТ / вагонеткой с ТНТ", true);
    private final BooleanSetting checkAnchors = new BooleanSetting("Anchors", "Брать тотем рядом с заряженным якорем возрождения", true);
    private final BooleanSetting checkMace = new BooleanSetting("Mace", "Брать тотем при падающем враге с булавой", true);
    private final BooleanSetting checkTrident = new BooleanSetting("Trident", "Брать тотем при летящем вражеском трезубце", true);
    private final BooleanSetting checkFall = new BooleanSetting("FallDistance", "Брать тотем при опасном падении", true);
    private final NumberSetting fallDistanceThreshold = new NumberSetting("FallDist", "Высота падения для триггера тотема", 6.0, 3.0, 20.0, 0.5);
    private final BooleanSetting saveGapples = new BooleanSetting("SaveGapples", "Не заменять золотое яблоко/щит во 2-й руке при безопасном ХП", true);
    private final BooleanSetting saveEnchanted = new BooleanSetting("SaveEnchanted", "Сначала брать незачарованный тотем (беречь талисманы/сферы)", true);
    private final BooleanSetting swapBack = new BooleanSetting("SwapBack", "Возвращать прежний предмет во 2-ю руку, когда угроза исчезла", false);
    private final NumberSetting tickDelay = new NumberSetting("Delay", "Задержка тиков между кликами для Paced Pickup", 1.0, 0.0, 5.0, 1.0);

    // Paced state machine
    private enum SwapState { IDLE, PICKUP_TOTEM, PLACE_OFFHAND, RETURN_SLOT }
    private SwapState currentState = SwapState.IDLE;
    private int activeTotemSlot = -1;
    private int delayTimer = 0;

    // SwapBack memory
    private Item savedOffhandItem = null;
    private int safeTicks = 0;

    public AutoTotemModule() {
        super("AutoTotem", "Автоматически берет тотем бессмертия во вторую руку", Category.COMBAT);
        addSetting(mode);
        addSetting(healthThreshold);
        addSetting(checkCrystals);
        addSetting(checkTnt);
        addSetting(checkAnchors);
        addSetting(checkMace);
        addSetting(checkTrident);
        addSetting(checkFall);
        addSetting(fallDistanceThreshold);
        addSetting(saveGapples);
        addSetting(saveEnchanted);
        addSetting(swapBack);
        addSetting(tickDelay);
    }

    @Override
    public void onDisable() {
        resetState();
        savedOffhandItem = null;
        safeTicks = 0;
    }

    private void resetState() {
        currentState = SwapState.IDLE;
        activeTotemSlot = -1;
        delayTimer = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            resetState();
            return;
        }

        if (currentState != SwapState.IDLE) {
            handlePacedSwap();
            return;
        }

        ItemStack offhand = mc.player.getOffhandItem();
        boolean threat = shouldEquipTotem();

        if (offhand.is(Items.TOTEM_OF_UNDYING)) {
            if (!threat && swapBack.isEnabled() && savedOffhandItem != null) {
                safeTicks++;
                if (safeTicks >= 10) {
                    int backSlot = findItemSlot(savedOffhandItem);
                    if (backSlot != -1) {
                        executeInstantSwap(backSlot);
                    }
                    savedOffhandItem = null;
                    safeTicks = 0;
                }
            } else {
                safeTicks = 0;
            }
            return;
        }

        safeTicks = 0;
        if (threat) {
            int totemSlot = findTotemSlot();
            if (totemSlot != -1) {
                if (swapBack.isEnabled() && !offhand.isEmpty() && !offhand.is(Items.TOTEM_OF_UNDYING)) {
                    savedOffhandItem = offhand.getItem();
                }
                if (mode.is("Swap")) {
                    executeInstantSwap(totemSlot);
                } else {
                    activeTotemSlot = totemSlot;
                    currentState = SwapState.PICKUP_TOTEM;
                    delayTimer = 0;
                    handlePacedSwap();
                }
            }
        }
    }

    private boolean shouldEquipTotem() {
        boolean urgentThreat = (checkCrystals.isEnabled() && isCrystalNearby())
                || (checkTnt.isEnabled() && isTntNearby())
                || (checkAnchors.isEnabled() && isAnchorNearby())
                || (checkMace.isEnabled() && isMaceThreatNearby())
                || (checkTrident.isEnabled() && isTridentThreatNearby());

        if (saveGapples.isEnabled() && mc.player.isUsingItem() && mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
            ItemStack active = mc.player.getActiveItem();
            if (active.is(Items.GOLDEN_APPLE) || active.is(Items.ENCHANTED_GOLDEN_APPLE) || active.is(Items.SHIELD)) {
                if (mc.player.getHealth() > 6.0f && !urgentThreat) {
                    return false;
                }
            }
        }

        if (mc.player.getHealth() <= healthThreshold.getValue()) {
            return true;
        }
        if (urgentThreat) {
            return true;
        }
        if (checkFall.isEnabled() && isFallDangerous()) {
            return true;
        }
        return false;
    }

    private boolean isCrystalNearby() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal && mc.player.distanceTo(entity) <= 6.0f) {
                return true;
            }
        }
        return false;
    }

    private boolean isTntNearby() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if ((entity instanceof PrimedTnt || entity instanceof MinecartTNT) && mc.player.distanceTo(entity) <= 7.0f) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnchorNearby() {
        if (mc.level.dimension() == Level.NETHER) return false;
        BlockPos origin = mc.player.blockPosition();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.is(Blocks.RESPAWN_ANCHOR)
                            && state.hasProperty(RespawnAnchorBlock.CHARGE)
                            && state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isMaceThreatNearby() {
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || FriendManager.getInstance().isFriend(p)) continue;
            if (p.getMainHandItem().is(Items.MACE) && !p.onGround() && p.getDeltaMovement().y < -0.25) {
                double horiz = Math.hypot(p.getX() - mc.player.getX(), p.getZ() - mc.player.getZ());
                double dy = p.getY() - mc.player.getY();
                if (horiz <= 7.0 && dy >= 1.5 && dy <= 16.0) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTridentThreatNearby() {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ThrownTrident trident && trident.getOwner() != mc.player) {
                if (trident.getDeltaMovement().lengthSqr() > 0.04 && mc.player.distanceTo(trident) <= 10.0f) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isFallDangerous() {
        if (mc.player.fallDistance <= fallDistanceThreshold.getValue()) return false;
        if (mc.player.isFallFlying() || mc.player.isInWater() || mc.player.hasEffect(MobEffects.SLOW_FALLING)) return false;
        return true;
    }

    private void executeInstantSwap(int slot) {
        mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot, 40, ContainerInput.SWAP, mc.player);
    }

    private void handlePacedSwap() {
        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        int containerId = mc.player.inventoryMenu.containerId;
        switch (currentState) {
            case PICKUP_TOTEM -> {
                mc.gameMode.handleContainerInput(containerId, activeTotemSlot, 0, ContainerInput.PICKUP, mc.player);
                currentState = SwapState.PLACE_OFFHAND;
                delayTimer = tickDelay.getIntValue();
            }
            case PLACE_OFFHAND -> {
                mc.gameMode.handleContainerInput(containerId, 45, 0, ContainerInput.PICKUP, mc.player);
                currentState = SwapState.RETURN_SLOT;
                delayTimer = tickDelay.getIntValue();
            }
            case RETURN_SLOT -> {
                mc.gameMode.handleContainerInput(containerId, activeTotemSlot, 0, ContainerInput.PICKUP, mc.player);
                resetState();
            }
            default -> resetState();
        }
    }

    private int findTotemSlot() {
        int enchantedFallback = -1;
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                if (!saveEnchanted.isEnabled() || !stack.isEnchanted()) {
                    return i;
                }
                if (enchantedFallback == -1) {
                    enchantedFallback = i;
                }
            }
        }
        return enchantedFallback;
    }

    private int findItemSlot(Item item) {
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = mc.player.inventoryMenu.getSlot(i).getItem();
            if (!stack.isEmpty() && stack.is(item)) {
                return i;
            }
        }
        return -1;
    }
}
