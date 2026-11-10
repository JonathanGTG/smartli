package com.smartlib.model;

/** Penulis koleksi (tabel authors). Relasi ke koleksi many-to-many lewat item_authors. */
public class Author {
    private int id;                 // 0 = belum disimpan ke database
    private String name;
    private String biography;

    public Author(int id, String name, String biography) {
        if (id < 0) throw new IllegalArgumentException("ID tidak boleh negatif.");
        this.id = id;
        setName(name);
        setBiography(biography);
    }

    /** Untuk penulis baru yang belum punya ID. */
    public Author(String name, String biography) {
        this(0, name, biography);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getBiography() { return biography; }

    /** Dipanggil DAO sekali saja setelah INSERT. */
    public void assignId(int generatedId) {
        if (id != 0) throw new IllegalStateException("ID sudah ditetapkan.");
        if (generatedId <= 0) throw new IllegalArgumentException("ID hasil generate harus positif.");
        this.id = generatedId;
    }

    public final void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Nama penulis wajib diisi.");
        this.name = name.trim();
    }

    public final void setBiography(String biography) {
        this.biography = biography == null ? "" : biography.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Author other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() { return id != 0 ? Integer.hashCode(id) : System.identityHashCode(this); }

    @Override
    public String toString() { return name; }
}
