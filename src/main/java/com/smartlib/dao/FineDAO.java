package com.smartlib.dao;

import com.smartlib.model.FineRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public final class FineDAO {
    private FineDAO() {
    }

    public static void insertFine(Connection connection, long loanItemId, int overdueDays,
                           java.math.BigDecimal ratePerDay, java.math.BigDecimal amount) throws SQLException {
        String sql = "INSERT INTO fines (loan_item_id, overdue_days, rate_per_day, amount, status, assessed_at, paid_at) VALUES (?, ?, ?, ?, 'UNPAID', NOW(), NULL)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, loanItemId);
            ps.setInt(2, overdueDays);
            ps.setBigDecimal(3, ratePerDay);
            ps.setBigDecimal(4, amount);
            ps.executeUpdate();
        }
    }

    public static List<FineRecord> findByMember(Connection connection, long memberId) throws SQLException {
        String sql = """
                SELECT f.fine_id, f.loan_item_id, i.title, f.overdue_days,
                       f.rate_per_day, f.amount, f.status, f.assessed_at, f.paid_at
                FROM fines f
                JOIN loan_items li ON li.loan_item_id = f.loan_item_id
                JOIN loans l ON l.loan_id = li.loan_id
                JOIN item_copies c ON c.copy_id = li.copy_id
                JOIN library_items i ON i.item_id = c.item_id
                WHERE l.member_id = ?
                ORDER BY f.assessed_at DESC, f.fine_id DESC
                """;
        List<FineRecord> records = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp assessed = rs.getTimestamp("assessed_at");
                    Timestamp paid = rs.getTimestamp("paid_at");
                    records.add(new FineRecord(
                            rs.getLong("fine_id"), rs.getLong("loan_item_id"),
                            rs.getString("title"), rs.getInt("overdue_days"),
                            rs.getBigDecimal("rate_per_day"), rs.getBigDecimal("amount"),
                            rs.getString("status"), assessed == null ? null : assessed.toLocalDateTime(),
                            paid == null ? null : paid.toLocalDateTime()
                    ));
                }
            }
        }
        return records;
    }

    public static void markPaid(Connection connection, long fineId) throws SQLException {
        String sql = "UPDATE fines SET status = 'PAID', paid_at = NOW() WHERE fine_id = ? AND status = 'UNPAID'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, fineId);
            if (ps.executeUpdate() != 1) throw new SQLException("Denda tidak ditemukan atau sudah dibayar.");
        }
    }
}
