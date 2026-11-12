package com.smartlib.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Strategi aktif SmartLib: denda dihitung per hari setelah tanggal jatuh tempo. */
public class DailyFinePolicy implements FinePolicy {
    private final BigDecimal ratePerDay;

    public DailyFinePolicy(BigDecimal ratePerDay) {
        this.ratePerDay = Objects.requireNonNull(ratePerDay, "Tarif denda wajib diisi.");
        if (ratePerDay.signum() < 0) throw new IllegalArgumentException("Tarif denda tidak boleh negatif.");
    }

    @Override
    public int overdueDays(LocalDate dueDate, LocalDate returnDate) {
        Objects.requireNonNull(dueDate, "Tanggal jatuh tempo wajib diisi.");
        Objects.requireNonNull(returnDate, "Tanggal pengembalian wajib diisi.");
        return Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(dueDate, returnDate)));
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
}
