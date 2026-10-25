package app.toonpace;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

final class Prefs {
    final SharedPreferences data;
    Prefs(Context context) { data = context.getSharedPreferences("toonpace", Context.MODE_PRIVATE); }
    boolean consent() { return data.getBoolean("consent", false); }
    boolean autoNext() { return data.getBoolean("next", false); }
    double speed() { return data.getFloat("speed", 1f); }
    Set<String> allowed() { return new HashSet<>(data.getStringSet("allowed", new HashSet<>())); }
    void setAllowed(Set<String> packages) { data.edit().putStringSet("allowed", new HashSet<>(packages)).apply(); }
    PaceModel model(String pkg) {
        return new PaceModel(data.getFloat(pkg + ".visual", 2.8f),
            data.getFloat(pkg + ".text", 6f), data.getInt(pkg + ".samples", 0));
    }
    void save(String pkg, PaceModel model) {
        data.edit().putFloat(pkg + ".visual", (float) model.visualSeconds())
            .putFloat(pkg + ".text", (float) model.textSecondsPer80())
            .putInt(pkg + ".samples", model.samples()).apply();
    }
    void resetLearning() {
        SharedPreferences.Editor editor = data.edit();
        for (String key : data.getAll().keySet())
            if (key.endsWith(".visual") || key.endsWith(".text") || key.endsWith(".samples")) editor.remove(key);
        editor.apply();
    }
}
