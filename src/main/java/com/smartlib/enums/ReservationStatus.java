package com.smartlib.enums;

/** Status antrean/penawaran eksemplar (tabel reservations, PRD bagian 9.5). */
public enum ReservationStatus {
    WAITING("Menunggu"),
    OFFERED("Ditawarkan"),
    FULFILLED("Terpenuhi"),
    CANCELLED("Dibatalkan"),
    EXPIRED("Kedaluwarsa");

    private final String label;

    ReservationStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    /** Antrean yang masih berjalan: ikut dihitung dalam aturan "satu antrean aktif per judul". */
    public boolean isActive() { return this == WAITING || this == OFFERED; }

    /** Anggota hanya boleh membatalkan antrean selama berstatus WAITING (PRD FR-08). */
    public boolean isCancellableByMember() { return this == WAITING; }

    public static ReservationStatus fromDatabase(String value) {
        return EnumParser.parse(ReservationStatus.class, value);
    }

    @Override
    public String toString() { return label; }
}
