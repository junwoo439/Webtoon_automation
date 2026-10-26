package app.toonpace;

import java.util.ArrayList;
import java.util.List;

public final class BrowserEndPanelTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static NextButtonDetector.Label label(String text, int l, int t, int r, int b, int scale) {
        return new NextButtonDetector.Label(text, l*scale, t*scale, r*scale, b*scale, false, "글자 인식");
    }
    private static List<NextButtonDetector.Label> panel(int current, int total, int scale) {
        return new ArrayList<>(List.of(
            label("오류신고", 240, 200, 355, 230, scale),
            label("이미지 오류", 125, 330, 225, 355, scale),
            label("회차 오류", 262, 330, 342, 355, scale),
            label("기타 오류", 385, 330, 460, 355, scale),
            label("[ " + current + " / 총" + total + " ]", 238, 532, 354, 556, scale),
            label("끝", 277, 584, 316, 623, scale),
            label("다음화 ›", 252, 708, 337, 736, scale),
            label("‹ 이전화", 250, 800, 340, 829, scale),
            label("홈", 125, 892, 151, 922, scale),
            label("목록", 270, 892, 320, 922, scale),
            label("회차", 433, 892, 477, 922, scale)));
    }
    private static ButtonPolicy.EpisodeProgress detect(List<NextButtonDetector.Label> labels) {
        return NextButtonDetector.endPanel(labels, NextButtonDetector.choose(labels, 591, 1280), 591, 1280);
    }
    public static void main(String[] args) {
        // Text and bounds manually transcribed from the two phone screenshots.
        // These are decision-logic regressions, not ML Kit pixel/OCR measurements.
        for (int scale : new int[]{1, 2, 3}) for (int current : new int[]{17, 18}) {
            var labels = panel(current, 60, scale);
            var next = NextButtonDetector.choose(labels, 591*scale, 1280*scale);
            check(next != null && next.text.equals("다음화 ›"), "Select next, not report/previous/home/list controls");
            var found = NextButtonDetector.endPanel(labels, next, 591*scale, 1280*scale);
            check(found != null && found.current == current && found.total == 60 && found.hasNext(),
                "Episode progress plus aligned end/next/previous panel confirms end");
            check(!NextButtonDetector.hasFooterBelow(labels, next, 591*scale, 1280*scale),
                "Browser panel does not require a recommendation heading");
        }
        for (String s : new String[]{"다음화 >", "다음화 〉", "다음 화 ❯", "다음화 ❱", "다음화 ⟩", "다음화 》"})
            check(ButtonPolicy.isNext(s), "Recognize right chevron variant");
        for (String s : new String[]{"‹ 이전화", "< 이전 화", "〈 이전화", "❮ 이전회차", "이전 편", "Previous chapter"})
            check(ButtonPolicy.isPrevious(s), "Recognize previous as context only");
        for (String s : new String[]{"끝내기", "여기서 끝", "끝났어요", "구매", "회차 오류", "다음화"})
            check(!ButtonPolicy.isEndMarker(s), "Do not treat arbitrary text as end marker");
        for (String s : new String[]{"[17 / 총60]", "17/60", "［１８ / 총６０］", "(18 / 총60화)"})
            check(ButtonPolicy.episodeProgress(s) != null, "Normalize counter formatting");
        for (String s : new String[]{"0/60", "61/60", "17/0", "1/9999999999", "댓글 17/60", "17/60 광고", "17화", "17:60"})
            check(ButtonPolicy.episodeProgress(s) == null, "Reject invalid or unrelated counter");

        var base = panel(17, 60, 1);
        for (int index : new int[]{4, 5, 6, 7}) {
            var missing = new ArrayList<>(base); missing.remove(index);
            check(detect(missing) == null, "Every panel cue is required: " + index);
        }
        var dialogue = new ArrayList<>(base); dialogue.set(5, label("이야기는 끝", 277, 584, 360, 623, 1));
        check(detect(dialogue) == null, "Dialogue ending is not a footer");
        var differentColumn = new ArrayList<>(base); differentColumn.set(5, label("끝", 30, 584, 70, 623, 1));
        check(detect(differentColumn) == null, "End marker in another column rejected");
        var previousAbove = new ArrayList<>(base); previousAbove.set(7, label("이전화", 250, 450, 340, 479, 1));
        check(detect(previousAbove) == null, "Previous must follow next in this layout");
        var distantEnd = new ArrayList<>(base); distantEnd.set(5, label("끝", 277, 300, 316, 339, 1));
        check(detect(distantEnd) == null, "Distant markers do not form a panel");
        var conflict = new ArrayList<>(base); conflict.add(label("[18/총60]", 238, 532, 354, 556, 1));
        check(detect(conflict) == null, "Conflicting OCR/node counters require manual confirmation");
        var duplicate = new ArrayList<>(base); duplicate.add(label("[17/총60]", 238, 532, 354, 556, 1));
        check(detect(duplicate) != null, "Duplicate matching OCR/node counters are allowed");
        var fullButton = new ArrayList<>(base);
        fullButton.add(new NextButtonDetector.Label("다음화", 74, 676, 518, 765, true, "버튼 글자"));
        check(detect(fullButton) != null, "Works with full accessibility button bounds as well as OCR text");
        var last = detect(panel(60, 60, 1));
        check(last != null && !last.hasNext(), "Explicit final episode disables next even if stale button remains");
        check(NextButtonDetector.endPanel(base, null, 591, 1280) == null, "No target means no automatic next");
        System.out.println("PASS: " + checks + " browser end-panel checks");
    }
}
