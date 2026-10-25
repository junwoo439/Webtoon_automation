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

final class ScreenReader {
    interface Callback { void done(Observation result, String error); }
    static final class Observation {
        int characters;
        String visibleText = "";
        Rect next;
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
                                if (ButtonPolicy.isNext(line.getText()) && box.centerY() > height * .45
                                        && box.left >= 0 && box.right <= width)
                                    observation.next = new Rect(box);
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
    static Rect findNext(AccessibilityNodeInfo root, int screenHeight) {
        return findNext(root, screenHeight, new int[]{0});
    }
    private static Rect findNext(AccessibilityNodeInfo node, int h, int[] visited) {
        if (node == null || ++visited[0] > 500) return null;
        if (node.isVisibleToUser() && node.isEnabled()
                && (ButtonPolicy.isNext(String.valueOf(node.getText())) || ButtonPolicy.isNext(String.valueOf(node.getContentDescription())))) {
            Rect box = new Rect(); node.getBoundsInScreen(box);
            if (!box.isEmpty() && box.centerY() > h * .45 && box.bottom < h) return box;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            Rect candidate = findNext(node.getChild(i), h, visited);
            if (candidate != null) return candidate;
        }
        return null;
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
