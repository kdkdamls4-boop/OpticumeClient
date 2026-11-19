package dev.opticumeclient.client.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.opticumeclient.client.autobuy.AutoBuyConfig;
import dev.opticumeclient.client.autobuy.AutoBuyEngine;
import dev.opticumeclient.client.autobuy.AutoBuyReport;
import dev.opticumeclient.client.command.ClientCommand;
import dev.opticumeclient.client.gui.ClickGuiScreen;
import dev.opticumeclient.client.modules.other.AutoBuyModule;
import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Locale;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

/**
 * {@code .autobuy} / {@code .ab} command for toggling, stats, blacklist management, and AutoPay.
 */
public final class AutoBuyCommand extends ClientCommand {
    private static final Minecraft mc = Minecraft.getInstance();

    public AutoBuyCommand() {
        super("autobuy", "Управление AutoBuy (.ab | toggle | stats | blacklist | pay)", "ab");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(context -> {
            openGui();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("items").executes(context -> {
            openGui();
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("gui").executes(context -> {
            openGui();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("toggle").executes(context -> {
            AutoBuyConfig cfg = AutoBuyConfig.cfg();
            cfg.enabled = !cfg.enabled;
            AutoBuyModule mod = AutoBuyModule.getInstance();
            if (mod != null) mod.setEnabled(cfg.enabled);
            AutoBuyConfig.save();
            ChatUtils.sendClientMessage("AutoBuy: " + (cfg.enabled ? "§aВКЛ" : "§cВЫКЛ"));
            return SINGLE_SUCCESS;
        }));

        for (String compactCmd : new String[]{"compact", "antispam"}) {
            builder.then(literal(compactCmd).executes(context -> {
                AutoBuyConfig cfg = AutoBuyConfig.cfg();
                cfg.compactChat = !cfg.compactChat;
                AutoBuyModule mod = AutoBuyModule.getInstance();
                if (mod != null) mod.compactChat.setValue(cfg.compactChat);
                AutoBuyConfig.save();
                ChatUtils.sendClientMessage("§6[AutoBuy] Анти-спам чата: " + (cfg.compactChat ? "§aВКЛ" : "§cВЫКЛ"));
                return SINGLE_SUCCESS;
            }));
        }

        builder.then(literal("stats").executes(context -> {
            AutoBuyConfig cfg = AutoBuyConfig.cfg();
            long profit = cfg.totalEarned - cfg.totalSpent;
            ChatUtils.sendClientMessage("§e[AutoBuy Отчёт]");
            ChatUtils.sendClientMessage("§7Потрачено: §c" + AutoBuyReport.formatMoney(cfg.totalSpent) + " §7(куплено: " + cfg.totalBoughtCount + ")");
            ChatUtils.sendClientMessage("§7Заработано: §a" + AutoBuyReport.formatMoney(cfg.totalEarned) + " §7(продано: " + cfg.totalSoldCount + ")");
            ChatUtils.sendClientMessage("§7Профит: " + (profit >= 0 ? "§a+" : "§c") + AutoBuyReport.formatMoney(profit));
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("resetstats").executes(context -> {
            AutoBuyConfig.cfg().resetStats();
            ChatUtils.sendClientMessage("§a[AutoBuy] Статистика сброшена!");
            return SINGLE_SUCCESS;
        }));

        for (String alias : new String[]{"nick", "blacklist", "ignore", "ban"}) {
            builder.then(createNickSubcommand(alias));
        }

        builder.then(createPaySubcommand("pay"));
        builder.then(createPaySubcommand("autopay"));
    }

    private LiteralArgumentBuilder<SharedSuggestionProvider> createNickSubcommand(String name) {
        return literal(name)
                .then(literal("add").then(argument("player", string())
                        .suggests((ctx, b) -> suggestOnlinePlayers(b))
                        .executes(ctx -> {
                            String player = cleanPlayer(getString(ctx, "player"));
                            if (player.isEmpty()) return SINGLE_SUCCESS;
                            AutoBuyConfig cfg = AutoBuyConfig.cfg();
                            if (cfg.ignored(player)) {
                                ChatUtils.sendClientMessage("§6[AutoBuy] §cИгрок §f" + player + " §cуже в ЧС!");
                            } else {
                                cfg.addBan(player);
                                ChatUtils.sendClientMessage("§6[AutoBuy] §aИгрок §f" + player + " §aдобавлен в ЧС!");
                            }
                            return SINGLE_SUCCESS;
                        })))
                .then(literal("remove").then(argument("player", string())
                        .suggests((ctx, b) -> suggestBanned(b))
                        .executes(ctx -> removeBanned(getString(ctx, "player")))))
                .then(literal("del").then(argument("player", string())
                        .suggests((ctx, b) -> suggestBanned(b))
                        .executes(ctx -> removeBanned(getString(ctx, "player")))))
                .then(literal("list").executes(ctx -> {
                    AutoBuyConfig cfg = AutoBuyConfig.cfg();
                    if (cfg.banList.isEmpty()) {
                        ChatUtils.sendClientMessage("§6[AutoBuy] §eЧёрный список пуст.");
                    } else {
                        ChatUtils.sendClientMessage("§6[AutoBuy] ЧС (§f" + cfg.banList.size() + "§6): §f" + String.join(", ", cfg.banList));
                    }
                    return SINGLE_SUCCESS;
                }))
                .then(literal("clear").executes(ctx -> {
                    AutoBuyConfig.cfg().banList.clear();
                    AutoBuyConfig.save();
                    ChatUtils.sendClientMessage("§6[AutoBuy] §aЧёрный список очищен!");
                    return SINGLE_SUCCESS;
                }))
                .then(argument("directPlayer", string())
                        .suggests((ctx, b) -> suggestOnlinePlayers(b))
                        .executes(ctx -> {
                            String player = cleanPlayer(getString(ctx, "directPlayer"));
                            if (!player.isEmpty()) {
                                AutoBuyConfig cfg = AutoBuyConfig.cfg();
                                if (!cfg.ignored(player)) {
                                    cfg.addBan(player);
                                    ChatUtils.sendClientMessage("§6[AutoBuy] §aИгрок §f" + player + " §aдобавлен в ЧС!");
                                }
                            }
                            return SINGLE_SUCCESS;
                        }));
    }

    private static int removeBanned(String raw) {
        String player = cleanPlayer(raw);
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (cfg.ignored(player)) {
            cfg.removeBan(player);
            ChatUtils.sendClientMessage("§6[AutoBuy] §aИгрок §f" + player + " §aудалён из ЧС!");
        } else {
            ChatUtils.sendClientMessage("§6[AutoBuy] §cИгрок §f" + player + " §cне найден в ЧС!");
        }
        return SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<SharedSuggestionProvider> createPaySubcommand(String name) {
        return literal(name)
                .executes(ctx -> {
                    AutoBuyConfig cfg = AutoBuyConfig.cfg();
                    ChatUtils.sendClientMessage("§6[AutoPay] " + (cfg.autoPay ? "§aВКЛ" : "§cВЫКЛ")
                            + " §7| Получатель: §f" + (cfg.autoPayNick.isEmpty() ? "—" : cfg.autoPayNick)
                            + " §7| Сумма: §f" + (cfg.autoPayAmount / 1_000_000L) + "кк"
                            + " §7| Остаток: §f" + (cfg.autoPayKeepBalance / 1_000_000L) + "кк");
                    return SINGLE_SUCCESS;
                })
                .then(literal("toggle").executes(ctx -> {
                    AutoBuyConfig cfg = AutoBuyConfig.cfg();
                    cfg.autoPay = !cfg.autoPay;
                    AutoBuyModule mod = AutoBuyModule.getInstance();
                    if (mod != null) mod.autoPay.setValue(cfg.autoPay);
                    AutoBuyConfig.save();
                    ChatUtils.sendClientMessage("§6[AutoPay] Авто-перевод: " + (cfg.autoPay ? "§aВКЛ" : "§cВЫКЛ"));
                    return SINGLE_SUCCESS;
                }))
                .then(literal("nick").then(argument("recipient", string())
                        .suggests((ctx, b) -> suggestOnlinePlayers(b))
                        .executes(ctx -> {
                            String recipient = cleanPlayer(getString(ctx, "recipient"));
                            if (!recipient.isEmpty()) {
                                AutoBuyConfig cfg = AutoBuyConfig.cfg();
                                cfg.autoPayNick = recipient;
                                AutoBuyModule mod = AutoBuyModule.getInstance();
                                if (mod != null) mod.autoPayNick.setText(recipient);
                                AutoBuyConfig.save();
                                ChatUtils.sendClientMessage("§6[AutoPay] Получатель: §f" + recipient);
                            }
                            return SINGLE_SUCCESS;
                        })))
                .then(literal("amount").then(argument("value", string()).executes(ctx -> {
                    Long amount = parseMoneyInput(getString(ctx, "value"));
                    if (amount == null || amount < 1_000_000L || amount > 100_000_000L) {
                        ChatUtils.sendError("[AutoPay] Укажите сумму от 1кк до 100кк!");
                        return SINGLE_SUCCESS;
                    }
                    AutoBuyConfig cfg = AutoBuyConfig.cfg();
                    cfg.autoPayAmount = amount;
                    AutoBuyModule mod = AutoBuyModule.getInstance();
                    if (mod != null) mod.autoPayAmount.setValue(amount / 1_000_000.0);
                    AutoBuyConfig.save();
                    ChatUtils.sendClientMessage("§6[AutoPay] Сумма перевода: §f" + (amount / 1_000_000L) + "кк");
                    return SINGLE_SUCCESS;
                })))
                .then(literal("keep").then(argument("value", string()).executes(ctx -> {
                    Long keep = parseMoneyInput(getString(ctx, "value"));
                    if (keep == null || keep < 0L || keep > 100_000_000L) {
                        ChatUtils.sendError("[AutoPay] Укажите остаток от 0кк до 100кк!");
                        return SINGLE_SUCCESS;
                    }
                    AutoBuyConfig cfg = AutoBuyConfig.cfg();
                    cfg.autoPayKeepBalance = keep;
                    AutoBuyModule mod = AutoBuyModule.getInstance();
                    if (mod != null) mod.autoPayKeep.setValue(keep / 1_000_000.0);
                    AutoBuyConfig.save();
                    ChatUtils.sendClientMessage("§6[AutoPay] Остаток баланса: §f" + (keep / 1_000_000L) + "кк");
                    return SINGLE_SUCCESS;
                })))
                .then(literal("send").executes(ctx -> {
                    AutoBuyEngine.INSTANCE.autoPay.forcePay();
                    ChatUtils.sendClientMessage("§6[AutoPay] Запрошен перевод баланса...");
                    return SINGLE_SUCCESS;
                }))
                .then(literal("now").executes(ctx -> {
                    AutoBuyEngine.INSTANCE.autoPay.forcePay();
                    ChatUtils.sendClientMessage("§6[AutoPay] Запрошен перевод баланса...");
                    return SINGLE_SUCCESS;
                }));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestOnlinePlayers(
            com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        if (mc.getConnection() != null) {
            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
            for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
                if (entry != null && entry.getProfile() != null && entry.getProfile().name() != null) {
                    String pName = entry.getProfile().name();
                    if (pName.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(pName);
                }
            }
        }
        return b.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBanned(
            com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase(Locale.ROOT);
        for (String s : AutoBuyConfig.cfg().banList) {
            if (s != null && s.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(s);
        }
        return b.buildFuture();
    }

    private static String cleanPlayer(String raw) {
        return raw == null ? "" : raw.replace("\"", "").replace("'", "").trim();
    }

    public static Long parseMoneyInput(String str) {
        if (str == null || str.isBlank()) return null;
        String clean = str.replace("\"", "").replace("'", "").trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (clean.endsWith("kk") || clean.endsWith("кк") || clean.endsWith("m") || clean.endsWith("м") || clean.endsWith("млн")) {
            multiplier = 1_000_000L;
            clean = clean.replaceAll("[a-zа-я]", "");
        } else if (clean.endsWith("k") || clean.endsWith("к") || clean.endsWith("тыс")) {
            multiplier = 1_000L;
            clean = clean.replaceAll("[a-zа-я]", "");
        }
        clean = clean.replace(',', '.').replaceAll("[\\s\\u00A0]", "");
        try {
            double val = Double.parseDouble(clean);
            if (multiplier == 1L && val <= 100.0) {
                val *= 1_000_000.0;
            } else {
                val *= multiplier;
            }
            return (long) Math.round(val);
        } catch (Exception e) {
            return null;
        }
    }

    private static void openGui() {
        mc.execute(ClickGuiScreen::openOnAutoBuy);
    }
}
