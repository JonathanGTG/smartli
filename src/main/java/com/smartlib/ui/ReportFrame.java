package com.smartlib.ui;

import com.smartlib.model.Permission;
import com.smartlib.model.User;
import com.smartlib.service.ReportService;
import com.smartlib.ui.components.ReportTablePanel;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Frame laporan lengkap SmartLib (Anggota 5).
 *
 * Menampilkan:
 * - Tab 1: Buku terlambat
 * - Tab 2: Top-10 buku paling sering dipinjam
 * - Tab 3: Aktivitas anggota
 * - Tab 4: Peminjaman per bulan
 */
public class ReportFrame extends JFrame {

    private final ReportService reportService = ReportService.getInstance();
    private final User user;

    private final ReportTablePanel overduePanel =
        new ReportTablePanel("Daftar Peminjaman Terlambat",
            new String[]{"Anggota", "Judul Buku", "Jatuh Tempo", "Hari Terlambat"});

    private final ReportTablePanel mostBorrowedPanel =
        new ReportTablePanel("Top-10 Buku Paling Sering Dipinjam",
            new String[]{"Judul Buku", "Jumlah Pinjam"});

    private final ReportTablePanel memberPanel =
        new ReportTablePanel("Aktivitas Anggota",
            new String[]{"Username", "Nama Lengkap", "Total Pinjam", "Aktif", "Terlambat"});

    private final ReportTablePanel monthlyPanel =
        new ReportTablePanel("Peminjaman per Bulan (12 Bulan Terakhir)",
            new String[]{"Bulan (YYYY-MM)", "Jumlah Pinjam"});

    public ReportFrame(User user) {
        super("SmartLib — Laporan Statistik");
        this.user = user;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        setSize(900, 620);
        setLocationRelativeTo(null);
        loadAll();
    }

    private void buildUi() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x1565C0));
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        JLabel lbl = new JLabel("📊  Laporan & Statistik SmartLib");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 16f));
        lbl.setForeground(Color.WHITE);
        header.add(lbl, BorderLayout.WEST);

        JButton btnRefresh = new JButton("⟳ Refresh");
        btnRefresh.addActionListener(e -> loadAll());
        header.add(btnRefresh, BorderLayout.EAST);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        tabs.addTab("⚠ Terlambat",          wrapScrollable(overduePanel));
        tabs.addTab("🏆 Buku Populer",       wrapScrollable(mostBorrowedPanel));
        tabs.addTab("👥 Aktivitas Anggota",  wrapScrollable(memberPanel));
        tabs.addTab("📅 Bulanan",            wrapScrollable(monthlyPanel));

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(tabs,   BorderLayout.CENTER);
    }

    private JScrollPane wrapScrollable(JPanel panel) {
        JScrollPane sp = new JScrollPane(panel);
        sp.setBorder(null);
        return sp;
    }

    private void loadAll() {
        if (!user.hasPermission(Permission.VIEW_REPORTS)) {
            JOptionPane.showMessageDialog(this,
                "Anda tidak memiliki akses ke laporan.",
                "Akses Ditolak", JOptionPane.WARNING_MESSAGE);
            return;
        }

        loadReport("overdue", () -> reportService.overdueLoans(), overduePanel);
        loadReport("mostBorrowed", () -> reportService.mostBorrowedBooks(10), mostBorrowedPanel);
        loadReport("member", () -> reportService.memberActivity(), memberPanel);

        // Bulanan (Map → List<Object[]>)
        new SwingWorker<Map<String, Integer>, Void>() {
            @Override protected Map<String, Integer> doInBackground() {
                return reportService.loansByMonth(12);
            }
            @Override protected void done() {
                try {
                    monthlyPanel.clearRows();
                    get().forEach((ym, cnt) -> monthlyPanel.addRow(new Object[]{ym, cnt}));
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void loadReport(String name, java.util.concurrent.Callable<List<Object[]>> supplier,
                            ReportTablePanel panel) {
        new SwingWorker<List<Object[]>, Void>() {
            @Override protected List<Object[]> doInBackground() throws Exception {
                return supplier.call();
            }
            @Override protected void done() {
                try { panel.setData(get()); }
                catch (Exception ex) { showError(ex); }
            }
        }.execute();
    }

    private void showError(Exception ex) {
        Throwable cause = (ex instanceof java.util.concurrent.ExecutionException)
            ? ex.getCause() : ex;
        JOptionPane.showMessageDialog(this,
            "Gagal memuat laporan:\n" + cause.getMessage(),
            "Error", JOptionPane.ERROR_MESSAGE);
    }
}
