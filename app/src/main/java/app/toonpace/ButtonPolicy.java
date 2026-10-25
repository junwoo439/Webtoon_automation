package app.toonpace;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deliberately small positive list. A generic arrow or 'next' alone is not enough. */
public final class ButtonPolicy {
    private static final Pattern NEXT = Pattern.compile(
        "^(다음\\s*(화|회차|회|편|에피소드)(\\s*(보기|읽기))?|next\\s*(episode|chapter))(\\s*[〉>›→»])?$");
    private static final Pattern PAY = Pattern.compile(
        "구매|결제|유료|대여|소장|캐시|쿠키|충전|이용권|잠금|성인\\s*인증|본인\\s*인증|"
        + "로그인|purchase|payment|subscribe|unlock|buy|rent|sign\\s*in", Pattern.CASE_INSENSITIVE);
    private ButtonPolicy() {}
    private static String clean(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
    public static boolean isNext(String s) { return NEXT.matcher(clean(s)).matches(); }
    public static boolean isBlocked(String s) { return PAY.matcher(clean(s)).find(); }
}
