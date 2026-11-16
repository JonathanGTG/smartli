package com.smartlib.ui;

import com.smartlib.model.Author;
import com.smartlib.model.Book;
import com.smartlib.model.BookStockSummary;
import com.smartlib.model.Category;
import com.smartlib.model.Permission;
import com.smartlib.service.AuthService;
import com.smartlib.service.BookService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Manajemen buku: lihat, cari, tambah, ubah, hapus, detail, dan kelola eksemplar.
 * Hanya untuk role dengan Permission.MANAGE_COLLECTION (Admin dan Petugas).
 * Frame ini hanya menampilkan data dan meneruskan perintah ke BookService; tidak ada SQL di sini.
 */
public class BookManagementFrame extends JFrame {

    private static final Category CHOOSE_CATEGORY = new Category(0, "-- pilih kategori --", "");

    private final BookService service = new BookService();
    private final CatalogPanel catalog;

    public BookManagementFrame() {
        super("SmartLib - Manajemen Buku");
        AuthService.getInstance().requirePermission(Permission.MANAGE_COLLECTION);   // cek di service, bukan hanya tombol

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        catalog = new CatalogPanel(service);
        catalog.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        catalog.setOnOpen(this::onDetail);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(UiKit.button("Tambah Buku", this::onAdd));
        buttons.add(UiKit.button("Ubah", this::onEdit));
        buttons.add(UiKit.button("Hapus", this::onDelete));
        buttons.add(UiKit.button("Detail", this::onDetail));
        buttons.add(UiKit.button("Kelola Eksemplar", this::onCopies));
        buttons.add(UiKit.button("Kategori & Penulis", this::onCategories));
        buttons.add(UiKit.button("Refresh", catalog::reload));

        add(catalog, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(1100, 560);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ aksi tombol

    private void onAdd() {
        if (showBookForm(null)) catalog.reload();
    }

    private void onEdit() {
        BookStockSummary s = catalog.requireSelected();
        if (s != null && showBookForm(s.book())) catalog.reload();
    }

    private void onDelete() {
        BookStockSummary s = catalog.requireSelected();
        if (s == null) return;
        String message = "Hapus buku \"" + s.book().getTitle() + "\"?\n"
                + "Seluruh eksemplarnya (" + s.totalCopies() + ") ikut dihapus dan tindakan ini tidak dapat dibatalkan.";
        if (!UiKit.confirm(this, message)) return;
        if (UiKit.run(this, () -> service.deleteBook(s.book().getId()))) catalog.reload();
    }

    private void onDetail() {
        BookStockSummary s = catalog.requireSelected();
        if (s != null) BookDetailDialog.show(this, service, s.book().getId());
    }

    private void onCopies() {
        BookStockSummary s = catalog.requireSelected();
        if (s == null) return;
        new CopyManagerDialog(this, service, s.book()).setVisible(true);
        catalog.reload();    // stok bisa berubah setelah eksemplar diubah
    }

    private void onCategories() {
        CategoryManagementFrame frame = new CategoryManagementFrame();
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                catalog.refreshCategories();
                catalog.reload();
            }
        });
        frame.setVisible(true);
    }

    // ------------------------------------------------------------------ form tambah / ubah

    /** Menampilkan form buku. existing = null untuk buku baru. Mengembalikan true bila tersimpan. */
    private boolean showBookForm(Book existing) {
        List<Category> categories = UiKit.call(this, service::listCategories);
        List<Author> authors = UiKit.call(this, service::listAuthors);
        if (categories == null || authors == null) return false;
        if (categories.isEmpty()) {
            UiKit.info(this, "Belum ada kategori. Tambahkan kategori terlebih dahulu lewat tombol \"Kategori & Penulis\".");
            return false;
        }

        JTextField tfTitle = new JTextField(existing == null ? "" : existing.getTitle(), 26);
        JTextField tfIsbn = new JTextField(existing == null || existing.getIsbn() == null ? "" : existing.getIsbn(), 26);
        JTextField tfPublisher = new JTextField(
                existing == null || existing.getPublisher() == null ? "" : existing.getPublisher(), 26);
        JTextField tfYear = new JTextField(
                existing == null || existing.getPublicationYear() == null ? "" : String.valueOf(existing.getPublicationYear()), 8);
        JTextField tfEdition = new JTextField(existing == null ? "" : existing.getEdition(), 26);
        JTextArea taDescription = new JTextArea(existing == null ? "" : existing.getDescription(), 3, 26);
        taDescription.setLineWrap(true);
        taDescription.setWrapStyleWord(true);

        JComboBox<Category> cbCategory = new JComboBox<>();
        cbCategory.addItem(CHOOSE_CATEGORY);
        categories.forEach(cbCategory::addItem);
        if (existing != null) {
            for (int i = 0; i < cbCategory.getItemCount(); i++) {
                if (cbCategory.getItemAt(i).getId() == existing.getCategory().getId()) cbCategory.setSelectedIndex(i);
            }
        }

        DefaultListModel<Author> authorModel = new DefaultListModel<>();
        authors.forEach(authorModel::addElement);
        JList<Author> authorList = new JList<>(authorModel);
        authorList.setVisibleRowCount(5);
        selectAuthors(authorList, existing == null ? List.of() : existing.getAuthors());
        JScrollPane authorScroll = new JScrollPane(authorList);
        authorScroll.setPreferredSize(new Dimension(300, 100));

        JButton newAuthor = UiKit.button("Penulis baru...", () -> {
            String name = JOptionPane.showInputDialog(this, "Nama penulis baru:", "Tambah Penulis",
                    JOptionPane.PLAIN_MESSAGE);
            if (name == null) return;
            List<Author> keep = authorList.getSelectedValuesList();
            Author added = UiKit.call(this, () -> service.addAuthor(name, ""));
            if (added == null) return;
            authorModel.addElement(added);
            List<Author> selection = new java.util.ArrayList<>(keep);
            selection.add(added);
            selectAuthors(authorList, selection);
        });
        JPanel authorPanel = new JPanel(new BorderLayout(0, 4));
        authorPanel.add(authorScroll, BorderLayout.CENTER);
        JPanel authorButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        authorButtons.add(newAuthor);
        authorButtons.add(new JLabel("   (tahan Ctrl untuk memilih lebih dari satu)"));
        authorPanel.add(authorButtons, BorderLayout.SOUTH);

        JPanel form = UiKit.form(
                "Judul *", tfTitle,
                "ISBN", tfIsbn,
                "Penerbit", tfPublisher,
                "Tahun terbit", tfYear,
                "Kategori *", cbCategory,
                "Penulis *", authorPanel,
                "Edisi", tfEdition,
                "Deskripsi", new JScrollPane(taDescription));

        return UiKit.editDialog(this, existing == null ? "Tambah Buku" : "Ubah Buku", form, () -> {
            Integer year = BookService.parseYear(tfYear.getText());
            Category category = (Category) cbCategory.getSelectedItem();
            List<Author> selectedAuthors = authorList.getSelectedValuesList();
            if (existing == null) {
                service.addBook(new Book(0, tfTitle.getText(), tfIsbn.getText(), tfPublisher.getText(), year,
                        category, taDescription.getText(), selectedAuthors, tfEdition.getText()));
            } else {
                service.updateBook(new Book(existing.getId(), tfTitle.getText(), tfIsbn.getText(),
                        tfPublisher.getText(), year, category, taDescription.getText(), selectedAuthors,
                        tfEdition.getText()));
            }
        });
    }

    private static void selectAuthors(JList<Author> list, List<Author> toSelect) {
        List<Integer> indices = new java.util.ArrayList<>();
        ListModel<Author> model = list.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            for (Author a : toSelect) {
                if (a.getId() == model.getElementAt(i).getId()) indices.add(i);
            }
        }
        list.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
    }
}