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
        tracker.reset();
        for (int i = 1; i <= 8; i++) {
            tracker.beginSwipe(i * 1000);
            tracker.onScroll(i * 1000 + 100, 400, i * 400, 10000);
            tracker.stalled(true);
            check(!tracker.nextReady(), "Similar white panels away from bottom must not skip an episode");
        }
        tracker.reset();
        tracker.beginSwipe(10000); tracker.onScroll(10100, 400, 2000, 2000); tracker.stalled(true);
        tracker.beginSwipe(11000); tracker.onScroll(11100, 400, 2000, 2000); tracker.stalled(true);
        check(tracker.nextReady(), "Confirmed bottom and stable image allow next despite noisy deltas");
        tracker.beginSwipe(12000);
        check(!tracker.nextReady(), "Bottom evidence is refreshed for each swipe");
        tracker.onScroll(12100, 400, 2400, 4000); tracker.stalled(true);
        check(!tracker.nextReady(), "Lazy-loaded content clears bottom evidence");
        System.out.println("PASS: " + checks + " end detection checks");
    }
}
