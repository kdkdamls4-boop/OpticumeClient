package dev.opticumeclient.client.autobuy;

import dev.opticumeclient.client.modules.other.AutoBuyModule;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PriceAutoParser {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final int MAX_PAGE_SCANS = 25;

    private State state = State.IDLE;
    private List<ItemEntry> parseEntries = new ArrayList<>();
    private int currentIndex = 0;
    private int tickDelay = 0;
    private long minFoundPrice = -1L;
    private int pageScanCount = 0;
    private long lastScreenHash = 0L;
    private int timeoutTicks = 0;
    private int sortClicksRemaining = 0;
    private String currentItemLabel = "";
    private long prevHash = 0L;
    private int stableHashCount = 0;
    private AuctionSortMode targetSortMode = null;
    private boolean wasAutoBuyEnabled = false;

    public synchronized void start() {
        if (this.state != State.IDLE) {
            this.stop();
            return;
        }
        if (mc.player == null) return;

        this.parseEntries = new ArrayList<>();
        for (ItemEntry e : AutoBuyConfig.cfg().entries) {
            if (e != null && e.enabled) {
                this.parseEntries.add(e);
            }
        }
        if (this.parseEntries.isEmpty()) {
            ChatUtils.sendClientMessage("§e[AutoBuy] Нет включённых предметов для парсинга");
            return;
        }

        this.currentIndex = 0;
        this.tickDelay = 0;
        this.timeoutTicks = 0;
        this.sortClicksRemaining = 0;
        this.wasAutoBuyEnabled = AutoBuyConfig.cfg().enabled;
        AutoBuyConfig.cfg().enabled = false;
        AutoBuyModule mod = AutoBuyModule.getInstance();
        if (mod != null && mod.isEnabled()) {
            mod.setEnabled(false);
        }

        this.currentItemLabel = "сортировка";
        this.state = State.PRE_OPEN;
        ChatUtils.sendClientMessage("§b[AutoBuy] Запуск парсинга цен: " + this.parseEntries.size() + " предмет(ов)");
    }

    public synchronized void stop() {
        this.state = State.IDLE;
        this.parseEntries.clear();
        this.currentIndex = 0;
        this.currentItemLabel = "";
        this.targetSortMode = null;
        this.minFoundPrice = -1L;
    }

    public boolean isActive() {
        return this.state != State.IDLE;
    }

    public String statusLine() {
        if (this.state == State.IDLE) return "Парсер цен: ожидание";
        int current = Math.min(this.currentIndex + 1, Math.max(1, this.parseEntries.size()));
        return "Парсер: " + current + "/" + this.parseEntries.size() + " — " + this.currentItemLabel;
    }

    public synchronized void tick() {
        if (this.state == State.IDLE) return;
        if (mc.player == null) {
            this.stop();
            return;
        }
        if (this.tickDelay > 0) {
            this.tickDelay--;
            return;
        }

        switch (this.state) {
            case PRE_OPEN -> this.handlePreOpen();
            case PRE_WAIT -> this.handlePreWait();
            case PRE_SORT -> this.handlePreSort();
            case NEXT -> this.handleNext();
            case WAIT_SCREEN -> this.handleWaitScreen();
            case SCAN -> this.handleScan();
            case WAIT_PAGE -> this.handleWaitPage();
            case ADVANCE -> this.handleAdvance();
            case POST_OPEN -> this.handlePostOpen();
            case POST_WAIT -> this.handlePostWait();
            case POST_SORT -> this.handlePostSort();
            default -> this.stop();
        }
    }

    private void handlePreOpen() {
        if (this.isAuctionScreen()) {
            this.prepareSort(AuctionSortMode.CHEAPEST_PER_UNIT, 4, State.PRE_SORT);
        } else if (mc.gui.screen() != null) {
            mc.player.closeContainer();
            this.tickDelay = 6;
        } else {
            mc.player.connection.sendCommand("ah");
            this.state = State.PRE_WAIT;
            this.timeoutTicks = 60;
        }
    }

    private void handlePreWait() {
        if (this.isAuctionScreen()) {
            this.prepareSort(AuctionSortMode.CHEAPEST_PER_UNIT, 4, State.PRE_SORT);
        } else if (--this.timeoutTicks <= 0) {
            this.state = State.NEXT;
            this.tickDelay = 8;
        }
    }

    private void handlePreSort() {
        if (!this.isAuctionScreen()) {
            this.state = State.NEXT;
            this.tickDelay = 8;
            return;
        }
        ChestMenu handler = this.getScreenHandler();
        int slot = AuctionSortMode.findButton(handler);
        if (slot < 0) {
            ChatUtils.sendClientMessage("§e[AutoBuy] Воронка «Сортировка» не найдена");
            this.state = State.NEXT;
            this.tickDelay = 8;
            return;
        }
        AuctionSortMode current = AuctionSortMode.current(handler.getSlot(slot).getItem());
        if ((this.targetSortMode != null && current == this.targetSortMode) || this.sortClicksRemaining <= 0) {
            this.state = State.NEXT;
            this.tickDelay = 8;
        } else {
            this.clickSlot(handler.containerId, slot);
            this.sortClicksRemaining--;
            this.tickDelay = 6;
        }
    }

    private void handlePostOpen() {
        this.currentItemLabel = "сортировка";
        if (this.isAuctionScreen()) {
            this.prepareSort(AuctionSortMode.NEWEST, 2, State.POST_SORT);
        } else if (mc.gui.screen() != null) {
            mc.player.closeContainer();
            this.tickDelay = 6;
        } else {
            mc.player.connection.sendCommand("ah");
            this.state = State.POST_WAIT;
            this.timeoutTicks = 60;
        }
    }

    private void handlePostWait() {
        if (this.isAuctionScreen()) {
            this.prepareSort(AuctionSortMode.NEWEST, 2, State.POST_SORT);
        } else if (--this.timeoutTicks <= 0) {
            this.finish();
        }
    }

    private void handlePostSort() {
        if (!this.isAuctionScreen()) {
            this.finish();
            return;
        }
        ChestMenu handler = this.getScreenHandler();
        int slot = AuctionSortMode.findButton(handler);
        if (slot < 0) {
            this.finish();
            return;
        }
        AuctionSortMode current = AuctionSortMode.current(handler.getSlot(slot).getItem());
        if ((this.targetSortMode != null && current == this.targetSortMode) || this.sortClicksRemaining <= 0) {
            this.finish();
        } else {
            this.clickSlot(handler.containerId, slot);
            this.sortClicksRemaining--;
            this.tickDelay = 6;
        }
    }

    private void finish() {
        ChatUtils.sendClientMessage("§a[AutoBuy] Парсинг цен окончен! Обновлено: " + this.parseEntries.size() + " предм.");
        if (mc.gui.screen() != null && mc.player != null) {
            mc.player.closeContainer();
        }
        if (this.wasAutoBuyEnabled) {
            AutoBuyConfig.cfg().enabled = true;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null && !mod.isEnabled()) {
                mod.setEnabled(true);
            }
        }
        AutoBuyConfig.save();
        this.stop();
    }

    private void handleNext() {
        if (this.currentIndex >= this.parseEntries.size()) {
            this.state = State.POST_OPEN;
            this.timeoutTicks = 60;
            this.tickDelay = 4;
            return;
        }
        ItemEntry entry = this.parseEntries.get(this.currentIndex);
        String label = entry.displayName != null && !entry.displayName.isBlank() ? entry.displayName : entry.itemId;
        if (entry.loreKeyword != null && !entry.loreKeyword.isBlank()) {
            label = label + " [" + entry.loreKeyword + "]";
        }

        this.currentItemLabel = label;
        this.minFoundPrice = -1L;
        this.pageScanCount = 0;
        this.lastScreenHash = 0L;
        if (mc.gui.screen() != null) {
            mc.player.closeContainer();
            this.tickDelay = 4;
        } else {
            String query = entry.query().replaceAll("[«»\"']", "").trim();
            mc.player.connection.sendCommand("ah search " + query);
            this.state = State.WAIT_SCREEN;
            this.tickDelay = 0;
            this.timeoutTicks = 60;
        }
    }

    private void handleWaitScreen() {
        if (--this.timeoutTicks <= 0) {
            this.commitPriceAndAdvance();
        } else if (this.isAuctionScreen()) {
            this.startScan();
        }
    }

    private void startScan() {
        this.state = State.SCAN;
        this.timeoutTicks = 14;
        this.prevHash = 0L;
        this.stableHashCount = 0;
    }

    private void handleScan() {
        if (!this.isAuctionScreen()) {
            this.commitPriceAndAdvance();
            return;
        }
        ChestMenu handler = this.getScreenHandler();
        int rows = handler.getRowCount();
        int lotCount = (rows - 1) * 9;
        ItemEntry entry = this.parseEntries.get(this.currentIndex);
        int nonEmptyCount = 0;

        for (int i = 0; i < lotCount; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            if (!stack.isEmpty()) nonEmptyCount++;
            Lot lot = PriceParser.parse(i, stack);
            if (lot != null && !lot.barrier && ItemMatcher.matches(entry, lot)
                    && (this.minFoundPrice < 0L || lot.unitPrice < this.minFoundPrice)) {
                this.minFoundPrice = lot.unitPrice;
            }
        }

        long hash = this.computeSlotsHash(handler, lotCount);
        if (hash == this.prevHash) {
            this.stableHashCount++;
        } else {
            this.stableHashCount = 0;
            this.prevHash = hash;
        }

        this.timeoutTicks--;
        boolean isStable = nonEmptyCount > 0 && this.stableHashCount >= 2;
        if (isStable || this.timeoutTicks <= 0) {
            boolean canAdvancePage = this.isArmorPiece(entry) && this.minFoundPrice < 0L && this.pageScanCount + 1 < MAX_PAGE_SCANS;
            if (!canAdvancePage) {
                this.commitPriceAndAdvance();
            } else {
                int nextPageSlot = this.findNextPageButton(handler, rows);
                if (nextPageSlot < 0) {
                    this.commitPriceAndAdvance();
                } else {
                    this.lastScreenHash = this.computeSlotsHash(handler, lotCount);
                    this.clickSlot(handler.containerId, nextPageSlot);
                    this.state = State.WAIT_PAGE;
                    this.tickDelay = 3;
                    this.timeoutTicks = 30;
                    this.pageScanCount++;
                }
            }
        }
    }

    private void handleWaitPage() {
        if (!this.isAuctionScreen()) {
            this.commitPriceAndAdvance();
            return;
        }
        ChestMenu handler = this.getScreenHandler();
        int lotCount = (handler.getRowCount() - 1) * 9;
        if (this.computeSlotsHash(handler, lotCount) != this.lastScreenHash) {
            this.startScan();
        } else if (--this.timeoutTicks <= 0) {
            this.commitPriceAndAdvance();
        }
    }

    private void handleAdvance() {
        if (mc.gui.screen() != null) {
            mc.player.closeContainer();
            this.tickDelay = 4;
        } else {
            this.currentIndex++;
            this.state = State.NEXT;
            this.tickDelay = 4;
        }
    }

    private void commitPriceAndAdvance() {
        ItemEntry entry = this.parseEntries.get(this.currentIndex);
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        int pct = Math.max(1, Math.min(100, cfg.parsePercent));
        entry.maxUnitPrice = this.minFoundPrice > 0L ? this.minFoundPrice * pct / 100L : 0L;
        if (this.minFoundPrice > 0L) {
            entry.marketPrice = this.minFoundPrice;
        }
        AutoBuyConfig.save();
        this.state = State.ADVANCE;
        this.tickDelay = 2;
    }

    private void prepareSort(AuctionSortMode target, int fallbackClicks, State nextState) {
        this.targetSortMode = target;
        this.sortClicksRemaining = fallbackClicks;
        AuctionSortMode current = AuctionSortMode.current(this.getScreenHandler());
        if (current != null) {
            this.sortClicksRemaining = current.clicksTo(target);
        }
        this.state = nextState;
        this.tickDelay = 6;
    }

    private boolean isAuctionScreen() {
        return mc.gui.screen() instanceof ContainerScreen gcs && AutoBuyEngine.isAuctionScreen(gcs);
    }

    private ChestMenu getScreenHandler() {
        return ((ContainerScreen) mc.gui.screen()).getMenu();
    }

    private boolean isArmorPiece(ItemEntry e) {
        if (e == null || e.itemId == null) return false;
        return e.itemId.endsWith("_helmet") || e.itemId.endsWith("_chestplate")
                || e.itemId.endsWith("_leggings") || e.itemId.endsWith("_boots");
    }

    private int findNextPageButton(ChestMenu handler, int rows) {
        int from = (rows - 1) * 9;
        int to = rows * 9;
        int slot = -1;
        for (int i = from; i < to; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (id.equals("minecraft:lime_dye") || id.equals("minecraft:green_dye")) {
                    slot = i;
                }
            }
        }
        return slot;
    }

    private long computeSlotsHash(ChestMenu handler, int count) {
        long h = 1L;
        for (int i = 0; i < count; i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            h = h * 31L + (stack.isEmpty() ? 0L : BuiltInRegistries.ITEM.getId(stack.getItem()) * 113L + stack.getCount());
        }
        return h;
    }

    private void clickSlot(int syncId, int slot) {
        if (mc.gameMode != null && mc.player != null) {
            mc.gameMode.handleContainerInput(syncId, slot, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private enum State {
        IDLE, PRE_OPEN, PRE_WAIT, PRE_SORT, NEXT, WAIT_SCREEN, SCAN, WAIT_PAGE, ADVANCE, POST_OPEN, POST_WAIT, POST_SORT
    }
}
