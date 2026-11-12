package com.smartlib.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void hashBukanTeksBiasaDanBerbedaTiapKali() {
        String h1 = PasswordHasher.hash("Rahasia#1".toCharArray());
        String h2 = PasswordHasher.hash("Rahasia#1".toCharArray());
        assertFalse(h1.contains("Rahasia#1"));
        assertNotEquals(h1, h2);            // salt acak
    }

    @Test
    void verifyBenarDanSalah() {
        String h = PasswordHasher.hash("Rahasia#1".toCharArray());
        assertTrue(PasswordHasher.verify("Rahasia#1".toCharArray(), h));
        assertFalse(PasswordHasher.verify("rahasia#1".toCharArray(), h));
    }

    @Test
    void formatHashRusakDitolakTanpaException() {
        assertFalse(PasswordHasher.verify("x".toCharArray(), "bukan-hash"));
        assertFalse(PasswordHasher.verify("x".toCharArray(), null));
    }
}
