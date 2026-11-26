

package com.smartlib.ui;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.model.Member;
import com.smartlib.model.User;
import com.smartlib.policy.FinePolicy;
import com.smartlib.service.LoanService;
import com.smartlib.service.MemberService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

public class MemberHistoryFrame extends JFrame {
    private final DefaultTableModel loanModel = new DefaultTableModel(
            new String[]{"Judul Buku", "Tanggal Pinjam", "Jatuh Tempo", "Status"}, 0);
    private final DefaultTableModel fineModel = new DefaultTableModel(
            new String[]{"ID Denda", "Buku Terkait", "Hari Terlambat", "Nominal", "Status Pembayaran"}, 0);

    public MemberHistoryFrame(User user) {
        super("SmartLib — Riwayat & Denda Pribadi");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(780, 500);
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("📖 Pinjaman Aktif & Riwayat", new JScrollPane(new JTable(loanModel)));
        tabs.addTab("💰 Informasi Denda Keterlambatan", new JScrollPane(new JTable(fineModel)));
        add(tabs, BorderLayout.CENTER);

        loadData(user);
    }

    private void loadData(User user) {
        MemberService ms = new MemberService();
        Optional<Member> m = ms.getMemberByUserId(user.getId());
        if (m.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Akun belum terhubung ke data anggota.");
            return;
        }
        int memberId = m.get().getId();

        // Muat Riwayat Pinjam
        String sqlLoan = "SELECT item.title, l.borrow_date, li.due_date, li.status "
                + "FROM loan_items li JOIN loans l ON l.id = li.loan_id "
                + "JOIN book_copies bc ON bc.id = li.copy_id "
                + "JOIN library_items item ON item.id = bc.item_id "
                + "WHERE l.member_id = ? ORDER BY l.id DESC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlLoan)) {
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                loanModel.addRow(new Object[]{
                        rs.getString("title"), rs.getDate("borrow_date"),
                        rs.getDate("due_date"), rs.getString("status")
                });
            }
        } catch (Exception ignored) { }

        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
        FinePolicy policy = LoanService.getFinePolicy();
        LocalDate today = LocalDate.now();

        // Muat Denda Berjalan (buku terlambat yang belum dikembalikan)
        String sqlRunningFine = "SELECT item.title, li.due_date "
                + "FROM loan_items li JOIN loans l ON l.id = li.loan_id "
                + "JOIN book_copies bc ON bc.id = li.copy_id "
                + "JOIN library_items item ON item.id = bc.item_id "
                + "WHERE l.member_id = ? AND li.status = 'BORROWED' AND li.due_date < CURDATE() "
                + "AND NOT EXISTS (SELECT 1 FROM fines f WHERE f.loan_item_id = li.id) "
                + "ORDER BY li.due_date ASC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlRunningFine)) {
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Date sqlDue = rs.getDate("due_date");
                if (sqlDue == null) continue;
                int overdueDays = policy.overdueDays(sqlDue.toLocalDate(), today);
                if (overdueDays > 0) {
                    BigDecimal amount = policy.calculateFine(overdueDays);
                    fineModel.addRow(new Object[]{
                            "- (Berjalan)", rs.getString("title"),
                            overdueDays, currency.format(amount),
                            "BERJALAN (Belum Dikembalikan)"
                    });
                }
            }
        } catch (Exception ignored) { }

        // Muat Denda Tercatat (sudah dikembalikan)
        String sqlFine = "SELECT f.id, item.title, f.overdue_days, f.amount, f.status "
                + "FROM fines f JOIN loan_items li ON li.id = f.loan_item_id "
                + "JOIN loans l ON l.id = li.loan_id "
                + "JOIN book_copies bc ON bc.id = li.copy_id "
                + "JOIN library_items item ON item.id = bc.item_id "
                + "WHERE l.member_id = ? ORDER BY f.id DESC";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlFine)) {
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                fineModel.addRow(new Object[]{
                        rs.getInt("id"), rs.getString("title"),
                        rs.getInt("overdue_days"), currency.format(rs.getBigDecimal("amount")),
                        rs.getString("status")
                });
            }
        } catch (Exception ignored) { }
    }
}
