package app.toonpace;

import java.util.List;

public final class NextButtonTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    private static NextButtonDetector.Label ocr(String text, int l, int t, int r, int b) {
        return new NextButtonDetector.Label(text, l, t, r, b, false, "글자 인식");
    }
    public static void main(String[] args) {
        for (String label : new String[]{"다음화 무료로 보기", "다음 화 무료로 보기", "다 음 화 무 료 로 보 기",
                "다음화 보기", "다음\n화 보기", "다음 회차로 이동", "다음 편으로 넘어가기", "다음화 보러가기",
                "다음화 보기 (무료)", "다음화 보기: 12화", "다음화 보기\n제３화", "다음\u200b화 보기", "다음 챕터"}) {
            if (label.equals("다음 챕터")) check(!ButtonPolicy.isNext(label), "Unknown label requires evidence");
            else check(ButtonPolicy.isNext(label), "Recognize supported label: " + label);
        }
        for (String label : new String[]{"이 작품을 원작소설로 보기", "다음화 구매하기", "다음화 무료로 보기 100 캐시",
                "다음 광고 보기", "광고 보고 다음화 보기", "다음화 보기 싫어", "다음화 보기 좋은 날", "다음", ">", "이전 화",
                "추천 다음화 보기", "다음 작품 보기", "로그인 후 다음화 보기", "다음화 보기 대여"})
            check(!ButtonPolicy.isNext(label), "Reject unrelated or paid action: " + label);
        for (String id : new String[]{"com.kakao.page:id/btn_next_episode", "test:id/nextChapterButton", "next_episode"})
            check(ButtonPolicy.isNextResourceId(id), "Explicit episode resource ID");
        for (String id : new String[]{null, "test:id/next", "test:id/btn_next", "test:id/next_ad", "purchase_next_episode", "next_episode_locked"})
            check(!ButtonPolicy.isNextResourceId(id), "Generic or purchase ID rejected");

        // Labels and approximate bounds transcribed from the supplied screenshots.
        // This tests decision logic; it does not run ML Kit on image pixels.
        for (int scale : new int[]{1, 2, 3}) {
            for (String label : new String[]{"다음화 무료로 보기", "다음화 보기"}) {
                var next = ocr(label, 90*scale, 380*scale, 210*scale, 402*scale);
                var footer = ocr("이 작품과 함께보는 웹툰 ⓘ", 23*scale, 496*scale, 240*scale, 516*scale);
                var novel = ocr("이 작품을 원작소설로 보기", 80*scale, 22*scale, 300*scale, 46*scale);
                var labels = List.of(novel, next, footer, ocr("추천 작품", 20*scale, 600*scale, 100*scale, 620*scale));
                var found = NextButtonDetector.choose(labels, 720*scale, 740*scale);
                check(found == next, "Choose next card, not original novel or recommendation");
                check(NextButtonDetector.hasFooterBelow(labels, found, 720*scale, 740*scale),
                    "Episode ends before recommendation carousel, not physical page bottom");
            }
        }
        // Five additional screenshots from the second work: different crops and
        // card positions, with the same episode-navigation and footer semantics.
        int[][] examples = {
            {733, 749, 118, 393, 225, 410, 51, 505, 172, 520},
            {717, 825, 100, 419, 207, 436, 32, 532, 155, 547},
            {766, 787, 100, 380, 207, 397, 32, 492, 155, 507},
            {671, 771, 96, 386, 205, 403, 29, 499, 153, 514},
            {700, 769, 96, 391, 205, 408, 30, 504, 155, 519}
        };
        for (int[] e : examples) {
            var button = ocr("다음화 무료로 보기", e[2], e[3], e[4], e[5]);
            var heading = ocr("이 작품과 함께보는 웹툰", e[6], e[7], e[8], e[9]);
            var labels = List.of(button, heading, ocr("이 작품을 원작소설로 보기", 80, 30, 300, 55),
                ocr("〉", e[0]-50, 600, e[0]-20, 625));
            check(NextButtonDetector.choose(labels, e[0], e[1]) == button, "Second work: use navigation label, not cover artwork");
            check(NextButtonDetector.hasFooterBelow(labels, button, e[0], e[1]), "Second work: recommendation area marks episode footer");
        }
        var first = ocr("다음", 140, 610, 180, 635);
        var second = ocr("화 무료로 보기", 100, 640, 240, 665);
        var joined = NextButtonDetector.choose(List.of(first, second), 400, 900);
        check(joined != null && ButtonPolicy.isNext(joined.text), "Join split OCR label");
        check(NextButtonDetector.choose(List.of(first, ocr("화 보기", 250, 640, 330, 665)), 400, 900) == null,
            "Do not combine neighbouring columns");
        check(NextButtonDetector.choose(List.of(first, ocr("화 보기", 100, 800, 240, 825)), 400, 900) == null,
            "Do not combine distant paragraphs");
        var next = ocr("다음화 보기", 100, 610, 240, 640);
        check(!NextButtonDetector.hasFooterBelow(List.of(next), next, 400, 900), "Persistent next control alone is not episode end");
        check(!NextButtonDetector.hasFooterBelow(List.of(ocr("이 작품과 함께보는 웹툰", 10, 400, 240, 430)), next, 400, 900),
            "Footer must appear below the next card");
        check(!NextButtonDetector.hasFooterBelow(List.of(ocr("함께보는 웹툰이라는 대사", 10, 700, 240, 730)), next, 400, 900),
            "Dialogue is not a footer heading");
        check(NextButtonDetector.choose(List.of(next, ocr("다음 편 보기", 100, 750, 240, 780)), 400, 900) == null,
            "Multiple separate next targets require manual choice");
        var node = new NextButtonDetector.Label("다음화 보기", 80, 590, 300, 670, true, "버튼 글자");
        check(NextButtonDetector.choose(List.of(next, node), 400, 900) == node, "Prefer actual clickable button over OCR bounds");
        check(NextButtonDetector.choose(List.of(ocr("다음화 보기", -20, 610, 240, 640)), 400, 900) == null, "Offscreen target rejected");
        check(NextButtonDetector.choose(List.of(ocr("다음화 보기", 100, 50, 240, 80)), 400, 900) == null, "Toolbar target rejected");
        var upperCard = ocr("다음화 무료로 보기", 100, 160, 240, 190);
        var upperFooter = ocr("이 작품과 함께보는 웹툰", 20, 250, 260, 280);
        check(NextButtonDetector.choose(List.of(upperCard, upperFooter), 400, 900) == upperCard,
            "A footer card scrolled into upper content area remains detectable");
        check(NextButtonDetector.hasFooterBelow(List.of(upperCard, upperFooter), upperCard, 400, 900),
            "No fixed lower-half requirement for episode footer");
        check(NextButtonDetector.choose(List.of(ocr("다음화 보기", 0, 100, 400, 850)), 400, 900) == null, "Whole-page bounds rejected");
        check(ButtonPolicy.isBlocked("광고 시청 후 무료로 보기"), "Never auto-start rewarded ad flow");
        System.out.println("PASS: " + checks + " next button checks");
    }
}
