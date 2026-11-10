package com.smartlib.enums;

/** Pembantu internal paket enums agar parsing nilai database konsisten. */
final class EnumParser {
    private EnumParser() { }

    static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Nilai " + type.getSimpleName() + " tidak boleh kosong.");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Nilai '" + value + "' tidak dikenal untuk " + type.getSimpleName() + ".", e);
        }
    }
}
