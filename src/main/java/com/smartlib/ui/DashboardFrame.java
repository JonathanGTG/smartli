package com.smartlib.ui;

import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Member;
import com.smartlib.model.Permission;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;
import com.smartlib.service.MemberService;
import com.smartlib.service.ReportService;
import com.smartlib.service.ReportService.DashboardStats;
import com.smartlib.service.ReportService.MemberStats;
import com.smartlib.ui.components.ReportTablePanel;
import com.smartlib.ui.components.StatCard;

import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class DashboardFrame extends JFrame {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AuthService authService =
            AuthService.getInstance();

    private final ReportService reportService =
            ReportService.getInstance();

    private final User user;
    private final boolean showSummary;

    // Dashboard khusus Anggota (role tanpa VIEW_REPORTS tetapi punya VIEW_OWN_HISTORY)
    private final boolean showMemberSummary;
    private final Integer memberId;

    private final StatCard cardMyLoans =
            new StatCard("Pinjaman aktif", Theme.BLUE);

    private final StatCard cardMyQueue =
            new StatCard("Antrean aktif", Theme.GOLD);

    private final StatCard cardMyReady =
            new StatCard("Buku siap diambil", Theme.SUCCESS);

    private final StatCard cardMyFines =
            new StatCard("Denda belum dibayar", Theme.DANGER);

    private final ReportTablePanel myLoansTable =
            new ReportTablePanel(
                    "Pinjaman saya yang sedang berjalan",
                    new String[]{"Judul", "Jatuh tempo", "Status"}
            );

    // Statistik dashboard
    private final StatCard cardBooks =
            new StatCard("Judul koleksi", Theme.NAVY_700);

    private final StatCard cardMembers =
            new StatCard("Anggota", Theme.BLUE);

    private final StatCard cardLoans =
            new StatCard("Pinjaman aktif", Theme.BLUE);

    private final StatCard cardOverdue =
            new StatCard("Terlambat", Theme.DANGER);

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

        this.showMemberSummary =
                !showSummary && user.hasPermission(Permission.VIEW_OWN_HISTORY);

        this.memberId = showMemberSummary
                ? new MemberService().getMemberByUserId(user.getId())
                        .map(Member::getId).orElse(null)
                : null;

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

        sidebar.setBackground(Theme.NAVY_950);

        sidebar.setBorder(
                BorderFactory.createEmptyBorder(22, 14, 16, 14)
        );


        // Bagian atas sidebar
        JPanel header = new JPanel();

        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        header.setBackground(Theme.NAVY_950);

        JLabel title = new JLabel("📚 SmartLib");

        title.setFont(Theme.font(Font.BOLD, 22f));

        title.setForeground(Color.WHITE);

        title.setAlignmentX(Component.LEFT_ALIGNMENT);


        JLabel subtitle = new JLabel("Library Management System");

        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 11));

        subtitle.setForeground(new Color(0x9FB2D3));

        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);


        header.add(title);

        header.add(Box.createVerticalStrut(5));

        header.add(subtitle);

        header.add(Box.createVerticalStrut(10));

        JLabel gold = new JLabel(" ");
        gold.setOpaque(true);
        gold.setBackground(Theme.GOLD);
        gold.setPreferredSize(new Dimension(36, 3));
        gold.setMaximumSize(new Dimension(36, 3));
        gold.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(gold);
        header.add(Box.createVerticalStrut(22));


        // Panel menu
        JPanel panel = new JPanel();

        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.setBackground(Theme.NAVY_950);


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
                "📋  Riwayat & Denda Saya",
                user.hasPermission(Permission.VIEW_OWN_HISTORY),
                () -> new MemberHistoryFrame(user).setVisible(true)
        );


        addNavButton(
                panel,
                "📌  Status Antrean Saya",
                user.hasPermission(Permission.REQUEST_QUEUE),
                () -> new MemberQueueFrame(user).setVisible(true)
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

        scrollPane.setBackground(Theme.NAVY_950);

        scrollPane.getViewport().setBackground(Theme.NAVY_950);

        scrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );


        // Tombol logout bagian bawah
        JButton logoutButton = new JButton("🚪  Keluar");

        styleNavButton(logoutButton);

        logoutButton.addActionListener(e -> doLogout());


        JPanel footer = new JPanel(new BorderLayout());

        footer.setBackground(Theme.NAVY_950);

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

        panel.add(Box.createVerticalStrut(3));
    }


    // =========================
    // STYLE TOMBOL SIDEBAR
    // =========================

    private static final Color NAV_BG = Theme.NAVY_950;
    private static final Color NAV_HOVER = Theme.NAVY_800;
    private static final Color NAV_PRESSED = Theme.NAVY_700;
    private static final Color NAV_ACTIVE = new Color(0x112A52);

    private boolean firstNavStyled;

    private void styleNavButton(JButton button) {

        // Tombol pertama (Dashboard) ditandai aktif dengan garis emas di kiri
        final boolean active = !firstNavStyled && button.getText().contains("Dashboard");
        if (active) firstNavStyled = true;
        final Color rest = active ? NAV_ACTIVE : NAV_BG;

        // BasicButtonUI: supaya warna tombol dihormati di semua Look and Feel
        button.setUI(new BasicButtonUI());

        button.setFont(Theme.font(Font.PLAIN, 13f));
        button.setForeground(active ? Color.WHITE : new Color(0xD3DDF0));
        button.setBackground(rest);
        button.setOpaque(true);
        button.setRolloverEnabled(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, active ? Theme.GOLD : rest),
                BorderFactory.createEmptyBorder(11, 12, 11, 10)));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Efek hover & tekan
        button.getModel().addChangeListener(e -> {
            if (button.getModel().isPressed()) {
                button.setBackground(NAV_PRESSED);
            } else if (button.getModel().isRollover()) {
                button.setBackground(NAV_HOVER);
                button.setForeground(Color.WHITE);
            } else {
                button.setBackground(rest);
                button.setForeground(active ? Color.WHITE : new Color(0xD3DDF0));
            }
        });
    }


    // =========================
    // MAIN DASHBOARD
    // =========================

    private JPanel buildMainContent() {

        JPanel root = new JPanel(new BorderLayout(10, 10));

        root.setBackground(Theme.BG);

        root.setBorder(
                BorderFactory.createEmptyBorder(
                        20, 22, 14, 22
                )
        );


        // Header dashboard: banner gradasi navy
        JPanel header = new Theme.HeroPanel(Theme.NAVY_900, Theme.NAVY_700, true);

        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        header.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));


        JLabel welcome = new JLabel(
                "Selamat datang, " + user.getFullName()
        );

        welcome.setFont(Theme.font(Font.BOLD, 24f));

        welcome.setForeground(Color.WHITE);


        JLabel description = new JLabel(
                user.getRole().getLabel()
                        + "  |  Sistem Manajemen Perpustakaan SmartLib"
        );

        description.setFont(Theme.font(Font.PLAIN, 13f));

        description.setForeground(new Color(0xBFCDE8));


        header.add(welcome);

        header.add(Box.createVerticalStrut(6));

        header.add(description);


        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        headerWrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        headerWrap.add(header, BorderLayout.CENTER);

        root.add(headerWrap, BorderLayout.NORTH);


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

        } else if (showMemberSummary) {

            // Dashboard Anggota: ringkasan pribadi
            JPanel memberData = new JPanel(
                    new BorderLayout(10, 15)
            );

            memberData.setOpaque(false);


            JPanel stats = new JPanel(
                    new GridLayout(1, 4, 10, 10)
            );

            stats.setOpaque(false);

            stats.add(cardMyLoans);

            stats.add(cardMyQueue);

            stats.add(cardMyReady);

            stats.add(cardMyFines);


            memberData.add(
                    stats,
                    BorderLayout.NORTH
            );

            memberData.add(
                    myLoansTable,
                    BorderLayout.CENTER
            );


            root.add(
                    memberData,
                    BorderLayout.CENTER
            );

        } else {

            // Dashboard untuk user tanpa permission laporan
            JLabel hello = new JLabel(
                    "<html><div style='padding:10px;color:#14305E;'>"
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
        if (showSummary || showMemberSummary) {

            JButton reload = UiKit.accentButton("🔄 Muat Ulang", Theme.NAVY_700);

            reload.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));

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


        row.setOpaque(false);

        JPanel bar = new JPanel(
                new BorderLayout()
        );

        bar.setBackground(Color.WHITE);

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

        if (showMemberSummary) {

            refreshMember();

            return;
        }

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
    // REFRESH DASHBOARD ANGGOTA
    // =========================

    private void refreshMember() {

        if (memberId == null) {

            lastLoaded.setText(
                    "Akun belum tertaut ke data anggota"
            );

            return;
        }

        errorShown = false;

        final int id = memberId;


        new SwingWorker<MemberStats, Void>() {

            @Override
            protected MemberStats doInBackground() {

                return reportService.getMemberStats(id);
            }


            @Override
            protected void done() {

                try {

                    MemberStats s = get();

                    cardMyLoans.setValue(
                            String.valueOf(s.activeLoans)
                    );

                    cardMyQueue.setValue(
                            String.valueOf(s.queued)
                    );

                    cardMyReady.setValue(
                            String.valueOf(s.readyToPickup)
                    );

                    cardMyFines.setValue(
                            NumberFormat.getCurrencyInstance(
                                    Locale.forLanguageTag("id-ID")
                            ).format(s.unpaidFines)
                    );

                    cardMyFines.setHighlight(
                            s.unpaidFines.signum() > 0
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


        new SwingWorker<List<Object[]>, Void>() {

            @Override
            protected List<Object[]> doInBackground() {

                return reportService.memberActiveLoans(id);
            }


            @Override
            protected void done() {

                try {

                    myLoansTable.setData(get());

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