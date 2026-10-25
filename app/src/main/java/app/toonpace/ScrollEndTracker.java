package app.toonpace;

/** Similar artwork alone is not evidence that a scroll reached the end. */
final class ScrollEndTracker {
    private int stillSwipes, lastY = -1;
    private long swipeStarted;
    private boolean attempted, moved;

    void reset() { stillSwipes = 0; lastY = -1; attempted = moved = false; }
    void beginSwipe(long now) { swipeStarted = now; attempted = true; moved = false; }
    void onScroll(long eventTime, int deltaY, int y) {
        if (!attempted || eventTime < swipeStarted) return;
        if (deltaY != 0 || (y >= 0 && lastY >= 0 && y != lastY) || (lastY < 0 && y > 0)) moved = true;
        if (y >= 0) lastY = y;
    }
    boolean stalled(boolean similar) {
        if (!attempted) return false;
        stillSwipes = similar && !moved ? stillSwipes + 1 : 0;
        attempted = false;
        return stillSwipes >= 4;
    }
    boolean repeatedlyStalled() { return stillSwipes >= 10; }
}
