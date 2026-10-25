package app.toonpace;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class ReaderService extends AccessibilityService {
    enum Mode { OFF, AUTO, LEARN }
    static ReaderService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private ScreenReader reader;
    private WindowManager windows;
    private LinearLayout overlay;
    private TextView status;
    private Button start, learn;
    private Mode mode = Mode.OFF;
    private String boundPackage;
    private int boundWindow, generation, unchanged, captureSerial, manualRevision;
    private PaceModel model;
    private ScreenReader.Observation previous, learningScreen;
    private long learningSince, learningDeadline, navigatingUntil, lastNext;
    private final InteractionGuard interaction = new InteractionGuard();
    private boolean captureBusy;
    private int lastScrollY = -1, learnedDistance;
    private long manualScrollStarted;
    private final Runnable learnSettled = this::finishManualScroll;

    @Override protected void onServiceConnected() {
        instance = this; prefs = new Prefs(this); reader = new ScreenReader(this);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (prefs.consent()) showOverlay();
    }
    void refresh() {
        if (prefs.consent() && overlay == null) showOverlay();
        if (!prefs.consent()) { stop("권한 동의가 해제되었습니다"); hideOverlay(); }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void showOverlay() {
        if (overlay != null) return;
        overlay = new LinearLayout(this); overlay.setOrientation(LinearLayout.VERTICAL); overlay.setPadding(dp(9), dp(4), dp(9), dp(5));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(22, 36, 31)); bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(100, 126, 105)); overlay.setBackground(bg);
        LinearLayout buttons = new LinearLayout(this);
        start = button("▶ 시작", () -> { if (mode == Mode.OFF) begin(Mode.AUTO); else stop("일시정지"); });
        learn = button("손으로 학습", () -> { if (mode == Mode.LEARN) stop("학습 저장 완료"); else begin(Mode.LEARN); });
        Button close = button("닫기", () -> { stop("일시정지"); hideOverlay(); });
        buttons.addView(start); buttons.addView(learn); buttons.addView(close); overlay.addView(buttons);
        status = new TextView(this); status.setTextColor(Color.rgb(205, 218, 206)); status.setTextSize(11);
        status.setPadding(dp(4), 0, dp(4), dp(4)); status.setText("웹툰을 연 뒤 시작 · 설정은 여기를 탭");
        status.setOnClickListener(v -> { stop("설정 화면"); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); });
        overlay.addView(status);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END; params.y = dp(70); params.x = dp(8);
        try { windows.addView(overlay, params); } catch (RuntimeException e) { overlay = null; }
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setAllCaps(false);
        b.setTextColor(Color.rgb(193, 245, 131)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setPadding(dp(9), 0, dp(9), 0); b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void hideOverlay() {
        if (overlay != null) { windows.removeView(overlay); overlay = null; }
    }
    private void begin(Mode requested) {
        stop("준비 중");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root == null ? "" : String.valueOf(root.getPackageName());
        if (!prefs.consent() || !prefs.allowed().contains(pkg) || pkg.equals(getPackageName())) {
            message("설정에서 선택한 웹툰 앱을 먼저 열어 주세요"); return;
        }
        boundPackage = pkg; boundWindow = root.getWindowId(); model = prefs.model(pkg);
        mode = requested; generation++; previous = null; unchanged = 0; lastScrollY = -1;
        start.setText("Ⅱ 정지"); learn.setText(requested == Mode.LEARN ? "학습 끝내기" : "손으로 학습");
        if (requested == Mode.LEARN) {
            learningDeadline = SystemClock.uptimeMillis() + 5 * 60_000;
            handler.postDelayed(() -> { if (mode == Mode.LEARN) stop("5분 학습이 끝났어요"); }, 5 * 60_000);
            captureLearning(generation);
        } else tick(generation);
    }
    private boolean valid(int token) {
        if (token != generation || mode == Mode.OFF) return false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (!prefs.consent() || root == null || !prefs.allowed().contains(boundPackage)
                || !boundPackage.equals(String.valueOf(root.getPackageName()))) { stop("앱이 바뀌어 멈췄어요"); return false; }
        if (root.getWindowId() != boundWindow) {
            if (SystemClock.uptimeMillis() > navigatingUntil) { stop("화면이 바뀌어 멈췄어요"); return false; }
            boundWindow = root.getWindowId();
        }
        return true;
    }
    private interface Result { void done(ScreenReader.Observation observation); }
    private void capture(int token, Result callback) {
        if (!valid(token)) return;
        if (captureBusy) { handler.postDelayed(() -> capture(token, callback), 350); return; }
        captureBusy = true;
        int serial = ++captureSerial;
        Runnable timeout = () -> { if (serial == captureSerial && valid(token)) stop("화면 인식 시간이 길어져 멈췄어요"); };
        handler.postDelayed(timeout, 12_000);
        if (overlay != null) overlay.setVisibility(View.INVISIBLE);
        handler.postDelayed(() -> reader.read((observation, error) -> {
            if (serial != captureSerial) return;
            handler.removeCallbacks(timeout);
            captureBusy = false;
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
            if (!valid(token)) return;
            if (error != null) { stop(error); return; }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            String allText = observation.visibleText + "\n" + ScreenReader.nodeText(root);
            if (ButtonPolicy.isBlocked(allText)) { stop("결제·로그인 등의 안내가 있어 멈췄어요"); return; }
            Rect nodeButton = ScreenReader.findNext(root, getResources().getDisplayMetrics().heightPixels);
            if (nodeButton != null) observation.next = nodeButton;
            callback.done(observation);
        }, () -> { if (serial == captureSerial && overlay != null) overlay.setVisibility(View.VISIBLE); }), 100);
    }
    private void tick(int token) {
        if (!valid(token) || mode != Mode.AUTO) return;
        capture(token, observation -> {
            if (previous != null) unchanged = observation.similarTo(previous) ? unchanged + 1 : 0;
            previous = observation;
            if (unchanged >= 2) {
                if (observation.next == null) { stop("스크롤이 멈췄어요. 다음 화는 직접 확인해 주세요"); return; }
                if (!prefs.autoNext()) { stop("끝에 도착했어요. 다음 화를 직접 눌러 주세요"); return; }
                if (SystemClock.uptimeMillis() - lastNext < 15_000) { stop("연속 이동을 막기 위해 멈췄어요"); return; }
                countdownNext(token, new Rect(observation.next)); return;
            }
            long dwell = model.dwellMillis(observation.characters, .42, prefs.speed());
            message("글자 약 " + observation.characters + "자 · " + String.format(java.util.Locale.KOREA, "%.1f", dwell / 1000.0)
                + "초 후 스크롤 · 학습 " + model.samples() + "회");
            handler.postDelayed(() -> scroll(token), dwell);
        });
    }
    private void scroll(int token) {
        if (!valid(token) || mode != Mode.AUTO) return;
        // Recheck live accessibility nodes at the moment of action, not only before the dwell.
        if (ButtonPolicy.isBlocked(ScreenReader.nodeText(getRootInActiveWindow()))) { stop("안내 화면이 있어 멈췄어요"); return; }
        int h = getResources().getDisplayMetrics().heightPixels, w = getResources().getDisplayMetrics().widthPixels;
        Path path = new Path(); path.moveTo(w * .48f, h * .76f); path.lineTo(w * .48f, h * .34f);
        interaction.begin(SystemClock.uptimeMillis());
        boolean accepted = dispatchGesture(new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 0, 650)).build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if (token != generation || mode != Mode.AUTO) return;
                interaction.completed(SystemClock.uptimeMillis());
                afterScrollSettled(token);
            }
            @Override public void onCancelled(GestureDescription gesture) { if (token == generation) stop("스크롤 동작이 취소되어 멈췄어요"); }
        }, handler);
        if (!accepted) stop("스크롤을 시작할 수 없어요");
    }
    private void afterScrollSettled(int token) {
        if (!valid(token) || mode != Mode.AUTO) return;
        long delay = interaction.settleDelay(SystemClock.uptimeMillis());
        if (delay > 0) handler.postDelayed(() -> afterScrollSettled(token), delay);
        else tick(token);
    }
    private void countdownNext(int token, Rect expected) {
        message("3초 후 다음 화 · 취소하려면 Ⅱ 정지");
        handler.postDelayed(() -> capture(token, fresh -> {
            if (mode != Mode.AUTO || !prefs.autoNext() || fresh.next == null
                    || !fresh.similarTo(previous)
                    || Math.abs(fresh.next.centerX() - expected.centerX()) > dp(40)
                    || Math.abs(fresh.next.centerY() - expected.centerY()) > dp(40)) {
                stop("버튼이나 화면이 바뀌어 멈췄어요"); return;
            }
            if (!valid(token)) return;
            Path path = new Path(); path.moveTo(fresh.next.centerX(), fresh.next.centerY());
            interaction.begin(SystemClock.uptimeMillis());
            navigatingUntil = SystemClock.uptimeMillis() + 8000; lastNext = SystemClock.uptimeMillis();
            boolean accepted = dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gesture) {
                    if (token != generation || mode != Mode.AUTO) return;
                    interaction.completed(SystemClock.uptimeMillis());
                    previous = null; unchanged = 0; message("다음 화를 기다리고 있어요");
                    handler.postDelayed(() -> tick(token), 4000);
                }
                @Override public void onCancelled(GestureDescription gesture) { if (token == generation) stop("이동이 취소되었어요"); }
            }, handler);
            if (!accepted) stop("다음 화를 누를 수 없어요");
        }), 3000);
    }
    private void captureLearning(int token) {
        if (!valid(token) || mode != Mode.LEARN) return;
        int revision = manualRevision;
        capture(token, observation -> {
            if (manualRevision != revision) {
                handler.postDelayed(() -> captureLearning(token), 800); return;
            }
            learningScreen = observation; learningSince = SystemClock.uptimeMillis(); manualScrollStarted = 0;
            message("평소처럼 직접 내려 주세요 · 학습 " + model.samples() + "회");
        });
    }
    private void finishManualScroll() {
        if (mode != Mode.LEARN || learningScreen == null) return;
        int token = generation;
        double fraction = learnedDistance / (double) getResources().getDisplayMetrics().heightPixels;
        long dwell = manualScrollStarted - learningSince;
        if (model.learn(learningScreen.characters, dwell, fraction)) prefs.save(boundPackage, model);
        learnedDistance = 0; learningScreen = null;
        captureLearning(token);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mode == Mode.OFF || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        // Overlay events do not invalidate the underlying reader window.
        if (pkg.equals(getPackageName())) return;
        if (!valid(generation)) return;
        if (!pkg.equals(boundPackage)) return;
        int type = event.getEventType();
        long now = SystemClock.uptimeMillis();
        long eventTime = event.getEventTime();
        if (mode == Mode.AUTO) {
            // Mouse clicks do not reliably produce TOUCH_INTERACTION_START.
            boolean direct = type == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
                || type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
            if ((direct && interaction.directInput(eventTime))
                    || (type == AccessibilityEvent.TYPE_VIEW_SCROLLED && interaction.scrolled(eventTime, now))) {
                stop("직접 조작하여 멈췄어요"); return;
            }
        }
        if (mode == Mode.LEARN && type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (now > learningDeadline) { stop("학습 종료"); return; }
            manualRevision++;
            if (manualScrollStarted == 0) manualScrollStarted = now;
            if (event.getScrollDeltaY() != 0) learnedDistance += event.getScrollDeltaY();
            else if (lastScrollY >= 0 && event.getScrollY() >= 0) learnedDistance += event.getScrollY() - lastScrollY;
            if (event.getScrollY() >= 0) lastScrollY = event.getScrollY();
            handler.removeCallbacks(learnSettled); handler.postDelayed(learnSettled, 700);
        }
    }
    private void message(String text) { if (status != null) status.setText(text); }
    private void stop(String reason) {
        generation++; mode = Mode.OFF; handler.removeCallbacksAndMessages(null);
        captureSerial++; captureBusy = false;
        if (overlay != null) overlay.setVisibility(View.VISIBLE);
        previous = null; learningScreen = null; unchanged = 0; learnedDistance = 0; navigatingUntil = 0;
        interaction.reset(SystemClock.uptimeMillis());
        if (start != null) start.setText("▶ 시작"); if (learn != null) learn.setText("손으로 학습"); message(reason);
    }
    void showControls() { showOverlay(); }
    void pauseForSettings() { stop("설정이 바뀌어 멈췄어요"); }
    @Override public void onInterrupt() { stop("접근성 서비스가 중단되었습니다"); }
    @Override public void onDestroy() {
        stop("종료"); hideOverlay(); if (reader != null) reader.close(); instance = null; super.onDestroy();
    }
}
