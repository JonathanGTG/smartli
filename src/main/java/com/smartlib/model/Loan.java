package com.smartlib.model;

import com.smartlib.enums.LoanStatus;

import java.time.LocalDate;

/** Transaksi peminjaman satu eksemplar buku oleh satu anggota. */
public class Loan {
    private final int id;
    private final int userId;
    private final int copyId;
    private final String itemTitle;   // denormalisasi untuk tampilan
    private final String username;    // denormalisasi untuk tampilan
    private final LocalDate borrowDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;
    private LoanStatus status;

    public Loan(int id, int userId, String username, int copyId, String itemTitle,
                LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate, LoanStatus status) {
        this.id         = id;
        this.userId     = userId;
        this.username   = username;
        this.copyId     = copyId;
        this.itemTitle  = itemTitle;
        this.borrowDate = borrowDate;
        this.dueDate    = dueDate;
        this.returnDate = returnDate;
        this.status     = status;
    }

    public int getId()             { return id; }
    public int getUserId()         { return userId; }
    public String getUsername()    { return username; }
    public int getCopyId()         { return copyId; }
    public String getItemTitle()   { return itemTitle; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate()    { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public LoanStatus getStatus()    { return status; }

    public boolean isOverdue() {
        return status == LoanStatus.ACTIVE && LocalDate.now().isAfter(dueDate);
    }

    public long getOverdueDays() {
        if (!isOverdue()) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
    }

    public void markReturned(LocalDate returnDate) {
        this.returnDate = returnDate;
        this.status     = LoanStatus.COMPLETED;
    }

    @Override
    public String toString() {
        return "Loan{id=" + id + ", user=" + username + ", item='" + itemTitle + "', status=" + status + "}";
    }
}
