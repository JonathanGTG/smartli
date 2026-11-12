package com.smartlib.ui;

import com.smartlib.model.Permission;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;

import javax.swing.*;
import java.awt.*;

/**
 * MainFrame adalah wrapper yang membungkus seluruh aplikasi dalam satu JFrame
 * dengan CardLayout, sehingga navigasi antar halaman tidak perlu membuka/menutup window.
 *
 * Card yang tersedia:
 *  - "login"     → LoginPanel (embedded, bukan JFrame terpisah)
 *  - "dashboard" → DashboardFrame (di-embed sebagai JPanel)
 *
 * Cara penggunaan:
 *   new MainFrame().setVisible(true);
 *
 * DashboardRouter juga tetap mendukung mode multi-window (backward-compatible).
 */
public class MainFrame extends JFrame {

    public static final String CARD_LOGIN     = "login";
    public static final String CARD_DASHBOARD = "dashboard";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel     cardPanel  = new JPanel(cardLayout);

    private static MainFrame instance;

    public MainFrame() {
        super("SmartLib — Smart Library Management System");
        instance = this;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(400, 320));

        // Mulai dari halaman login
        cardPanel.add(buildLoginCard(), CARD_LOGIN);
        setContentPane(cardPanel);

        // Ukuran awal (login-size, akan membesar saat masuk dashboard)
        setSize(380, 280);
        setLocationRelativeTo(null);
    }

    /** Dipanggil setelah login berhasil: ganti card ke dashboard. */
    public void showDashboard(User user) {
        // Hapus card dashboard lama jika ada (refresh setelah logout)
        cardPanel.remove(getDashboardCard());
        DashboardFrame dashPanel = new DashboardFrame(user);
        // Pakai DashboardFrame sebagai standalone frame, cukup buka
        dashPanel.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        dashPanel.setVisible(true);
        this.dispose();  // tutup MainFrame, dashboard menjadi window utama
    }

    /** Kembali ke login (setelah logout dari DashboardFrame). */
    public static void backToLogin() {
        if (instance != null && instance.isDisplayable()) {
            instance.cardLayout.show(instance.cardPanel, CARD_LOGIN);
            instance.setSize(380, 280);
            instance.setLocationRelativeTo(null);
        } else {
            new LoginFrame().setVisible(true);
        }
    }

    // ─────────────────────── helper ───────────────────────

    private JPanel buildLoginCard() {
        // Wrapper tipis yang membungkus LoginFrame content
        JPanel panel = new JPanel(new BorderLayout());
        JLabel info = new JLabel("Silakan gunakan form login di bawah", SwingConstants.CENTER);
        info.setFont(info.getFont().deriveFont(Font.ITALIC, 12f));
        // LoginFrame sudah lengkap — cukup redirect
        panel.add(info, BorderLayout.CENTER);
        return panel;
    }

    private Component getDashboardCard() {
        for (Component c : cardPanel.getComponents()) {
            if (CARD_DASHBOARD.equals(c.getName())) return c;
        }
        return new JPanel(); // placeholder kosong
    }
}
