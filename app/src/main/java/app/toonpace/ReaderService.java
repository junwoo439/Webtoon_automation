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
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Toast;
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
    private Button start, learn, speedButton;
    private LinearLayout details;
    private View touchPauseLayer;
    private Button nextButton;
    private WindowManager.LayoutParams overlayParams;
    private final ScrollEndTracker endTracker = new ScrollEndTracker();
    private Runnable pendingScroll;
    private long readingSince;
    private int readingCharacters;
    private Mode mode = Mode.OFF;
    private String boundPackage;
    private int boundWindow, generation, captureSerial, manualRevision;
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
        overlay.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> {
            if (r - l != oldR - oldL || b - t != oldB - oldT) keepOverlayOnScreen();
        });
        LinearLayout buttons = new LinearLayout(this);
        start = button("▶", () -> { if (mode == Mode.OFF) begin(Mode.AUTO); else stop("일시정지"); });
        start.setContentDescription("자동 스크롤 시작 또는 정지");
        Button menu = button("⋮", () -> {
            nextButton.setText(prefs.autoNext() ? "다음 화 자동: 켜짐" : "다음 화 자동: 꺼짐");
            speedButton.setText(String.format(java.util.Locale.KOREA, "%.2f×", prefs.speed()));
            details.setVisibility(details.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            overlay.post(this::keepOverlayOnScreen);
        });
        menu.setContentDescription("조작 메뉴 열기, 끌어서 위치 이동");
        menu.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int initialX, initialY; boolean dragging;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY();
                    initialX = overlayParams.x; initialY = overlayParams.y; dragging = false; return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (Math.hypot(dx, dy) > ViewConfiguration.get(ReaderService.this).getScaledTouchSlop()) dragging = true;
                    if (dragging) {
                        overlayParams.x = initialX + Math.round(dx); overlayParams.y = initialY + Math.round(dy);
                        keepOverlayOnScreen();
                    }
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP) { if (!dragging) v.performClick(); return true; }
                return true;
            }
        });
        buttons.addView(start); buttons.addView(menu); overlay.addView(buttons);
        details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
        learn = button("손으로 학습", () -> { if (mode == Mode.LEARN) stop("학습 저장 완료"); else begin(Mode.LEARN); });
        details.addView(learn);
        nextButton = button(prefs.autoNext() ? "다음 화 자동: 켜짐" : "다음 화 자동: 꺼짐", () -> {
            boolean enabled = !prefs.autoNext();
            prefs.data.edit().putBoolean("next", enabled).apply();
            nextButton.setText(enabled ? "다음 화 자동: 켜짐" : "다음 화 자동: 꺼짐");
        });
        details.addView(nextButton);
        LinearLayout speedRow = new LinearLayout(this);
        speedRow.addView(button("−", () -> changeSpeed(-.25)));
        speedButton = button(String.format(java.util.Locale.KOREA, "%.2f×", prefs.speed()), () -> changeSpeed(0));
        speedButton.setContentDescription("현재 읽기 속도"); speedRow.addView(speedButton);
        speedRow.addView(button("+", () -> changeSpeed(.25))); details.addView(speedRow);
        status = new TextView(this); status.setTextColor(Color.rgb(205, 218, 206)); status.setTextSize(11);
        status.setMaxWidth(dp(210)); status.setPadding(dp(4), 0, dp(4), dp(4));
        status.setText("웹툰을 연 뒤 ▶ · ⋮를 끌어 이동"); details.addView(status);
        details.addView(button("설정", () -> {
            stop("설정 화면"); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }));
        details.addView(button("닫기", () -> { stop("일시정지"); hideOverlay(); }));
        details.setVisibility(View.GONE); overlay.addView(details);
        overlayParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.LEFT;
        overlayParams.y = dp(150); overlayParams.x = getResources().getDisplayMetrics().widthPixels - dp(116);
        try {
            // Add the transparent pause target BEFORE controls, so controls stay on top.
            touchPauseLayer = new View(this);
            touchPauseLayer.setContentDescription("터치하면 자동 읽기 일시정지");
            touchPauseLayer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            touchPauseLayer.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mode == Mode.AUTO)
                    stop("터치하여 일시정지했어요");
                return true;
            });
            touchPauseLayer.setVisibility(View.GONE);
            windows.addView(touchPauseLayer, new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT));
            windows.addView(overlay, overlayParams); overlay.post(this::keepOverlayOnScreen);
        } catch (RuntimeException e) {
            if (touchPauseLayer != null && touchPauseLayer.isAttachedToWindow()) windows.removeView(touchPauseLayer);
            touchPauseLayer = null; overlay = null;
        }
    }
    private void keepOverlayOnScreen() {
        if (overlay == null || overlayParams == null || !overlay.isAttachedToWindow()) return;
        int width = getResources().getDisplayMetrics().widthPixels, height = getResources().getDisplayMetrics().heightPixels;
        overlayParams.x = Math.max(0, Math.min(overlayParams.x, width - overlay.getWidth()));
        overlayParams.y = Math.max(0, Math.min(overlayParams.y, height - overlay.getHeight() - dp(32)));
        windows.updateViewLayout(overlay, overlayParams);
    }
    private void changeSpeed(double delta) {
        double speed = Math.max(.5, Math.min(5, prefs.speed() + delta));
        prefs.data.edit().putFloat("speed", (float) speed).apply();
        speedButton.setText(String.format(java.util.Locale.KOREA, "%.2f×", speed));
        if (pendingScroll != null && mode == Mode.AUTO) scheduleScroll(generation);
    }
    private void scheduleScroll(int token) {
        if (pendingScroll != null) handler.removeCallbacks(pendingScroll);
        long dwell = model.dwellMillis(readingCharacters, .42, prefs.speed());
        long remaining = Math.max(0, dwell - (SystemClock.uptimeMillis() - readingSince));
        message(String.format(java.util.Locale.KOREA, "%.2f× · 약 %d자 · %.1f초 후 이동", prefs.speed(), readingCharacters, remaining / 1000.0));
        pendingScroll = () -> { pendingScroll = null; scroll(token); };
        handler.postDelayed(pendingScroll, remaining);
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setTextSize(12); b.setAllCaps(false);
        b.setTextColor(Color.rgb(193, 245, 131)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setMinimumWidth(dp(48)); b.setMinimumHeight(dp(48));
        b.setPadding(dp(7), 0, dp(7), 0); b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void hideOverlay() {
        if (overlay != null) { windows.removeView(overlay); overlay = null; }
        if (touchPauseLayer != null) { windows.removeView(touchPauseLayer); touchPauseLayer = null; }
    }
    private void showTouchPause(boolean visible) {
        if (touchPauseLayer != null) touchPauseLayer.setVisibility(visible && mode == Mode.AUTO ? View.VISIBLE : View.GONE);
    }
    private void begin(Mode requested) {
        stop("준비 중");
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String pkg = root == null ? "" : String.valueOf(root.getPackageName());
        if (!prefs.consent() || !prefs.allowed().contains(pkg) || pkg.equals(getPackageName())) {
            message("설정에서 선택한 웹툰 앱을 먼저 열어 주세요"); return;
        }
        boundPackage = pkg; boundWindow = root.getWindowId(); model = prefs.model(pkg);
        mode = requested; generation++; previous = null; lastScrollY = -1;
        start.setText("Ⅱ");
        details.setVisibility(View.GONE); learn.setText(requested == Mode.LEARN ? "학습 끝내기" : "손으로 학습");
        if (requested == Mode.LEARN) {
            learningDeadline = SystemClock.uptimeMillis() + 5 * 60_000;
            handler.postDelayed(() -> { if (mode == Mode.LEARN) stop("5분 학습이 끝났어요"); }, 5 * 60_000);
            captureLearning(generation);
        } else { showTouchPause(true); tick(generation); }
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
            boolean stalled = endTracker.stalled(observation.similarTo(previous));
            previous = observation;
            if ((observation.next != null && (stalled || endTracker.nextReady())) || endTracker.repeatedlyStalled()) {
                if (observation.next == null) { stop("여러 번 내려도 이동을 확인하지 못했어요. 화면을 확인해 주세요"); return; }
                if (!prefs.autoNext()) { stop("끝에 도착했어요. 다음 화를 직접 눌러 주세요"); return; }
                if (SystemClock.uptimeMillis() - lastNext < 15_000) { stop("연속 이동을 막기 위해 멈췄어요"); return; }
                countdownNext(token, new Rect(observation.next)); return;
            }
            readingCharacters = observation.characters; readingSince = SystemClock.uptimeMillis();
            scheduleScroll(token);
        });
    }
    private void scroll(int token) {
        if (!valid(token) || mode != Mode.AUTO) return;
        // Recheck live accessibility nodes at the moment of action, not only before the dwell.
        if (ButtonPolicy.isBlocked(ScreenReader.nodeText(getRootInActiveWindow()))) { stop("안내 화면이 있어 멈췄어요"); return; }
        int h = getResources().getDisplayMetrics().heightPixels, w = getResources().getDisplayMetrics().widthPixels;
        float swipeX = w * .48f;
        if (overlay != null) {
            int[] location = new int[2]; overlay.getLocationOnScreen(location);
            for (float candidate : new float[]{w * .48f, w * .22f, w * .78f, w * .12f, w * .88f}) {
                if (candidate < location[0] - dp(12) || candidate > location[0] + overlay.getWidth() + dp(12)) {
                    swipeX = candidate; break;
                }
            }
        }
        Path path = new Path(); path.moveTo(swipeX, h * .76f); path.lineTo(swipeX, h * .34f);
        endTracker.beginSwipe(SystemClock.uptimeMillis());
        interaction.begin(SystemClock.uptimeMillis(), Math.round(900 / prefs.speed()));
        // Let the input window disappear before injecting our own touch.
        showTouchPause(false);
        boolean accepted = dispatchGesture(new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(path, 100, PaceModel.swipeMillis(prefs.speed()))).build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gesture) {
                if (token != generation || mode != Mode.AUTO) return;
                interaction.completed(SystemClock.uptimeMillis());
                showTouchPause(true);
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
                    || Math.abs(fresh.next.centerX() - expected.centerX()) > dp(40)
                    || Math.abs(fresh.next.centerY() - expected.centerY()) > dp(40)) {
                stop("버튼이나 화면이 바뀌어 멈췄어요"); return;
            }
            if (!valid(token)) return;
            Path path = new Path(); path.moveTo(fresh.next.centerX(), fresh.next.centerY());
            interaction.begin(SystemClock.uptimeMillis(), Math.round(900 / prefs.speed()));
            navigatingUntil = SystemClock.uptimeMillis() + 8000; lastNext = SystemClock.uptimeMillis();
            showTouchPause(false);
            if (overlay != null) overlay.setVisibility(View.INVISIBLE);
            boolean accepted = dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 100, 80)).build(), new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gesture) {
                    if (token != generation || mode != Mode.AUTO) return;
                    interaction.completed(SystemClock.uptimeMillis());
                    if (overlay != null) overlay.setVisibility(View.VISIBLE);
                    showTouchPause(true);
                    previous = null; endTracker.reset(); message("다음 화를 기다리고 있어요");
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
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                interaction.scrolled(eventTime, now);
                endTracker.onScroll(eventTime, event.getScrollDeltaY(), event.getScrollY(), event.getMaxScrollY());
            }
            // Scroll events can come from delayed rendering, not only user input.
            if (direct && interaction.directInput(eventTime)) {
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
        generation++; mode = Mode.OFF; showTouchPause(false); handler.removeCallbacksAndMessages(null);
        captureSerial++; captureBusy = false; pendingScroll = null; endTracker.reset();
        if (overlay != null) overlay.setVisibility(View.VISIBLE);
        previous = null; learningScreen = null; learnedDistance = 0; navigatingUntil = 0;
        interaction.reset(SystemClock.uptimeMillis());
        if (start != null) start.setText("▶"); if (learn != null) learn.setText("손으로 학습"); message(reason);
        if (overlay != null && !"준비 중".equals(reason) && !"종료".equals(reason))
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show();
    }
    void showControls() { showOverlay(); }
    void pauseForSettings() { stop("설정이 바뀌어 멈췄어요"); }
    @Override public void onInterrupt() { stop("접근성 서비스가 중단되었습니다"); }
    @Override public void onDestroy() {
        stop("종료"); hideOverlay(); if (reader != null) reader.close(); instance = null; super.onDestroy();
    }
}
