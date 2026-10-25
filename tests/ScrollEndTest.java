package app.toonpace;

public final class ScrollEndTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ScrollEndTracker tracker = new ScrollEndTracker();
        tracker.reset();
        check(!tracker.stalled(true), "First capture is not a scroll attempt");
        for (int i = 1; i <= 20; i++) {
            tracker.beginSwipe(i * 1000);
            tracker.onScroll(i * 1000 + 100, 400, i * 400);
            check(!tracker.stalled(true), "Similar white panels with scroll progress must keep reading");
        }
        tracker.reset();
        for (int i = 1; i <= 3; i++) {
            tracker.beginSwipe(i * 1000);
            check(!tracker.stalled(true), "Do not stop after just two unchanged frames");
        }
        tracker.beginSwipe(4000);
        check(tracker.stalled(true), "Repeated unchanged swipes become a stall candidate");
        check(!tracker.repeatedlyStalled(), "No-button uncertainty allows extra retries");
        for (int i = 5; i <= 10; i++) { tracker.beginSwipe(i * 1000); tracker.stalled(true); }
        check(tracker.repeatedlyStalled(), "Blocked scrolling has bounded retries");
        tracker.beginSwipe(11000);
        check(!tracker.stalled(false), "New artwork resets stall count");
        tracker.reset();
        tracker.beginSwipe(12000);
        tracker.onScroll(12100, 0, 200);
        check(!tracker.stalled(true), "Scroll position works when delta is missing");
        tracker.beginSwipe(13000);
        tracker.onScroll(13100, 0, 600);
        check(!tracker.stalled(true), "Position change is movement evidence");
        tracker.reset();
        tracker.beginSwipe(14000);
        tracker.onScroll(13900, 400, 1000);
        tracker.stalled(true);
        for (int i = 15; i <= 17; i++) { tracker.beginSwipe(i * 1000); tracker.stalled(true); }
        check(tracker.stalled(true) == false, "A capture cannot count the same swipe twice");
        check(!tracker.repeatedlyStalled(), "Old scroll events do not manufacture repeated attempts");
        System.out.println("PASS: " + checks + " end detection checks");
    }
}
