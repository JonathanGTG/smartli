package com.smartlib.model;

/**
 * Kriteria pencarian katalog buku. Semua kriteria bersifat opsional dan digabung dengan AND.
 *
 * @param titleOrIsbn   kata kunci judul atau ISBN (mengandung, tidak peka huruf besar/kecil)
 * @param authorName    kata kunci nama penulis
 * @param categoryId    id kategori, null = semua kategori
 * @param onlyAvailable true = hanya buku yang masih punya eksemplar tersedia
 */
public record BookFilter(String titleOrIsbn, String authorName, Integer categoryId, boolean onlyAvailable) {

    /** Tanpa filter: tampilkan semua buku. */
    public static BookFilter none() { return new BookFilter("", "", null, false); }

    public boolean hasTitle()  { return titleOrIsbn != null && !titleOrIsbn.isBlank(); }
    public boolean hasAuthor() { return authorName != null && !authorName.isBlank(); }
}