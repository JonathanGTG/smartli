package com.smartlib.ui;

import com.smartlib.model.Permission;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;

import javax.swing.*;
import java.awt.*;

/**
 * Menentukan tampilan awal berdasarkan role setelah login.
 *
 * SEMENTARA: setiap role membuka menu placeholder di bawah. Ketika DashboardFrame
 * (Anggota 5) dan form katalog (Anggota 3) siap, ganti isi method open() ini
 * dengan "new DashboardFrame(user).setVisible(true)" dan sejenisnya.
 */
public final class DashboardRouter {

    private DashboardRouter() { }

    public static void open(User user) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("SmartLib - Dashboard " + user.getRole().getLabel());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(new BorderLayout(12, 12));
            ((JComponent) frame.getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

            JLabel welcome = new JLabel("<html><b>Selamat datang, " + escape(user.getFullName())
                    + "</b><br>Role: " + user.getRole().getLabel() + "</html>");
            frame.add(welcome, BorderLayout.NORTH);

            JPanel menu = new JPanel(new GridLayout(0, 1, 8, 8));
            addMenu(menu, "Katalog Buku", user.hasPermission(Permission.VIEW_CATALOG), null);
            addMenu(menu, "Peminjaman & Pengembalian", user.hasPermission(Permission.PROCESS_LOAN), null);
            addMenu(menu, "Kelola Koleksi & Anggota", user.hasPermission(Permission.MANAGE_COLLECTION), null);
            addMenu(menu, "Riwayat & Denda Saya", user.hasPermission(Permission.VIEW_OWN_HISTORY), null);
            addMenu(menu, "Laporan", user.hasPermission(Permission.VIEW_REPORTS), null);
            addMenu(menu, "Kelola Pengguna", user.hasPermission(Permission.MANAGE_USERS), () -> {
                new UserManagementFrame().setVisible(true);
            });
            frame.add(menu, BorderLayout.CENTER);

            JButton logout = new JButton("Logout");
            logout.addActionListener(e -> {
                AuthService.getInstance().logout();
                frame.dispose();
                new LoginFrame().setVisible(true);
            });
            frame.add(logout, BorderLayout.SOUTH);

            frame.setSize(380, 460);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    /** Menu hanya ditampilkan bila role berhak; menu tanpa aksi = placeholder modul lain. */
    private static void addMenu(JPanel panel, String text, boolean allowed, Runnable action) {
        if (!allowed) return;
        JButton b = new JButton(text);
        if (action != null) {
            b.addActionListener(e -> action.run());
        } else {
            b.addActionListener(e -> JOptionPane.showMessageDialog(b, "Modul \"" + text + "\" belum terhubung."));
        }
        panel.add(b);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Dipakai pengujian: role -> judul tampilan. */
    static String titleFor(Role role) { return "Dashboard " + role.getLabel(); }
}
