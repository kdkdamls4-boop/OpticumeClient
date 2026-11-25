package dev.opticumeclient.client.modules.other;

import dev.opticumeclient.client.friend.FriendManager;
import dev.opticumeclient.client.modules.Category;
import dev.opticumeclient.client.modules.Module;
import dev.opticumeclient.client.setting.BooleanSetting;
import dev.opticumeclient.client.setting.TextSetting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks your own nickname (and optionally friends' nicknames) across chat, Tab list,
 * NameTags, TargetHUD and StaffList without altering outgoing server commands.
 */
public class NameProtectModule extends Module {
    private static NameProtectModule instance;

    private final TextSetting alias = new TextSetting("Alias", "Подменный ник для себя", "OpticumeUser");
    private final BooleanSetting hideFriends = new BooleanSetting("Hide Friends", "Скрывать ники друзей из FriendManager", false);
    private final TextSetting friendAlias = new TextSetting("Friend Alias", "Подменный ник для друзей", "Friend")
            .visibleWhen(hideFriends::isEnabled);
    private final BooleanSetting maskChat = new BooleanSetting("Mask Chat", "Подменять ники в сообщениях чата", true);
    private final BooleanSetting maskTab = new BooleanSetting("Mask Tab", "Подменять ники в Tab-листе", true);
    private final BooleanSetting maskHud = new BooleanSetting("Mask HUD", "Подменять ники в HUD, NameTags и TargetHUD", true);

    public NameProtectModule() {
        super("NameProtect", "Скрывает ваш ник и ники друзей в чате, Tab и HUD", Category.OTHER);
        instance = this;
        addSetting(alias);
        addSetting(hideFriends);
        addSetting(friendAlias);
        addSetting(maskChat);
        addSetting(maskTab);
        addSetting(maskHud);
    }

    public static NameProtectModule getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean shouldMaskChat() {
        return isActive() && instance.maskChat.isEnabled();
    }

    public static boolean shouldMaskTab() {
        return isActive() && instance.maskTab.isEnabled();
    }

    public static boolean shouldMaskHud() {
        return isActive() && instance.maskHud.isEnabled();
    }

    public static String protect(String text) {
        if (text == null || text.isEmpty() || !isActive()) {
            return text;
        }
        return instance.replaceNames(text);
    }

    public static String protectHudName(String text) {
        if (text == null || text.isEmpty() || !shouldMaskHud()) {
            return text;
        }
        return instance.replaceNames(text);
    }

    public static Component protectComponent(Component component) {
        if (component == null || !isActive()) {
            return component;
        }
        String raw = component.getString();
        String replaced = instance.replaceNames(raw);
        if (raw.equals(replaced)) {
            return component;
        }
        return replaceComponentPreservingSiblings(component);
    }

    private static Component replaceComponentPreservingSiblings(Component component) {
        String raw = component.getString();
        if (component.getSiblings().isEmpty()) {
            String replaced = instance.replaceNames(raw);
            if (replaced.equals(raw)) {
                return component;
            }
            return Component.literal(replaced).withStyle(component.getStyle());
        }
        // Check if leaf replacement changes everything cleanly; otherwise collapse styled fallback
        MutableComponent rebuilt = Component.empty().withStyle(component.getStyle());
        String selfOwn = extractDirectText(component);
        if (!selfOwn.isEmpty()) {
            rebuilt.append(Component.literal(instance.replaceNames(selfOwn)).withStyle(component.getStyle()));
        }
        for (Component sibling : component.getSiblings()) {
            rebuilt.append(replaceComponentPreservingSiblings(sibling));
        }
        // Handle cases where a name was split across adjacent siblings
        if (!rebuilt.getString().equals(instance.replaceNames(raw))) {
            return Component.literal(instance.replaceNames(raw)).withStyle(component.getStyle());
        }
        return rebuilt;
    }

    private static String extractDirectText(Component component) {
        StringBuilder sb = new StringBuilder();
        component.getContents().visit(str -> {
            sb.append(str);
            return java.util.Optional.empty();
        });
        return sb.toString();
    }

    private String replaceNames(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        String selfName = getSelfName();
        String selfReplacement = getAliasValue();
        if (selfName != null && !selfName.isBlank()) {
            result = replaceIgnoreCaseWordAware(result, selfName, selfReplacement);
        }
        if (hideFriends.isEnabled()) {
            String fReplacement = getFriendAliasValue();
            Set<String> friends = FriendManager.getInstance().getFriends();
            if (friends != null && !friends.isEmpty()) {
                for (String friend : friends) {
                    if (friend != null && !friend.isBlank() && !friend.equalsIgnoreCase(selfName)) {
                        result = replaceIgnoreCaseWordAware(result, friend, fReplacement);
                    }
                }
            }
        }
        return result;
    }

    private static String replaceIgnoreCaseWordAware(String text, String target, String replacement) {
        Pattern pattern = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(target) + "(?![A-Za-z0-9_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private String getSelfName() {
        if (mc == null) return null;
        if (mc.player != null) {
            return mc.player.getGameProfile().name();
        }
        if (mc.getUser() != null) {
            return mc.getUser().getName();
        }
        return null;
    }

    private String getAliasValue() {
        String val = alias.getValue();
        return (val == null || val.isBlank()) ? "OpticumeUser" : val;
    }

    private String getFriendAliasValue() {
        String val = friendAlias.getValue();
        return (val == null || val.isBlank()) ? "Friend" : val;
    }
}
