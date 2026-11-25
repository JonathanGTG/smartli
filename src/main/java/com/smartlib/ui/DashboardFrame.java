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
import java.awt.event.KeyEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Jendela utama setelah login. Navigasi lewat menu bar (hanya menu yang boleh diakses role pengguna);
 * bagian tengah menampilkan ringkasan dan dua tabel untuk Admin/Petugas.
 */
public class DashboardFrame extends JFrame {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AuthService authService = AuthService.getInstance();
    private final ReportService reportService = ReportService.getInstance();
    private final User user;
    private final boolean showSummary;

    private final StatCard cardBooks = new StatCard("Judul koleksi");
    private final StatCard cardMembers = new StatCard("Anggota");
    private final StatCard cardLoans = new StatCard("Pinjaman aktif");
    private final StatCard cardOverdue = new StatCard("Terlambat");

    private final ReportTablePanel overdueTable =
            new ReportTablePanel("Peminjaman terlambat",
                    new String[]{"Anggota", "Judul", "Jatuh tempo", "Hari terlambat"});

    private final ReportTablePanel mostBorrowedTable =
            new ReportTablePanel("Paling sering dipinjam (5 teratas)",
                    new String[]{"Judul", "Jumlah pinjam"});

    private final JLabel lastLoaded = new JLabel(" ");
    private boolean errorShown;

    public DashboardFrame(User user) {
        super("SmartLib - Dashboard " + user.getRole().getLabel());
        this.user = user;
        this.showSummary = user.hasPermission(Permission.VIEW_REPORTS);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());
        setContentPane(buildContent());
        setSize(980, 620);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        refresh();
    }

    // ---------- menu ----------

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu data = new JMenu("Data");
        data.setMnemonic(KeyEvent.VK_D);
        item(data, "Katalog Buku", user.hasPermission(Permission.VIEW_CATALOG),
                () -> new CatalogFrame().setVisible(true));
        item(data, "Kelola Buku & Eksemplar", user.hasPermission(Permission.MANAGE_COLLECTION),
                () -> new BookManagementFrame().setVisible(true));
        item(data, "Kelola Kategori & Penulis", user.hasPermission(Permission.MANAGE_COLLECTION),
                () -> new CategoryManagementFrame().setVisible(true));
        item(data, "Kelola Anggota", user.hasPermission(Permission.MANAGE_MEMBERS),
                () -> new MemberManagementFrame().setVisible(true));
        addIfNotEmpty(bar, data);

        JMenu transaksi = new JMenu("Transaksi");
        transaksi.setMnemonic(KeyEvent.VK_T);
        item(transaksi, "Peminjaman", user.hasPermission(Permission.PROCESS_LOAN),
                () -> new LoanFrame().setVisible(true));
        item(transaksi, "Pengembalian", user.hasPermission(Permission.PROCESS_RETURN),
                () -> new ReturnFrame().setVisible(true));
        item(transaksi, "Antrean", user.hasPermission(Permission.PROCESS_LOAN),
                () -> new ReservationFrame().setVisible(true));
        item(transaksi, "Denda", user.hasPermission(Permission.RECORD_FINE_PAYMENT),
                () -> new FineFrame().setVisible(true));
        addIfNotEmpty(bar, transaksi);

        JMenu laporan = new JMenu("Laporan");
        laporan.setMnemonic(KeyEvent.VK_L);
        item(laporan, "Laporan Lengkap", user.hasPermission(Permission.VIEW_REPORTS),
                () -> new ReportFrame(user).setVisible(true));
        addIfNotEmpty(bar, laporan);

        JMenu akun = new JMenu("Akun");
        akun.setMnemonic(KeyEvent.VK_A);
        item(akun, "Riwayat, Denda & Antrean Saya", user.hasPermission(Permission.VIEW_OWN_HISTORY),
                () -> new MyAccountFrame(user).setVisible(true));
        item(akun, "Kelola Pengguna", user.hasPermission(Permission.MANAGE_USERS),
                () -> new UserManagementFrame().setVisible(true));
        if (akun.getItemCount() > 0) akun.addSeparator();
        item(akun, "Keluar", true, this::doLogout);
        bar.add(akun);

        return bar;
    }

    private static void item(JMenu menu, String text, boolean allowed, Runnable action) {
        if (!allowed) return;
        JMenuItem mi = new JMenuItem(text);
        mi.addActionListener(e -> action.run());
        menu.add(mi);
    }

    private static void addIfNotEmpty(JMenuBar bar, JMenu menu) {
        if (menu.getItemCount() > 0) bar.add(menu);
    }

    // ---------- isi jendela ----------

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 0, 12));

        if (showSummary) {
            JPanel stats = new JPanel(new GridLayout(1, 4, 8, 0));
            stats.add(cardBooks);
            stats.add(cardMembers);
            stats.add(cardLoans);
            stats.add(cardOverdue);

            JSplitPane tables = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, overdueTable, mostBorrowedTable);
            tables.setResizeWeight(0.6);
            tables.setBorder(null);

            root.add(stats, BorderLayout.NORTH);
            root.add(tables, BorderLayout.CENTER);
        } else {
            JLabel hello = new JLabel("<html>Selamat datang, " + escape(user.getFullName()) + ".<br>"
                    + "Katalog, riwayat pinjaman, denda, dan antrean Anda dapat dibuka dari menu di atas.</html>");
            hello.setVerticalAlignment(SwingConstants.TOP);
            root.add(hello, BorderLayout.CENTER);
        }

        root.add(buildStatusBar(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildStatusBar() {
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.add(lastLoaded);
        if (showSummary) {
            JButton reload = new JButton("Muat Ulang");
            reload.addActionListener(e -> refresh());
            right.add(reload);
        }

        JPanel row = new JPanel(new BorderLayout());
        row.add(new JLabel("Masuk sebagai " + user.getFullName() + " (" + user.getRole().getLabel() + ")"),
                BorderLayout.WEST);
        row.add(right, BorderLayout.EAST);

        JPanel bar = new JPanel(new BorderLayout());
        bar.add(new JSeparator(), BorderLayout.NORTH);
        bar.add(row, BorderLayout.CENTER);
        return bar;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ---------- aksi ----------

    private void refresh() {
        if (!showSummary) return;
        errorShown = false;

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
                    cardOverdue.setHighlight(s.overdueLoans > 0);
                    lastLoaded.setText("Dimuat pukul " + LocalTime.now().format(TIME));
                } catch (Exception ex) {
                    handleError(ex);
                }
            }
        }.execute();

        new SwingWorker<List<Object[]>, Void>() {
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
        int confirm = JOptionPane.showConfirmDialog(this, "Keluar dari SmartLib?", "Konfirmasi",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        authService.logout();
        dispose();
        new LoginFrame().setVisible(true);
    }

    /** Satu dialog saja per pemuatan ulang, walau beberapa kueri gagal bersamaan. */
    private void handleError(Exception ex) {
        Throwable cause = (ex instanceof java.util.concurrent.ExecutionException) ? ex.getCause() : ex;
        if (cause instanceof DataAccessException && !errorShown) {
            errorShown = true;
            JOptionPane.showMessageDialog(this,
                    "Tidak dapat terhubung ke database.\n" + cause.getMessage(),
                    "Kesalahan database", JOptionPane.ERROR_MESSAGE);
        }
    }
}
