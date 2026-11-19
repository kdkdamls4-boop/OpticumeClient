package dev.opticumeclient.client.autobuy;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String out = PriceParser.stripFormatting(s);
        out = Normalizer.normalize(out, Form.NFKC);
        out = replaceSmallCaps(out);
        out = out.toLowerCase(Locale.ROOT);
        out = out.replace('ё', 'е');
        out = out.replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        return out.replaceAll("\\s+", " ").trim();
    }

    private static String replaceSmallCaps(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            sb.append(mapSmallCap(s.charAt(i)));
        }
        return sb.toString();
    }

    private static char mapSmallCap(char c) {
        return switch (c) {
            case 'ǫ' -> 'Q';
            case 'ɢ' -> 'G';
            case 'ɪ' -> 'I';
            case 'ɴ' -> 'N';
            case 'ʀ' -> 'R';
            case 'ʏ' -> 'Y';
            case 'ʙ' -> 'B';
            case 'ʜ' -> 'H';
            case 'ʟ' -> 'L';
            case 'ғ' -> 'F';
            case 'ᴀ' -> 'A';
            case 'ᴄ' -> 'C';
            case 'ᴅ' -> 'D';
            case 'ᴇ' -> 'E';
            case 'ᴊ' -> 'J';
            case 'ᴋ' -> 'K';
            case 'ᴍ' -> 'M';
            case 'ᴏ' -> 'O';
            case 'ᴘ' -> 'P';
            case 'ᴛ' -> 'T';
            case 'ᴜ' -> 'U';
            case 'ᴠ' -> 'V';
            case 'ᴡ' -> 'W';
            case 'ᴢ' -> 'Z';
            case 'ꜰ' -> 'F';
            case 'ꜱ' -> 'S';
            default -> c;
        };
    }

    public static boolean nameMatches(String expected, String actual) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        String exp = normalize(expected);
        String act = normalize(actual);
        if (exp.isEmpty()) {
            return true;
        }
        return exp.equals(act) || act.contains(exp) || exp.contains(act) || containsWholeWord(act, exp);
    }

    public static boolean keywordIn(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        if (haystack == null) {
            return false;
        }
        String hay = normalize(haystack);
        String ndl = normalize(needle);
        if (ndl.isEmpty()) {
            return true;
        }
        return hay.contains(ndl) || containsWholeWord(hay, ndl);
    }

    private static boolean containsWholeWord(String text, String sub) {
        if (text.equals(sub)) {
            return true;
        }
        int from = 0;
        while (true) {
            int idx = text.indexOf(sub, from);
            if (idx < 0) {
                return false;
            }
            boolean startOk = idx == 0 || text.charAt(idx - 1) == ' ';
            int end = idx + sub.length();
            boolean endOk = end == text.length() || text.charAt(end) == ' ';
            if (startOk && endOk) {
                return true;
            }
            from = idx + 1;
        }
    }
}
