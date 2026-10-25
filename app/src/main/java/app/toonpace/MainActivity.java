package app.toonpace;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(14, 23, 21), CARD = Color.rgb(25, 39, 33),
        INK = Color.rgb(240, 244, 230), MUTED = Color.rgb(164, 183, 168), LIME = Color.rgb(188, 242, 126);
    private Prefs prefs;
    private TextView serviceStatus, appsStatus, learningStatus;
    private LinearLayout content;
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs = new Prefs(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(24), dp(38), dp(24), dp(32));
        scroll.addView(content); setContentView(scroll);
        TextView brand = text("TOONPACE  /  툰페이스", 12, LIME); brand.setLetterSpacing(.16f); content.addView(brand);
        TextView title = text("이야기에만\n집중하세요.", 37, INK); title.setTypeface(null, Typeface.BOLD); space(title, 22, 12); content.addView(title);
        TextView intro = text("대사가 많으면 천천히.\n내가 읽는 속도에 맞춰 내려가는 웹툰 도우미.", 16, MUTED);
        intro.setLineSpacing(dp(5), 1); space(intro, 0, 26); content.addView(intro);

        LinearLayout setup = card();
        setup.addView(text("01   읽기 도우미 연결", 18, INK));
        serviceStatus = body(setup, "");
        setup.addView(action("접근성 설정 열기", this::requestAccess));
        body(setup, "화면을 읽고 스크롤하려면 접근성 설정에서 ‘툰페이스 읽기 도우미’를 켜 주세요.");

        LinearLayout apps = card(); apps.addView(text("02   사용할 앱 선택", 18, INK));
        appsStatus = body(apps, "");
        apps.addView(action("웹툰 앱 · 브라우저 선택", this::chooseApps));
        body(apps, "선택한 앱에서만 동작합니다. 브라우저는 웹툰 회차를 연 뒤 시작하세요. 멈추려면 떠 있는 Ⅱ 정지 버튼을 누르세요. 자동 읽기 중 화면을 누르면 일시정지합니다. 첫 터치는 정지에 사용되고, 웹툰 조작은 멈춘 뒤 다시 터치하세요. 자동 스와이프가 진행되는 짧은 동안은 감지가 제한될 수 있어요.");

        LinearLayout pace = card(); pace.addView(text("03   내 속도 찾기", 18, INK));
        body(pace, "웹툰 화면의 떠 있는 버튼에서 ‘손으로 학습’을 누르고 평소처럼 읽어 보세요. 글자량과 스크롤 간격으로 앱별 읽기 속도를 맞춥니다.");
        learningStatus = body(pace, "");
        TextView speedLabel = body(pace, "");
        SeekBar speed = new SeekBar(this); speed.setMax(45); speed.setProgress((int) Math.round((prefs.speed() - .5) * 10));
        speedLabel.setText(String.format(Locale.KOREA, "읽기 속도  %.1f×", prefs.speed()));
        speed.setContentDescription("읽기 속도, 0.5배부터 5배까지");
        speed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                float value = .5f + progress / 10f; prefs.data.edit().putFloat("speed", value).apply();
                speedLabel.setText(String.format(Locale.KOREA, "읽기 속도  %.1f×", value));
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        }); pace.addView(speed);
        Switch next = new Switch(this); next.setText("다음 화 자동 이동"); next.setTextColor(INK); next.setTextSize(16);
        next.setPadding(0, dp(14), 0, dp(12)); next.setChecked(prefs.autoNext());
        next.setOnCheckedChangeListener((button, checked) -> prefs.data.edit().putBoolean("next", checked).apply()); pace.addView(next);
        body(pace, "화면 끝과 ‘다음 화’ 버튼을 확인한 뒤 3초를 기다립니다. 결제·대여·로그인 안내가 보이면 멈춥니다. 무료 회차에서만 사용하고, 서비스의 자동 구매 설정은 먼저 꺼 주세요.");

        Button controls = action("떠 있는 조작 버튼 표시", () -> {
            if (!prefs.consent() || !enabled()) requestAccess();
            else if (ReaderService.instance != null) ReaderService.instance.showControls();
            else new AlertDialog.Builder(this).setMessage("접근성 서비스 연결을 기다린 뒤 다시 눌러 주세요.").setPositiveButton("확인", null).show();
        }); space(controls, 6, 8); content.addView(controls);
        content.addView(action("선택한 웹툰 앱 열기", this::launchApp));

        LinearLayout privacy = card(); privacy.addView(text("내 폰 안에서 배우는 속도", 17, INK));
        body(privacy, "화면 글자 인식은 기기에서 처리합니다. 캡처와 대사는 저장하지 않고, 앱별 속도 수치와 학습 횟수만 저장합니다. 화면의 작은 ▶/Ⅱ 버튼으로 시작·정지하고, ⋮를 끌면 위치를 옮길 수 있습니다. ⋮를 열어 −/+로 속도를 즉시 바꿀 수 있습니다. 학습은 한 번에 최대 5분이며, 너무 짧거나 긴 대기 시간은 제외합니다.");
        privacy.addView(action("읽기 속도 학습 초기화", () -> new AlertDialog.Builder(this)
            .setTitle("학습한 속도를 지울까요?").setMessage("앱별 읽기 속도가 기본값으로 돌아갑니다.")
            .setNegativeButton("취소", null).setPositiveButton("초기화", (d, w) -> {
                if (ReaderService.instance != null) ReaderService.instance.pauseForSettings();
                prefs.resetLearning(); update();
            }).show()));
        privacy.addView(action("접근 동의 철회", () -> {
            prefs.data.edit().putBoolean("consent", false).apply();
            if (ReaderService.instance != null) ReaderService.instance.refresh(); update();
        }));
        TextView footer = text("안드로이드 11 이상 · 실험 버전 0.1.3\n각 서비스의 실제 화면에서 확인이 필요해요.", 12, MUTED);
        space(footer, 16, 0); content.addView(footer);
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private TextView body(LinearLayout parent, String value) {
        TextView t = text(value, 14, MUTED); t.setLineSpacing(dp(4), 1); space(t, 12, 10); parent.addView(t); return t;
    }
    private void space(View view, int top, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); p.bottomMargin = dp(bottom); view.setLayoutParams(p);
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(20), dp(22), dp(20), dp(18));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(CARD); shape.setCornerRadius(dp(22)); c.setBackground(shape);
        space(c, 0, 16); content.addView(c); return c;
    }
    private Button action(String label, Runnable click) {
        Button b = new Button(this); b.setText(label); b.setTextColor(BG); b.setTextSize(14); b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(LIME); bg.setCornerRadius(dp(12)); b.setBackground(bg);
        b.setPadding(dp(12), dp(12), dp(12), dp(12)); b.setMinimumHeight(dp(48)); space(b, 4, 8);
        b.setOnClickListener(v -> click.run()); return b;
    }
    private boolean enabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        String id = new ComponentName(this, ReaderService.class).flattenToString();
        for (AccessibilityServiceInfo service : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
            if (id.equals(service.getId()) || new ComponentName(this, ReaderService.class).flattenToShortString().equals(service.getId())) return true;
        return false;
    }
    private void requestAccess() {
        if (prefs.consent()) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return; }
        new AlertDialog.Builder(this).setTitle("화면 읽기와 조작에 동의해 주세요")
            .setMessage("선택한 앱에서 화면의 글자와 버튼을 읽고, 화면을 캡처해 글자를 인식하며, 스크롤·탭을 실행합니다.\n\n화면과 대사는 저장하거나 전송하지 않습니다. 직접 ‘손으로 학습’을 누른 동안 글자 수와 스크롤 간격을 이용해 읽기 속도 수치만 저장합니다.\n\n접근성 권한은 Android 설정에서 언제든 끌 수 있고, 이 앱에서도 동의를 철회할 수 있습니다.")
            .setNegativeButton("취소", null).setPositiveButton("동의하고 설정 열기", (dialog, which) -> {
                prefs.data.edit().putBoolean("consent", true).apply();
                if (ReaderService.instance != null) ReaderService.instance.refresh();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }).show();
    }
    private List<ResolveInfo> apps() {
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        LinkedHashMap<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (ResolveInfo item : getPackageManager().queryIntentActivities(launcher, 0))
            if (!getPackageName().equals(item.activityInfo.packageName)) unique.put(item.activityInfo.packageName, item);
        List<ResolveInfo> result = new ArrayList<>(unique.values()); Collator collator = Collator.getInstance(Locale.KOREAN);
        result.sort((a, b) -> collator.compare(a.loadLabel(getPackageManager()).toString(), b.loadLabel(getPackageManager()).toString())); return result;
    }
    private void chooseApps() {
        List<ResolveInfo> choices = apps(); String[] labels = new String[choices.size()]; boolean[] checked = new boolean[choices.size()];
        Set<String> selected = prefs.allowed();
        for (int i = 0; i < choices.size(); i++) { ResolveInfo item = choices.get(i);
            labels[i] = item.loadLabel(getPackageManager()) + "\n" + item.activityInfo.packageName; checked[i] = selected.contains(item.activityInfo.packageName); }
        new AlertDialog.Builder(this).setTitle("사용할 웹툰 앱을 선택해 주세요")
            .setMultiChoiceItems(labels, checked, (d, which, value) -> checked[which] = value)
            .setNegativeButton("취소", null).setPositiveButton("저장", (d, which) -> {
                Set<String> updated = new HashSet<>();
                for (int i = 0; i < choices.size(); i++) if (checked[i]) updated.add(choices.get(i).activityInfo.packageName);
                if (ReaderService.instance != null) ReaderService.instance.pauseForSettings();
                prefs.setAllowed(updated); update();
            }).show();
    }
    private void launchApp() {
        List<ResolveInfo> selected = new ArrayList<>();
        for (ResolveInfo app : apps()) if (prefs.allowed().contains(app.activityInfo.packageName)) selected.add(app);
        if (selected.isEmpty()) { chooseApps(); return; }
        String[] labels = new String[selected.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = selected.get(i).loadLabel(getPackageManager()).toString();
        new AlertDialog.Builder(this).setTitle("열 앱을 선택하세요").setItems(labels, (d, which) -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage(selected.get(which).activityInfo.packageName);
            if (launch != null) startActivity(launch);
        }).show();
    }
    private void update() {
        serviceStatus.setText(prefs.consent() && enabled() ? "● 연결됨 · 웹툰 화면에서 시작할 수 있어요" : "○ 연결이 필요해요");
        appsStatus.setText(prefs.allowed().isEmpty() ? "아직 선택한 앱이 없어요" : prefs.allowed().size() + "개 앱에서 사용하도록 설정했어요");
        int count = 0; for (String pkg : prefs.allowed()) count += prefs.model(pkg).samples();
        learningStatus.setText(count == 0 ? "아직 학습 전이에요. 기본 속도로도 시작할 수 있어요." : "선택한 앱에서 " + count + "회 학습했어요.");
    }
    @Override protected void onResume() { super.onResume(); if (prefs != null) update(); }
}
