package com.smartlib.interfaces;

import java.math.BigDecimal;

/**
 * Strategy untuk menghitung denda keterlambatan.
 *
 * Kontrak untuk semua implementasi:
 *  - overdueDays <= 0 harus menghasilkan BigDecimal.ZERO (denda tidak pernah negatif);
 *  - nominal uang selalu BigDecimal, tidak pernah double.
 *
 * Satu interface fungsional, sehingga implementasi sederhana dapat berupa lambda.
 */
@FunctionalInterface
public interface FinePolicy {
    BigDecimal calculateFine(long overdueDays);
}
