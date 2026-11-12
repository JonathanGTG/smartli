package com.smartlib.ui;

import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Permission;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;
import com.smartlib.service.ReportService;
import com.smartlib.service.ReportService.DashboardStats;
import com.smartlib.ui.components.ReportTablePanel;
import com.smartlib.ui.components.StatCard;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Dashboard utama SmartLib (Anggota 5).
 *
 * Menampilkan:
 * - Ringkasan statistik (kartu angka)
 * - Daftar buku terlambat
 * - Top-5 buku paling sering dipinjam
 * - Navigasi menu berdasarkan role
 */
public class DashboardFrame extends JFrame {

    private static final Color COLOR_BOOKS    = new Color(0x1976D2);
    private static final Color COLOR_MEMBERS  = new Color(0x388E3C);
    private static final Color COLOR_LOANS    = new Color(0xF57C00);
    private static final Color COLOR_OVERDUE  = new Color(0xC62828);

    private final AuthService   authService   = AuthService.getInstance();
    private final ReportService reportService = ReportService.getInstance();
    private final User user;

    // Kartu statistik
    private final StatCard cardBooks    = new StatCard("Total Judul Buku", "—", COLOR_BOOKS);
    private final StatCard cardMembers  = new StatCard("Total Anggota",    "—", COLOR_MEMBERS);
    private final StatCard cardLoans    = new StatCard("Peminjaman Aktif", "—", COLOR_LOANS);
    private final StatCard cardOverdue  = new StatCard("Buku Terlambat",  "—", COLOR_OVERDUE);

    // Tabel laporan cepat
    private final ReportTablePanel overdueTable =
        new ReportTablePanel("📋 Buku Terlambat",
            new String[]{"Anggota", "Judul Buku", "Jatuh Tempo", "Hari Terlambat"});

    private final ReportTablePanel mostBorrowedTable =
        new ReportTablePanel("🏆 Top-5 Buku Paling Sering Dipinjam",
            new String[]{"Judul Buku", "Jumlah Pinjam"});

    public DashboardFrame(User user) {
        super("SmartLib — Dashboard " + user.getRole().getLabel());
        this.user = user;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUi();
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(null);
        refresh();
    }

    // ─────────────────────── Pembangunan UI ───────────────────────

    private void buildUi() {
        // Header
        JPanel header = buildHeader();

        // Kartu statistik
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 12, 0));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        statsPanel.add(cardBooks);
        statsPanel.add(cardMembers);
        statsPanel.add(cardLoans);
        statsPanel.add(cardOverdue);

        // Tabel laporan
        JSplitPane tables = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, overdueTable, mostBorrowedTable);
        tables.setResizeWeight(0.6);
        tables.setBorder(null);

        // Panel kiri: menu navigasi
        JPanel nav = buildNavMenu();

        // Panel tengah: statistik + tabel
        JPanel center = new JPanel(new BorderLayout(0, 12));
        center.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        center.add(statsPanel, BorderLayout.NORTH);
        center.add(tables, BorderLayout.CENTER);

        // Footer
        JPanel footer = buildFooter();

        // Layout utama
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.add(header,  BorderLayout.NORTH);
        content.add(nav,     BorderLayout.WEST);
        content.add(center,  BorderLayout.CENTER);
        content.add(footer,  BorderLayout.SOUTH);
        setContentPane(content);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(new Color(0x1565C0));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("📚 SmartLib — Smart Library Management System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(Color.WHITE);

        JLabel info = new JLabel("Selamat datang, " + user.getFullName()
            + "  |  Role: " + user.getRole().getLabel());
        info.setForeground(new Color(0xBBDEFB));

        panel.add(title, BorderLayout.WEST);
        panel.add(info,  BorderLayout.EAST);
        return panel;
    }

    private JPanel buildNavMenu() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(0x263238));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
        panel.setPreferredSize(new Dimension(190, 0));

        addNavLabel(panel, "MENU");

        addNavButton(panel, "🏠  Dashboard",
            user.hasPermission(Permission.VIEW_REPORTS), this::refresh);

        addNavButton(panel, "📖  Katalog Buku",
            user.hasPermission(Permission.VIEW_CATALOG),
            () -> JOptionPane.showMessageDialog(this, "Modul Katalog (Anggota 3)."));

        addNavButton(panel, "🔄  Peminjaman",
            user.hasPermission(Permission.PROCESS_LOAN),
            () -> JOptionPane.showMessageDialog(this, "Modul Peminjaman (Anggota 4)."));

        addNavButton(panel, "↩  Pengembalian",
            user.hasPermission(Permission.PROCESS_RETURN),
            () -> JOptionPane.showMessageDialog(this, "Modul Pengembalian (Anggota 4)."));

        addNavButton(panel, "📊  Laporan",
            user.hasPermission(Permission.VIEW_REPORTS),
            () -> new ReportFrame(user).setVisible(true));

        addNavButton(panel, "👥  Kelola Pengguna",
            user.hasPermission(Permission.MANAGE_USERS),
            () -> new UserManagementFrame().setVisible(true));

        addNavButton(panel, "📜  Riwayat Saya",
            user.hasPermission(Permission.VIEW_OWN_HISTORY),
            () -> JOptionPane.showMessageDialog(this, "Modul Riwayat (Anggota 4)."));

        panel.add(Box.createVerticalGlue());

        addNavButton(panel, "🚪  Logout", true, this::doLogout);

        return panel;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(0xECEFF1));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        JLabel lbl = new JLabel("SmartLib v1.0  |  © 2026  |  Data diperbarui setiap klik Refresh");
        lbl.setForeground(Color.GRAY);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 11f));

        JButton btnRefresh = new JButton("⟳ Refresh");
        btnRefresh.addActionListener(e -> refresh());
        panel.add(lbl, BorderLayout.WEST);
        panel.add(btnRefresh, BorderLayout.EAST);
        return panel;
    }

    // ─────────────────────── Navigasi helper ───────────────────────

    private void addNavLabel(JPanel panel, String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setForeground(new Color(0x90A4AE));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 10f));
        lbl.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 0));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lbl);
    }

    private void addNavButton(JPanel panel, String text, boolean allowed, Runnable action) {
        if (!allowed) return;
        JButton btn = new JButton(text);
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 13f));
        btn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 8));
        btn.setBackground(new Color(0x263238));
        btn.setForeground(new Color(0xECEFF1));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorderPainted(false);

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(new Color(0x37474F));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(new Color(0x263238));
            }
        });
        btn.addActionListener(e -> action.run());
        panel.add(btn);
    }

    // ─────────────────────── Aksi ───────────────────────

    private void refresh() {
        if (!user.hasPermission(Permission.VIEW_REPORTS)) return;

        new SwingWorker<DashboardStats, Void>() {
            @Override protected DashboardStats doInBackground() {
                return reportService.getDashboardStats();
            }
            @Override protected void done() {
                try {
                    DashboardStats s = get();
                    cardBooks.setValue(String.valueOf(s.totalItems));
                    cardMembers.setValue(String.valueOf(s.totalMembers));
                    cardLoans.setValue(String.valueOf(s.activeLoans));
                    cardOverdue.setValue(String.valueOf(s.overdueLoans));
                } catch (Exception ex) {
                    handleError(ex);
                }
            }
        }.execute();

        new SwingWorker<java.util.List<Object[]>, Void>() {
            @Override protected List<Object[]> doInBackground() {
                return reportService.overdueLoans();
            }
            @Override protected void done() {
                try { overdueTable.setData(get()); }
                catch (Exception ex) { handleError(ex); }
            }
        }.execute();

        new SwingWorker<List<Object[]>, Void>() {
            @Override protected List<Object[]> doInBackground() {
                return reportService.mostBorrowedBooks(5);
            }
            @Override protected void done() {
                try { mostBorrowedTable.setData(get()); }
                catch (Exception ex) { handleError(ex); }
            }
        }.execute();
    }

    private void doLogout() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Yakin ingin logout?", "Konfirmasi Logout",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        authService.logout();
        dispose();
        new LoginFrame().setVisible(true);
    }

    private void handleError(Exception ex) {
        Throwable cause = (ex instanceof java.util.concurrent.ExecutionException)
            ? ex.getCause() : ex;
        if (cause instanceof DataAccessException) {
            JOptionPane.showMessageDialog(this,
                "Tidak dapat terhubung ke database.\n" + cause.getMessage(),
                "Error Database", JOptionPane.WARNING_MESSAGE);
        }
        // Error lain diabaikan secara diam-diam (UI tetap tampil)
    }
}
