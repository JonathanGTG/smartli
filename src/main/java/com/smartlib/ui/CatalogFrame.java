package com.smartlib.ui;

import com.smartlib.model.BookStockSummary;
import com.smartlib.model.Permission;
import com.smartlib.service.AuthService;
import com.smartlib.service.BookService;

import javax.swing.*;
import java.awt.*;

/**
 * Katalog buku untuk semua role yang berhak melihat katalog (Permission.VIEW_CATALOG):
 * cari berdasarkan judul/ISBN, penulis, kategori, dan ketersediaan, lalu lihat detail dan stok.
 * Tampilan hanya-baca: tidak ada tombol tambah/ubah/hapus.
 */
public class CatalogFrame extends JFrame {

    private final BookService service = new BookService();
    private final CatalogPanel catalog;

    public CatalogFrame() {
        super("SmartLib - Katalog Buku");
        AuthService.getInstance().requirePermission(Permission.VIEW_CATALOG);   // cek di service, bukan hanya tombol

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        catalog = new CatalogPanel(service);
        catalog.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        catalog.setOnOpen(this::showDetail);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(UiKit.button("Lihat Detail", this::showDetail));
        buttons.add(new JLabel("(atau klik dua kali pada baris buku)"));

        add(catalog, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSize(1000, 520);
        setLocationRelativeTo(null);
    }

    private void showDetail() {
        BookStockSummary s = catalog.requireSelected();
        if (s != null) BookDetailDialog.show(this, service, s.book().getId());
    }
}