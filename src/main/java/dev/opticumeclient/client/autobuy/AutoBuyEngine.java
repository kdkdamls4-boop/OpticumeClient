package dev.opticumeclient.client.autobuy;

import dev.opticumeclient.client.modules.other.AutoBuyModule;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class AutoBuyEngine {
    public static final AutoBuyEngine INSTANCE = new AutoBuyEngine();

    public static AutoBuyEngine getInstance() {
        return INSTANCE;
    }

    private static final Minecraft mc = Minecraft.getInstance();
    private static final Random RNG = ThreadLocalRandom.current();

    public final PriceAutoParser parser = new PriceAutoParser();
    public final AutoSeller seller = new AutoSeller();
    public final RelistManager relist = new RelistManager();
    public final AutoPay autoPay = new AutoPay();

    private int relistCooldown = -1;
    private boolean relistPending = false;
    private int buyCooldownTicks = 0;
    private long lastSlotsHash = 0L;
    private int unchangedHashTicks = 0;
    private int refreshCooldownTicks = 0;
    private int commandCooldownTicks = 0;
    private int noScreenTicks = 0;

    private volatile String targetedItemId = null;
    private volatile String targetedDisplayName = null;
    private volatile String targetedLoreKeyword = null;
    private volatile long targetedMaxUnitPrice = 0L;
    private volatile boolean targetedRequireCustom = false;
    private volatile List<Marker> targetedMarkers = null;
    private volatile String targetedSeller = null;
    private volatile boolean targetedSuspicious = false;
    private volatile boolean pendingPurchaseSuspicious = false;
    private long lastSuspiciousEscapeTime = 0L;

    private int confirmActionCooldown = 0;
    private int awaitingConfirmationTicks = 0;
    private boolean awaitingInventoryArrival = false;
    private String awaitingItemId = null;
    private int preBuyInventoryCount = 0;
    private int arrivalTimeoutTicks = 0;
    private PurchaseRecord pendingPurchaseRecord = null;

    private float targetAfkYaw = Float.NaN;
    private float targetAfkPitch = Float.NaN;
    private int afkRotateTicks = 0;
    private int afkPauseTicks = 0;
    private boolean isAfkRotating = false;
    private boolean afkInitDone = false;
    private float currentAfkSpeed = 8.0F;
    private int pauseBeforeActionTicks = 0;
    private int randomMicroPauseTicks = 0;
    private float speedJitterMultiplier = 1.0F;
    private int speedJitterTimer = 0;
    private int refreshThrottleCounter = 0;
    private long lastAutoParseMs = 0L;

    private AutoBuyEngine() {
    }

    public void onTick() {
        if (mc.player == null || mc.gameMode == null) return;

        try {
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.syncSettings();
        } catch (Throwable ignored) {
        }

        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (cfg.autoPay) {
            try {
                this.autoPay.tick();
            } catch (Throwable ignored) {
            }
        }

        this.speedJitterTimer++;
        if (this.speedJitterTimer >= 3) {
            this.speedJitterTimer = 0;
            this.speedJitterMultiplier = 0.9F + RNG.nextFloat() * 0.3F;
        }

        if (this.randomMicroPauseTicks > 0) {
            this.randomMicroPauseTicks--;
            return;
        }

        boolean wasParserActive = this.parser.isActive();
        try {
            this.parser.tick();
        } catch (Throwable ignored) {
        }
        if (wasParserActive && !this.parser.isActive()) {
            this.lastAutoParseMs = System.currentTimeMillis();
        }
        if (this.parser.isActive()) return;

        if (cfg.enabled) {
            if (!this.afkInitDone) {
                this.afkPauseTicks = randomBetween(2, 20);
                this.currentAfkSpeed = 8.0F * (0.9F + RNG.nextFloat() * 0.2F) * this.speedJitterMultiplier;
                this.afkInitDone = true;
            }
            this.tickAfkCamera();
        } else if (this.afkInitDone) {
            this.isAfkRotating = false;
            this.targetAfkYaw = Float.NaN;
            this.targetAfkPitch = Float.NaN;
            this.afkInitDone = false;
        }

        if (!cfg.enabled) {
            this.awaitingConfirmationTicks = 0;
            return;
        }

        if (this.pauseBeforeActionTicks > 0) {
            this.pauseBeforeActionTicks--;
            return;
        }

        try {
            if (RNG.nextInt(2) == 0) this.relist.tick();
        } catch (Throwable ignored) {
        }
        if (this.relist.isActive()) return;

        if (cfg.autoSell) {
            try {
                if (RNG.nextInt(2) == 0) this.seller.tick();
            } catch (Throwable ignored) {
            }
        } else if (this.seller.isActive()) {
            this.seller.stop();
        }
        if (this.seller.isActive()) return;

        try {
            if (this.awaitingConfirmationTicks > 0) {
                boolean confirmOpen = mc.gui != null && mc.gui.screen() instanceof ContainerScreen gcs && isConfirmScreen(gcs);
                if (!confirmOpen) {
                    this.awaitingConfirmationTicks--;
                    if (this.awaitingConfirmationTicks == 0 && !this.awaitingInventoryArrival) {
                        this.targetedItemId = null;
                        this.targetedMaxUnitPrice = 0L;
                        this.targetedMarkers = null;
                        this.targetedSeller = null;
                        this.targetedSuspicious = false;
                    }
                }
            }
            if (this.checkAutoParseTimer(cfg)) return;
            this.checkAutoRelistTimer();
            this.processAuctionScreen();
            this.processConfirmationScreen();
            this.processAutoRefresh();
        } catch (Throwable ignored) {
        }
    }

    private boolean checkAutoParseTimer(AutoBuyConfig cfg) {
        if (!cfg.autoParse || this.awaitingInventoryArrival || this.awaitingConfirmationTicks > 0
                || this.parser.isActive() || this.relist.isActive() || this.seller.isActive()) {
            return false;
        }
        if (mc.gui != null && mc.gui.screen() instanceof ContainerScreen gcs && isConfirmScreen(gcs)) {
            return false;
        }
        boolean hasEnabled = false;
        boolean hasUnpriced = false;
        for (ItemEntry e : cfg.entries) {
            if (e != null && e.enabled) {
                hasEnabled = true;
                if (e.maxUnitPrice <= 0L) {
                    hasUnpriced = true;
                }
            }
        }
        if (!hasEnabled) return false;

        long now = System.currentTimeMillis();
        long intervalMs = Math.max(60L, cfg.parseIntervalSeconds) * 1000L;
        if (this.lastAutoParseMs == 0L) {
            this.lastAutoParseMs = now;
            if (!hasUnpriced) {
                return false;
            }
        } else if (now - this.lastAutoParseMs < intervalMs) {
            return false;
        }

        this.lastAutoParseMs = now;
        this.parser.start();
        return this.parser.isActive();
    }

    /**
     * Handles incoming server packets for AutoBuy tracking/cooldowns.
     * @return {@code true} if the packet should be cancelled/suppressed.
     */
    public boolean onReceivePacket(Packet<?> packet) {
        if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ClientboundRespawnPacket) {
            this.onPlayerTeleport();
            return false;
        }

        String rawMsg = this.extractMessage(packet);
        if (rawMsg == null) return false;

        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        String msg = rawMsg.toLowerCase(Locale.ROOT);
        if (msg.contains("недостаточно средств")) {
            this.onInsufficientFunds();
        }
        if (msg.contains("не можете больше выставлять")) {
            this.seller.onAuctionFull();
        }

        Long saleEarned = AutoBuyReport.parseSalePrice(rawMsg);
        if (saleEarned != null && saleEarned > 0L) {
            AutoBuyReport.recordSale(saleEarned);
            this.seller.onSold();
        } else if (msg.contains("купил у вас") || msg.contains("купила у вас") || msg.contains("приобрел у вас")) {
            long fallbackPrice = this.seller.lastListedPrice;
            if (fallbackPrice > 0L) {
                AutoBuyReport.recordSale(fallbackPrice);
            }
            this.seller.onSold();
        }

        Long buySpent = AutoBuyReport.parseBuyPrice(rawMsg);
        if (buySpent != null && buySpent > 0L) {
            AutoBuyReport.recordPurchase(buySpent);
            if (cfg.safeMode && buySpent >= 1L && buySpent <= 15000L) {
                this.triggerSuspiciousEscape();
            }
        }

        if (msg.contains("успешно выставили")) {
            this.seller.onListed();
        }

        if (msg.contains("подождите") && msg.contains("для использования этой команды")) {
            this.seller.onCommandCooldown();
            this.commandCooldownTicks = Math.max(this.commandCooldownTicks, this.calcCommandCooldownTicks(cfg));
            if (cfg.enabled) {
                return true;
            }
        }

        if (cfg.enabled && ((msg.contains("вы уже") && msg.contains("аукцион")) || msg.contains("вы уже на аукционе"))) {
            return true;
        }

        if (this.autoPay.isWaitingForMoneyResponse()) {
            Long bal = AutoPay.parseBalance(rawMsg);
            if (bal != null) {
                this.autoPay.setWaitingForMoneyResponse(false);
                this.autoPay.onBalance(bal);
                return true;
            }
        }

        if (!msg.contains("пополнен") && !msg.contains("купил у вас")) {
            this.autoPay.onChatMessage(rawMsg);
        }
        return false;
    }

    private String extractMessage(Packet<?> packet) {
        if (packet instanceof ClientboundSystemChatPacket g) {
            return g.content().getString();
        } else if (packet instanceof ClientboundDisguisedChatPacket p) {
            return p.message().getString();
        } else if (packet instanceof ClientboundSetActionBarTextPacket o) {
            return o.text().getString();
        } else if (packet instanceof ClientboundPlayerChatPacket c) {
            return c.unsignedContent().map(Component::getString)
                    .orElse(c.body() != null ? c.body().content() : null);
        }
        return null;
    }

    private void processAutoRefresh() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (!cfg.autoRefresh) return;
        if (mc.gui.screen() instanceof ContainerScreen gcs && isAuctionScreen(gcs)) {
            ChestMenu handler = gcs.getMenu();
            int lotSlotsCount = (handler.getRowCount() - 1) * 9;
            long hash = this.computeSlotsHash(handler, lotSlotsCount);
            this.refreshThrottleCounter++;
            if (this.refreshThrottleCounter >= -1) {
                this.refreshThrottleCounter = 0;
                if (hash != this.lastSlotsHash) {
                    this.lastSlotsHash = hash;
                    this.unchangedHashTicks = 0;
                    if (this.refreshCooldownTicks <= 0) {
                        this.triggerRefreshClick(handler, handler.containerId, handler.getRowCount());
                        this.refreshCooldownTicks = this.calcRefreshDelayTicks(cfg);
                    }
                } else {
                    this.unchangedHashTicks++;
                    if (this.unchangedHashTicks >= 1 + RNG.nextInt(2) && this.refreshCooldownTicks <= 0) {
                        this.triggerRefreshClick(handler, handler.containerId, handler.getRowCount());
                        this.refreshCooldownTicks = this.calcRefreshDelayTicks(cfg);
                        this.unchangedHashTicks = 0;
                    }
                }
            }
        }
    }

    private void tickAfkCamera() {
        if (mc.player == null) return;
        if (RNG.nextInt(4) == 0) {
            this.currentAfkSpeed = 8.0F * (0.8F + RNG.nextFloat() * 0.4F) * this.speedJitterMultiplier;
        }
        if (this.isAfkRotating) {
            if (this.afkRotateTicks > 0) {
                this.afkRotateTicks--;
                float curYaw = mc.player.getYRot();
                float curPitch = mc.player.getXRot();
                float deltaYaw = Mth.wrapDegrees(this.targetAfkYaw - curYaw);
                float deltaPitch = Mth.wrapDegrees(this.targetAfkPitch - curPitch);
                float speedMod = 0.9F + RNG.nextFloat() * 0.2F;
                float stepYaw = Math.min(Math.abs(deltaYaw), this.currentAfkSpeed * speedMod) * Math.signum(deltaYaw);
                float stepPitch = Math.min(Math.abs(deltaPitch), this.currentAfkSpeed * 0.9F * speedMod) * Math.signum(deltaPitch);
                if (Math.abs(deltaYaw) < 0.5F && Math.abs(deltaPitch) < 0.5F) {
                    this.isAfkRotating = false;
                    this.afkPauseTicks = randomBetween(2, 30);
                } else {
                    mc.player.setYRot(curYaw + stepYaw);
                    mc.player.setXRot(curPitch + stepPitch);
                }
            } else {
                this.isAfkRotating = false;
                this.afkPauseTicks = randomBetween(2, 30);
            }
        } else if (this.afkPauseTicks > 0) {
            this.afkPauseTicks--;
        } else {
            this.targetAfkYaw = mc.player.getYRot() + (RNG.nextFloat() * 160.0F - 80.0F);
            this.targetAfkPitch = Mth.clamp(mc.player.getXRot() + (RNG.nextFloat() * 60.0F - 30.0F), -80.0F, 80.0F);
            this.afkRotateTicks = randomBetween(1, 10);
            this.isAfkRotating = true;
            this.currentAfkSpeed = 8.0F * (0.8F + RNG.nextFloat() * 0.4F) * this.speedJitterMultiplier;
        }
    }

    private void checkAutoRelistTimer() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (!cfg.enabled) {
            this.relistPending = false;
            this.relistCooldown = -1;
            return;
        }
        if (this.relistPending) {
            this.relistPending = false;
            cfg.enabled = true;
        }
        if (cfg.autoRelist) {
            if (this.relistCooldown < 0) {
                this.relistCooldown = Math.max(5, cfg.relistIntervalSeconds * 10) + randomBetween(-20, 50);
            }
            if (--this.relistCooldown <= 0) {
                int base = Math.max(5, cfg.relistIntervalSeconds * 10);
                this.relistCooldown = base + randomBetween(-base / 4, base / 3);
                if (RNG.nextInt(3) == 0) {
                    this.relistCooldown += randomBetween(5, 20);
                } else {
                    this.relist.start();
                }
                return;
            }
        } else {
            this.relistCooldown = -1;
        }
        this.pauseBeforeActionTicks = Math.max(0, this.calcPreActionDelayTicks(cfg) - 1);
    }

    public void onPlayerTeleport() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        AutoBuyModule mod = AutoBuyModule.getInstance();
        boolean parserWasActive = this.parser.isActive();
        if (parserWasActive) {
            this.parser.stop(false);
        }
        if (cfg.enabled || parserWasActive || (mod != null && mod.isEnabled())) {
            cfg.enabled = false;
            if (mod != null) {
                mc.execute(() -> mod.setEnabled(false));
            }
            AutoBuyConfig.save();
            this.resetPendingPurchase();
            if (mc.player != null) {
                ChatUtils.sendClientMessage("§c[AutoBuy] Персонаж телепортировался — AutoBuy отключён!");
            }
        }
    }

    private void processAuctionScreen() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (this.buyCooldownTicks > 0) this.buyCooldownTicks--;
        if (this.refreshCooldownTicks > 0) this.refreshCooldownTicks--;
        if (this.commandCooldownTicks > 0) this.commandCooldownTicks--;

        if (RNG.nextInt(20) == 0) {
            this.randomMicroPauseTicks = randomBetween(1, 3);
            return;
        }

        Screen screen = mc.gui.screen();
        if (!(screen instanceof ContainerScreen gcs)) {
            this.unchangedHashTicks = 0;
            if (screen == null) {
                this.noScreenTicks++;
                if (this.noScreenTicks >= Math.max(1, this.calcCommandCooldownTicks(cfg) / 2)) {
                    this.reopenAuction();
                }
            } else {
                this.noScreenTicks = 0;
            }
            return;
        }

        this.noScreenTicks = 0;
        if (!isAuctionScreen(gcs)) {
            this.unchangedHashTicks = 0;
            return;
        }
        if (this.buyCooldownTicks > 0) return;

        ChestMenu handler = gcs.getMenu();
        int syncId = handler.containerId;
        int rows = handler.getRowCount();
        int lotSlotsCount = (rows - 1) * 9;
        if (this.checkAndCorrectSort(handler, syncId)) return;

        long hash = this.computeSlotsHash(handler, lotSlotsCount);
        if (hash != this.lastSlotsHash) {
            this.unchangedHashTicks = 0;
            this.lastSlotsHash = hash;
        } else {
            this.unchangedHashTicks++;
        }

        ArrayList<Lot> lots = new ArrayList<>();
        for (int slot = 0; slot < lotSlotsCount; slot++) {
            ItemStack stack = handler.getSlot(slot).getItem();
            Lot lot = PriceParser.parse(slot, stack);
            if (lot != null && !lot.barrier) lots.add(lot);
        }

        Lot chosenLot = null;
        ItemEntry chosenEntry = null;
        for (Lot lot : lots) {
            ItemEntry entry = this.findBestMatchingEntry(cfg, lot);
            if (entry != null) {
                chosenLot = lot;
                chosenEntry = entry;
                break;
            }
        }

        if (chosenLot != null && chosenEntry != null) {
            this.targetedItemId = chosenLot.itemId;
            this.targetedDisplayName = chosenEntry.displayName;
            this.targetedLoreKeyword = chosenEntry.loreKeyword;
            this.targetedMaxUnitPrice = chosenEntry.maxUnitPrice;
            this.targetedRequireCustom = chosenEntry.requireCustom;
            this.targetedMarkers = chosenEntry.resolveMarkers();
            this.targetedSeller = (chosenLot.seller != null && !chosenLot.seller.isBlank()) ? chosenLot.seller : null;
            this.targetedSuspicious = isSuspiciousPrice(chosenLot);
            if (this.targetedSuspicious) {
                AutoBuyOverlay.showSuspiciousNotification();
                if (mc.gui.hud != null) {
                    mc.gui.hud.setOverlayMessage(Component.literal("§c§lПодозрительная цена!"), false);
                }
            }
            this.buyCooldownTicks = this.calcBuyClickDelayTicks(cfg);
            if (RNG.nextInt(8) == 0) {
                this.buyCooldownTicks += Math.max(1, this.calcPreActionDelayTicks(cfg));
                this.targetedItemId = null;
                this.targetedSeller = null;
                this.targetedSuspicious = false;
                return;
            }
            this.clickSlot(syncId, chosenLot.slot);
            if (this.awaitingConfirmationTicks <= 0) {
                this.awaitingConfirmationTicks = 60;
            }
        }
    }

    private boolean checkAndCorrectSort(ChestMenu handler, int syncId) {
        int hopperSlot = AuctionSortMode.findButton(handler);
        if (hopperSlot < 0) return false;
        AuctionSortMode currentMode = AuctionSortMode.current(handler.getSlot(hopperSlot).getItem());
        if (currentMode != null && currentMode != AuctionSortMode.NEWEST) {
            this.clickSlot(syncId, hopperSlot);
            this.buyCooldownTicks = Math.max(this.buyCooldownTicks, 6);
            this.lastSlotsHash = 0L;
            this.unchangedHashTicks = 0;
            return true;
        }
        return false;
    }

    private void reopenAuction() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (this.commandCooldownTicks <= 0 && mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendCommand("ah");
            this.commandCooldownTicks = this.calcCommandCooldownTicks(cfg);
        }
    }

    private ItemEntry findBestMatchingEntry(AutoBuyConfig cfg, Lot lot) {
        String myName = mc.player.getGameProfile().name();
        if (myName != null && lot.seller != null && myName.equalsIgnoreCase(lot.seller)) return null;
        if (this.isSellerIgnored(cfg, lot)) return null;
        if (cfg.minPriceEnabled && cfg.minPrice > 0L && lot.unitPrice < cfg.minPrice && !isSuspiciousPrice(lot)) {
            return null;
        }

        ItemEntry bestEntry = null;
        int bestScore = -1;
        ArrayList<ItemEntry> candidates = new ArrayList<>(cfg.entries);
        if (RNG.nextInt(2) == 0) {
            Collections.shuffle(candidates, RNG);
        }

        for (ItemEntry e : candidates) {
            if (e != null && e.enabled && e.maxUnitPrice > 0L && ItemMatcher.matches(e, lot)
                    && lot.unitPrice <= e.maxUnitPrice) {
                int score = (e.displayName != null ? e.displayName.length() * 2 : 0)
                        + (e.loreKeyword != null && !e.loreKeyword.isBlank() ? randomBetween(15, 50) : 0)
                        + (e.matchAnySword ? -randomBetween(10, 30) : 0)
                        + (e.requireCustom ? randomBetween(10, 30) : 0)
                        + (RNG.nextInt(20) - 10);
                if (score > bestScore) {
                    bestScore = score;
                    bestEntry = e;
                }
            }
        }
        return bestEntry;
    }

    public static boolean isAuctionScreen(ContainerScreen gcs) {
        if (gcs == null || gcs.getMenu() == null) return false;
        ChestMenu handler = gcs.getMenu();
        if (handler.getRowCount() < 3) return false;
        String title = PriceParser.stripFormatting(gcs.getTitle().getString()).toLowerCase(Locale.ROOT);
        return title.contains("аукцион") || title.contains("auction") || title.contains("рынок")
                || title.contains("поиск") || title.contains("хранилище");
    }

    private void triggerRefreshClick(ChestMenu handler, int syncId, int rows) {
        if (this.refreshCooldownTicks > 0) return;
        int total = rows * 9;
        int slot = -1;
        int cfgSlot = AutoBuyConfig.cfg().refreshSlot;
        if (cfgSlot >= 0 && cfgSlot < total) {
            ItemStack stack = handler.getSlot(cfgSlot).getItem();
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("minecraft:emerald")) {
                slot = cfgSlot;
            }
        }
        if (slot < 0) {
            slot = this.findSlotWithItem(handler, (rows - 1) * 9, total, "minecraft:emerald");
        }
        if (slot >= 0) {
            this.clickSlot(syncId, slot);
            this.refreshCooldownTicks = this.calcRefreshDelayTicks(AutoBuyConfig.cfg());
            this.lastSlotsHash = 0L;
            this.unchangedHashTicks = 0;
        }
    }

    private int findSlotWithItem(ChestMenu handler, int from, int to, String itemId) {
        ArrayList<Integer> slots = new ArrayList<>();
        for (int i = from; i < to; i++) slots.add(i);
        if (RNG.nextInt(2) == 0) Collections.shuffle(slots, RNG);
        for (int slot : slots) {
            ItemStack stack = handler.getSlot(slot).getItem();
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                return slot;
            }
        }
        return -1;
    }

    private long computeSlotsHash(ChestMenu handler, int count) {
        long h = 1L;
        for (int i = 0; i < count; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            h = h * 31L + (stack.isEmpty() ? 0L : BuiltInRegistries.ITEM.getId(stack.getItem()) * 113L + stack.getCount());
        }
        return h;
    }

    private void processConfirmationScreen() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (this.awaitingInventoryArrival) {
            this.arrivalTimeoutTicks--;
            if (this.awaitingItemId != null && this.countItemInInventory(this.awaitingItemId) > this.preBuyInventoryCount) {
                String boughtId = this.awaitingItemId;
                boolean wasSuspicious = this.pendingPurchaseSuspicious;
                if (this.pendingPurchaseRecord != null) {
                    this.pendingPurchaseRecord.success = true;
                    AutoBuyConfig.cfg().addHistory(this.pendingPurchaseRecord);
                }
                this.notifyPurchaseSuccess();
                boolean autoSellEnabled = AutoBuyConfig.cfg().autoSell;
                this.resetPendingPurchase();
                if (wasSuspicious && cfg.safeMode) {
                    this.triggerSuspiciousEscape();
                } else if (autoSellEnabled && boughtId != null) {
                    this.seller.trigger();
                }
            } else if (this.arrivalTimeoutTicks <= 0) {
                this.resetPendingPurchase();
            }
        }

        if (this.confirmActionCooldown > 0) {
            this.confirmActionCooldown--;
            return;
        }
        if (!(mc.gui.screen() instanceof ContainerScreen gcs) || !isConfirmScreen(gcs) || !cfg.enabled) {
            return;
        }
        this.awaitingConfirmationTicks = 0;

        ChestMenu handler = gcs.getMenu();
        int syncId = handler.containerId;
        Lot confirmLot = this.findConfirmLot(handler);
        String targetId = this.targetedItemId;
        long maxPrice = this.targetedMaxUnitPrice;
        if (targetId == null || maxPrice <= 0L) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            return;
        }
        if (confirmLot == null || !targetId.equals(confirmLot.itemId)) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            return;
        }

        String reqKeyword = this.targetedLoreKeyword;
        String reqDisplay = this.targetedDisplayName;
        if (reqKeyword != null && !reqKeyword.isBlank()) {
            boolean inLore = TextNormalizer.keywordIn(confirmLot.lore, reqKeyword);
            boolean inName = TextNormalizer.keywordIn(confirmLot.name, reqKeyword);
            if (!inLore && !inName) {
                this.cancelConfirmation(handler, syncId);
                this.targetedItemId = null;
                return;
            }
        } else if (reqDisplay != null && !reqDisplay.isBlank()
                && !TextNormalizer.nameMatches(reqDisplay, confirmLot.name)) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            return;
        }

        if (this.targetedRequireCustom && !confirmLot.hasMarkers) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            return;
        }

        List<Marker> markers = this.targetedMarkers;
        if (markers != null && !markers.isEmpty()) {
            boolean matched = markers.stream().anyMatch(m -> ItemMatcher.markerMatches(m, confirmLot));
            if (!matched) {
                this.cancelConfirmation(handler, syncId);
                this.targetedItemId = null;
                this.targetedMarkers = null;
                return;
            }
        } else if (this.targetedRequireCustom) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            this.targetedMarkers = null;
            return;
        }

        boolean ignoredSeller = this.isSellerIgnored(cfg, confirmLot)
                || (this.targetedSeller != null && cfg.ignored(this.targetedSeller))
                || (confirmLot.lore != null && this.loreContainsBannedSeller(cfg, confirmLot.lore));
        if (ignoredSeller || confirmLot.unitPrice > maxPrice) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            return;
        }

        int confirmSlot = this.findConfirmGreenButton(handler);
        if (confirmSlot < 0) return;

        this.confirmActionCooldown = this.calcPreActionDelayTicks(cfg);
        if (RNG.nextInt(10) == 0) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            return;
        }

        boolean isSuspicious = this.targetedSuspicious || isSuspiciousPrice(confirmLot);
        if (isSuspicious) {
            AutoBuyOverlay.showSuspiciousNotification();
            if (mc.gui.hud != null) {
                mc.gui.hud.setOverlayMessage(Component.literal("§c§lПодозрительная цена!"), false);
            }
        }
        this.pendingPurchaseSuspicious = isSuspicious;

        String finalSeller = (confirmLot.seller != null && !confirmLot.seller.isBlank())
                ? confirmLot.seller
                : (this.targetedSeller != null ? this.targetedSeller : "");
        if (finalSeller.isBlank() && confirmLot.lore != null && !confirmLot.lore.isBlank()) {
            finalSeller = PriceParser.extractSeller(confirmLot.lore);
        }
        if (!finalSeller.isBlank() && cfg.ignored(finalSeller)) {
            this.cancelConfirmation(handler, syncId);
            this.targetedItemId = null;
            return;
        }

        this.clickSlot(syncId, confirmSlot);
        this.awaitingInventoryArrival = true;
        this.awaitingItemId = confirmLot.itemId;
        this.preBuyInventoryCount = this.countItemInInventory(confirmLot.itemId);
        this.arrivalTimeoutTicks = 20 + RNG.nextInt(10);
        long totalCost = confirmLot.totalPrice > 0L ? confirmLot.totalPrice : confirmLot.unitPrice * Math.max(1, confirmLot.count);
        String label = confirmLot.name != null && !confirmLot.name.isBlank() ? confirmLot.name : confirmLot.itemId;
        this.pendingPurchaseRecord = new PurchaseRecord(
                System.currentTimeMillis(), confirmLot.itemId, label, finalSeller, confirmLot.unitPrice, totalCost, confirmLot.count
        );
        this.targetedItemId = null;
        this.targetedMaxUnitPrice = 0L;
        this.targetedMarkers = null;
        this.targetedSeller = null;
        this.targetedSuspicious = false;
    }

    private void cancelConfirmation(ChestMenu handler, int syncId) {
        this.confirmActionCooldown = this.calcPreActionDelayTicks(AutoBuyConfig.cfg());
        this.targetedSeller = null;
        this.targetedSuspicious = false;
        this.clickCancelOrClose(handler, syncId);
    }

    public static boolean isConfirmScreen(ContainerScreen gcs) {
        if (gcs == null || gcs.getTitle() == null) return false;
        String title = PriceParser.stripFormatting(gcs.getTitle().getString()).toLowerCase(Locale.ROOT);
        return title.contains("покупка") || title.contains("подтвержд") || title.contains("купить");
    }

    private Lot findConfirmLot(ChestMenu handler) {
        int totalSlots = handler.getRowCount() * 9;
        int mid = RNG.nextInt(Math.max(1, totalSlots / 2));
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? mid : 0;
            int to = pass == 0 ? totalSlots : mid;
            for (int i = from; i < to; i++) {
                ItemStack stack = handler.getSlot(i).getItem();
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (!id.contains("stained_glass") && !id.contains("concrete") && !id.contains("wool")) {
                        Lot lot = PriceParser.parse(i, stack);
                        if (lot != null && !lot.barrier) return lot;
                    }
                }
            }
        }
        return null;
    }

    private int findConfirmGreenButton(ChestMenu handler) {
        int totalSlots = handler.getRowCount() * 9;
        int mid = RNG.nextInt(Math.max(1, totalSlots));
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? mid : 0;
            int to = pass == 0 ? totalSlots : mid;
            for (int i = from; i < to; i++) {
                String id = BuiltInRegistries.ITEM.getKey(handler.getSlot(i).getItem().getItem()).toString();
                if (id.contains("lime_stained_glass") || id.contains("green_stained_glass")
                        || id.contains("lime_concrete") || id.contains("green_wool") || id.contains("lime_wool")) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findCancelRedButton(ChestMenu handler) {
        int totalSlots = handler.getRowCount() * 9;
        for (int i = 0; i < totalSlots; i++) {
            String id = BuiltInRegistries.ITEM.getKey(handler.getSlot(i).getItem().getItem()).toString();
            if (id.contains("red_stained_glass") || id.contains("red_concrete") || id.contains("red_wool")) {
                return i;
            }
        }
        return -1;
    }

    private void clickCancelOrClose(ChestMenu handler, int syncId) {
        int cancelSlot = this.findCancelRedButton(handler);
        if (cancelSlot < 0) {
            mc.player.closeContainer();
        } else {
            this.clickSlot(syncId, cancelSlot);
        }
    }

    public synchronized void onInsufficientFunds() {
        if (!this.awaitingInventoryArrival) return;
        if (this.pendingPurchaseRecord != null) {
            this.pendingPurchaseRecord.success = false;
            this.pendingPurchaseRecord.failReason = "недостаточно средств";
            AutoBuyConfig.cfg().addHistory(this.pendingPurchaseRecord);
        }
        ChatUtils.sendClientMessage("§c[AutoBuy] Недостаточно средств — покупка отменена");
        this.resetPendingPurchase();
    }

    private int countItemInInventory(String itemId) {
        if (mc.player == null || itemId == null) return 0;
        Inventory inv = mc.player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void clickSlot(int syncId, int slot) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(syncId, slot, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private void resetPendingPurchase() {
        this.awaitingInventoryArrival = false;
        this.awaitingItemId = null;
        this.preBuyInventoryCount = 0;
        this.arrivalTimeoutTicks = 0;
        this.pendingPurchaseRecord = null;
        this.pendingPurchaseSuspicious = false;
    }

    private void notifyPurchaseSuccess() {
        String name = this.pendingPurchaseRecord != null && this.pendingPurchaseRecord.label != null
                && !this.pendingPurchaseRecord.label.isBlank()
                ? this.pendingPurchaseRecord.label
                : (this.awaitingItemId == null ? "?" : this.awaitingItemId);
        int count = this.pendingPurchaseRecord != null ? this.pendingPurchaseRecord.count : 1;
        String seller = this.pendingPurchaseRecord != null ? this.pendingPurchaseRecord.seller : null;
        long price = this.pendingPurchaseRecord != null
                ? (this.pendingPurchaseRecord.totalPrice > 0L ? this.pendingPurchaseRecord.totalPrice
                : this.pendingPurchaseRecord.pricePerUnit * Math.max(1, count))
                : 0L;

        StringBuilder sb = new StringBuilder();
        sb.append("§a[AutoBuy] Куплено: §f").append(name);
        if (count > 1) sb.append(" §7(×").append(count).append(")");
        if (seller != null && !seller.isBlank()) sb.append(" §7у игрока §f").append(seller);
        if (price > 0L) {
            sb.append(" §7за §6").append(String.format(Locale.ROOT, "%,d", price).replace(',', ' ')).append("$");
            AutoBuyReport.recordPurchase(price);
        }
        ChatUtils.sendClientMessage(sb.toString());
    }

    public static boolean isSuspiciousPrice(Lot lot) {
        if (lot == null) return false;
        long total = lot.totalPrice > 0L ? lot.totalPrice : lot.unitPrice * Math.max(1, lot.count);
        return (total >= 1L && total <= 15000L) || (lot.unitPrice >= 1L && lot.unitPrice <= 15000L);
    }

    public synchronized void triggerSuspiciousEscape() {
        long now = System.currentTimeMillis();
        if (now - this.lastSuspiciousEscapeTime < 3000L) return;
        this.lastSuspiciousEscapeTime = now;

        ChatUtils.sendClientMessage("§c[AutoBuy] Подозрительная цена! Выход в /hub...");
        AutoBuyOverlay.showSuspiciousNotification();
        mc.execute(() -> {
            if (mc.gui.hud != null) {
                mc.gui.hud.setOverlayMessage(Component.literal("§c§lПодозрительная цена! Выход в /hub"), false);
            }
            if (mc.player != null) {
                mc.player.closeContainer();
                if (mc.player.connection != null) {
                    mc.player.connection.sendCommand("hub");
                }
            }
        });
    }

    public void forceRefresh() {
        this.refreshCooldownTicks = 0;
        this.lastSlotsHash = 0L;
        this.unchangedHashTicks = 999;
        this.refreshThrottleCounter = 999;
        if (mc.gui.screen() instanceof ContainerScreen gcs && isAuctionScreen(gcs)) {
            ChestMenu handler = gcs.getMenu();
            this.triggerRefreshClick(handler, handler.containerId, handler.getRowCount());
        }
    }

    private int calcBuyClickDelayTicks(AutoBuyConfig cfg) {
        int delayMs = cfg.delayMsForStage(0.18F, 28, 210);
        int clickInterval = Math.max(0, cfg.clickIntervalTicks) * 16;
        return Math.max(1, this.millisToTicks((delayMs + clickInterval + RNG.nextInt(28)) / 100));
    }

    private int calcRefreshDelayTicks(AutoBuyConfig cfg) {
        if (cfg.refreshIntervalTicks > 0) return Math.max(1, cfg.refreshIntervalTicks / 100);
        int delayMs = cfg.delayMsForStage(0.45F, 18, 160);
        return Math.max(0, this.millisToTicks((delayMs + RNG.nextInt(20)) / 100));
    }

    private int calcPreActionDelayTicks(AutoBuyConfig cfg) {
        int delayMs = cfg.delayMsForStage(0.7F, 10, 130);
        return Math.max(0, this.millisToTicks((delayMs + RNG.nextInt(18)) / 100));
    }

    private int calcCommandCooldownTicks(AutoBuyConfig cfg) {
        int delayMs = cfg.delayMsForStage(1.0F, 200, 1150);
        return Math.max(1, this.millisToTicks(delayMs + RNG.nextInt(60)));
    }

    private int millisToTicks(int millis) {
        return (int) Math.ceil(Math.max(0, millis) / 50.0);
    }

    private boolean isSellerIgnored(AutoBuyConfig cfg, Lot lot) {
        if (lot == null || cfg == null || cfg.banList == null || cfg.banList.isEmpty()) return false;
        if (lot.seller != null && !lot.seller.isBlank() && cfg.ignored(lot.seller)) return true;
        if (this.targetedSeller != null && !this.targetedSeller.isBlank() && cfg.ignored(this.targetedSeller)) return true;
        if (lot.lore != null && !lot.lore.isBlank()) {
            if (lot.seller == null || lot.seller.isBlank()) {
                String extracted = PriceParser.extractSeller(lot.lore);
                if (!extracted.isBlank() && cfg.ignored(extracted)) return true;
            }
            if (this.loreContainsBannedSeller(cfg, lot.lore)) return true;
        }
        return false;
    }

    private boolean loreContainsBannedSeller(AutoBuyConfig cfg, String rawLore) {
        if (rawLore == null || rawLore.isBlank() || cfg == null || cfg.banList == null || cfg.banList.isEmpty()) {
            return false;
        }
        String extracted = PriceParser.extractSeller(rawLore);
        if (extracted != null && !extracted.isBlank() && cfg.ignored(extracted)) return true;

        String cleanLore = PriceParser.stripFormatting(rawLore).replace('\u00A0', ' ');
        for (String rawLine : cleanLore.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String lineLower = line.toLowerCase(Locale.ROOT);
            boolean isSellerLine = lineLower.contains("продавец") || lineLower.contains("выставил")
                    || lineLower.contains("продает") || lineLower.contains("продаёт")
                    || lineLower.contains("владелец") || lineLower.contains("торговец")
                    || lineLower.contains("seller") || lineLower.contains("owner")
                    || lineLower.contains("игрок") || lineLower.contains("ник")
                    || lineLower.contains("купить") || lineLower.contains("покупка")
                    || lineLower.contains("покупаете") || lineLower.contains("приобрести")
                    || lineLower.contains("продажа");
            if (!isSellerLine) continue;

            for (String banned : cfg.banList) {
                if (banned == null || banned.isBlank()) continue;
                String cleanBanned = AutoBuyConfig.cleanNickForCheck(banned);
                if (cleanBanned.isEmpty()) continue;
                String lowBanned = cleanBanned.toLowerCase(Locale.ROOT);
                if (AutoBuyConfig.containsWord(line.toLowerCase(Locale.ROOT), lowBanned)) return true;
                String alphaOnly = line.replaceAll("[^A-Za-z0-9_]", " ").toLowerCase(Locale.ROOT);
                if (AutoBuyConfig.containsWord(alphaOnly, lowBanned)) return true;
            }
        }
        return false;
    }

    private static int randomBetween(int min, int max) {
        return min >= max ? min : min + RNG.nextInt(max - min + 1);
    }
}
