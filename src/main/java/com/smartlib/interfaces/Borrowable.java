package com.smartlib.interfaces;

import java.time.LocalDate;

/**
 * Kontrak untuk jenis koleksi yang boleh dipinjam.
 * Koleksi yang tidak mengimplementasikan interface ini (misalnya Journal)
 * hanya dapat dibaca di tempat.
 */
public interface Borrowable {

    /** Masa pinjam awal sesuai PRD bagian 8: 7 hari kalender. */
    int DEFAULT_LOAN_PERIOD_DAYS = 7;

    /** Apakah koleksi ini saat ini boleh dipinjam. */
    boolean isBorrowable();

    /** Lama peminjaman dalam hari kalender. Subclass boleh meng-override. */
    default int getLoanPeriodDays() { return DEFAULT_LOAN_PERIOD_DAYS; }

    /** Tanggal jatuh tempo = tanggal pinjam + masa pinjam (PRD 8.2). */
    default LocalDate calculateDueDate(LocalDate borrowDate) {
        if (borrowDate == null) throw new IllegalArgumentException("Tanggal pinjam wajib diisi.");
        return borrowDate.plusDays(getLoanPeriodDays());
    }
}
