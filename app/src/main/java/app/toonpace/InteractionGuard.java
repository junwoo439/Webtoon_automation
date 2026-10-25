package app.toonpace;

/** Distinguishes injected gesture feedback from subsequent user input.
 * Accessibility events cannot report every touch on every app; the stop button
 * remains the unconditional way to pause.
 */
final class InteractionGuard {
    private long quietMillis = 900;
    private long startedAt, ignoreThrough, settleDeadline;
    private boolean injecting, settling;

    void reset(long now) {
        startedAt = now;
        ignoreThrough = now;
        injecting = settling = false;
    }

    void begin(long now) { begin(now, 900); }
    void begin(long now, long quiet) {
        quietMillis = Math.max(300, Math.min(900, quiet));
        injecting = true;
        settling = true;
        ignoreThrough = now + quietMillis;
        settleDeadline = now + 6000;
    }

    void completed(long now) {
        injecting = false;
        ignoreThrough = now + quietMillis;
        settleDeadline = now + 6000;
    }

    boolean directInput(long eventTime) {
        return eventTime > startedAt && !injecting && eventTime > ignoreThrough;
    }

    boolean scrolled(long eventTime, long now) {
        if (eventTime <= startedAt) return false;
        if (injecting || eventTime <= ignoreThrough) {
            if (settling) ignoreThrough = Math.min(settleDeadline, Math.max(ignoreThrough, now + quietMillis));
            return false;
        }
        return true;
    }

    long settleDelay(long now) {
        long delay = Math.max(0, ignoreThrough - now);
        if (delay == 0) settling = false;
        return delay;
    }
}
