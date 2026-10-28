package com.rafael.privatephone;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Índice em memória dos números da agenda.
 *
 * O Android dá uns 5 s para a triagem responder; passar disso faz a chamada tocar
 * mesmo que a resposta seja "recusar". Consultar o provedor de contatos a cada
 * chamada chegou a levar mais que isso, então a agenda é lida uma vez, em segundo
 * plano, e relida quando muda (ContentObserver). A triagem só consulta o mapa.
 */
public final class ContactIndex {

    private static final String TAG = "PrivatePhone";

    /** Espera depois de uma mudança na agenda antes de reler: edições vêm em rajadas. */
    private static final long RELOAD_DELAY_MS = 2000;

    private static volatile ContactIndex instance;

    /** Fotografia imutável da agenda; trocada inteira a cada releitura. */
    public static final class Snapshot {
        private final Map<String, String> byE164;
        private final Map<String, String> byKey;

        Snapshot(Map<String, String> byE164, Map<String, String> byKey) {
            this.byE164 = byE164;
            this.byKey = byKey;
        }

        /**
         * @return o nome do contato ("" se ele não tiver nome), ou {@code null} se o
         *         número não estiver na agenda.
         */
        public String find(String number) {
            String e164 = Contacts.toE164(number);
            if (e164 != null) {
                String name = byE164.get(e164);
                if (name != null) return name;
            }
            String key = Contacts.nationalKey(number);
            return key == null ? null : byKey.get(key);
        }

        public int size() {
            return byE164.size();
        }
    }

    private final Context context;
    private final Handler handler;
    private final Runnable reload = this::load;

    private volatile Snapshot snapshot;
    // Só mexidos na thread do handler.
    private boolean observing;

    public static ContactIndex get(Context context) {
        if (instance == null) {
            synchronized (ContactIndex.class) {
                if (instance == null) instance = new ContactIndex(context);
            }
        }
        return instance;
    }

    private ContactIndex(Context context) {
        this.context = context.getApplicationContext();
        HandlerThread thread = new HandlerThread("contact-index");
        thread.start();
        this.handler = new Handler(thread.getLooper());
    }

    /**
     * Carrega a agenda em segundo plano, se ainda não carregou, e passa a vigiá-la.
     * Pode ser chamado à vontade; sem a permissão de contatos não faz nada e tenta
     * de novo na próxima chamada.
     */
    public void start() {
        handler.post(() -> {
            if (!Contacts.hasPermission(context)) return;
            observe();
            if (snapshot == null) load();
        });
    }

    /** @return o índice pronto, ou {@code null} se a primeira carga ainda não terminou. */
    public Snapshot snapshot() {
        return snapshot;
    }

    private void observe() {
        if (observing) return;
        try {
            context.getContentResolver().registerContentObserver(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI, true,
                    new ContentObserver(handler) {
                        @Override
                        public void onChange(boolean selfChange) {
                            handler.removeCallbacks(reload);
                            handler.postDelayed(reload, RELOAD_DELAY_MS);
                        }
                    });
            observing = true;
        } catch (SecurityException e) {
            Log.w(TAG, "sem acesso para vigiar a agenda", e);
        }
    }

    private void load() {
        if (!Contacts.hasPermission(context)) return;
        long start = SystemClock.elapsedRealtime();
        Map<String, String> byE164 = new HashMap<>();
        Map<String, String> byKey = new HashMap<>();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        };
        try (Cursor cursor = context.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String number = cursor.getString(0);
                String normalized = cursor.getString(1);
                String name = cursor.getString(2);
                if (name == null) name = "";

                String e164 = TextUtils.isEmpty(normalized) ? Contacts.toE164(number) : normalized;
                if (e164 != null) byE164.putIfAbsent(e164, name);

                String key = Contacts.nationalKey(number);
                if (key == null) key = Contacts.nationalKey(normalized);
                if (key != null) byKey.putIfAbsent(key, name);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "falha ao ler a agenda", e);
            return;
        }
        snapshot = new Snapshot(byE164, byKey);
        Log.i(TAG, "agenda carregada: " + byE164.size() + " números em "
                + (SystemClock.elapsedRealtime() - start) + " ms");
    }
}
