package com.smartlib.ui;

import com.smartlib.model.BookFilter;
import com.smartlib.model.BookStockSummary;
import com.smartlib.model.Category;
import com.smartlib.service.BookService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Panel pencarian + tabel buku. Dipakai bersama oleh CatalogFrame (hanya melihat) dan
 * BookManagementFrame (mengelola), sehingga logika pencarian cukup ditulis satu kali.
 * Pencarian: judul/ISBN, nama penulis, kategori, dan ketersediaan; semuanya lewat BookService ke MySQL.
 */
class CatalogPanel extends JPanel {

    private static final Category ALL_CATEGORIES = new Category(0, "Semua kategori", "");

    private final BookService service;
    private final JTextField tfTitle = new JTextField(16);
    private final JTextField tfAuthor = new JTextField(12);
    private final JComboBox<Category> cbCategory = new JComboBox<>();
    private final JCheckBox cbAvailable = new JCheckBox("Hanya yang tersedia");
    private final DefaultTableModel model = UiKit.model("Judul", "Penulis", "Kategori", "ISBN", "Penerbit",
            "Tahun", "Total", "Tersedia", "Dipinjam");
    private final JTable table = UiKit.table(model);
    private final JLabel statusLabel = new JLabel(" ");
    private List<BookStockSummary> rows = List.of();
    private Runnable onOpen = () -> { };

    CatalogPanel(BookService service) {
        super(new BorderLayout(0, 6));
        this.service = service;

        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        search.setBorder(BorderFactory.createTitledBorder("Pencarian dan filter"));
        search.add(new JLabel("Judul / ISBN"));
        search.add(tfTitle);
        search.add(new JLabel("Penulis"));
        search.add(tfAuthor);
        search.add(new JLabel("Kategori"));
        search.add(cbCategory);
        search.add(cbAvailable);
        search.add(UiKit.button("Cari", this::reload));
        search.add(UiKit.button("Reset", this::reset));
        tfTitle.addActionListener(e -> reload());
        tfAuthor.addActionListener(e -> reload());

        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && selected() != null) onOpen.run();
            }
        });
        int[] widths = {220, 200, 130, 140, 120, 55, 50, 65, 70};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        add(search, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        refreshCategories();
        reload();
    }

    /** Aksi yang dijalankan saat baris di-double-click. */
    void setOnOpen(Runnable action) { this.onOpen = action; }

    /** Mengisi ulang daftar kategori pada filter (dipakai setelah kategori diubah). */
    void refreshCategories() {
        Object current = cbCategory.getSelectedItem();
        List<Category> categories = UiKit.call(this, service::listCategories);
        cbCategory.removeAllItems();
        cbCategory.addItem(ALL_CATEGORIES);
        if (categories != null) categories.forEach(cbCategory::addItem);
        if (current instanceof Category c) {
            for (int i = 0; i < cbCategory.getItemCount(); i++) {
                if (cbCategory.getItemAt(i).getId() == c.getId()) cbCategory.setSelectedIndex(i);
            }
        }
    }

    /** Memuat ulang tabel sesuai kriteria pencarian saat ini. */
    void reload() {
        Category category = (Category) cbCategory.getSelectedItem();
        Integer categoryId = (category == null || category.getId() == 0) ? null : category.getId();
        BookFilter filter = new BookFilter(tfTitle.getText(), tfAuthor.getText(), categoryId,
                cbAvailable.isSelected());
        List<BookStockSummary> result = UiKit.call(this, () -> service.search(filter));
        if (result == null) return;
        rows = result;
        model.setRowCount(0);
        for (BookStockSummary s : rows) {
            model.addRow(new Object[]{
                    s.book().getTitle(),
                    s.book().getAuthorNames(),
                    s.book().getCategory().getName(),
                    s.book().getIsbn() == null ? "" : s.book().getIsbn(),
                    s.book().getPublisher() == null ? "" : s.book().getPublisher(),
                    s.book().getPublicationYear() == null ? "" : String.valueOf(s.book().getPublicationYear()),
                    s.totalCopies(), s.availableCopies(), s.borrowedCopies()});
        }
        statusLabel.setText(rows.isEmpty() ? "Tidak ada buku yang cocok dengan pencarian."
                : rows.size() + " buku ditemukan.");
    }

    private void reset() {
        tfTitle.setText("");
        tfAuthor.setText("");
        cbCategory.setSelectedIndex(0);
        cbAvailable.setSelected(false);
        reload();
    }

    /** Buku terpilih pada tabel, atau null (dengan pesan) bila belum ada yang dipilih. */
    BookStockSummary selected() {
        int row = UiKit.selectedModelRow(table);
        return row < 0 ? null : rows.get(row);
    }

    BookStockSummary requireSelected() {
        BookStockSummary s = selected();
        if (s == null) UiKit.info(this, "Pilih buku pada tabel terlebih dahulu.");
        return s;
    }
}