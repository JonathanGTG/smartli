package com.smartlib.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashing password memakai PBKDF2-HMAC-SHA256 + salt acak (bawaan JDK, tanpa library tambahan).
 * Format tersimpan: pbkdf2$iterasi$salt(Base64)$hash(Base64)
 */
public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static String hash(char[] password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder enc = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(hash);
    }

    public static boolean verify(char[] password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations);
            return MessageDigest.isEqual(expected, actual);   // perbandingan waktu-konstan
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritma hashing tidak tersedia.", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Dipakai untuk membuat hash seed SQL: java ... PasswordHasher <password> */
    public static void main(String[] args) {
        for (String p : args) System.out.println(hash(p.toCharArray()));
    }
}
