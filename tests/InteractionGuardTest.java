package app.toonpace;

public final class InteractionGuardTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        InteractionGuard guard = new InteractionGuard();
        guard.reset(100);
        check(!guard.directInput(90), "Old session input ignored");
        check(guard.directInput(200), "Mouse click during reading pauses");
        guard.begin(1000);
        check(!guard.directInput(1100), "Injected touch ignored");
        check(!guard.scrolled(1500, 1500), "Injected scroll ignored");
        guard.completed(1650);
        check(!guard.scrolled(2300, 2300), "Scroll after old 1050ms cutoff ignored");
        check(guard.settleDelay(2550) > 0, "Inertia delays next capture");
        check(!guard.scrolled(3000, 3000), "Continued inertia ignored");
        check(guard.settleDelay(3900) == 0, "Settles after quiet interval");
        check(!guard.scrolled(2300, 4000), "Late delivery of old injected scroll ignored after settling");
        check(guard.directInput(4000), "Mouse click after settling pauses");
        check(guard.scrolled(4100, 4100), "Manual scroll after settling is outside the injection window");
        check(!guard.directInput(1200), "Delayed injected touch uses event timestamp");
        guard.begin(5000);
        guard.completed(5080);
        check(!guard.directInput(5200), "Injected next-episode click ignored");
        check(guard.directInput(6100), "Manual click during navigation is not ignored for eight seconds");
        guard.reset(7000);
        check(!guard.directInput(6100), "Stopped session cannot affect restarted session");
        check(guard.directInput(7100), "Stop clears injection state");
        guard.begin(8000);
        guard.completed(8650);
        for (long t = 9000; t < 15000; t += 500) guard.scrolled(t, t);
        check(guard.settleDelay(15000) == 0, "Continuous events cannot extend settling forever");
        System.out.println("PASS: " + checks + " interaction checks");
    }
}
