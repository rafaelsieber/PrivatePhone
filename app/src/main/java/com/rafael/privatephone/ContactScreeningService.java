package com.rafael.privatephone;

import android.net.Uri;
import android.os.SystemClock;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.text.TextUtils;
import android.util.Log;

/**
 * O coração do app.
 *
 * O Android chama {@link #onScreenCall} para cada chamada que chega, antes de o
 * telefone tocar. Quem está na agenda passa; todo o resto é recusado em silêncio.
 * A resposta precisa sair em bem menos de ~5 s, ou a chamada toca de qualquer
 * jeito — por isso a agenda vem do {@link ContactIndex}, em memória.
 */
public class ContactScreeningService extends CallScreeningService {

    private static final String TAG = "PrivatePhone";

    @Override
    public void onCreate() {
        super.onCreate();
        ContactIndex.get(this).start();
    }

    @Override
    public void onScreenCall(Call.Details details) {
        long start = SystemClock.elapsedRealtime();
        Store store = new Store(this);

        String number = numberOf(details);
        boolean incoming = details.getCallDirection() == Call.Details.DIRECTION_INCOMING;

        // Chamada que sai do próprio aparelho nunca é filtrada.
        if (!incoming) {
            allow(details);
            return;
        }

        // Filtro desligado pelo usuário: passa tudo, sem registrar nada.
        if (!store.isBlockingEnabled()) {
            allow(details);
            log(details, start, number, false, "filtro pausado");
            return;
        }

        // Sem permissão de contatos não dá para saber quem é conhecido. Nesse caso
        // liberamos a chamada de propósito: é melhor deixar passar um desconhecido
        // do que bloquear a família inteira por causa de uma permissão revogada.
        // Mas não em silêncio: o Guard deixa uma notificação de filtro desligado.
        if (!Contacts.hasPermission(this)) {
            allow(details);
            log(details, start, number, false, "sem permissão de contatos");
            store.record(now(details), number, false, getString(R.string.log_no_permission));
            Guard.check(this);
            return;
        }

        ContactIndex.Snapshot index = ContactIndex.get(this).snapshot();
        String source = index != null ? "índice" : "consulta direta";
        String name = index != null ? index.find(number) : Contacts.findName(this, number);
        if (name != null) {
            allow(details);
            log(details, start, number, false, "na agenda (" + source + ")");
            store.record(now(details), number, false, name);
        } else {
            block(details, store.isSkipCallLog());
            log(details, start, number, true, TextUtils.isEmpty(number)
                    ? "número oculto" : "fora da agenda (" + source + ")");
            store.record(now(details), number, true, "");
        }
    }

    private void allow(Call.Details details) {
        respondToCall(details, new CallResponse.Builder().build());
    }

    private void block(Call.Details details, boolean skipCallLog) {
        CallResponse response = new CallResponse.Builder()
                .setDisallowCall(true)   // a chamada não chega ao aparelho
                .setRejectCall(true)     // desliga na operadora, em vez de só silenciar
                .setSilenceCall(true)    // nenhum toque, nem por um instante
                .setSkipCallLog(skipCallLog)
                .setSkipNotification(true)
                .build();
        respondToCall(details, response);
    }

    /**
     * Uma linha por chamada no logcat: decisão, motivo, quanto a triagem levou e
     * quanto passou desde que a chamada chegou ao aparelho.
     */
    private static void log(Call.Details details, long start, String number,
                            boolean blocked, String reason) {
        long elapsed = SystemClock.elapsedRealtime() - start;
        long creation = details.getCreationTimeMillis();
        String sinceArrival = creation > 0
                ? ", " + (System.currentTimeMillis() - creation) + " ms desde a chegada" : "";
        Log.i(TAG, (blocked ? "recusada " : "liberada ")
                + (TextUtils.isEmpty(number) ? "(oculto)" : number)
                + ": " + reason + " — resposta em " + elapsed + " ms" + sinceArrival);
    }

    private static String numberOf(Call.Details details) {
        Uri handle = details.getHandle();
        if (handle == null) return "";
        String number = handle.getSchemeSpecificPart();
        return TextUtils.isEmpty(number) ? "" : number;
    }

    private static long now(Call.Details details) {
        long creation = details.getCreationTimeMillis();
        return creation > 0 ? creation : System.currentTimeMillis();
    }
}
