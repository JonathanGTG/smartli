package com.smartlib.enums;

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

    public String getLabel() { return label; }


    public boolean isLoanable() { return loanable; }


    public static ItemStatus fromDatabase(String value) {
        return EnumParser.parse(ItemStatus.class, value);
    }

    @Override
    public String toString() { return label; }
}
