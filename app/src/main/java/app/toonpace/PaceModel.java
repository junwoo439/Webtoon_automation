package app.toonpace;

/** Small, bounded online regression. Stores timing coefficients, never text or images. */
public final class PaceModel {
    private double visualSeconds;
    private double textSecondsPer80;
    private int samples;

    public PaceModel() { this(2.8, 6.0, 0); }
    public PaceModel(double visualSeconds, double textSecondsPer80, int samples) {
        this.visualSeconds = clamp(finite(visualSeconds, 2.8), 1.2, 12);
        this.textSecondsPer80 = clamp(finite(textSecondsPer80, 6), 1, 24);
        this.samples = Math.max(0, samples);
    }
    public long dwellMillis(int characters, double viewportFraction, double speed) {
        double fraction = clamp(finite(viewportFraction, .42), .15, 1);
        double seconds = (visualSeconds + textSecondsPer80 * clamp(characters, 0, 500) / 80.0)
            * fraction / clamp(finite(speed, 1), .5, 2);
        return Math.round(clamp(seconds * 1000, 350, 30000));
    }
    public static long swipeMillis(double speed) {
        return Math.round(650 / clamp(finite(speed, 1), .5, 2));
    }
    /** Input is the OLD viewport, dwell until the next manual scroll, and distance moved. */
    public boolean learn(int characters, long elapsedMillis, double viewportFraction) {
        if (characters < 0 || elapsedMillis < 1200 || elapsedMillis > 45000
                || !Double.isFinite(viewportFraction) || viewportFraction < .20 || viewportFraction > 1.2)
            return false;
        double x = clamp(characters, 0, 500) / 80.0;
        double predicted = visualSeconds + textSecondsPer80 * x;
        double observed = clamp(elapsedMillis / (1000.0 * viewportFraction), 2, 60);
        // Bounded residual prevents a notification or distraction from dominating a profile.
        double error = clamp(observed - predicted, -predicted * .35, predicted * .35);
        double step = .24 * error / (1 + x * x);
        visualSeconds = clamp(visualSeconds + step, 1.2, 12);
        textSecondsPer80 = clamp(textSecondsPer80 + step * x, 1, 24);
        samples = Math.min(Integer.MAX_VALUE - 1, samples + 1);
        return true;
    }
    public double visualSeconds() { return visualSeconds; }
    public double textSecondsPer80() { return textSecondsPer80; }
    public int samples() { return samples; }
    private static double finite(double n, double fallback) { return Double.isFinite(n) ? n : fallback; }
    private static double clamp(double n, double lo, double hi) { return Math.max(lo, Math.min(hi, n)); }
}
