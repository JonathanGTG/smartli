package com.smartlib.ui;

import com.smartlib.enums.ItemStatus;
import com.smartlib.model.Book;
import com.smartlib.model.BookCopy;
import com.smartlib.model.BookStockSummary;
import com.smartlib.service.BookService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Dialog pengelolaan eksemplar (barcode, status, lokasi rak) untuk satu judul buku.
 * Aturan seperti "eksemplar yang sedang dipinjam tidak boleh dihapus" ada di BookService.
 */
class CopyManagerDialog extends JDialog {

    private final BookService service;
    private final Book book;
    private final DefaultTableModel model = UiKit.model("Barcode", "Status", "Lokasi Rak");
    private final JTable table = UiKit.table(model);
    private final JLabel stockLabel = new JLabel(" ");
    private List<BookCopy> copies = List.of();

    CopyManagerDialog(Window owner, BookService service, Book book) {
        super(owner, "Eksemplar: " + book.getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
        this.service = service;
        this.book = book;

        stockLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(UiKit.button("Tambah Eksemplar", this::onAdd));
        buttons.add(UiKit.button("Ubah", this::onEdit));
        buttons.add(UiKit.button("Hapus", this::onDelete));
        buttons.add(UiKit.button("Tutup", this::dispose));

        add(stockLabel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(560, 380);
        setLocationRelativeTo(owner);
        reload();
    }

    private void reload() {
        List<BookCopy> result = UiKit.call(this, () -> service.listCopies(book.getId()));
        BookStockSummary summary = UiKit.call(this, () -> service.getSummary(book.getId()));
        if (result == null || summary == null) return;
        copies = result;
        model.setRowCount(0);
        for (BookCopy c : copies) {
            model.addRow(new Object[]{c.getBarcode(), c.getStatus().getLabel(),
                    c.getShelfLocation().isEmpty() ? "-" : c.getShelfLocation()});
        }
        stockLabel.setText("Total " + summary.totalCopies() + " | Tersedia " + summary.availableCopies()
                + " | Dipinjam " + summary.borrowedCopies() + " | Lainnya " + summary.otherCopies());
    }

    private BookCopy selected() {
        int row = UiKit.selectedModelRow(table);
        if (row < 0) {
            UiKit.info(this, "Pilih eksemplar pada tabel terlebih dahulu.");
            return null;
        }
        return copies.get(row);
    }

    private void onAdd() {
        JTextField tfBarcode = new JTextField(18);
        JComboBox<ItemStatus> cbStatus = new JComboBox<>(
                new ItemStatus[]{ItemStatus.AVAILABLE, ItemStatus.DAMAGED, ItemStatus.LOST});
        JTextField tfShelf = new JTextField(18);
        JPanel form = UiKit.form("Barcode", tfBarcode, "Status", cbStatus, "Lokasi rak", tfShelf);
        BookCopy[] added = new BookCopy[1];
        if (UiKit.editDialog(this, "Tambah Eksemplar", form, () ->
                added[0] = service.addCopy(book.getId(), tfBarcode.getText(), (ItemStatus) cbStatus.getSelectedItem(),
                        tfShelf.getText()))) {
            reload();
            if (added[0] != null && added[0].getStatus() == ItemStatus.RESERVED) {
                UiKit.info(this, "Eksemplar baru berhasil ditambahkan dan langsung dialokasikan "
                        + "(Dicadangkan / RESERVED) untuk anggota pada antrean FIFO terdepan (batas 24 jam).");
            }
        }
    }

    private void onEdit() {
        BookCopy copy = selected();
        if (copy == null) return;
        JTextField tfBarcode = new JTextField(copy.getBarcode(), 18);
        JComboBox<ItemStatus> cbStatus = new JComboBox<>(ItemStatus.values());
        cbStatus.setSelectedItem(copy.getStatus());
        JTextField tfShelf = new JTextField(copy.getShelfLocation(), 18);
        JPanel form = UiKit.form("Barcode", tfBarcode, "Status", cbStatus, "Lokasi rak", tfShelf);
        if (UiKit.editDialog(this, "Ubah Eksemplar", form, () ->
                service.updateCopy(copy.getId(), tfBarcode.getText(), (ItemStatus) cbStatus.getSelectedItem(),
                        tfShelf.getText()))) {
            reload();
            boolean offered = copies.stream()
                    .anyMatch(c -> c.getId() == copy.getId() && c.getStatus() == ItemStatus.RESERVED);
            if (cbStatus.getSelectedItem() == ItemStatus.AVAILABLE && offered) {
                UiKit.info(this, "Eksemplar yang kini tersedia langsung dialokasikan "
                        + "(Dicadangkan / RESERVED) untuk anggota pada antrean FIFO terdepan (batas 24 jam).");
            }
        }
    }

    private void onDelete() {
        BookCopy copy = selected();
        if (copy == null) return;
        if (!UiKit.confirm(this, "Hapus eksemplar " + copy.getBarcode() + "?")) return;
        if (UiKit.run(this, () -> service.deleteCopy(copy.getId()))) reload();
    }
}