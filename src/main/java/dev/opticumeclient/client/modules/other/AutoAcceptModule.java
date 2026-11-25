package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.autobuy.PriceParser;
import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.NumberSetting;
import dev.opticumeclient.client.setting.TextSetting;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically accepts incoming /tpa teleport requests in chat
 * with Friends-Only filter, configurable delay, and custom accept command.
 */
public class AutoAcceptModule extends Module {
    private static AutoAcceptModule INSTANCE;

    private static final Pattern TPA_RU = Pattern.compile("(?iu)^(?:\\[[^\\]]+\\]\\s*)?(?:игрок\\s+)?([A-Za-z0-9_]{3,16})\\s+(?:просит|хочет|запрашивает|отправил)\\s+[^:<>]{0,48}(?:телепорт|тп)");
    private static final Pattern TPA_EN = Pattern.compile("(?i)^(?:\\[[^\\]]+\\]\\s*)?([A-Za-z0-9_]{3,16})\\s+has requested to teleport\\b");
    private static final Pattern PLAYER_CHAT_LIKE = Pattern.compile("(?i)(?:^<[^>]+>|^\\[[^\\]]*\\]\\s*[A-Za-z0-9_]{3,16}\\s*(?::|»|->)|\\b(?:сказал|пишет|whispered)\\b)");

    private final BooleanSetting friendsOnly = new BooleanSetting("FriendsOnly", "Принимать запросы только от друзей (.friend)", true);
    private final NumberSetting delayTicks = new NumberSetting("DelayTicks", "Задержка ответа (тики)", 6.0, 0.0, 40.0, 1.0);
    private final TextSetting command = new TextSetting("Command", "Команда принятия телепорта", "tpaccept");

    private int pendingDelay = -1;
    private String pendingSender = null;

    public AutoAcceptModule() {
        super("AutoAccept", "Автоматическое принятие запросов телепортации (/tpaccept)", Category.OTHER);
        INSTANCE = this;
        addSetting(friendsOnly);
        addSetting(delayTicks);
        addSetting(command);
    }

    public static AutoAcceptModule getInstance() {
        return INSTANCE;
    }

    @Override
    public void onDisable() {
        pendingDelay = -1;
        pendingSender = null;
    }

    public void onIncomingChat(String rawMessage) {
        if (!isEnabled() || mc.player == null || rawMessage == null || rawMessage.isBlank()) return;
        String clean = PriceParser.stripFormatting(rawMessage).trim();
        String lower = clean.toLowerCase(Locale.ROOT);

        if (!lower.contains("телепорт") && !lower.contains("teleport") && !lower.contains("/tpaccept") && !lower.contains("tpa")) {
            return;
        }

        if (PLAYER_CHAT_LIKE.matcher(clean).find()) {
            return;
        }
        String sender = extractSender(clean);
        if (sender == null) {
            return;
        }
        if (friendsOnly.isEnabled() && !FriendManager.getInstance().isFriend(sender)) {
            pendingDelay = -1;
            pendingSender = null;
            return;
        }
        if (sender.equalsIgnoreCase(mc.player.getGameProfile().name())) {
            return;
        }

        pendingSender = sender;
        pendingDelay = delayTicks.getIntValue();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.connection == null) {
            pendingDelay = -1;
            return;
        }
        if (pendingDelay < 0) return;
        if (pendingDelay > 0) {
            pendingDelay--;
            return;
        }
        if (friendsOnly.isEnabled() && (pendingSender == null || !FriendManager.getInstance().isFriend(pendingSender))) {
            pendingDelay = -1;
            pendingSender = null;
            return;
        }

        String cmd = command.getValue();
        if (cmd == null || cmd.isBlank()) cmd = "tpaccept";
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        mc.player.connection.sendCommand(cmd.trim());
        pendingDelay = -1;
        pendingSender = null;
    }

    private String extractSender(String clean) {
        Matcher mRu = TPA_RU.matcher(clean);
        if (mRu.find()) return mRu.group(1);

        Matcher mEn = TPA_EN.matcher(clean);
        if (mEn.find()) return mEn.group(1);

        return null;
    }
}
