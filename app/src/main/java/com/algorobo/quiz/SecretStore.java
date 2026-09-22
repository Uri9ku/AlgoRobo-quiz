package com.algorobo.quiz;

import android.content.Context;
import android.os.Build;
import android.util.Base64;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * API Key 等敏感信息的加密存储：使用 AndroidKeyStore AES/GCM 硬件保护密钥，
 * 密文以 "enc:" 前缀 + 自定义 Base64 格式（URL_SAFE|NO_WRAP）持久化。
 * 加密结果绑定当前设备的 Keystore，换设备无法解密（配合备份导出时跳过敏感项）。
 */
public final class SecretStore {
    private static final String KS_ALIAS = "algorobo_pref_key";
    private static final String PREFIX = "enc:";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static volatile SecretKey cachedKey;

    private SecretStore() {}

    public static boolean isEncrypted(String s) {
        return s != null && s.startsWith(PREFIX);
    }

    /** 加密明文；失败时原样返回并日志告警（不阻断功能）。 */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            SecretKey key = key();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(PREFIX);
            sb.append(Base64.encodeToString(iv, Base64.NO_WRAP | Base64.NO_PADDING));
            sb.append(":");
            sb.append(Base64.encodeToString(ct, Base64.NO_WRAP | Base64.NO_PADDING));
            return sb.toString();
        } catch (Exception e) {
            android.util.Log.e("SecretStore", "encrypt failed", e);
            return plain;
        }
    }

    /** 解密；非 "enc:" 前缀的值原样返回（兼容历史明文）。 */
    public static String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        try {
            String body = stored.substring(PREFIX.length());
            int sep = body.indexOf(':');
            if (sep <= 0) return "";
            byte[] iv = Base64.decode(body.substring(0, sep), Base64.NO_WRAP | Base64.NO_PADDING);
            byte[] ct = Base64.decode(body.substring(sep + 1), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), "UTF-8");
        } catch (Exception e) {
            android.util.Log.e("SecretStore", "decrypt failed", e);
            return "";
        }
    }

    private static SecretKey key() throws Exception {
        SecretKey local = cachedKey;
        if (local != null) return local;
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        KeyStore.Entry entry = null;
        try {
            entry = ks.getEntry(KS_ALIAS, null);
        } catch (Exception ignore) {}
        if (entry instanceof KeyStore.SecretKeyEntry) {
            local = ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        } else {
            KeyGenerator gen = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            gen.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                    KS_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                            | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(false)
                    .build());
            local = gen.generateKey();
        }
        cachedKey = local;
        return local;
    }
}
