package app.toonpace;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Display;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;

final class ScreenReader {
    interface Callback { void done(Observation result, String error); }
    static final class Observation {
        int characters;
        String visibleText = "";
        Rect next;
        String nextLabel = "", nextSource = "";
        boolean blocked, footerReady;
        final List<NextButtonDetector.Label> labels = new ArrayList<>();
        int[] fingerprint;
        boolean similarTo(Observation old) {
            if (old == null || old.fingerprint == null || fingerprint == null) return false;
            long delta = 0;
            for (int i = 0; i < fingerprint.length; i++) delta += Math.abs(fingerprint[i] - old.fingerprint[i]);
            return delta / (double) fingerprint.length < 3;
        }
    }
    private final AccessibilityService service;
    private final TextRecognizer recognizer = TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build());
    private boolean closed;
    ScreenReader(AccessibilityService service) { this.service = service; }

    void read(Callback callback, Runnable captured) {
        if (closed) { callback.done(null, "화면 인식이 종료되었습니다"); return; }
        service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(), new AccessibilityService.TakeScreenshotCallback() {
            @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                Bitmap software = null;
                try {
                    Bitmap hardware = Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(), result.getColorSpace());
                    if (hardware != null) { software = hardware.copy(Bitmap.Config.ARGB_8888, false); hardware.recycle(); }
                } catch (RuntimeException ignored) {
                    // Secure/unsupported surfaces must stop automation instead of guessing.
                } finally { result.getHardwareBuffer().close(); }
                captured.run();
                if (software == null) { callback.done(null, "이 화면은 인식할 수 없어요"); return; }
                if (closed) { software.recycle(); return; }
                Bitmap pixels = software;
                Observation observation = new Observation();
                observation.fingerprint = fingerprint(pixels);
                recognizer.process(InputImage.fromBitmap(pixels, 0)).addOnCompleteListener(task -> {
                    try {
                        if (closed) return;
                        if (!task.isSuccessful()) { callback.done(null, "글자 인식에 실패했어요. 잠시 후 다시 시작해 주세요"); return; }
                        StringBuilder visible = new StringBuilder();
                        int width = pixels.getWidth(), height = pixels.getHeight();
                        for (Text.TextBlock block : task.getResult().getTextBlocks()) {
                            for (Text.Line line : block.getLines()) {
                                Rect box = line.getBoundingBox();
                                if (box == null || box.top < height * .07 || box.bottom > height * .97) continue;
                                // Overlay is hidden during capture. Include navigation in safety checks,
                                // but count body text only for pace estimation.
                                visible.append(line.getText()).append('\n');
                                if (box.top > height * .14 && box.bottom < height * .86)
                                    observation.characters += (int) line.getText().codePoints().filter(Character::isLetterOrDigit).count();
                                if (observation.labels.size() < 200)
                                    observation.labels.add(new NextButtonDetector.Label(line.getText(), box.left,
                                        box.top, box.right, box.bottom, false, "글자 인식"));
                            }
                        }
                        observation.visibleText = visible.toString();
                        callback.done(observation, null);
                    } finally { pixels.recycle(); }
                });
            }
            @Override public void onFailure(int code) {
                captured.run();
                callback.done(null, "화면 캡처가 허용되지 않거나 아직 준비되지 않았어요 (" + code + ")");
            }
        });
    }
    static String nodeText(AccessibilityNodeInfo root) {
        StringBuilder text = new StringBuilder();
        append(root, text, new int[]{0});
        return text.toString();
    }
    private static void append(AccessibilityNodeInfo node, StringBuilder text, int[] visited) {
        if (node == null || ++visited[0] > 500 || text.length() > 24000) return;
        if (node.isVisibleToUser()) {
            if (node.getText() != null) text.append(node.getText()).append('\n');
            if (node.getContentDescription() != null) text.append(node.getContentDescription()).append('\n');
        }
        for (int i = 0; i < node.getChildCount(); i++) append(node.getChild(i), text, visited);
    }
    private static final class NodeButton {
        final NextButtonDetector.Label label;
        final AccessibilityNodeInfo action;
        NodeButton(NextButtonDetector.Label label, AccessibilityNodeInfo action) {
            this.label = label; this.action = action;
        }
    }
    static void resolveNext(AccessibilityNodeInfo root, Observation observation, int w, int h) {
        List<NextButtonDetector.Label> labels = new ArrayList<>(observation.labels);
        for (NodeButton button : nodeButtons(root)) labels.add(button.label);
        collectFooters(root, labels, new int[]{0});
        NextButtonDetector.Label next = NextButtonDetector.choose(labels, w, h);
        observation.next = next == null ? null : new Rect(next.left, next.top, next.right, next.bottom);
        observation.nextLabel = next == null ? "" : next.text;
        observation.nextSource = next == null ? "" : next.source;
        observation.footerReady = NextButtonDetector.hasFooterBelow(labels, next, w, h);
    }
    /** Reacquire the enabled control immediately before acting; never retain old UI nodes. */
    static boolean clickNext(AccessibilityNodeInfo root, Rect expected, int w, int h, int tolerance) {
        List<NodeButton> buttons = nodeButtons(root);
        List<NextButtonDetector.Label> labels = new ArrayList<>();
        for (NodeButton button : buttons) labels.add(button.label);
        NextButtonDetector.Label chosen = NextButtonDetector.choose(labels, w, h);
        if (chosen == null || Math.abs(chosen.cx() - expected.centerX()) > tolerance
                || Math.abs(chosen.cy() - expected.centerY()) > tolerance) return false;
        for (NodeButton button : buttons) {
            if (button.label == chosen && button.action != null)
                return button.action.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }
    private static List<NodeButton> nodeButtons(AccessibilityNodeInfo root) {
        List<NodeButton> result = new ArrayList<>();
        collectButtons(root, result, new int[]{0}); return result;
    }
    private static void collectFooters(AccessibilityNodeInfo node, List<NextButtonDetector.Label> out, int[] visited) {
        if (node == null || ++visited[0] > 500) return;
        if (node.isVisibleToUser()) {
            for (String text : new String[]{String.valueOf(node.getText()), String.valueOf(node.getContentDescription())}) {
                if (ButtonPolicy.isEpisodeFooter(text)) {
                    Rect box = new Rect(); node.getBoundsInScreen(box);
                    out.add(new NextButtonDetector.Label(text, box.left, box.top, box.right, box.bottom, false, "회차 하단"));
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) collectFooters(node.getChild(i), out, visited);
    }
    private static void collectButtons(AccessibilityNodeInfo node, List<NodeButton> out, int[] visited) {
        if (node == null || ++visited[0] > 500 || !node.isEnabled()) return;
        if (node.isVisibleToUser()) {
            addButton(node, String.valueOf(node.getText()), "버튼 글자", out);
            addButton(node, String.valueOf(node.getContentDescription()), "버튼 설명", out);
            if (node.isClickable()) {
                if (ButtonPolicy.isNextResourceId(node.getViewIdResourceName()))
                    addButton(node, "다음 화", "버튼 ID", out);
                StringBuilder children = new StringBuilder();
                childLabels(node, children, 0);
                addButton(node, children.toString(), "버튼 내부 글자", out);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) collectButtons(node.getChild(i), out, visited);
    }
    private static void childLabels(AccessibilityNodeInfo node, StringBuilder text, int depth) {
        if (node == null || depth > 3 || text.length() > 80 || !node.isVisibleToUser()) return;
        if (node.getText() != null) text.append(node.getText()).append(' ');
        else if (node.getContentDescription() != null) text.append(node.getContentDescription()).append(' ');
        for (int i = 0; i < Math.min(20, node.getChildCount()); i++) childLabels(node.getChild(i), text, depth + 1);
    }
    private static void addButton(AccessibilityNodeInfo node, String text, String source, List<NodeButton> out) {
        if (!ButtonPolicy.isNext(text)) return;
        Rect box = new Rect(); node.getBoundsInScreen(box);
        AccessibilityNodeInfo action = node;
        for (int depth = 0; action != null && !action.isClickable() && depth < 4; depth++) action = action.getParent();
        if (action != null && (!action.isClickable() || !action.isEnabled() || !action.isVisibleToUser())) action = null;
        out.add(new NodeButton(new NextButtonDetector.Label(text, box.left, box.top, box.right, box.bottom,
            action != null, source), action));
    }
    private static int[] fingerprint(Bitmap b) {
        int[] sample = new int[24 * 32];
        for (int y = 0; y < 32; y++) for (int x = 0; x < 24; x++) {
            int pixel = b.getPixel((int) ((x + .5) * b.getWidth() / 24),
                (int) ((.16 + (y + .5) * .64 / 32) * b.getHeight()));
            sample[y * 24 + x] = (Color.red(pixel) * 3 + Color.green(pixel) * 6 + Color.blue(pixel)) / 10;
        }
        return sample;
    }
    void close() { closed = true; recognizer.close(); }
}
