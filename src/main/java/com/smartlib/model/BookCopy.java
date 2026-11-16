package com.smartlib.model;

import com.smartlib.enums.ItemStatus;

/**
 * Satu eksemplar fisik dari sebuah judul buku (tabel book_copies).
 * Satu judul (Book) dapat memiliki banyak eksemplar.
 * Status eksemplar memakai enum ItemStatus (Tersedia, Dipinjam, dst.).
 */
public class BookCopy {
    private int id;                 // 0 = belum disimpan ke database
    private int itemId;             // id judul pada tabel library_items
    private String barcode;
    private ItemStatus status;
    private String shelfLocation;

    public BookCopy(int id, int itemId, String barcode, ItemStatus status, String shelfLocation) {
        if (id < 0) throw new IllegalArgumentException("ID tidak boleh negatif.");
        this.id = id;
        setItemId(itemId);
        setBarcode(barcode);
        setStatus(status);
        setShelfLocation(shelfLocation);
    }

    /** Untuk eksemplar baru yang belum disimpan (id = 0). */
    public BookCopy(int itemId, String barcode, ItemStatus status, String shelfLocation) {
        this(0, itemId, barcode, status, shelfLocation);
    }

    public int getId() { return id; }
    public int getItemId() { return itemId; }
    public String getBarcode() { return barcode; }
    public ItemStatus getStatus() { return status; }
    public String getShelfLocation() { return shelfLocation; }

    public boolean isAvailable() { return status == ItemStatus.AVAILABLE; }

    public void assignId(int generatedId) {
        if (id != 0) throw new IllegalStateException("ID sudah ditetapkan.");
        if (generatedId <= 0) throw new IllegalArgumentException("ID hasil generate harus positif.");
        this.id = generatedId;
    }

    public final void setItemId(int itemId) {
        if (itemId <= 0) throw new IllegalArgumentException("Eksemplar harus terhubung ke sebuah buku.");
        this.itemId = itemId;
    }

    public final void setBarcode(String barcode) {
        if (barcode == null || barcode.isBlank())
            throw new IllegalArgumentException("Barcode eksemplar wajib diisi.");
        String code = barcode.trim().toUpperCase();
        if (code.length() > 50)
            throw new IllegalArgumentException("Barcode eksemplar maksimal 50 karakter.");
        this.barcode = code;
    }

    public final void setStatus(ItemStatus status) {
        if (status == null) throw new IllegalArgumentException("Status eksemplar wajib diisi.");
        this.status = status;
    }

    public final void setShelfLocation(String shelfLocation) {
        String value = (shelfLocation == null) ? "" : shelfLocation.trim();
        if (value.length() > 50)
            throw new IllegalArgumentException("Lokasi rak maksimal 50 karakter.");
        this.shelfLocation = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BookCopy other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() { return id != 0 ? Integer.hashCode(id) : System.identityHashCode(this); }

    @Override
    public String toString() { return barcode + " (" + status.getLabel() + ")"; }
}