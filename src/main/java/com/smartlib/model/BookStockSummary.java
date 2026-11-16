
package com.smartlib.model;

/**
 * Ringkasan stok eksemplar dari sebuah buku.
 * Jumlah stok diperoleh dari query database.
 */
public record BookStockSummary(
        Book book,
        int totalCopies,
        int availableCopies,
        int borrowedCopies
) {

    public BookStockSummary {
        if (book == null) {
            throw new IllegalArgumentException(
                    "Data buku tidak boleh kosong."
            );
        }

        if (totalCopies < 0 || availableCopies < 0
                || borrowedCopies < 0) {
            throw new IllegalArgumentException(
                    "Jumlah eksemplar tidak boleh negatif."
            );
        }

        if (availableCopies + borrowedCopies > totalCopies) {
            throw new IllegalArgumentException(
                    "Jumlah stok tidak sesuai."
            );
        }
    }

    /**
     * Eksemplar yang dicadangkan, rusak, atau hilang.
     */
    public int otherCopies() {
        return totalCopies - availableCopies - borrowedCopies;
    }
}
