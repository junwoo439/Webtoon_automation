package app.toonpace;

/** Similar artwork alone is not evidence that a scroll reached the end. */
final class ScrollEndTracker {
    private int visualStill, stillSwipes, lastY = -1;
    private long swipeStarted;
    private boolean attempted, moved, atBottom;

    void reset() { visualStill = 0; stillSwipes = 0; lastY = -1; attempted = moved = atBottom = false; }
    void beginSwipe(long now) { swipeStarted = now; attempted = true; moved = false; atBottom = false; }
    void onScroll(long eventTime, int deltaY, int y) { onScroll(eventTime, deltaY, y, -1); }
    void onScroll(long eventTime, int deltaY, int y, int maxY) {
        if (!attempted || eventTime < swipeStarted) return;
        if (maxY > 0 && y >= 0) atBottom = y >= maxY;
        else atBottom = false;
        // Some readers emit a requested delta at the boundary without moving.
        if (y >= 0 && lastY >= 0) moved |= y != lastY;
        else moved |= deltaY != 0 || y > 0;
        if (y >= 0) lastY = y;
    }
    boolean stalled(boolean similar) {
        if (!attempted) return false;
        visualStill = similar ? visualStill + 1 : 0;
        stillSwipes = similar && !moved ? stillSwipes + 1 : 0;
        attempted = false;
        return stillSwipes >= 4;
    }
    boolean nextReady() { return atBottom && visualStill >= 2; }
    boolean repeatedlyStalled() { return stillSwipes >= 10; }
}
