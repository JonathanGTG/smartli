package com.smartlib.enums;

/**
 * Status sebuah eksemplar fisik (tabel item_copies).
 * Nilai enum sama persis dengan string yang disimpan di database (PRD bagian 9.2).
 */
public enum ItemStatus {
    AVAILABLE("Tersedia", true),
    BORROWED("Dipinjam", false),
    RESERVED("Dicadangkan", false),
    DAMAGED("Rusak", false),
    LOST("Hilang", false);

    private final String label;
    private final boolean loanable;

    ItemStatus(String label, boolean loanable) {
        this.label = label;
        this.loanable = loanable;
    }

    /** Teks untuk ditampilkan di UI (JTable, JComboBox). */
    public String getLabel() { return label; }

    /** True jika eksemplar dengan status ini boleh langsung dipinjamkan. */
    public boolean isLoanable() { return loanable; }

    /** Membaca nilai dari kolom database dan menolak string yang tidak dikenal. */
    public static ItemStatus fromDatabase(String value) {
        return EnumParser.parse(ItemStatus.class, value);
    }

    @Override
    public String toString() { return label; }
}
