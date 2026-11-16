package com.smartlib.ui;

import com.smartlib.model.Permission;
import com.smartlib.service.AuthService;
import com.smartlib.service.BookService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Manajemen kategori buku dan penulis (dua tab). Hanya untuk role dengan Permission.MANAGE_COLLECTION.
 * Aturan seperti "kategori yang masih dipakai tidak boleh dihapus" ada di BookService.
 */
public class CategoryManagementFrame extends JFrame {

    private final BookService service = new BookService();

    public CategoryManagementFrame() {
        super("SmartLib - Kategori & Penulis");
        AuthService.getInstance().requirePermission(Permission.MANAGE_COLLECTION);   // cek di service, bukan hanya tombol

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        CrudPanel categories = new CrudPanel("kategori", "Deskripsi",
                () -> service.listCategories().stream()
                        .map(c -> new CrudPanel.Entry(c.getId(), c.getName(), c.getDescription())).toList(),
                service::addCategory, service::updateCategory, service::deleteCategory);
        CrudPanel authors = new CrudPanel("penulis", "Biografi",
                () -> service.listAuthors().stream()
                        .map(a -> new CrudPanel.Entry(a.getId(), a.getName(), a.getBiography())).toList(),
                service::addAuthor, service::updateAuthor, service::deleteAuthor);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Kategori", categories);
        tabs.addTab("Penulis", authors);
        add(tabs, BorderLayout.CENTER);
        setSize(720, 480);
        setLocationRelativeTo(null);
    }

    /** Panel CRUD sederhana (tabel + form) yang dipakai untuk kategori dan penulis. */
    private static final class CrudPanel extends JPanel {

        record Entry(int id, String name, String description) { }

        @FunctionalInterface
        interface Adder { void add(String name, String description); }

        @FunctionalInterface
        interface Updater { void update(int id, String name, String description); }

        private final String noun;
        private final Supplier<List<Entry>> loader;
        private final Adder adder;
        private final Updater updater;
        private final IntConsumer deleter;
        private final DefaultTableModel model;
        private final JTable table;
        private final JTextField tfName = new JTextField(22);
        private final JTextField tfDescription = new JTextField(34);
        private List<Entry> entries = List.of();

        CrudPanel(String noun, String descriptionLabel, Supplier<List<Entry>> loader,
                  Adder adder, Updater updater, IntConsumer deleter) {
            super(new BorderLayout(0, 8));
            this.noun = noun;
            this.loader = loader;
            this.adder = adder;
            this.updater = updater;
            this.deleter = deleter;
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            model = UiKit.model("Nama", descriptionLabel);
            table = UiKit.table(model);
            table.getSelectionModel().addListSelectionListener(e -> {
                if (e.getValueIsAdjusting()) return;
                int row = UiKit.selectedModelRow(table);
                if (row >= 0) {
                    tfName.setText(entries.get(row).name());
                    tfDescription.setText(entries.get(row).description());
                }
            });

            JPanel fields = UiKit.form("Nama " + noun, tfName, descriptionLabel, tfDescription);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            buttons.add(UiKit.button("Tambah", this::onAdd));
            buttons.add(UiKit.button("Simpan Perubahan", this::onUpdate));
            buttons.add(UiKit.button("Hapus", this::onDelete));
            buttons.add(UiKit.button("Kosongkan Form", this::clearForm));
            JPanel south = new JPanel(new BorderLayout());
            south.setBorder(BorderFactory.createTitledBorder("Form " + noun));
            south.add(fields, BorderLayout.CENTER);
            south.add(buttons, BorderLayout.SOUTH);

            add(new JScrollPane(table), BorderLayout.CENTER);
            add(south, BorderLayout.SOUTH);
            reload();
        }

        private void reload() {
            List<Entry> result = UiKit.call(this, loader);
            if (result == null) return;
            entries = result;
            model.setRowCount(0);
            for (Entry e : entries) model.addRow(new Object[]{e.name(), e.description()});
        }

        private void clearForm() {
            table.clearSelection();
            tfName.setText("");
            tfDescription.setText("");
        }

        private Entry selected() {
            int row = UiKit.selectedModelRow(table);
            if (row < 0) {
                UiKit.info(this, "Pilih " + noun + " pada tabel terlebih dahulu.");
                return null;
            }
            return entries.get(row);
        }

        private void onAdd() {
            if (UiKit.run(this, () -> adder.add(tfName.getText(), tfDescription.getText()))) {
                reload();
                clearForm();
            }
        }

        private void onUpdate() {
            Entry e = selected();
            if (e == null) return;
            if (UiKit.run(this, () -> updater.update(e.id(), tfName.getText(), tfDescription.getText()))) {
                reload();
                clearForm();
            }
        }

        private void onDelete() {
            Entry e = selected();
            if (e == null) return;
            if (!UiKit.confirm(this, "Hapus " + noun + " \"" + e.name() + "\"?")) return;
            if (UiKit.run(this, () -> deleter.accept(e.id()))) {
                reload();
                clearForm();
            }
        }
    }
}