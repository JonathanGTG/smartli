package com.smartlib.ui;

import com.smartlib.model.Permission;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;

import javax.swing.*;

public final class DashboardRouter {

    private DashboardRouter() { }

    public static void open(User user) {
        SwingUtilities.invokeLater(() -> {
            // Admin & Petugas membuka Dashboard Utama yang kaya statistik
            if (user.getRole() == Role.ADMIN || user.getRole() == Role.PETUGAS) {
                new DashboardFrame(user).setVisible(true);
                return;
            }

            // Anggota membuka antarmuka khusus Anggota
            openMemberMenu(user);
        });
    }

    private static void openMemberMenu(User user) {
        JFrame frame = new JFrame("SmartLib — Area Anggota (" + user.getFullName() + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(440, 380);
        frame.setLocationRelativeTo(null);

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel welcome = new JLabel("<html><b>Halo, " + user.getFullName() + "</b><br>Role: Anggota Perpustakaan</html>");
        panel.add(welcome);

        JButton btnCatalog = new JButton("📖  Katalog & Pencarian Buku");
        btnCatalog.addActionListener(e -> new CatalogFrame().setVisible(true));
        panel.add(btnCatalog);

        JButton btnHistory = new JButton("📋  Riwayat Peminjaman & Denda Saya");
        btnHistory.addActionListener(e -> new MemberHistoryFrame(user).setVisible(true));
        panel.add(btnHistory);

        JButton btnQueue = new JButton("📌  Status Antrean Saya");
        btnQueue.addActionListener(e -> new ReservationFrame().setVisible(true));
        panel.add(btnQueue);

        JButton btnAccount = new JButton("👤  Kelola Akun Saya");
        btnAccount.addActionListener(e -> new ManageAccountFrame(null).setVisible(true));
        panel.add(btnAccount);

        JButton btnLogout = new JButton("Logout");
        btnLogout.addActionListener(e -> {
            AuthService.getInstance().logout();
            frame.dispose();
            new LoginFrame().setVisible(true);
        });
        panel.add(btnLogout);

        frame.add(panel);
        frame.setVisible(true);
    }
}