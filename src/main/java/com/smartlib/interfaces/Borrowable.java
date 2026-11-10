package com.smartlib.interfaces;

import java.time.LocalDate;


public interface Borrowable {


    int DEFAULT_LOAN_PERIOD_DAYS = 7;


    boolean isBorrowable();


    default int getLoanPeriodDays() { return DEFAULT_LOAN_PERIOD_DAYS; }


    default LocalDate calculateDueDate(LocalDate borrowDate) {
        if (borrowDate == null) throw new IllegalArgumentException("Tanggal pinjam wajib diisi.");
        return borrowDate.plusDays(getLoanPeriodDays());
    }
}
