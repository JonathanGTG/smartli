package com.smartlib.enums;


public enum ReservationStatus {
    WAITING("Menunggu"),
    OFFERED("Ditawarkan"),
    FULFILLED("Terpenuhi"),
    CANCELLED("Dibatalkan"),
    EXPIRED("Kedaluwarsa");

    private final String label;

    ReservationStatus(String label) { this.label = label; }

    public String getLabel() { return label; }


    public boolean isActive() { return this == WAITING || this == OFFERED; }


    public boolean isCancellableByMember() { return this == WAITING; }

    public static ReservationStatus fromDatabase(String value) {
        return EnumParser.parse(ReservationStatus.class, value);
    }

    @Override
    public String toString() { return label; }
}
