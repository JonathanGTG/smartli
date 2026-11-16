package com.smartlib.ui;

import com.smartlib.model.Book;
import com.smartlib.model.BookCopy;
import com.smartlib.model.BookStockSummary;
import com.smartlib.service.BookService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/** Dialog detail buku: data lengkap, ringkasan stok, dan daftar eksemplar. Hanya membaca data. */
final class BookDetailDialog {

    private BookDetailDialog() { }

    static void show(Component parent, BookService service, int bookId) {
        BookStockSummary summary = UiKit.call(parent, () -> service.getSummary(bookId));
        List<BookCopy> copies = UiKit.call(parent, () -> service.listCopies(bookId));
        if (summary == null || copies == null) return;

        Book b = summary.book();
        StringBuilder text = new StringBuilder();
        text.append("Judul        : ").append(b.getTitle()).append('\n');
        text.append("Penulis      : ").append(b.getAuthorNames().isEmpty() ? "-" : b.getAuthorNames()).append('\n');
        text.append("Kategori     : ").append(b.getCategory().getName()).append('\n');
        text.append("ISBN         : ").append(b.getIsbn() == null ? "-" : b.getIsbn()).append('\n');
        text.append("Penerbit     : ").append(b.getPublisher() == null ? "-" : b.getPublisher()).append('\n');
        text.append("Tahun terbit : ").append(b.getPublicationYear() == null ? "-" : b.getPublicationYear()).append('\n');
        text.append("Jenis        : ").append(b.getDetails()).append('\n');
        text.append("Deskripsi    : ").append(b.getDescription().isEmpty() ? "-" : b.getDescription()).append('\n');
        text.append('\n');
        text.append("Stok: total ").append(summary.totalCopies())
                .append(" | tersedia ").append(summary.availableCopies())
                .append(" | dipinjam ").append(summary.borrowedCopies())
                .append(" | lainnya (dicadangkan/rusak/hilang) ").append(summary.otherCopies());

        JTextArea area = new JTextArea(text.toString());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        DefaultTableModel model = UiKit.model("Barcode", "Status", "Lokasi Rak");
        for (BookCopy c : copies) {
            model.addRow(new Object[]{c.getBarcode(), c.getStatus().getLabel(),
                    c.getShelfLocation().isEmpty() ? "-" : c.getShelfLocation()});
        }
        JScrollPane copyScroll = new JScrollPane(UiKit.table(model));
        copyScroll.setBorder(BorderFactory.createTitledBorder("Eksemplar"));
        copyScroll.setPreferredSize(new Dimension(560, 150));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Detail Buku",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(copyScroll, BorderLayout.CENTER);
        JButton close = UiKit.button("Tutup", dialog::dispose);
        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        closeRow.add(close);
        south.add(closeRow, BorderLayout.SOUTH);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(600, 520);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}