package app.toonpace;

public final class CoreTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        PaceModel model = new PaceModel();
        check(model.dwellMillis(180, .42, 1) > model.dwellMillis(20, .42, 1), "More dialogue must read slower");
        check(model.dwellMillis(100, .42, .5) > model.dwellMillis(100, .42, 2), "Speed control is monotonic");
        long baseline = model.dwellMillis(80, 1, 1);
        for (int i = 0; i < 30; i++) check(model.learn(80, 5500, .5), "Valid manual demonstration accepted");
        check(model.dwellMillis(80, 1, 1) > baseline, "Learns slower reader");
        PaceModel quick = new PaceModel();
        for (int i = 0; i < 40; i++) quick.learn(80, 1800, .5);
        check(quick.dwellMillis(80, 1, 1) < baseline, "Learns faster reader");
        check(quick.dwellMillis(160, .42, 1) > quick.dwellMillis(10, .42, 1), "Learning preserves density relationship");
        int count = quick.samples();
        check(!quick.learn(40, 100, .5), "Accidental fast flick ignored");
        check(!quick.learn(40, 90_000, .5), "Long interruption ignored");
        check(!quick.learn(40, 3000, -.4), "Upward rereading ignored");
        check(!quick.learn(40, 3000, 0), "Unknown scroll distance ignored");
        check(!quick.learn(40, 3000, Double.NaN), "Invalid distance ignored");
        check(quick.samples() == count, "Rejected examples do not count");
        PaceModel restored = new PaceModel(quick.visualSeconds(), quick.textSecondsPer80(), quick.samples());
        check(restored.dwellMillis(120, .42, 1) == quick.dwellMillis(120, .42, 1), "Profile roundtrip");
        PaceModel broken = new PaceModel(Double.NaN, Double.POSITIVE_INFINITY, -5);
        check(broken.dwellMillis(100, .42, 1) == new PaceModel().dwellMillis(100, .42, 1), "Corrupt settings have defaults");
        for (int chars : new int[]{0, 10, 80, 500, 10000}) {
            long delay = quick.dwellMillis(chars, .42, 1);
            check(delay >= 1400 && delay <= 30000, "Dwell remains bounded");
        }
        for (String label : new String[]{"다음 화", "다음화 보기", "다음 회", "다음 편 읽기", "다음 에피소드", "Next episode", "다음 화 〉"})
            check(ButtonPolicy.isNext(label), "Recognize episode button: " + label);
        for (String label : new String[]{"다음", ">", "다음 광고", "다음 화 구매", "이전 화", "다음 화 소장", "추천 다음 화", "다음 화\n100 쿠키"})
            check(!ButtonPolicy.isNext(label), "Do not guess ambiguous next: " + label);
        for (String label : new String[]{"쿠키 3개", "대여하기", "소장", "캐시 충전", "로그인", "본인 인증", "Unlock chapter", "Purchase", "Sign in"})
            check(ButtonPolicy.isBlocked(label), "Stop on payment/login: " + label);
        check(!ButtonPolicy.isBlocked("다음 화 보기"), "Ordinary next is not a purchase");
        System.out.println("PASS: " + checks + " core checks");
    }
}
