package app.toonpace;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Activity-only banner host. Never instantiated by the accessibility service. */
final class BannerSlot extends LinearLayout {
    /** An ad SDK adapter must obtain required consent before returning an ad view. */
    interface Provider {
        View createView(Activity activity);
        void resume();
        void pause();
        void destroy();
    }
    private Provider provider;
    private final FrameLayout host;
    private final TextView label;
    public BannerSlot(Context context) { this(context, false); }
    BannerSlot(Context context, boolean preview) {
        super(context);
        setOrientation(VERTICAL);
        int pad = Math.round(10 * getResources().getDisplayMetrics().density);
        setPadding(pad, pad, pad, pad);
        setBackgroundColor(Color.rgb(25, 39, 33));
        label = new TextView(context);
        label.setText(preview ? "광고 영역 미리보기 · 현재 광고 없음" : "광고");
        label.setTextColor(Color.rgb(164, 183, 168)); label.setTextSize(12); label.setGravity(Gravity.CENTER);
        addView(label);
        host = new FrameLayout(context); addView(host, new LayoutParams(-1, -2));
        // Release builds consume no space until a real provider is connected.
        if (!preview) setVisibility(GONE);
        else setMinimumHeight(Math.round(60 * getResources().getDisplayMetrics().density));
    }
    void attach(Activity activity, Provider next) {
        destroy();
        provider = next;
        View ad = next == null ? null : next.createView(activity);
        if (ad == null) { setVisibility(GONE); return; }
        label.setText("광고"); host.addView(ad); setVisibility(VISIBLE);
    }
    void resume() { if (provider != null) provider.resume(); }
    void pause() { if (provider != null) provider.pause(); }
    void destroy() {
        if (provider != null) provider.destroy();
        provider = null; host.removeAllViews(); setVisibility(GONE);
    }
}
