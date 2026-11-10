package com.smartlib.enums;

/** Status transaksi pinjaman (tabel loans, PRD bagian 9.3). */
public enum LoanStatus {
    ACTIVE("Aktif"),
    COMPLETED("Selesai"),
    CANCELLED("Dibatalkan");

    private final String label;

    LoanStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** Transaksi yang sudah selesai atau dibatalkan tidak boleh diubah lagi. */
    public boolean isFinal() { return this != ACTIVE; }

    public static LoanStatus fromDatabase(String value) {
        return EnumParser.parse(LoanStatus.class, value);
    }

    @Override
    public String toString() { return label; }
}
