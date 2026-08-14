package com.rafael.privatephone;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.text.TextUtils;

/**
 * O coração do app.
 *
 * O Android chama {@link #onScreenCall} para cada chamada que chega, antes de o
 * telefone tocar. Quem está na agenda passa; todo o resto é recusado em silêncio.
 */
public class ContactScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details details) {
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
            return;
        }

        // Sem permissão de contatos não dá para saber quem é conhecido. Nesse caso
        // liberamos a chamada de propósito: é melhor deixar passar um desconhecido
        // do que bloquear a família inteira por causa de uma permissão revogada.
        if (!Contacts.hasPermission(this)) {
            allow(details);
            store.record(now(details), number, false, getString(R.string.log_no_permission));
            return;
        }

        String name = Contacts.findName(this, number);
        if (name != null) {
            allow(details);
            store.record(now(details), number, false, name);
        } else {
            block(details, store.isSkipCallLog());
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
