package com.smartlib.model;

import java.util.Objects;


public class Category {
    private int id;                 // 0 = belum disimpan ke database
    private String name;
    private String description;

    public Category(int id, String name, String description) {
        if (id < 0) throw new IllegalArgumentException("ID tidak boleh negatif.");
        this.id = id;
        setName(name);
        setDescription(description);
    }


    public Category(String name, String description) {
        this(0, name, description);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }


    public void assignId(int generatedId) {
        if (id != 0) throw new IllegalStateException("ID sudah ditetapkan.");
        if (generatedId <= 0) throw new IllegalArgumentException("ID hasil generate harus positif.");
        this.id = generatedId;
    }

    public final void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Nama kategori wajib diisi.");
        this.name = name.trim();
    }

    public final void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() { return id != 0 ? Integer.hashCode(id) : System.identityHashCode(this); }


    @Override
    public String toString() { return name; }
}
