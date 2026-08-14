package com.rafael.privatephone;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;

/**
 * Descobre se um número pertence a alguém da agenda.
 */
public final class Contacts {

    /** Quantos dígitos finais são comparados na checagem de reserva. */
    private static final int TAIL_DIGITS = 8;

    private Contacts() {}

    public static boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @return o nome do contato dono do número, ou {@code null} se ele não estiver na agenda.
     */
    public static String findName(Context context, String number) {
        if (TextUtils.isEmpty(number)) return null;
        if (!hasPermission(context)) return null;

        String name = lookupByProvider(context, number);
        if (name != null) return name;
        return lookupByTail(context, number);
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
     * Reserva: compara os últimos dígitos. Resolve casos em que o número chega num
     * formato que o PhoneLookup não casa — no Brasil, tipicamente o nono dígito dos
     * celulares e o DDD/DDI presente numa ponta e ausente na outra.
     */
    private static String lookupByTail(Context context, String number) {
        String tail = tail(number);
        if (tail == null) return null;

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
                if (tail.equals(tail(cursor.getString(0)))
                        || tail.equals(tail(cursor.getString(1)))) {
                    String name = cursor.getString(2);
                    return TextUtils.isEmpty(name) ? "" : name;
                }
            }
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    /** Só os dígitos, e só os {@value #TAIL_DIGITS} últimos. */
    private static String tail(String number) {
        if (number == null) return null;
        StringBuilder digits = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c >= '0' && c <= '9') digits.append(c);
        }
        if (digits.length() < TAIL_DIGITS) return null;
        return digits.substring(digits.length() - TAIL_DIGITS);
    }
}
