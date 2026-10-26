package app.toonpace;

import java.util.ArrayList;
import java.util.List;

/** Geometry and ambiguity checks shared by OCR and accessibility candidates. No learned click policy. */
final class NextButtonDetector {
    static final class Label {
        final String text, source;
        final int left, top, right, bottom;
        final boolean actionable;
        Label(String text, int left, int top, int right, int bottom, boolean actionable, String source) {
            this.text = text; this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.actionable = actionable; this.source = source;
        }
        int cx() { return (left + right) / 2; }
        int cy() { return (top + bottom) / 2; }
        int height() { return bottom - top; }
    }
    private NextButtonDetector() {}
    static boolean hasFooterBelow(List<Label> labels, Label next, int width, int height) {
        if (next == null) return false;
        for (Label label : labels) {
            if (ButtonPolicy.isEpisodeFooter(label.text) && visible(label, width, height)
                    && label.top >= next.bottom) return true;
        }
        return false;
    }
    static Label choose(List<Label> labels, int width, int height) {
        List<Label> candidates = new ArrayList<>();
        for (Label label : labels)
            if (ButtonPolicy.isNext(label.text) && visible(label, width, height)) candidates.add(label);
        // ML Kit may put '다음' and '화 보기' on separate lines. Join only nearby,
        // horizontally aligned OCR fragments; never join unrelated accessibility controls.
        for (int i = 0; i < labels.size(); i++) {
            Label a = labels.get(i);
            if (!"글자 인식".equals(a.source) || a.text.length() > 24 || a.height() <= 0) continue;
            for (int j = i + 1; j < labels.size(); j++) {
                Label b = labels.get(j);
                if (!"글자 인식".equals(b.source) || b.text.length() > 24 || b.height() <= 0) continue;
                Label first = a.top <= b.top ? a : b, second = first == a ? b : a;
                int overlap = Math.min(first.right, second.right) - Math.max(first.left, second.left);
                int gap = second.top - first.bottom;
                if (overlap <= 0 || gap < -Math.min(a.height(), b.height()) / 3
                        || gap > Math.max(a.height(), b.height()) * 1.5) continue;
                Label joined = new Label(first.text + " " + second.text,
                    Math.min(a.left, b.left), first.top, Math.max(a.right, b.right),
                    Math.max(a.bottom, b.bottom), false, "글자 인식");
                if (ButtonPolicy.isNext(joined.text) && visible(joined, width, height)) candidates.add(joined);
            }
        }
        Label best = null;
        for (Label candidate : candidates) {
            if (best == null || rank(candidate) > rank(best)) best = candidate;
        }
        if (best == null) return null;
        // Two separate controls with the same purpose are ambiguous. Do not choose
        // whichever happened to be visited last (could be an ad or recommendation).
        for (Label other : candidates) if (!sameControl(best, other)) return null;
        return best;
    }
    private static int rank(Label label) {
        return label.actionable ? 3 : "글자 인식".equals(label.source) ? 1 : 2;
    }
    private static boolean visible(Label label, int w, int h) {
        return w > 0 && h > 0 && label.left >= 0 && label.right <= w && label.right > label.left
            && label.top >= h * .10 && label.bottom <= h * .98 && label.height() > 0
            && label.height() <= h * .18;
    }
    private static boolean sameControl(Label a, Label b) {
        return contains(a, b.cx(), b.cy()) || contains(b, a.cx(), a.cy());
    }
    private static boolean contains(Label a, int x, int y) {
        return x >= a.left && x <= a.right && y >= a.top && y <= a.bottom;
    }
}
