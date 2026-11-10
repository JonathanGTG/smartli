package com.smartlib.model;

import java.util.List;

/**
 * Jurnal ilmiah. Sengaja TIDAK mengimplementasikan Borrowable:
 * jurnal hanya dibaca di tempat, sehingga tidak bisa masuk transaksi pinjaman.
 */
public class Journal extends LibraryItem {
    private String volume;
    private String issue;

    public Journal(int id, String title, String isbn, String publisher, Integer publicationYear,
                   Category category, String description, List<Author> authors,
                   String volume, String issue) {
        super(id, title, isbn, publisher, publicationYear, category, description, authors);
        setVolume(volume);
        setIssue(issue);
    }

    @Override
    public String getItemType() { return "Jurnal"; }

    @Override
    public String getDetails() {
        return "Jurnal, vol. " + (volume.isEmpty() ? "-" : volume)
                + ", no. " + (issue.isEmpty() ? "-" : issue) + " (baca di tempat)";
    }

    public String getVolume() { return volume; }
    public String getIssue() { return issue; }

    public final void setVolume(String volume) { this.volume = volume == null ? "" : volume.trim(); }
    public final void setIssue(String issue) { this.issue = issue == null ? "" : issue.trim(); }
}
