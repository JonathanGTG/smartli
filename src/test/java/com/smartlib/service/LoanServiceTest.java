package com.smartlib.service;

import com.smartlib.enums.LoanStatus;
import com.smartlib.model.BorrowReceipt;
import com.smartlib.model.Loan;
import com.smartlib.model.ReturnReceipt;
import com.smartlib.policy.DailyFinePolicy;
import com.smartlib.policy.FinePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-test untuk aturan bisnis peminjaman, model Loan, dan receipt (PRD Bagian 8).
 * Tidak membutuhkan koneksi database MySQL.
 */
class LoanServiceTest {

    @Test
    @DisplayName("Konstanta aturan peminjaman sesuai PRD Bagian 8")
    void konstantaAturanSesuaiPRD() {
        assertEquals(3, LoanService.MAX_ACTIVE_BOOKS, "Maksimal peminjaman aktif harus 3");
        assertEquals(7, LoanService.LOAN_PERIOD_DAYS, "Masa peminjaman harus 7 hari");
        assertEquals(0, new BigDecimal("2000.00").compareTo(LoanService.DAILY_FINE_RATE),
                "Tarif denda harian harus Rp2.000");
    }

    @Test
    @DisplayName("Model Loan: status aktif belum jatuh tempo tidak dianggap overdue")
    void loanBelumJatuhTempo() {
        Loan loan = new Loan(1, 1, "anggota1", 10, "Clean Code",
                LocalDate.now().minusDays(2),
                LocalDate.now().plusDays(5),
                null,
                LoanStatus.ACTIVE);

        assertFalse(loan.isOverdue());
        assertEquals(0, loan.getOverdueDays());
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
    }

    @Test
    @DisplayName("Model Loan: status aktif melewati jatuh tempo terdeteksi overdue")
    void loanMelewatiJatuhTempo() {
        Loan loan = new Loan(2, 1, "anggota1", 10, "Clean Code",
                LocalDate.now().minusDays(10),
                LocalDate.now().minusDays(3),
                null,
                LoanStatus.ACTIVE);

        assertTrue(loan.isOverdue());
        assertEquals(3, loan.getOverdueDays());
    }

    @Test
    @DisplayName("Model Loan: pengembalian buku mengubah status menjadi COMPLETED")
    void loanMarkReturnedSelesai() {
        Loan loan = new Loan(3, 1, "anggota1", 10, "Clean Code",
                LocalDate.now().minusDays(5),
                LocalDate.now().plusDays(2),
                null,
                LoanStatus.ACTIVE);

        loan.markReturned(LocalDate.now());
        assertEquals(LoanStatus.COMPLETED, loan.getStatus());
        assertEquals(LocalDate.now(), loan.getReturnDate());
        assertFalse(loan.isOverdue());
    }

    @Test
    @DisplayName("Receipt peminjaman mencatat detail transaksi dengan benar")
    void borrowReceiptData() {
        LocalDate due = LocalDate.now().plusDays(7);
        BorrowReceipt receipt = new BorrowReceipt(100L, 200L, due, false);
        assertEquals(100L, receipt.loanId());
        assertEquals(200L, receipt.loanItemId());
        assertEquals(due, receipt.dueDate());
        assertFalse(receipt.fromReservation());
    }

    @Test
    @DisplayName("Receipt pengembalian mencatat denda dan penawaran antrean")
    void returnReceiptData() {
        ReturnReceipt receipt = new ReturnReceipt(200L, 2, new BigDecimal("4000.00"), true, 50L);
        assertEquals(200L, receipt.loanItemId());
        assertEquals(2, receipt.overdueDays());
        assertEquals(new BigDecimal("4000.00"), receipt.fineAmount());
        assertTrue(receipt.copyReservedForNextMember());
        assertEquals(50L, receipt.nextReservationId());
    }

    @Test
    @DisplayName("Inisialisasi LoanService dengan FinePolicy kustom")
    void initLoanServiceCustomPolicy() {
        FinePolicy customPolicy = new DailyFinePolicy(new BigDecimal("1000.00"));
        LoanService service = new LoanService(customPolicy);
        assertNotNull(service);
    }
}
