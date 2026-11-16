package com.smartlib.service;

import com.smartlib.model.Fine;
import com.smartlib.policy.DailyFinePolicy;
import com.smartlib.policy.FinePolicy;
import com.smartlib.policy.GracePeriodFinePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-test kebijakan denda (FinePolicy) dan kalkulasi keterlambatan (PRD Bagian 8.3).
 * Tidak membutuhkan koneksi database MySQL.
 */
class FineServiceTest {

    @Test
    @DisplayName("DailyFinePolicy: denda tepat waktu adalah 0")
    void dailyPolicyTepatWaktu() {
        DailyFinePolicy policy = new DailyFinePolicy(new BigDecimal("2000.00"));
        assertEquals(0, policy.overdueDays(LocalDate.now(), LocalDate.now()));
        assertEquals(0, BigDecimal.ZERO.compareTo(policy.calculateFine(0)));
    }

    @Test
    @DisplayName("DailyFinePolicy: keterlambatan 3 hari menghasilkan denda Rp6.000")
    void dailyPolicyTerlambat() {
        DailyFinePolicy policy = new DailyFinePolicy(new BigDecimal("2000.00"));
        LocalDate due = LocalDate.now().minusDays(3);
        LocalDate returnDate = LocalDate.now();

        int overdueDays = policy.overdueDays(due, returnDate);
        assertEquals(3, overdueDays);

        BigDecimal fine = policy.calculateFine(overdueDays);
        assertEquals(0, new BigDecimal("6000.00").compareTo(fine));
    }

    @Test
    @DisplayName("GracePeriodFinePolicy: dalam toleransi denda Rp0")
    void gracePeriodDalamToleransi() {
        GracePeriodFinePolicy policy = new GracePeriodFinePolicy(new BigDecimal("2000.00"), 2);
        // Terlambat 2 hari (masih dalam grace period 2 hari) -> overdueDays = 0
        int overdueDays = policy.overdueDays(LocalDate.now().minusDays(2), LocalDate.now());
        assertEquals(0, overdueDays);
        assertEquals(0, BigDecimal.ZERO.compareTo(policy.calculateFine(overdueDays)));
    }

    @Test
    @DisplayName("GracePeriodFinePolicy: melebihi toleransi dihitung selisihnya")
    void gracePeriodMelebihiToleransi() {
        GracePeriodFinePolicy policy = new GracePeriodFinePolicy(new BigDecimal("2000.00"), 2);
        // Terlambat 4 hari (grace period 2 hari) -> dihitung terlambat 2 hari -> denda 2 x 2000 = 4000
        int overdueDays = policy.overdueDays(LocalDate.now().minusDays(4), LocalDate.now());
        assertEquals(2, overdueDays);
        assertEquals(0, new BigDecimal("4000.00").compareTo(policy.calculateFine(overdueDays)));
    }

    @Test
    @DisplayName("Model Fine: status awal dan perubahan saat pembayaran")
    void fineModelPembayaran() {
        Fine fine = new Fine(1, 10, "anggota1", "Clean Code", new BigDecimal("6000.00"), false, null);
        assertFalse(fine.isPaid());
        assertNull(fine.getPaidAt());
        assertEquals(new BigDecimal("6000.00"), fine.getAmount());

        LocalDateTime now = LocalDateTime.now();
        fine.markPaid(now);
        assertTrue(fine.isPaid());
        assertEquals(now, fine.getPaidAt());
    }

    @Test
    @DisplayName("FineService menghitung hari keterlambatan dan denda dengan tepat")
    void fineServiceKalkulasi() {
        FineService service = new FineService();
        LocalDate due = LocalDate.now().minusDays(5);
        LocalDate ret = LocalDate.now();

        int days = service.calculateOverdueDays(due, ret);
        assertEquals(5, days);

        BigDecimal amount = service.calculateFine(days);
        assertEquals(0, new BigDecimal("10000.00").compareTo(amount));
    }
}
