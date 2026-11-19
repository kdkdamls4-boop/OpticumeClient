package dev.opticumeclient.client.autobuy;

import dev.opticumeclient.client.util.ChatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPay {
    private static final Minecraft mc = Minecraft.getInstance();

    private int paymentCooldownTicks = 0;
    private int commandRequestCooldownTicks = 0;
    private int warnNickCooldownTicks = 0;
    private int scoreboardCheckTicks = 0;
    private long lastKnownBalance = -1L;
    private long lastPaymentTime = 0L;
    private volatile boolean waitingForMoneyResponse = false;

    public boolean isWaitingForMoneyResponse() {
        return this.waitingForMoneyResponse;
    }

    public void setWaitingForMoneyResponse(boolean waiting) {
        this.waitingForMoneyResponse = waiting;
    }

    public void tick() {
        if (mc.player == null || mc.player.connection == null) return;

        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (!cfg.autoPay) {
            this.paymentCooldownTicks = 0;
            this.commandRequestCooldownTicks = 0;
            return;
        }

        if (this.paymentCooldownTicks > 0) this.paymentCooldownTicks--;
        if (this.commandRequestCooldownTicks > 0) this.commandRequestCooldownTicks--;
        if (this.warnNickCooldownTicks > 0) this.warnNickCooldownTicks--;

        String targetNick = cfg.autoPayNick != null ? cfg.autoPayNick.trim() : "";
        if (targetNick.isEmpty()) {
            if (this.warnNickCooldownTicks <= 0) {
                ChatUtils.sendClientMessage("§e[AutoPay] §cУкажите ник получателя в настройках AutoBuy или: §f.autobuy pay nick <ник>");
                this.warnNickCooldownTicks = 1200;
            }
            return;
        }

        if (mc.player.getName() != null && targetNick.equalsIgnoreCase(mc.player.getName().getString())) {
            return;
        }

        this.scoreboardCheckTicks++;
        if (this.scoreboardCheckTicks >= 20) {
            this.scoreboardCheckTicks = 0;
            Long sbBal = getScoreboardBalance();
            if (sbBal != null) {
                this.lastKnownBalance = sbBal;
                this.checkAndPay(sbBal);
                return;
            }
        }

        if (this.commandRequestCooldownTicks <= 0) {
            int intervalTicks = Math.max(100, cfg.autoPayIntervalSeconds * 20);
            this.commandRequestCooldownTicks = intervalTicks;
            this.waitingForMoneyResponse = true;
            mc.player.connection.sendCommand("money");
        }
    }

    public void onChatMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return;
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (!cfg.autoPay) return;

        Long bal = parseBalance(rawMessage);
        if (bal != null) {
            this.lastKnownBalance = bal;
            this.checkAndPay(bal);
        }
    }

    public void onBalance(long balance) {
        this.lastKnownBalance = balance;
        this.checkAndPay(balance);
    }

    public void forcePay() {
        this.commandRequestCooldownTicks = 0;
        this.paymentCooldownTicks = 0;
        Long sbBal = getScoreboardBalance();
        if (sbBal != null) {
            this.lastKnownBalance = sbBal;
            this.checkAndPay(sbBal);
        } else if (mc.player != null && mc.player.connection != null) {
            this.waitingForMoneyResponse = true;
            mc.player.connection.sendCommand("money");
        }
    }

    public synchronized void checkAndPay(long balance) {
        if (mc.player == null || mc.player.connection == null) return;
        AutoBuyConfig cfg = AutoBuyConfig.cfg();
        if (!cfg.autoPay) return;

        String targetNick = cfg.autoPayNick != null ? cfg.autoPayNick.replace("\"", "").replace("'", "").trim() : "";
        if (targetNick.isEmpty()) return;

        if (mc.player.getName() != null && targetNick.equalsIgnoreCase(mc.player.getName().getString())) {
            return;
        }

        if (this.paymentCooldownTicks > 0) return;
        if (System.currentTimeMillis() - this.lastPaymentTime < 3500L) return;

        long keepBalance = Math.max(0L, Math.min(100_000_000L, cfg.autoPayKeepBalance));
        long transferAmount = Math.max(1_000_000L, Math.min(100_000_000L, cfg.autoPayAmount));
        if (balance - keepBalance < transferAmount) {
            return;
        }

        mc.player.connection.sendCommand("pay " + targetNick + " " + transferAmount);
        this.paymentCooldownTicks = 80;
        this.lastPaymentTime = System.currentTimeMillis();
        this.lastKnownBalance = balance - transferAmount;

        ChatUtils.sendClientMessage("§a[AutoPay] Переведено §f" + formatMoney(transferAmount)
                + " §aигроку §f" + targetNick + " §7(Остаток: " + formatMoney(balance - transferAmount) + ")");
    }

    public Long getScoreboardBalance() {
        if (mc.level == null) return null;
        try {
            Scoreboard scoreboard = mc.level.getScoreboard();
            if (scoreboard == null) return null;
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective == null) return null;

            Collection<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective);
            if (entries == null || entries.isEmpty()) return null;

            for (PlayerScoreEntry entry : entries) {
                if (entry == null) continue;
                PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                Component baseName = entry.display() != null ? entry.display() : Component.literal(entry.owner());
                Component decorated = team != null ? PlayerTeam.formatNameForTeam(team, baseName) : baseName;
                String line = decorated != null ? decorated.getString() : "";
                Long b = parseBalance(line);
                if (b != null) return b;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Long parseBalance(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.replaceAll("(?i)§[0-9a-fk-or]", "").trim();

        Pattern kw = Pattern.compile("(?iu)(?:(?:ваш\\s+)?баланс|balance|на\\s+счету|на\\s+счете|на\\s+счёте|(?:ваш\\s+)?счёт|(?:ваш\\s+)?счет|монеты|коины|coins|деньги|money)");
        Matcher kwMatcher = kw.matcher(clean);
        if (!kwMatcher.find()) return null;

        String afterKw = clean.substring(kwMatcher.end());
        Pattern numPattern = Pattern.compile("(?iu)([0-9]+(?:[.,][0-9]+)?(?:[\\s\\u00A0._,][0-9]{3})*(?:[.,][0-9]+)?)\\s*(кк|kk|млн|m|м|k|к|тыс)?");
        Matcher m = numPattern.matcher(afterKw);
        if (!m.find()) {
            m = numPattern.matcher(clean);
            if (!m.find()) return null;
        }

        String numStr = m.group(1);
        String suffix = m.group(2);
        if (numStr == null || numStr.isEmpty()) return null;

        long multiplier = 1L;
        if (suffix != null) {
            String s = suffix.toLowerCase(Locale.ROOT);
            if (s.equals("кк") || s.equals("kk") || s.equals("m") || s.equals("м") || s.equals("млн")) {
                multiplier = 1_000_000L;
            } else if (s.equals("k") || s.equals("к") || s.equals("тыс")) {
                multiplier = 1_000L;
            }
        }

        try {
            String numClean = numStr.replace('\u00A0', ' ').trim();
            if (multiplier > 1L) {
                String normalized = numClean.replaceAll("[\\s]", "").replace(',', '.');
                return (long) Math.floor(Double.parseDouble(normalized) * multiplier);
            } else {
                String integerPart = numClean.replaceAll("[.,]\\d{1,2}$", "").replaceAll("[^0-9]", "");
                if (integerPart.isEmpty()) return null;
                return Long.parseLong(integerPart);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String formatMoney(long v) {
        if (v >= 1_000_000L && v % 1_000_000L == 0L) {
            return (v / 1_000_000L) + "кк";
        }
        return String.format(Locale.ROOT, "%,d", v).replace(',', ' ');
    }
}
