package com.rafael.privatephone;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Preferências e histórico de chamadas filtradas.
 *
 * Fica tudo em SharedPreferences: o app não tem banco, nem rede, nem servidor.
 */
public final class Store {

    private static final String FILE = "socontatos";
    private static final String KEY_ENABLED = "blocking_enabled";
    private static final String KEY_SKIP_CALL_LOG = "skip_call_log";
    private static final String KEY_LOG = "log";

    /** Quantos eventos recentes o histórico guarda. */
    private static final int MAX_LOG = 100;

    private final SharedPreferences prefs;

    public Store(Context context) {
        // Direct-boot aware: a triagem pode acontecer antes do primeiro desbloqueio.
        Context storage = context.createDeviceProtectedStorageContext();
        this.prefs = storage.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public boolean isBlockingEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public void setBlockingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public boolean isSkipCallLog() {
        return prefs.getBoolean(KEY_SKIP_CALL_LOG, true);
    }

    public void setSkipCallLog(boolean skip) {
        prefs.edit().putBoolean(KEY_SKIP_CALL_LOG, skip).apply();
    }

    // ---------------------------------------------------------------- histórico

    public static final class Entry {
        public final long timestamp;
        public final String number;
        public final boolean blocked;
        /** Nome do contato quando a chamada foi liberada; vazio quando bloqueada. */
        public final String name;

        Entry(long timestamp, String number, boolean blocked, String name) {
            this.timestamp = timestamp;
            this.number = number;
            this.blocked = blocked;
            this.name = name;
        }
    }

    public synchronized void record(long timestamp, String number, boolean blocked, String name) {
        JSONArray array = readArray();
        JSONObject item = new JSONObject();
        try {
            item.put("t", timestamp);
            item.put("n", number == null ? "" : number);
            item.put("b", blocked);
            item.put("c", name == null ? "" : name);
        } catch (JSONException e) {
            return;
        }

        JSONArray trimmed = new JSONArray();
        trimmed.put(item);
        for (int i = 0; i < array.length() && trimmed.length() < MAX_LOG; i++) {
            trimmed.put(array.optJSONObject(i));
        }
        prefs.edit().putString(KEY_LOG, trimmed.toString()).apply();
    }

    public List<Entry> history() {
        JSONArray array = readArray();
        List<Entry> out = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            out.add(new Entry(
                    item.optLong("t"),
                    item.optString("n"),
                    item.optBoolean("b"),
                    item.optString("c")));
        }
        return out;
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_LOG).apply();
    }

    private JSONArray readArray() {
        String raw = prefs.getString(KEY_LOG, null);
        if (raw == null) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
