package com.smartlib.enums;

/** Status akun pengguna (PRD bagian 9.1). Akun INACTIVE tidak dapat login. */
public enum AccountStatus {
    ACTIVE("Aktif"),
    INACTIVE("Nonaktif");

    private final String label;

    AccountStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static AccountStatus fromDatabase(String value) {
        return EnumParser.parse(AccountStatus.class, value);
    }

    @Override
    public String toString() { return label; }
}
