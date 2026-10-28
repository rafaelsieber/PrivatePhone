package com.rafael.privatephone;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

/**
 * Descobre se um número pertence a alguém da agenda.
 */
public final class Contacts {

    /** País usado para completar números salvos sem DDI. */
    private static final String COUNTRY = "BR";

    private Contacts() {}

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Consulta direta ao provedor de contatos. É o caminho lento: a triagem só
     * cai aqui enquanto o {@link ContactIndex} não terminou a primeira carga.
     *
     * @return o nome do contato dono do número, ou {@code null} se ele não estiver na agenda.
     */
    public static String findName(Context context, String number) {
        if (TextUtils.isEmpty(number)) return null;
        if (!hasPermission(context)) return null;

        String name = lookupByProvider(context, number);
        if (name != null) return name;
        return lookupByKey(context, number);
    }

    /** O número em E.164 (+5522998617349), ou {@code null} se não der para normalizar. */
    static String toE164(String number) {
        if (TextUtils.isEmpty(number)) return null;
        String e164 = PhoneNumberUtils.formatNumberToE164(number, COUNTRY);
        return TextUtils.isEmpty(e164) ? null : e164;
    }

    /**
     * Chave de reserva para números brasileiros: DDD + os 8 últimos dígitos, sem
     * DDI, sem o 0 de tronco/código de operadora e sem o nono dígito dos celulares.
     * Assim "(22) 9861-7349", salvo antes do nono dígito, casa com "+55 22 99861-7349".
     *
     * @return os 10 dígitos, ou {@code null} se o número não tiver DDD ou não for do Brasil.
     */
    static String nationalKey(String number) {
        if (TextUtils.isEmpty(number)) return null;
        String digits = digitsOf(number);
        boolean international = number.trim().startsWith("+");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
            international = true;
        }
        if (international) {
            if (!digits.startsWith("55")) return null;
            digits = digits.substring(2);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);                       // 0 de tronco
            if (digits.length() >= 12) digits = digits.substring(2); // código da operadora
        } else if (digits.length() >= 12 && digits.startsWith("55")) {
            digits = digits.substring(2);                       // DDI sem o "+"
        }

        boolean mobileWithNine = digits.length() == 11 && digits.charAt(2) == '9';
        if (digits.length() != 10 && !mobileWithNine) return null;
        return digits.substring(0, 2) + digits.substring(digits.length() - 8);
    }

    /**
     * Caminho principal: o PhoneLookup do próprio Android, que já faz a comparação
     * "esperta" de números (ignora formatação, DDI, etc).
     */
    private static String lookupByProvider(Context context, String number) {
        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
        try (Cursor cursor = context.getContentResolver()
                .query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                return TextUtils.isEmpty(name) ? "" : name;
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    /**
     * Reserva: compara E.164 inteiro e, se não der, a {@link #nationalKey chave
     * nacional}. Resolve casos em que o número chega num formato que o PhoneLookup
     * não casa — no Brasil, tipicamente o nono dígito dos celulares.
     */
    private static String lookupByKey(Context context, String number) {
        String e164 = toE164(number);
        String key = nationalKey(number);
        if (e164 == null && key == null) return null;

        ContentResolver resolver = context.getContentResolver();
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        };
        try (Cursor cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                String saved = cursor.getString(0);
                String normalized = cursor.getString(1);
                boolean match = e164 != null
                        && (e164.equals(normalized) || e164.equals(toE164(saved)));
                if (!match && key != null) {
                    match = key.equals(nationalKey(saved)) || key.equals(nationalKey(normalized));
                }
                if (match) {
                    String name = cursor.getString(2);
                    return TextUtils.isEmpty(name) ? "" : name;
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    private static String digitsOf(String number) {
        StringBuilder digits = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        return digits.toString();
    }
}
