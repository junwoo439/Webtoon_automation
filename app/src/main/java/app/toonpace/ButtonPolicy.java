package app.toonpace;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Episode navigation labels, not arbitrary substring matches in dialogue. */
public final class ButtonPolicy {
    private static final Pattern NEXT = Pattern.compile(
        "^[▶▷〉>›→»]*(무료)?(다음(회차|에피소드|화|회|편)(으로|로)?(무료로|무료)?"
        + "(보러가기|읽으러가기|넘어가기|이동하기|계속보기|보기|읽기|이동)?"
        + "|next(episode|chapter))([:·\\-]?(제)?[0-9]{1,5}(회차|화|회|편))?"
        + "(\\(무료\\)|무료)?[〉>›→»▶▷❯❱⟩》]*$");
    private static final Pattern PREVIOUS = Pattern.compile(
        "^[〈<‹←«◀◁❮❰⟨《]*(이전(회차|화|회|편)(보기|읽기)?|previous(episode|chapter))$");
    private static final Pattern PROGRESS = Pattern.compile(
        "^(?:\\[|\\()?([0-9]{1,5})/(?:총)?([0-9]{1,5})(?:화|회)?(?:\\]|\\))?$");
    static final class EpisodeProgress {
        final int current, total;
        EpisodeProgress(int current, int total) { this.current = current; this.total = total; }
        boolean hasNext() { return current < total; }
    }
    private static final Pattern NEXT_ID = Pattern.compile(
        "^(btn|button|iv|img|image|tv|text|action)?next(episode|chapter)(btn|button|icon|text)?$");
    private static final Pattern PAY = Pattern.compile(
        "구매|결제|유료|대여|소장|캐시|쿠키|충전|이용권|잠금|성인\\s*인증|본인\\s*인증|"
        + "로그인|광고\\s*보고|광고\\s*시청|purchase|payment|subscribe|unlock|buy|rent|sign\\s*in", Pattern.CASE_INSENSITIVE);
    private ButtonPolicy() {}
    private static String clean(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT).replaceAll("[\\p{Cf}]", "").replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
    public static boolean isNext(String s) {
        return !isBlocked(s) && NEXT.matcher(clean(s).replace(" ", "")).matches();
    }
    public static boolean isNextResourceId(String id) {
        String value = clean(id);
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[_\\-]", "");
        return NEXT_ID.matcher(value).matches();
    }
    public static boolean isEpisodeFooter(String text) {
        return clean(text).replace(" ", "").matches("이작품과함께보는(웹툰|작품)i?[\\p{P}\\p{S}]*");
    }
    public static boolean isEndMarker(String text) { return clean(text).equals("끝"); }
    public static boolean isPrevious(String text) {
        return PREVIOUS.matcher(clean(text).replace(" ", "")).matches();
    }
    static EpisodeProgress episodeProgress(String text) {
        Matcher match = PROGRESS.matcher(clean(text).replace(" ", ""));
        if (!match.matches()) return null;
        int current = Integer.parseInt(match.group(1)), total = Integer.parseInt(match.group(2));
        return current > 0 && current <= total ? new EpisodeProgress(current, total) : null;
    }
    public static boolean isBlocked(String s) { return PAY.matcher(clean(s)).find(); }
}
