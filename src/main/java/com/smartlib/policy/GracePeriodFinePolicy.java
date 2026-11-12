package com.smartlib.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Strategi alternatif untuk demonstrasi Strategy Pattern.
 * Strategi ini memberi masa tenggang sebelum denda mulai dihitung.
 * Tidak menjadi kebijakan default SmartLib.
 */
public class GracePeriodFinePolicy implements FinePolicy {
    private final BigDecimal ratePerDay;
    private final int gracePeriodDays;

    public GracePeriodFinePolicy(BigDecimal ratePerDay, int gracePeriodDays) {
        this.ratePerDay = Objects.requireNonNull(ratePerDay, "Tarif denda wajib diisi.");
        if (ratePerDay.signum() < 0) throw new IllegalArgumentException("Tarif denda tidak boleh negatif.");
        if (gracePeriodDays < 0) throw new IllegalArgumentException("Masa tenggang tidak boleh negatif.");
        this.gracePeriodDays = gracePeriodDays;
    }

    @Override
    public int overdueDays(LocalDate dueDate, LocalDate returnDate) {
        Objects.requireNonNull(dueDate, "Tanggal jatuh tempo wajib diisi.");
        Objects.requireNonNull(returnDate, "Tanggal pengembalian wajib diisi.");
        long days = ChronoUnit.DAYS.between(dueDate, returnDate) - gracePeriodDays;
        return Math.toIntExact(Math.max(0, days));
    }

    @Override
    public BigDecimal calculateFine(int overdueDays) {
        if (overdueDays < 0) throw new IllegalArgumentException("Hari keterlambatan tidak boleh negatif.");
        return ratePerDay.multiply(BigDecimal.valueOf(overdueDays));
    }

    @Override
    public BigDecimal ratePerDay() {
        return ratePerDay;
    }

    public int gracePeriodDays() {
        return gracePeriodDays;
    }
}
