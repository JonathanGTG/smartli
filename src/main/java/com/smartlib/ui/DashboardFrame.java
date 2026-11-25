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
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DashboardFrame extends JFrame {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AuthService authService =
            AuthService.getInstance();

    private final ReportService reportService =
            ReportService.getInstance();

    private final User user;
    private final boolean showSummary;

    // Statistik dashboard
    private final StatCard cardBooks =
            new StatCard("Judul koleksi");

    private final StatCard cardMembers =
            new StatCard("Anggota");

    private final StatCard cardLoans =
            new StatCard("Pinjaman aktif");

    private final StatCard cardOverdue =
            new StatCard("Terlambat");

    // Tabel peminjaman terlambat
    private final ReportTablePanel overdueTable =
            new ReportTablePanel(
                    "Peminjaman terlambat",
                    new String[]{
                            "Anggota",
                            "Judul",
                            "Jatuh tempo",
                            "Hari terlambat"
                    }
            );

    // Tabel buku paling sering dipinjam
    private final ReportTablePanel mostBorrowedTable =
            new ReportTablePanel(
                    "Paling sering dipinjam (5 teratas)",
                    new String[]{
                            "Judul",
                            "Jumlah pinjam"
                    }
            );

    private final JLabel lastLoaded = new JLabel(" ");

    private boolean errorShown;


    // =========================
    // CONSTRUCTOR
    // =========================

    public DashboardFrame(User user) {

        super("SmartLib - Dashboard " + user.getRole().getLabel());

        this.user = user;

        this.showSummary =
                user.hasPermission(Permission.VIEW_REPORTS);

        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setContentPane(buildContent());

        setSize(1100, 700);

        setMinimumSize(new Dimension(900, 600));

        setLocationRelativeTo(null);

        refresh();
    }


    // =========================
    // MAIN CONTENT
    // =========================

    private JPanel buildContent() {

        JPanel root = new JPanel(new BorderLayout());

        // Sidebar
        JPanel sidebar = buildSidebar();

        // Area utama dashboard
        JPanel mainContent = buildMainContent();

        // Status bar bagian bawah
        JPanel statusBar = buildStatusBar();

        root.add(sidebar, BorderLayout.WEST);

        root.add(mainContent, BorderLayout.CENTER);

        root.add(statusBar, BorderLayout.SOUTH);

        return root;
    }


    // =========================
    // SIDEBAR
    // =========================

    private JPanel buildSidebar() {

        JPanel sidebar = new JPanel();

        sidebar.setLayout(new BorderLayout());

        sidebar.setPreferredSize(new Dimension(230, 0));

        sidebar.setBackground(new Color(35, 45, 60));

        sidebar.setBorder(
                BorderFactory.createEmptyBorder(15, 10, 15, 10)
        );


        // Bagian atas sidebar
        JPanel header = new JPanel();

        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        header.setBackground(new Color(35, 45, 60));

        JLabel title = new JLabel("📚 SmartLib");

        title.setFont(new Font("SansSerif", Font.BOLD, 22));

        title.setForeground(Color.WHITE);

        title.setAlignmentX(Component.LEFT_ALIGNMENT);


        JLabel subtitle = new JLabel("Library Management System");

        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 11));

        subtitle.setForeground(new Color(190, 200, 215));

        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);


        header.add(title);

        header.add(Box.createVerticalStrut(5));

        header.add(subtitle);

        header.add(Box.createVerticalStrut(25));


        // Panel menu
        JPanel panel = new JPanel();

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.setBackground(new Color(35, 45, 60));


        // =========================
        // MENU NAVIGASI
        // =========================

        addNavButton(
                panel,
                "🏠  Dashboard",
                true,
                () -> refresh()
        );


        addNavButton(
                panel,
                "📖  Katalog Buku",
                user.hasPermission(Permission.VIEW_CATALOG),
                () -> new CatalogFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "📚  Kelola Koleksi",
                user.hasPermission(Permission.MANAGE_COLLECTION),
                () -> new BookManagementFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "🗂  Kategori & Penulis",
                user.hasPermission(Permission.MANAGE_COLLECTION),
                () -> new CategoryManagementFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "👤  Kelola Anggota",
                user.hasPermission(Permission.MANAGE_MEMBERS),
                () -> new MemberManagementFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "🔄  Peminjaman",
                user.hasPermission(Permission.PROCESS_LOAN),
                () -> new LoanFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "↩  Pengembalian",
                user.hasPermission(Permission.PROCESS_RETURN),
                () -> new ReturnFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "📌  Antrean Buku",
                user.hasPermission(Permission.PROCESS_LOAN),
                () -> new ReservationFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "💰  Denda Keterlambatan",
                user.hasPermission(Permission.PROCESS_RETURN),
                () -> new FineFrame().setVisible(true)
        );


        addNavButton(
                panel,
                "📊  Laporan",
                user.hasPermission(Permission.VIEW_REPORTS),
                () -> new ReportFrame(user).setVisible(true)
        );


        addNavButton(
                panel,
                "👥  Kelola Pengguna",
                user.hasPermission(Permission.MANAGE_USERS),
                () -> new UserManagementFrame().setVisible(true)
        );

        addNavButton(
                panel,
                "⚙  Aturan Perpustakaan",
                user.hasPermission(Permission.CHANGE_LOAN_RULES),
                () -> new PolicyManagementFrame().setVisible(true)
        );

        addNavButton(
                panel,
                "🧾  Akun Saya",
                user.hasPermission(Permission.VIEW_OWN_HISTORY),
                () -> new ManageAccountFrame().setVisible(true)
        );


        // Scroll apabila menu terlalu banyak
        JScrollPane scrollPane = new JScrollPane(panel);

        scrollPane.setBorder(null);

        scrollPane.setBackground(new Color(35, 45, 60));

        scrollPane.getViewport().setBackground(new Color(35, 45, 60));

        scrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );


        // Tombol logout bagian bawah
        JButton logoutButton = new JButton("🚪  Keluar");

        styleNavButton(logoutButton);

        logoutButton.addActionListener(e -> doLogout());


        JPanel footer = new JPanel(new BorderLayout());

        footer.setBackground(new Color(35, 45, 60));

        footer.add(logoutButton, BorderLayout.CENTER);


        sidebar.add(header, BorderLayout.NORTH);

        sidebar.add(scrollPane, BorderLayout.CENTER);

        sidebar.add(footer, BorderLayout.SOUTH);


        return sidebar;
    }


    // =========================
    // METHOD UNTUK MEMBUAT
    // TOMBOL NAVIGASI
    // =========================

    private void addNavButton(
            JPanel panel,
            String text,
            boolean allowed,
            Runnable action
    ) {

        // Jika permission tidak tersedia,
        // tombol tidak ditampilkan
        if (!allowed) {
            return;
        }

        JButton button = new JButton(text);

        styleNavButton(button);

        button.addActionListener(e -> action.run());

        panel.add(button);

        panel.add(Box.createVerticalStrut(5));
    }


    // =========================
    // STYLE TOMBOL SIDEBAR
    // =========================

    private static final Color NAV_BG = new Color(50, 65, 85);
    private static final Color NAV_HOVER = new Color(70, 90, 118);
    private static final Color NAV_PRESSED = new Color(30, 40, 55);

    private void styleNavButton(JButton button) {

        // BasicButtonUI: supaya warna tombol dihormati di semua Look and Feel
        // (Windows LAF mengabaikan setBackground sehingga tombol jadi putih)
        button.setUI(new BasicButtonUI());

        button.setFont(
                new Font("SansSerif", Font.PLAIN, 13)
        );

        button.setForeground(Color.WHITE);

        button.setBackground(NAV_BG);

        button.setOpaque(true);

        button.setRolloverEnabled(true);

        button.setContentAreaFilled(true);

        button.setBorderPainted(false);

        button.setFocusPainted(false);

        button.setBorder(
                BorderFactory.createEmptyBorder(
                        12, 10, 12, 10
                )
        );

        button.setHorizontalAlignment(
                SwingConstants.LEFT
        );

        button.setAlignmentX(Component.LEFT_ALIGNMENT);

        button.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 45)
        );

        button.setCursor(
                new Cursor(Cursor.HAND_CURSOR)
        );

        // Efek hover & tekan
        button.getModel().addChangeListener(e -> {
            if (button.getModel().isPressed()) {
                button.setBackground(NAV_PRESSED);
            } else if (button.getModel().isRollover()) {
                button.setBackground(NAV_HOVER);
            } else {
                button.setBackground(NAV_BG);
            }
        });
    }


    // =========================
    // MAIN DASHBOARD
    // =========================

    private JPanel buildMainContent() {

        JPanel root = new JPanel(new BorderLayout(10, 10));

        root.setBackground(new Color(245, 247, 250));

        root.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 20, 15, 20
                )
        );


        // Header dashboard
        JPanel header = new JPanel();

        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        header.setOpaque(false);


        JLabel welcome = new JLabel(
                "Selamat datang, " + user.getFullName()
        );

        welcome.setFont(
                new Font("SansSerif", Font.BOLD, 24)
        );


        JLabel description = new JLabel(
                "Dashboard Sistem Manajemen Perpustakaan SmartLib"
        );

        description.setFont(
                new Font("SansSerif", Font.PLAIN, 13)
        );

        description.setForeground(Color.GRAY);


        header.add(welcome);

        header.add(Box.createVerticalStrut(5));

        header.add(description);

        header.add(Box.createVerticalStrut(15));


        root.add(header, BorderLayout.NORTH);


        // Jika user memiliki permission laporan
        if (showSummary) {

            JPanel dashboardData = new JPanel(
                    new BorderLayout(10, 15)
            );

            dashboardData.setOpaque(false);


            // =========================
            // STATISTIK
            // =========================

            JPanel stats = new JPanel(
                    new GridLayout(1, 4, 10, 10)
            );

            stats.setOpaque(false);

            stats.add(cardBooks);

            stats.add(cardMembers);

            stats.add(cardLoans);

            stats.add(cardOverdue);


            // =========================
            // TABEL
            // =========================

            JSplitPane tables = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    overdueTable,
                    mostBorrowedTable
            );

            tables.setResizeWeight(0.6);

            tables.setBorder(null);

            tables.setPreferredSize(
                    new Dimension(700, 350)
            );


            dashboardData.add(
                    stats,
                    BorderLayout.NORTH
            );

            dashboardData.add(
                    tables,
                    BorderLayout.CENTER
            );


            root.add(
                    dashboardData,
                    BorderLayout.CENTER
            );

        } else {

            // Dashboard untuk user tanpa permission laporan
            JLabel hello = new JLabel(
                    "<html><div style='padding:10px;'>"
                            + "<h2>Selamat datang di SmartLib!</h2>"
                            + "<p>Silakan pilih menu pada sidebar "
                            + "untuk mulai menggunakan sistem.</p>"
                            + "</div></html>"
            );

            hello.setVerticalAlignment(
                    SwingConstants.TOP
            );

            root.add(
                    hello,
                    BorderLayout.CENTER
            );
        }


        return root;
    }


    // =========================
    // STATUS BAR
    // =========================

    private JPanel buildStatusBar() {

        JPanel right = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 4)
        );

        right.setOpaque(false);

        right.add(lastLoaded);


        // Tombol refresh statistik
        if (showSummary) {

            JButton reload = new JButton("🔄 Muat Ulang");

            reload.addActionListener(e -> refresh());

            right.add(reload);
        }


        // Informasi user login
        JPanel row = new JPanel(
                new BorderLayout()
        );

        row.setBorder(
                BorderFactory.createEmptyBorder(
                        5, 10, 5, 10
                )
        );

        row.add(
                new JLabel(
                        "Masuk sebagai "
                                + user.getFullName()
                                + " ("
                                + user.getRole().getLabel()
                                + ")"
                ),
                BorderLayout.WEST
        );

        row.add(
                right,
                BorderLayout.EAST
        );


        JPanel bar = new JPanel(
                new BorderLayout()
        );

        bar.add(
                new JSeparator(),
                BorderLayout.NORTH
        );

        bar.add(
                row,
                BorderLayout.CENTER
        );


        return bar;
    }


    // =========================
    // REFRESH DATA DASHBOARD
    // =========================

    private void refresh() {

        if (!showSummary) {
            return;
        }

        errorShown = false;


        // Statistik
        new SwingWorker<DashboardStats, Void>() {

            @Override
            protected DashboardStats doInBackground() {

                return reportService.getDashboardStats();
            }


            @Override
            protected void done() {

                try {

                    DashboardStats s = get();

                    cardBooks.setValue(
                            String.valueOf(s.totalItems)
                    );

                    cardMembers.setValue(
                            String.valueOf(s.totalMembers)
                    );

                    cardLoans.setValue(
                            String.valueOf(s.activeLoans)
                    );

                    cardOverdue.setValue(
                            String.valueOf(s.overdueLoans)
                    );

                    cardOverdue.setHighlight(
                            s.overdueLoans > 0
                    );

                    lastLoaded.setText(
                            "Dimuat pukul "
                                    + LocalTime.now().format(TIME)
                    );

                } catch (Exception ex) {

                    handleError(ex);
                }
            }

        }.execute();


        // Data peminjaman terlambat
        new SwingWorker<List<Object[]>, Void>() {

            @Override
            protected List<Object[]> doInBackground() {

                return reportService.overdueLoans();
            }


            @Override
            protected void done() {

                try {

                    overdueTable.setData(get());

                } catch (Exception ex) {

                    handleError(ex);
                }
            }

        }.execute();


        // Buku paling sering dipinjam
        new SwingWorker<List<Object[]>, Void>() {

            @Override
            protected List<Object[]> doInBackground() {

                return reportService.mostBorrowedBooks(5);
            }


            @Override
            protected void done() {

                try {

                    mostBorrowedTable.setData(get());

                } catch (Exception ex) {

                    handleError(ex);
                }
            }

        }.execute();
    }


    // =========================
    // LOGOUT
    // =========================

    private void doLogout() {

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Keluar dari SmartLib?",
                "Konfirmasi",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );


        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }


        authService.logout();

        dispose();

        new LoginFrame().setVisible(true);
    }


    // =========================
    // ERROR DATABASE
    // =========================

    private void handleError(Exception ex) {

        Throwable cause =
                (ex instanceof java.util.concurrent.ExecutionException)
                        ? ex.getCause()
                        : ex;


        if (cause instanceof DataAccessException
                && !errorShown) {

            errorShown = true;

            JOptionPane.showMessageDialog(
                    this,
                    "Tidak dapat terhubung ke database.\n"
                            + cause.getMessage(),
                    "Kesalahan database",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

}