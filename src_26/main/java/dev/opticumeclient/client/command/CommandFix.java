package dev.opticumeclient.client.command;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Bidirectional RU <-> EN QWERTY keyboard layout converter + Russian search synonyms
 * for CommandFix (.рудз / юрудз -> .help) and ClickGUI SystemSearchModal.
 */
public final class CommandFix {
    private static final String RU_CHARS = "йцукенгшщзхъфывапролджэячсмитьбюёЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮЁ";
    private static final String EN_CHARS = "qwertyuiop[]asdfghjkl;'zxcvbnm,.`QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>~";

    private static final Map<Character, Character> RU_TO_EN = new HashMap<>();
    private static final Map<Character, Character> EN_TO_RU = new HashMap<>();
    private static final Map<String, String[]> RU_SYNONYMS = new HashMap<>();

    static {
        for (int i = 0; i < RU_CHARS.length(); i++) {
            RU_TO_EN.put(RU_CHARS.charAt(i), EN_CHARS.charAt(i));
            EN_TO_RU.put(EN_CHARS.charAt(i), RU_CHARS.charAt(i));
        }

        RU_SYNONYMS.put("аура", new String[]{"opticumeaura", "legitaura", "aura"});
        RU_SYNONYMS.put("киллка", new String[]{"opticumeaura", "legitaura"});
        RU_SYNONYMS.put("килла", new String[]{"opticumeaura", "legitaura"});
        RU_SYNONYMS.put("триггер", new String[]{"triggerbot"});
        RU_SYNONYMS.put("тотем", new String[]{"autototem", "totem"});
        RU_SYNONYMS.put("свап", new String[]{"autoswap"});
        RU_SYNONYMS.put("кристалл", new String[]{"autocrystal"});
        RU_SYNONYMS.put("якор", new String[]{"autoanchor"});
        RU_SYNONYMS.put("булав", new String[]{"macehelper"});
        RU_SYNONYMS.put("зель", new String[]{"autopotion", "potstimer", "potion"});
        RU_SYNONYMS.put("лук", new String[]{"bowaimbot"});
        RU_SYNONYMS.put("жемчуг", new String[]{"middleclickpearl", "targetpearl"});
        RU_SYNONYMS.put("перл", new String[]{"middleclickpearl", "targetpearl"});
        RU_SYNONYMS.put("атмосфер", new String[]{"ambience", "atmosphere"});
        RU_SYNONYMS.put("небо", new String[]{"ambience"});
        RU_SYNONYMS.put("смерт", new String[]{"killeffect"});
        RU_SYNONYMS.put("киллэффект", new String[]{"killeffect"});
        RU_SYNONYMS.put("чамс", new String[]{"popchams", "glowesp"});
        RU_SYNONYMS.put("свечен", new String[]{"glowesp", "targetesp"});
        RU_SYNONYMS.put("подсвет", new String[]{"glowesp", "blockesp", "playeresp", "itemesp"});
        RU_SYNONYMS.put("блок", new String[]{"blockesp", "blockoverlay"});
        RU_SYNONYMS.put("инвент", new String[]{"inventoryhud", "itemscroller"});
        RU_SYNONYMS.put("шалкер", new String[]{"shulkerpreview"});
        RU_SYNONYMS.put("таб", new String[]{"bettertab"});
        RU_SYNONYMS.put("блюр", new String[]{"motionblur", "interface"});
        RU_SYNONYMS.put("размыт", new String[]{"motionblur", "interface"});
        RU_SYNONYMS.put("фейер", new String[]{"fireworkesp", "superfirework"});
        RU_SYNONYMS.put("тнт", new String[]{"tnttimer"});
        RU_SYNONYMS.put("динами", new String[]{"tnttimer"});
        RU_SYNONYMS.put("ник", new String[]{"nameprotect", "nametags"});
        RU_SYNONYMS.put("паник", new String[]{"panic"});
        RU_SYNONYMS.put("обс", new String[]{"capturebypass"});
        RU_SYNONYMS.put("стрим", new String[]{"capturebypass", "nameprotect"});
        RU_SYNONYMS.put("запис", new String[]{"capturebypass"});
        RU_SYNONYMS.put("физик", new String[]{"itemphysics"});
        RU_SYNONYMS.put("трапк", new String[]{"objectinfo"});
        RU_SYNONYMS.put("пласт", new String[]{"objectinfo"});
        RU_SYNONYMS.put("спринт", new String[]{"sprint"});
        RU_SYNONYMS.put("скорост", new String[]{"speed"});
        RU_SYNONYMS.put("элитр", new String[]{"elytrahelper", "superfirework"});
        RU_SYNONYMS.put("аукц", new String[]{"auctionhelper", "autobuy"});
        RU_SYNONYMS.put("автобай", new String[]{"autobuy"});
        RU_SYNONYMS.put("бот", new String[]{"bots", "antibot"});
        RU_SYNONYMS.put("фуллбрайт", new String[]{"fullbright"});
        RU_SYNONYMS.put("гамма", new String[]{"fullbright"});
        RU_SYNONYMS.put("руки", new String[]{"viewmodel", "hands", "swinganimations"});
        RU_SYNONYMS.put("прицел", new String[]{"crosshair"});
        RU_SYNONYMS.put("зум", new String[]{"zoom"});
    }

    private CommandFix() {
    }

    public static String translateRuToEn(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(RU_TO_EN.getOrDefault(c, c));
        }
        return sb.toString();
    }

    public static String translateEnToRu(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(EN_TO_RU.getOrDefault(c, c));
        }
        return sb.toString();
    }

    /**
     * Checks whether a lowercase search query matches target string either directly,
     * via RU->EN layout transliteration, via EN->RU layout transliteration, or via Russian synonyms.
     */
    public static boolean matchesSearch(String queryLower, String targetLower) {
        if (queryLower == null || queryLower.isEmpty()) return true;
        if (targetLower == null || targetLower.isEmpty()) return false;
        if (targetLower.contains(queryLower)) return true;

        String ruToEn = translateRuToEn(queryLower).toLowerCase(Locale.ROOT);
        if (!ruToEn.equals(queryLower) && targetLower.contains(ruToEn)) {
            return true;
        }
        String enToRu = translateEnToRu(queryLower).toLowerCase(Locale.ROOT);
        if (!enToRu.equals(queryLower) && targetLower.contains(enToRu)) {
            return true;
        }

        for (Map.Entry<String, String[]> entry : RU_SYNONYMS.entrySet()) {
            String key = entry.getKey();
            if (queryLower.contains(key) || key.contains(queryLower)) {
                for (String alias : entry.getValue()) {
                    if (targetLower.contains(alias)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
