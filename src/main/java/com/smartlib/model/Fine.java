package com.smartlib.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Denda keterlambatan yang terkait dengan satu transaksi peminjaman. */
public class Fine {
    private final int id;
    private final int loanId;
    private final String itemTitle;   // denormalisasi untuk tampilan
    private final String username;    // denormalisasi untuk tampilan
    private final BigDecimal amount;
    private boolean paid;
    private LocalDateTime paidAt;

    public Fine(int id, int loanId, String username, String itemTitle,
                BigDecimal amount, boolean paid, LocalDateTime paidAt) {
        this.id        = id;
        this.loanId    = loanId;
        this.username  = username;
        this.itemTitle = itemTitle;
        this.amount    = amount;
        this.paid      = paid;
        this.paidAt    = paidAt;
    }

    public int getId()              { return id; }
    public int getLoanId()          { return loanId; }
    public String getUsername()     { return username; }
    public String getItemTitle()    { return itemTitle; }
    public BigDecimal getAmount()   { return amount; }
    public boolean isPaid()         { return paid; }
    public LocalDateTime getPaidAt(){ return paidAt; }

    public void markPaid(LocalDateTime at) {
        this.paid   = true;
        this.paidAt = at;
    }

    @Override
    public String toString() {
        return "Fine{id=" + id + ", loan=" + loanId + ", amount=" + amount + ", paid=" + paid + "}";
    }
}
