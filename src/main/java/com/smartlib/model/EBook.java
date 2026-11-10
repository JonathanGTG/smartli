package com.smartlib.model;

import com.smartlib.interfaces.Borrowable;
import java.util.List;


public class EBook extends LibraryItem implements Borrowable {
    private String fileFormat;
    private String downloadUrl;

    public EBook(int id, String title, String isbn, String publisher, Integer publicationYear,
                 Category category, String description, List<Author> authors,
                 String fileFormat, String downloadUrl) {
        super(id, title, isbn, publisher, publicationYear, category, description, authors);
        setFileFormat(fileFormat);
        setDownloadUrl(downloadUrl);
    }

    @Override
    public String getItemType() { return "E-Book"; }

    @Override
    public String getDetails() { return "Digital, format " + fileFormat; }

    @Override
    public boolean isBorrowable() { return true; }

    public String getFileFormat() { return fileFormat; }
    public String getDownloadUrl() { return downloadUrl; }

    public final void setFileFormat(String fileFormat) {
        if (fileFormat == null || fileFormat.isBlank())
            throw new IllegalArgumentException("Format file wajib diisi.");
        this.fileFormat = fileFormat.trim().toUpperCase();
    }

    public final void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl == null ? "" : downloadUrl.trim();
    }
}
