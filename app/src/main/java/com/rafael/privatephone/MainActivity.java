package com.rafael.privatephone;

import android.Manifest;
import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tela única: mostra se a proteção está de pé, pede o que falta e lista as
 * chamadas recentes.
 */
public class MainActivity extends Activity {

    private static final int REQUEST_ROLE = 1;
    private static final int REQUEST_CONTACTS = 2;

    private static final int GREEN = 0xFF1B8E3C;
    private static final int RED = 0xFFC62828;
    private static final int AMBER = 0xFFB26A00;

    private Store store;

    private TextView statusView;
    private TextView statusDetailView;
    private Button roleButton;
    private Button contactsButton;
    private Switch blockingSwitch;
    private Switch callLogSwitch;
    private LinearLayout historyContainer;
    private Button clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ------------------------------------------------------------------ layout

    private View buildUi() {
        LinearLayout root = column();
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = text(getString(R.string.app_name), 24, true);
        root.addView(title);

        statusView = text("", 18, true);
        statusView.setPadding(0, dp(16), 0, 0);
        root.addView(statusView);

        statusDetailView = text("", 14, false);
        statusDetailView.setPadding(0, dp(4), 0, 0);
        root.addView(statusDetailView);

        roleButton = new Button(this);
        roleButton.setText(R.string.action_request_role);
        roleButton.setOnClickListener(v -> requestRole());
        root.addView(roleButton, buttonParams());

        contactsButton = new Button(this);
        contactsButton.setText(R.string.action_request_contacts);
        contactsButton.setOnClickListener(v -> requestContacts());
        root.addView(contactsButton, buttonParams());

        root.addView(divider());

        blockingSwitch = new Switch(this);
        blockingSwitch.setText(R.string.switch_blocking);
        blockingSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        blockingSwitch.setPadding(0, dp(12), 0, dp(12));
        blockingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (!button.isPressed()) return;
                store.setBlockingEnabled(checked);
                refresh();
            }
        });
        root.addView(blockingSwitch);

        callLogSwitch = new Switch(this);
        callLogSwitch.setText(R.string.switch_call_log);
        callLogSwitch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        callLogSwitch.setPadding(0, dp(4), 0, dp(12));
        callLogSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (!button.isPressed()) return;
                store.setSkipCallLog(checked);
            }
        });
        root.addView(callLogSwitch);

        root.addView(divider());

        TextView historyTitle = text(getString(R.string.history_title), 18, true);
        historyTitle.setPadding(0, dp(12), 0, dp(4));
        root.addView(historyTitle);

        historyContainer = column();
        root.addView(historyContainer);

        clearButton = new Button(this);
        clearButton.setText(R.string.action_clear_history);
        clearButton.setOnClickListener(v -> {
            store.clearHistory();
            refresh();
        });
        root.addView(clearButton, buttonParams());

        ScrollView scroller = new ScrollView(this);
        scroller.addView(root);
        return scroller;
    }

    // ------------------------------------------------------------------ estado

    private void refresh() {
        boolean roleHeld = isScreeningApp();
        boolean contactsGranted = Contacts.hasPermission(this);
        boolean enabled = store.isBlockingEnabled();

        roleButton.setVisibility(roleHeld ? View.GONE : View.VISIBLE);
        contactsButton.setVisibility(contactsGranted ? View.GONE : View.VISIBLE);

        blockingSwitch.setChecked(enabled);
        callLogSwitch.setChecked(store.isSkipCallLog());

        if (!roleHeld) {
            statusView.setText(R.string.status_inactive);
            statusView.setTextColor(RED);
            statusDetailView.setText(R.string.status_inactive_detail);
        } else if (!contactsGranted) {
            statusView.setText(R.string.status_partial);
            statusView.setTextColor(AMBER);
            statusDetailView.setText(R.string.status_partial_detail);
        } else if (!enabled) {
            statusView.setText(R.string.status_paused);
            statusView.setTextColor(AMBER);
            statusDetailView.setText(R.string.status_paused_detail);
        } else {
            statusView.setText(R.string.status_active);
            statusView.setTextColor(GREEN);
            statusDetailView.setText(R.string.status_active_detail);
        }

        renderHistory();
    }

    private void renderHistory() {
        historyContainer.removeAllViews();
        List<Store.Entry> entries = store.history();
        if (entries.isEmpty()) {
            TextView empty = text(getString(R.string.history_empty), 14, false);
            empty.setPadding(0, dp(8), 0, dp(8));
            historyContainer.addView(empty);
            clearButton.setVisibility(View.GONE);
            return;
        }
        clearButton.setVisibility(View.VISIBLE);

        SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        for (Store.Entry entry : entries) {
            LinearLayout row = column();
            row.setPadding(0, dp(8), 0, dp(8));

            String number = TextUtils.isEmpty(entry.number)
                    ? getString(R.string.number_hidden) : entry.number;
            TextView headline = text(number, 16, true);
            headline.setTextColor(entry.blocked ? RED : GREEN);
            row.addView(headline);

            String label = entry.blocked
                    ? getString(R.string.entry_blocked)
                    : (TextUtils.isEmpty(entry.name)
                        ? getString(R.string.entry_allowed)
                        : getString(R.string.entry_allowed_named, entry.name));
            TextView detail = text(format.format(new Date(entry.timestamp)) + "  ·  " + label, 13, false);
            row.addView(detail);

            historyContainer.addView(row);
            historyContainer.addView(divider());
        }
    }

    private boolean isScreeningApp() {
        RoleManager roles = getSystemService(RoleManager.class);
        return roles != null
                && roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && roles.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    // ------------------------------------------------------------------ ações

    private void requestRole() {
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            // Alguns fabricantes não expõem o papel de triagem. Sobra abrir os
            // ajustes de apps padrão para o usuário escolher na mão.
            openAppSettings();
            return;
        }
        startActivityForResult(
                roles.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQUEST_ROLE);
    }

    private void requestContacts() {
        requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQUEST_CONTACTS);
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ROLE) refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CONTACTS) refresh();
    }

    // ------------------------------------------------------------------ helpers

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private TextView text(String value, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.START);
        return view;
    }

    private View divider() {
        View view = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        params.topMargin = dp(8);
        view.setLayoutParams(params);
        view.setBackgroundColor(Color.argb(60, 128, 128, 128));
        return view;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
