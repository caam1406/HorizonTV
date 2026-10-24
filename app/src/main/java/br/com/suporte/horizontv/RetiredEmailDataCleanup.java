package br.com.suporte.horizontv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

final class RetiredEmailDataCleanup {
    private static final String PREFERENCES = "horizon_imap_account";
    private static final String KEY_ALIAS = "horizon_imap_password_v1";
    private static final String HOME_PREFERENCES = "horizon_home";
    private static final String CLEANUP_KEY = "retired_account_data_cleared";

    private RetiredEmailDataCleanup() { }

    static void clear(Context context) {
        SharedPreferences homePreferences = context.getSharedPreferences(HOME_PREFERENCES, Context.MODE_PRIVATE);
        if (homePreferences.getBoolean(CLEANUP_KEY, false)) return;
        SharedPreferences oldPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        if (!oldPreferences.edit().clear().commit()) {
            Log.w("HorizonTV", "Could not remove retired account preferences.");
            return;
        }
        context.deleteSharedPreferences(PREFERENCES);
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
            boolean migrationSaved = homePreferences.edit()
                    .remove("selected_gmail_account")
                    .remove("gmail_notification_count")
                    .putBoolean(CLEANUP_KEY, true)
                    .commit();
            if (!migrationSaved) Log.w("HorizonTV", "Could not save retired account cleanup state.");
        } catch (GeneralSecurityException | IOException error) {
            Log.w("HorizonTV", "Could not remove retired account credentials: "
                    + error.getClass().getSimpleName());
        }
    }
}
