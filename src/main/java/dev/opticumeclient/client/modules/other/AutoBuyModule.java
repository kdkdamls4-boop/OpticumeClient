package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.autobuy.AutoBuyConfig;
import dev.opticumeclient.client.autobuy.AutoBuyEngine;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.RunSetting;
import dev.opticumeclient.client.setting.TextSetting;

/**
 * Automated Auction House sniper, auto-seller, price parser, and auto-pay module
 * ported from Desire's {@code FlowAutoBuyModule}.
 */
public class AutoBuyModule extends Module {
    private static AutoBuyModule instance;

    public final BooleanSetting autoRefresh = new BooleanSetting("Авто-обновление", "Автоматически обновлять аукцион", true);
    public final BooleanSetting autoSell = new BooleanSetting("Авто-продажа", "Выставлять купленные предметы на продажу", false);
    public final BooleanSetting autoRelist = new BooleanSetting("Авто-релист", "Перевыставлять некупленные лоты", false);
    public final BooleanSetting autoParse = new BooleanSetting("Авто-парсинг цен", "Автоматически отслеживать цены рынка", true);
    public final BooleanSetting safeMode = new BooleanSetting("SafeMode", "Защита от подозрительно дешёвых лотов (выход в /hub)", true);
    public final NumberSetting armorDurability = new NumberSetting("Мин. прочность %", "Минимальная прочность брони в %", 50.0, 0.0, 100.0, 1.0);
    public final TextSetting autoPayNick = new TextSetting("Ник AutoPay", "Ник игрока для автоперевода баланса", "", "Ник получателя", 32);
    public final BooleanSetting autoPay = new BooleanSetting("AutoPay", "Автоматический перевод монет при избытке баланса", false);
    public final NumberSetting autoPayAmount = new NumberSetting("Сумма перевода (кк)", "Сумма одного перевода в миллионах", 5.0, 1.0, 100.0, 1.0);
    public final NumberSetting autoPayKeep = new NumberSetting("Остаток баланс (кк)", "Сколько оставлять на балансе в миллионах", 5.0, 0.0, 100.0, 1.0);
    public final NumberSetting sellDiscount = new NumberSetting("Скидка продажи %", "Скидка от рыночной цены при перепродаже", 5.0, 1.0, 50.0, 1.0);
    public final NumberSetting minPrice = new NumberSetting("Мин. цена", "Минимальная цена лота для покупки", 15000.0, 1000.0, 500000.0, 1000.0);
    public final BooleanSetting compactChat = new BooleanSetting("Анти-спам чата", "Сжимать одинаковые сообщения чата в (xN)", true);
    public final RunSetting openCatalog = new RunSetting("Каталог AutoBuy", "Открыть вкладку AutoBuy с предметами и пресетами",
            () -> mc.execute(ClickGuiScreen::openOnAutoBuy));
    public final RunSetting parsePricesNow = new RunSetting("Парсить цены", "Запустить/остановить автопарсер цен аукциона",
            () -> {
                if (AutoBuyEngine.INSTANCE.parser.isActive()) {
                    AutoBuyEngine.INSTANCE.parser.stop();
                } else {
                    AutoBuyEngine.INSTANCE.parser.start();
                }
            });
    public final RunSetting resetStatsBtn = new RunSetting("Сброс статистики", "Обнулить статистику покупок и продаж",
            () -> AutoBuyConfig.cfg().resetStats());

    public AutoBuyModule() {
        super("AutoBuy", "Автоматическая покупка и перепродажа предметов на аукционе", Category.OTHER);
        instance = this;

        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        autoRefresh.setValue(cfg.autoRefresh);
        autoSell.setValue(cfg.autoSell);
        autoRelist.setValue(cfg.autoRelist);
        autoParse.setValue(cfg.autoParse);
        safeMode.setValue(cfg.safeMode);
        armorDurability.setValue((double) cfg.armorDurabilityPercent);
        autoPay.setValue(cfg.autoPay);
        autoPayNick.setText(cfg.autoPayNick != null ? cfg.autoPayNick : "");
        autoPayAmount.setValue(Math.max(1_000_000L, Math.min(100_000_000L, cfg.autoPayAmount)) / 1_000_000.0);
        autoPayKeep.setValue(Math.max(0L, Math.min(100_000_000L, cfg.autoPayKeepBalance)) / 1_000_000.0);
        sellDiscount.setValue((double) cfg.sellDiscountPercent);
        minPrice.setValue((double) cfg.minPrice);
        compactChat.setValue(cfg.compactChat);

        autoPayAmount.visibleWhen(autoPay::getValue);
        autoPayKeep.visibleWhen(autoPay::getValue);

        addSetting(autoRefresh);
        addSetting(autoSell);
        addSetting(autoRelist);
        addSetting(autoParse);
        addSetting(safeMode);
        addSetting(armorDurability);
        addSetting(autoPayNick);
        addSetting(autoPay);
        addSetting(autoPayAmount);
        addSetting(autoPayKeep);
        addSetting(sellDiscount);
        addSetting(minPrice);
        addSetting(compactChat);
        addSetting(openCatalog);
        addSetting(parsePricesNow);
        addSetting(resetStatsBtn);
    }

    public static AutoBuyModule getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        cfg.enabled = true;
        syncSettings();
        AutoBuyConfig.save();
    }

    @Override
    public void onDisable() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        cfg.enabled = false;
        AutoBuyConfig.save();
    }

    public void syncSettings() {
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (cfg.enabled != isEnabled()) {
            setEnabled(cfg.enabled);
        }

        boolean changed = false;
        if (cfg.autoRefresh != autoRefresh.getValue()) {
            cfg.autoRefresh = autoRefresh.getValue();
            changed = true;
        }
        if (cfg.autoSell != autoSell.getValue()) {
            cfg.autoSell = autoSell.getValue();
            if (!cfg.autoSell) AutoBuyEngine.INSTANCE.seller.stop();
            changed = true;
        }
        if (cfg.autoRelist != autoRelist.getValue()) {
            cfg.autoRelist = autoRelist.getValue();
            changed = true;
        }
        if (cfg.autoParse != autoParse.getValue()) {
            cfg.autoParse = autoParse.getValue();
            changed = true;
        }
        if (cfg.safeMode != safeMode.getValue()) {
            cfg.safeMode = safeMode.getValue();
            changed = true;
        }
        int armorDur = armorDurability.getIntValue();
        if (cfg.armorDurabilityPercent != armorDur) {
            cfg.armorDurabilityPercent = armorDur;
            changed = true;
        }
        if (cfg.autoPay != autoPay.getValue()) {
            cfg.autoPay = autoPay.getValue();
            changed = true;
        }
        String cleanNick = autoPayNick.getText().trim();
        if (!cleanNick.equals(cfg.autoPayNick)) {
            cfg.autoPayNick = cleanNick;
            changed = true;
        }
        long payAmount = Math.max(1_000_000L, Math.min(100_000_000L, Math.round(autoPayAmount.getValue() * 1_000_000.0)));
        if (cfg.autoPayAmount != payAmount) {
            cfg.autoPayAmount = payAmount;
            changed = true;
        }
        long payKeep = Math.max(0L, Math.min(100_000_000L, Math.round(autoPayKeep.getValue() * 1_000_000.0)));
        if (cfg.autoPayKeepBalance != payKeep) {
            cfg.autoPayKeepBalance = payKeep;
            changed = true;
        }
        int discount = sellDiscount.getIntValue();
        if (cfg.sellDiscountPercent != discount) {
            cfg.sellDiscountPercent = discount;
            changed = true;
        }
        long minPr = Math.round(minPrice.getValue());
        if (cfg.minPrice != minPr) {
            cfg.minPrice = minPr;
            changed = true;
        }
        if (cfg.compactChat != compactChat.getValue()) {
            cfg.compactChat = compactChat.getValue();
            changed = true;
        }
        if (changed) {
            AutoBuyConfig.save();
        }
    }
}
