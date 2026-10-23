package br.com.suporte.horizontv;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ImapCredentialStore {
    private static final String PREFERENCES = "horizon_imap_account";
    private static final String KEY_ALIAS = "horizon_imap_password_v1";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD_CIPHERTEXT = "password_ciphertext";
    private static final String KEY_PASSWORD_IV = "password_iv";
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_PASSWORD_CHARACTERS = 1024;
    private static final int MAX_CIPHERTEXT_BASE64_CHARACTERS = 8192;

    private final SharedPreferences preferences;

    ImapCredentialStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    ImapAccountSettings loadSettings() {
        String host = preferences.getString(KEY_HOST, null);
        String username = preferences.getString(KEY_USERNAME, null);
        if (host == null || username == null) return null;
        try {
            return new ImapAccountSettings(host, preferences.getInt(KEY_PORT, 993), username);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    char[] loadPassword() throws GeneralSecurityException, IOException {
        String ciphertextValue = preferences.getString(KEY_PASSWORD_CIPHERTEXT, null);
        String ivValue = preferences.getString(KEY_PASSWORD_IV, null);
        if (ciphertextValue == null || ivValue == null) return null;
        if (ciphertextValue.length() > MAX_CIPHERTEXT_BASE64_CHARACTERS || ivValue.length() != 16) {
            throw new GeneralSecurityException("Senha salva inválida.");
        }
        byte[] ciphertext;
        byte[] iv;
        try {
            ciphertext = Base64.decode(ciphertextValue, Base64.NO_WRAP);
            iv = Base64.decode(ivValue, Base64.NO_WRAP);
        } catch (IllegalArgumentException error) {
            throw new GeneralSecurityException("Senha salva inválida.", error);
        }
        byte[] plaintext = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            plaintext = cipher.doFinal(ciphertext);
            CharBuffer decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(plaintext));
            char[] password = new char[decoded.remaining()];
            decoded.get(password);
            if (password.length > MAX_PASSWORD_CHARACTERS) {
                Arrays.fill(password, '\0');
                throw new GeneralSecurityException("Senha salva inválida.");
            }
            return password;
        } catch (CharacterCodingException error) {
            throw new GeneralSecurityException("Senha salva inválida.", error);
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            Arrays.fill(iv, (byte) 0);
        }
    }

    void save(ImapAccountSettings settings, char[] password) throws GeneralSecurityException, IOException {
        if (password.length == 0 || password.length > MAX_PASSWORD_CHARACTERS) {
            Arrays.fill(password, '\0');
            throw new GeneralSecurityException("Senha fora do tamanho permitido.");
        }
        ByteBuffer encoded = null;
        byte[] plaintext = null;
        byte[] ciphertext = null;
        byte[] iv = null;
        try {
            encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password));
            plaintext = new byte[encoded.remaining()];
            encoded.get(plaintext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            iv = cipher.getIV();
            ciphertext = cipher.doFinal(plaintext);
            preferences.edit()
                    .putString(KEY_HOST, settings.host)
                    .putInt(KEY_PORT, settings.port)
                    .putString(KEY_USERNAME, settings.username)
                    .putString(KEY_PASSWORD_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(KEY_PASSWORD_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply();
        } catch (CharacterCodingException error) {
            throw new GeneralSecurityException("Não foi possível proteger a senha.", error);
        } finally {
            Arrays.fill(password, '\0');
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            if (ciphertext != null) Arrays.fill(ciphertext, (byte) 0);
            if (iv != null) Arrays.fill(iv, (byte) 0);
            if (encoded != null && encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        }
    }

    void clear() throws GeneralSecurityException, IOException {
        if (!preferences.edit().clear().commit()) {
            throw new IOException("Não foi possível remover os dados locais da conta.");
        }
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            SecretKey existingKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            if (existingKey != null) return existingKey;
            throw new GeneralSecurityException("Chave local indisponível.");
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(keySpec);
        return keyGenerator.generateKey();
    }

    private SecretKey getExistingKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) throw new GeneralSecurityException("Chave local indisponível.");
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (key == null) throw new GeneralSecurityException("Chave local indisponível.");
        return key;
    }
}
