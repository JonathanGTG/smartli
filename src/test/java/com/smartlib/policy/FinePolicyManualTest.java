package com.smartlib.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Tes manual sederhana; jalankan main() untuk memeriksa aturan denda tanpa database. */
public class FinePolicyManualTest {
    public static void main(String[] args) {
        FinePolicy daily = new DailyFinePolicy(new BigDecimal("2000.00"));
        LocalDate dueDate = LocalDate.of(2026, 11, 10);

        assert daily.overdueDays(dueDate, LocalDate.of(2026, 11, 10)) == 0;
        assert daily.calculateFine(0).compareTo(new BigDecimal("0.00")) == 0;
        assert daily.overdueDays(dueDate, LocalDate.of(2026, 11, 14)) == 4;
        assert daily.calculateFine(4).compareTo(new BigDecimal("8000.00")) == 0;

        FinePolicy grace = new GracePeriodFinePolicy(new BigDecimal("2000.00"), 2);
        assert grace.overdueDays(dueDate, LocalDate.of(2026, 11, 12)) == 0;
        assert grace.overdueDays(dueDate, LocalDate.of(2026, 11, 13)) == 1;

        System.out.println("Semua tes FinePolicy berhasil.");
    }
}
