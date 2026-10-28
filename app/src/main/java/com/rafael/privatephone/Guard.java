package com.rafael.privatephone;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/**
 * Vigia se o filtro continua de pé: o papel de triagem e a permissão de contatos.
 *
 * Se um dos dois sumir, as chamadas passam a entrar todas — e o usuário não teria
 * como saber. Por isso fica uma notificação persistente até a situação voltar ao
 * normal. A checagem roda ao abrir o app, a cada triagem, no boot e uma vez por dia.
 */
public final class Guard {

    private static final String CHANNEL = "filtro";
    private static final int NOTIFICATION_ID = 1;
    private static final int JOB_ID = 1;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Guard() {}

    public static boolean isScreeningApp(Context context) {
        RoleManager roles = context.getSystemService(RoleManager.class);
        return roles != null
                && roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    /**
     * Confere papel e permissão; mostra ou tira a notificação de filtro desligado.
     *
     * @return {@code true} se o filtro está de pé.
     */
    public static boolean check(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null) return true;

        boolean roleHeld = isScreeningApp(context);
        boolean contactsGranted = Contacts.hasPermission(context);
        if (roleHeld && contactsGranted) {
            notifications.cancel(NOTIFICATION_ID);
            return true;
        }

        notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                context.getString(R.string.notify_channel), NotificationManager.IMPORTANCE_DEFAULT));

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String detail = context.getString(roleHeld
                ? R.string.notify_off_no_contacts : R.string.notify_off_no_role);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(context.getString(R.string.notify_off_title))
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setContentIntent(tap)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        notifications.notify(NOTIFICATION_ID, notification);
        return false;
    }

    /** Agenda a checagem diária, se ainda não estiver agendada. Sobrevive a reboot. */
    public static void scheduleDaily(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.getPendingJob(JOB_ID) != null) return;
        scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, GuardJob.class))
                .setPeriodic(DAY_MS)
                .setPersisted(true)
                .build());
    }
}
