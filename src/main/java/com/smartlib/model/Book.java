package com.smartlib.model;

import com.smartlib.interfaces.Borrowable;
import java.util.List;


public class Book extends LibraryItem implements Borrowable {
    private String edition;

    public Book(int id, String title, String isbn, String publisher, Integer publicationYear,
                Category category, String description, List<Author> authors, String edition) {
        super(id, title, isbn, publisher, publicationYear, category, description, authors);
        setEdition(edition);
    }

    @Override
    public String getItemType() { return "Buku"; }

    @Override
    public String getDetails() {
        return edition.isEmpty() ? "Buku fisik" : "Buku fisik, edisi " + edition;
    }

    @Override
    public boolean isBorrowable() { return true; }

    public String getEdition() { return edition; }

    public final void setEdition(String edition) {
        this.edition = edition == null ? "" : edition.trim();
    }
}
