package com.smartlib.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Kelas dasar abstrak semua jenis koleksi (tabel library_items).
 * Satu LibraryItem adalah satu JUDUL; eksemplar fisiknya (BookCopy) ada di modul lain.
 *
 * Semua setter bersifat final supaya aman dipanggil dari constructor
 * tanpa risiko di-override oleh subclass.
 */
public abstract class LibraryItem {
    private int id;                 // 0 = belum disimpan ke database
    private String title;
    private String isbn;
    private String publisher;
    private Integer publicationYear;
    private Category category;
    private String description;
    private final List<Author> authors = new ArrayList<>();

    protected LibraryItem(int id, String title, String isbn, String publisher,
                          Integer publicationYear, Category category,
                          String description, List<Author> authors) {
        if (id < 0) throw new IllegalArgumentException("ID tidak boleh negatif.");
        this.id = id;
        setTitle(title);
        setIsbn(isbn);
        setPublisher(publisher);
        setPublicationYear(publicationYear);
        setCategory(category);
        setDescription(description);
        setAuthors(authors);
    }

    // ---- Method abstract: wajib diimplementasikan, perilakunya berbeda per jenis ----

    /** Label jenis koleksi, mis. "Buku", "E-Book", "Jurnal". */
    public abstract String getItemType();

    /** Ringkasan satu baris yang berisi atribut khusus tiap jenis koleksi. */
    public abstract String getDetails();

    // ---- Method konkret yang diwariskan apa adanya ----

    /** Nama penulis dipisah koma, siap untuk kolom JTable. */
    public String getAuthorNames() {
        return authors.stream().map(Author::getName).collect(Collectors.joining(", "));
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getIsbn() { return isbn; }
    public String getPublisher() { return publisher; }
    public Integer getPublicationYear() { return publicationYear; }
    public Category getCategory() { return category; }
    public String getDescription() { return description; }

    /** Daftar read-only; ubah lewat addAuthor, removeAuthor, atau setAuthors. */
    public List<Author> getAuthors() { return Collections.unmodifiableList(authors); }

    /** Dipanggil DAO sekali saja setelah INSERT. */
    public void assignId(int generatedId) {
        if (id != 0) throw new IllegalStateException("ID sudah ditetapkan.");
        if (generatedId <= 0) throw new IllegalArgumentException("ID hasil generate harus positif.");
        this.id = generatedId;
    }

    public final void setTitle(String title) {
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Judul wajib diisi.");
        this.title = title.trim();
    }

    /** ISBN kosong disimpan sebagai null agar tidak melanggar constraint UNIQUE di database. */
    public final void setIsbn(String isbn) {
        this.isbn = (isbn == null || isbn.isBlank()) ? null : isbn.trim();
    }

    public final void setPublisher(String publisher) {
        this.publisher = (publisher == null || publisher.isBlank()) ? null : publisher.trim();
    }

    public final void setPublicationYear(Integer year) {
        if (year != null && (year < 1 || year > 9999))
            throw new IllegalArgumentException("Tahun terbit tidak valid.");
        this.publicationYear = year;
    }

    public final void setCategory(Category category) {
        this.category = Objects.requireNonNull(category, "Kategori wajib diisi.");
    }

    public final void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    public final void setAuthors(List<Author> newAuthors) {
        authors.clear();
        if (newAuthors != null) newAuthors.forEach(this::addAuthor);
    }

    /** Menambah penulis; penulis yang sama (berdasarkan equals) tidak ditambahkan dua kali. */
    public final void addAuthor(Author author) {
        Objects.requireNonNull(author, "Penulis tidak boleh null.");
        if (!authors.contains(author)) authors.add(author);
    }

    public final void removeAuthor(Author author) { authors.remove(author); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LibraryItem other = (LibraryItem) o;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() { return id != 0 ? Integer.hashCode(id) : System.identityHashCode(this); }

    @Override
    public String toString() { return "[" + getItemType() + "] " + title; }
}
