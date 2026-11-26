package com.smartlib.ui;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.BorrowReceipt;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;
import com.smartlib.service.LoanService;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Form peminjaman buku untuk Petugas.
 * Menggunakan dropdown pencarian anggota dan eksemplar tersedia,
 * serta otomatisasi ID Petugas dari user yang sedang login.
 * Dikerjakan oleh: Novita Barti Br Barus (novtbrtiii)
 */
public class LoanFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    public record MemberItem(long id, String code, String name) {
        @Override public String toString() { return "[" + code + "] " + name; }
    }

    public record CopyItem(long id, String barcode, String title, String shelf) {
        @Override public String toString() {
            return "[" + barcode + "] " + title + " (Rak: " + (shelf == null || shelf.isBlank() ? "-" : shelf) + ")";
        }
    }

    private final JComboBox<MemberItem> cbMember = new JComboBox<>();
    private final JComboBox<CopyItem> cbCopy = new JComboBox<>();
    private final JTextField tfStaffName = new JTextField(20);
    private final JLabel lblDueDate = new JLabel();
    private final LoanService loanService = new LoanService();
    private long currentStaffId = 0;

    public LoanFrame() {
        super("SmartLib — Peminjaman Buku");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(580, 360);
        setLocationRelativeTo(null);

        User u = AuthService.getInstance().getCurrentUser().orElse(null);
        if (u != null) {
            currentStaffId = u.getId();
            tfStaffName.setText(u.getFullName() + " (ID: " + u.getId() + ")");
        } else {
            tfStaffName.setText("Petugas (ID: 1)");
            currentStaffId = 1;
        }
        tfStaffName.setEditable(false);
        tfStaffName.setBackground(new Color(240, 240, 240));

        LocalDate today = LocalDate.now();
        LocalDate due = today.plusDays(LoanService.getLoanPeriodDays());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        lblDueDate.setText("<html>Jatuh tempo: <b>" + due.format(fmt) + "</b> (" + LoanService.getLoanPeriodDays() + " hari)</html>");

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, c, 0, "Petugas Sirkulasi:", tfStaffName);
        addRow(form, c, 1, "Pilih Anggota Peminjam *:", cbMember);
        addRow(form, c, 2, "Pilih Buku Tersedia *:", cbCopy);
        addRow(form, c, 3, "Ketentuan Jatuh Tempo:", lblDueDate);

        JButton btnBorrow = new JButton("✓ Proses Peminjaman Buku");
        btnBorrow.setFont(btnBorrow.getFont().deriveFont(Font.BOLD, 13f));
        btnBorrow.setBackground(new Color(25, 118, 210));
        btnBorrow.setForeground(Color.WHITE);
        btnBorrow.setPreferredSize(new Dimension(220, 38));
        btnBorrow.addActionListener(e -> borrowBook());

        JButton btnRefresh = new JButton("⟳ Refresh Data");
        btnRefresh.setPreferredSize(new Dimension(130, 38));
        btnRefresh.addActionListener(e -> loadData());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.add(btnRefresh);
        btnPanel.add(btnBorrow);

        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        form.add(btnPanel, c);

        add(form, BorderLayout.CENTER);
        loadData();
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent field) {
        c.gridwidth = 1; c.gridy = row; c.gridx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1; panel.add(field, c);
    }

    private void loadData() {
        cbMember.removeAllItems();
        cbCopy.removeAllItems();

        try (Connection conn = DatabaseConnection.getConnection()) {
            String sqlMember = "SELECT m.id, m.member_code, m.full_name "
                    + "FROM members m "
                    + "LEFT JOIN users u ON u.id = m.user_id "
                    + "WHERE m.status = 'ACTIVE' AND (u.status IS NULL OR u.status = 'ACTIVE') "
                    + "ORDER BY m.member_code ASC";
            try (PreparedStatement ps = conn.prepareStatement(sqlMember);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cbMember.addItem(new MemberItem(rs.getLong("id"), rs.getString("member_code"), rs.getString("full_name")));
                }
            }

            String sqlCopies = "SELECT c.id, c.barcode, c.shelf_location, i.title "
                    + "FROM item_copies c "
                    + "JOIN library_items i ON i.id = c.item_id "
                    + "WHERE c.status = 'AVAILABLE' "
                    + "ORDER BY i.title ASC, c.barcode ASC";
            try (PreparedStatement ps = conn.prepareStatement(sqlCopies);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cbCopy.addItem(new CopyItem(rs.getLong("id"), rs.getString("barcode"), rs.getString("title"), rs.getString("shelf_location")));
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat data anggota atau buku: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void borrowBook() {
        MemberItem member = (MemberItem) cbMember.getSelectedItem();
        CopyItem copy = (CopyItem) cbCopy.getSelectedItem();

        if (member == null) {
            JOptionPane.showMessageDialog(this, "Silakan pilih anggota peminjam.", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (copy == null) {
            JOptionPane.showMessageDialog(this, "Tidak ada eksemplar buku yang tersedia untuk dipinjam.", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            BorrowReceipt receipt = loanService.borrow(member.id(), copy.id(), currentStaffId);
            JOptionPane.showMessageDialog(this,
                    "Peminjaman Berhasil!\n\n"
                            + "Peminjam    : " + member.name() + "\n"
                            + "Buku        : " + copy.title() + " (" + copy.barcode() + ")\n"
                            + "Jatuh Tempo : " + receipt.dueDate() + "\n"
                            + "No Transaksi: #" + receipt.loanId(),
                    "Sukses", JOptionPane.INFORMATION_MESSAGE);
            loadData();
        } catch (BusinessRuleException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validasi Peminjaman Ditolak", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gagal memproses peminjaman: " + ex.getMessage(), "Kesalahan", JOptionPane.ERROR_MESSAGE);
        }
    }
}