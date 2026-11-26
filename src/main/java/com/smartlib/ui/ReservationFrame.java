package com.smartlib.ui;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.BorrowReceipt;
import com.smartlib.model.ReservationReceipt;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;
import com.smartlib.service.LoanService;
import com.smartlib.service.ReservationService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Form pengelolaan antrean peminjaman buku (FIFO) untuk Admin & Petugas.
 * Menggunakan dropdown anggota & judul buku layak antre serta tabel antrean aktif
 * tanpa input ID manual.
 * Dikerjakan oleh: Royceyana Yosephine Silaban
 */
public class ReservationFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private record MemberItem(long id, String code, String name) {
        @Override
        public String toString() {
            return "[" + code + "] " + name;
        }
    }

    private record BookItem(long id, String title, int borrowedCount) {
        @Override
        public String toString() {
            return title + " (" + borrowedCount + " dipinjam)";
        }
    }

    private record ReservationRow(
            long reservationId,
            long memberId,
            String memberDisplay,
            String bookTitle,
            String status
    ) { }

    private final JTextField tfStaff = new JTextField(20);
    private final JComboBox<MemberItem> cbMember = new JComboBox<>();
    private final JComboBox<BookItem> cbBook = new JComboBox<>();
    private final JTextField tfSearch = new JTextField(20);

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID Antrean", "Anggota", "Judul Buku", "Waktu Pengajuan", "Posisi / Giliran", "Status", "Batas Ambil"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JLabel lblSelected = new JLabel("Pilih baris antrean pada tabel untuk menerima penawaran atau membatalkan.");

    private final List<ReservationRow> activeRows = new ArrayList<>();
    private final ReservationService reservationService = new ReservationService();
    private final LoanService loanService = new LoanService();
    private long currentStaffId = 1;

    public ReservationFrame() {
        super("SmartLib — Kelola Antrean Buku (FIFO)");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(980, 580);
        setMinimumSize(new Dimension(860, 500));
        setLocationRelativeTo(null);

        initStaffInfo();
        initLayout();
        loadAllData();
    }

    private void initStaffInfo() {
        User u = AuthService.getInstance().getCurrentUser().orElse(null);
        if (u != null) {
            currentStaffId = u.getId();
            tfStaff.setText(u.getFullName() + " (ID: " + u.getId() + ")");
        } else {
            currentStaffId = 1;
            tfStaff.setText("Petugas (ID: 1)");
        }
        tfStaff.setEditable(false);
        tfStaff.setBackground(new Color(240, 240, 240));
    }

    private void initLayout() {
        // ===== PANEL ATAS: FORM TAMBAH ANTREAN BARU =====
        JPanel addPanel = new JPanel(new GridBagLayout());
        addPanel.setBorder(BorderFactory.createTitledBorder("Daftarkan Anggota ke Antrean Baru"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 8, 5, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        addPanel.add(new JLabel("Petugas:"), c);
        c.gridx = 1; c.weightx = 1.0;
        addPanel.add(tfStaff, c);

        c.gridx = 2; c.weightx = 0;
        addPanel.add(new JLabel("Pilih Anggota:"), c);
        c.gridx = 3; c.weightx = 1.0;
        addPanel.add(cbMember, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        addPanel.add(new JLabel("Judul Buku (Sedang Dipinjam):"), c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1.0;
        addPanel.add(cbBook, c);

        JButton btnJoin = UiKit.accentButton("+ Masuk Antrean", Theme.NAVY_700);
        btnJoin.addActionListener(e -> joinQueue());
        c.gridx = 3; c.gridwidth = 1; c.weightx = 0;
        addPanel.add(btnJoin, c);

        // ===== PANEL FILTER TABEL =====
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        filterPanel.add(new JLabel("Cari Antrean (Anggota / Judul / Status):"));
        filterPanel.add(tfSearch);
        JButton btnFilter = new JButton("Filter");
        btnFilter.addActionListener(e -> applyFilter());
        JButton btnReset = new JButton("Reset");
        btnReset.addActionListener(e -> {
            tfSearch.setText("");
            sorter.setRowFilter(null);
        });
        JButton btnRefresh = new JButton("⟳ Muat Ulang");
        btnRefresh.addActionListener(e -> loadAllData());
        tfSearch.addActionListener(e -> applyFilter());

        filterPanel.add(btnFilter);
        filterPanel.add(btnReset);
        filterPanel.add(btnRefresh);

        JPanel topContainer = new JPanel(new BorderLayout(0, 4));
        topContainer.setBorder(BorderFactory.createEmptyBorder(10, 12, 4, 12));
        topContainer.add(addPanel, BorderLayout.NORTH);
        topContainer.add(filterPanel, BorderLayout.SOUTH);

        // ===== PANEL TENGAH: TABEL ANTREAN AKTIF =====
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        Theme.styleTable(table);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelectionLabel();
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 12, 0, 12),
                BorderFactory.createTitledBorder("Daftar Antrean Aktif (WAITING & OFFERED)")
        ));

        // ===== PANEL BAWAH: INFO TERPILIH & TOMBOL AKSI =====
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        lblSelected.setFont(lblSelected.getFont().deriveFont(Font.BOLD, 12f));
        bottomPanel.add(lblSelected, BorderLayout.NORTH);

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnExpire = UiKit.accentButton("⏱ Proses Kedaluwarsa (>24 Jam)", new Color(117, 117, 117));
        btnExpire.addActionListener(e -> processExpired());

        JButton btnCancel = UiKit.accentButton("✕ Batalkan Antrean Terpilih", new Color(198, 40, 40));
        btnCancel.addActionListener(e -> cancelSelectedQueue());

        JButton btnAccept = UiKit.accentButton("✓ Terima Penawaran & Pinjam", Theme.BLUE);
        btnAccept.addActionListener(e -> acceptSelectedOffer());

        actionButtons.add(btnExpire);
        actionButtons.add(btnCancel);
        actionButtons.add(btnAccept);
        bottomPanel.add(actionButtons, BorderLayout.SOUTH);

        add(topContainer, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void applyFilter() {
        String text = tfSearch.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }

    private void loadAllData() {
        loadDropdowns();
        loadActiveReservations();
    }

    private void loadDropdowns() {
        cbMember.removeAllItems();
        cbBook.removeAllItems();

        String sqlMembers = "SELECT m.id, m.member_code, m.full_name "
                + "FROM members m "
                + "LEFT JOIN users u ON u.id = m.user_id "
                + "WHERE m.status = 'ACTIVE' AND (u.status IS NULL OR u.status = 'ACTIVE') "
                + "ORDER BY m.member_code ASC";

        // Hanya judul buku yang layak diantrekan: tidak ada AVAILABLE, tidak ada RESERVED, dan ada BORROWED
        String sqlBooks = "SELECT i.id, i.title, "
                + "       SUM(CASE WHEN c.status = 'BORROWED' THEN 1 ELSE 0 END) AS borrowed_cnt "
                + "FROM library_items i "
                + "JOIN item_copies c ON c.item_id = i.id "
                + "WHERE i.item_type = 'BUKU' "
                + "GROUP BY i.id, i.title "
                + "HAVING SUM(CASE WHEN c.status = 'AVAILABLE' THEN 1 ELSE 0 END) = 0 "
                + "   AND SUM(CASE WHEN c.status = 'RESERVED' THEN 1 ELSE 0 END) = 0 "
                + "   AND SUM(CASE WHEN c.status = 'BORROWED' THEN 1 ELSE 0 END) > 0 "
                + "ORDER BY i.title ASC";

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlMembers);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cbMember.addItem(new MemberItem(
                            rs.getLong("id"),
                            rs.getString("member_code"),
                            rs.getString("full_name")));
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlBooks);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cbBook.addItem(new BookItem(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getInt("borrowed_cnt")));
                }
            }
        } catch (SQLException ex) {
            showError("Gagal memuat daftar anggota / buku: " + ex.getMessage());
        }
    }

    private void loadActiveReservations() {
        tableModel.setRowCount(0);
        activeRows.clear();

        String sql = "SELECT r.id AS reservation_id, r.member_id, m.member_code, m.full_name, "
                + "       i.title, r.requested_at, r.status, r.expires_at, "
                + "       (SELECT COUNT(*) FROM reservations w "
                + "        WHERE w.item_id = r.item_id AND w.status = 'WAITING' "
                + "          AND (w.requested_at < r.requested_at "
                + "               OR (w.requested_at = r.requested_at AND w.id <= r.id))) AS queue_pos "
                + "FROM reservations r "
                + "JOIN members m ON m.id = r.member_id "
                + "JOIN library_items i ON i.id = r.item_id "
                + "WHERE r.status IN ('WAITING', 'OFFERED') "
                + "ORDER BY CASE WHEN r.status = 'OFFERED' THEN 0 ELSE 1 END, "
                + "         r.requested_at ASC, r.id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long resId = rs.getLong("reservation_id");
                long memberId = rs.getLong("member_id");
                String memberDisplay = rs.getString("member_code") + " - " + rs.getString("full_name");
                String title = rs.getString("title");
                Timestamp reqAt = rs.getTimestamp("requested_at");
                String status = rs.getString("status");
                Timestamp expAt = rs.getTimestamp("expires_at");
                int pos = rs.getInt("queue_pos");

                String reqText = reqAt != null ? reqAt.toLocalDateTime().format(FMT) : "-";
                String posText = "WAITING".equalsIgnoreCase(status) ? "Urutan ke-" + pos : "⭐ Siap Diambil";
                String expText = expAt != null ? expAt.toLocalDateTime().format(FMT) + " WIB" : "Menunggu giliran";

                activeRows.add(new ReservationRow(resId, memberId, memberDisplay, title, status));
                tableModel.addRow(new Object[]{resId, memberDisplay, title, reqText, posText, status, expText});
            }
            updateSelectionLabel();
        } catch (SQLException ex) {
            showError("Gagal memuat daftar antrean aktif: " + ex.getMessage());
        }
    }

    private ReservationRow getSelectedReservation() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= activeRows.size()) return null;
        return activeRows.get(modelRow);
    }

    private void updateSelectionLabel() {
        ReservationRow row = getSelectedReservation();
        if (row == null) {
            lblSelected.setText("Pilih baris antrean pada tabel untuk menerima penawaran atau membatalkan.");
        } else {
            lblSelected.setText("<html>Terpilih: <b>#" + row.reservationId() + "</b> — "
                    + row.memberDisplay() + " | Buku: <b>" + row.bookTitle()
                    + "</b> | Status: <b>" + row.status() + "</b></html>");
        }
    }

    private void joinQueue() {
        MemberItem member = (MemberItem) cbMember.getSelectedItem();
        BookItem book = (BookItem) cbBook.getSelectedItem();
        if (member == null) {
            showError("Pilih anggota terlebih dahulu.");
            return;
        }
        if (book == null) {
            showError("Tidak ada judul buku yang memenuhi syarat antrean saat ini.\n"
                    + "Antrean hanya berlaku untuk buku yang stoknya habis karena sedang dipinjam (bukan dicadangkan/rusak/hilang).");
            return;
        }

        try {
            ReservationReceipt receipt = reservationService.requestReservation(member.id(), book.id());
            JOptionPane.showMessageDialog(this,
                    "Berhasil masuk antrean.\n"
                            + "Anggota        : " + member + "\n"
                            + "Judul Buku     : " + book.title() + "\n"
                            + "ID Antrean     : #" + receipt.reservationId() + "\n"
                            + "Posisi Antrean : Urutan ke-" + receipt.queuePosition(),
                    "Antrean Berhasil", JOptionPane.INFORMATION_MESSAGE);
            loadAllData();
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal membuat antrean: " + ex.getMessage());
        }
    }

    private void acceptSelectedOffer() {
        ReservationRow selected = getSelectedReservation();
        if (selected == null) {
            showError("Pilih baris antrean berstatus OFFERED pada tabel terlebih dahulu.");
            return;
        }
        if (!"OFFERED".equalsIgnoreCase(selected.status())) {
            showError("Hanya antrean berstatus OFFERED (siap diambil) yang dapat diproses menjadi peminjaman.\n"
                    + "Status saat ini: " + selected.status());
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Konfirmasi peminjaman dari penawaran antrean?\n\n"
                        + "ID Antrean : #" + selected.reservationId() + "\n"
                        + "Anggota    : " + selected.memberDisplay() + "\n"
                        + "Judul Buku : " + selected.bookTitle(),
                "Terima Penawaran & Pinjam", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            BorrowReceipt receipt = loanService.borrowReserved(
                    selected.memberId(), selected.reservationId(), currentStaffId);
            JOptionPane.showMessageDialog(this,
                    "Penawaran berhasil diproses menjadi peminjaman!\n\n"
                            + "Anggota      : " + selected.memberDisplay() + "\n"
                            + "Judul Buku   : " + selected.bookTitle() + "\n"
                            + "ID Transaksi : #" + receipt.loanId() + "\n"
                            + "Jatuh Tempo  : " + receipt.dueDate(),
                    "Penawaran Diterima", JOptionPane.INFORMATION_MESSAGE);
            loadAllData();
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal memproses penawaran: " + ex.getMessage());
        }
    }

    private void cancelSelectedQueue() {
        ReservationRow selected = getSelectedReservation();
        if (selected == null) {
            showError("Pilih baris antrean pada tabel yang ingin dibatalkan.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Batalkan antrean berikut?\n\n"
                        + "ID Antrean : #" + selected.reservationId() + "\n"
                        + "Anggota    : " + selected.memberDisplay() + "\n"
                        + "Judul Buku : " + selected.bookTitle() + "\n"
                        + "Status     : " + selected.status(),
                "Konfirmasi Pembatalan Antrean", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            reservationService.cancelReservation(selected.memberId(), selected.reservationId());
            JOptionPane.showMessageDialog(this, "Antrean #" + selected.reservationId() + " berhasil dibatalkan.");
            loadAllData();
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal membatalkan antrean: " + ex.getMessage());
        }
    }

    private void processExpired() {
        try {
            int total = reservationService.processExpiredOffers();
            JOptionPane.showMessageDialog(this, total + " penawaran kedaluwarsa diproses.");
            loadAllData();
        } catch (Exception ex) {
            showError("Gagal memproses penawaran kedaluwarsa: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Antrean Buku", JOptionPane.WARNING_MESSAGE);
    }
}