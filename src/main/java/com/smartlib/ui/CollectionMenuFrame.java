package com.smartlib.ui;

import com.smartlib.model.Permission;
import com.smartlib.model.User;

import javax.swing.*;
import java.awt.*;

/**
 * Menu "Kelola Koleksi & Anggota": pintu masuk ke form buku, kategori/penulis, dan anggota.
 * Setiap tombol hanya muncul bila role pengguna berhak (Admin dan Petugas); hak akses juga
 * dicek ulang oleh masing-masing frame dan service.
 */
public class CollectionMenuFrame extends JFrame {

    public static void open(User user) {
        new CollectionMenuFrame(user).setVisible(true);
    }

    public CollectionMenuFrame(User user) {
        super("SmartLib - Kelola Koleksi & Anggota");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel menu = new JPanel(new GridLayout(0, 1, 8, 8));
        menu.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        if (user.hasPermission(Permission.MANAGE_COLLECTION)) {
            menu.add(UiKit.button("Kelola Buku & Eksemplar", () -> new BookManagementFrame().setVisible(true)));
            menu.add(UiKit.button("Kelola Kategori & Penulis", () -> new CategoryManagementFrame().setVisible(true)));
        }
        if (user.hasPermission(Permission.MANAGE_MEMBERS)) {
            menu.add(UiKit.button("Kelola Anggota", () -> new MemberManagementFrame().setVisible(true)));
        }
        add(menu, BorderLayout.CENTER);
        setSize(320, 40 + menu.getComponentCount() * 52 + 16);
        setLocationRelativeTo(null);
    }
}