package com.smartlib.dao;

import java.sql.*;
import java.time.LocalDate;

/**
 * Akses database transaksi peminjaman dan pengembalian (tabel loans dan loan_items).
 * Diselaraskan dengan skema database SmartLib dan PRD.
 */
public final class LoanDAO {
    private LoanDAO() {
    }

    public record MemberData(boolean active, String message) { }
    public record CopyData(long copyId, long itemId, String status, String title) { }
    public record LoanItemData(long loanItemId, long loanId, long copyId, long memberId,
                        long itemId, LocalDate dueDate, String status) { }

    public static MemberData lockAndValidateMember(Connection connection, long memberId) throws SQLException {
        String sql = """
                SELECT m.valid_until, m.status AS member_status, u.status AS user_status
                FROM members m
                LEFT JOIN users u ON u.id = m.user_id
                WHERE m.id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new MemberData(false, "Data anggota tidak ditemukan.");
                }
                String memberStatus = rs.getString("member_status");
                String userStatus = rs.getString("user_status");
                Date validUntil = rs.getDate("valid_until");
                if (memberStatus == null || !"ACTIVE".equalsIgnoreCase(memberStatus)) {
                    return new MemberData(false, "Status keanggotaan tidak aktif.");
                }
                if (userStatus != null && !"ACTIVE".equalsIgnoreCase(userStatus)) {
                    return new MemberData(false, "Akun pengguna anggota tidak aktif.");
                }
                if (validUntil != null && validUntil.toLocalDate().isBefore(LocalDate.now())) {
                    return new MemberData(false, "Masa berlaku keanggotaan sudah berakhir.");
                }
                return new MemberData(true, "OK");
            }
        }
    }

    public static MemberData validateProcessor(Connection connection, long userId) throws SQLException {
        String sql = """
                SELECT u.status AS account_status, r.name AS role_name
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE u.id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new MemberData(false, "Akun petugas tidak ditemukan.");
                String accountStatus = rs.getString("account_status");
                String roleName = rs.getString("role_name");
                if (accountStatus == null || !"ACTIVE".equalsIgnoreCase(accountStatus)) {
                    return new MemberData(false, "Akun petugas tidak aktif.");
                }
                if (roleName == null || (!"ADMIN".equalsIgnoreCase(roleName)
                        && !"PETUGAS".equalsIgnoreCase(roleName))) {
                    return new MemberData(false, "Transaksi hanya dapat diproses oleh Admin atau Petugas.");
                }
                return new MemberData(true, "OK");
            }
        }
    }

    public static int countActiveLoans(Connection connection, long memberId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM loans l
                JOIN loan_items li ON li.loan_id = l.id
                WHERE l.member_id = ? AND li.status = 'BORROWED'
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public static boolean hasUnpaidFine(Connection connection, long memberId) throws SQLException {
        String sql = """
                SELECT EXISTS(
                    SELECT 1
                    FROM fines f
                    JOIN loan_items li ON li.id = f.loan_item_id
                    JOIN loans l ON l.id = li.loan_id
                    WHERE l.member_id = ? AND f.status = 'UNPAID'
                )
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    public static CopyData lockCopy(Connection connection, long copyId) throws SQLException {
        String sql = """
                SELECT c.id AS copy_id, c.item_id, c.status AS availability_status, i.title
                FROM item_copies c
                JOIN library_items i ON i.id = c.item_id
                WHERE c.id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, copyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new CopyData(
                        rs.getLong("copy_id"),
                        rs.getLong("item_id"),
                        rs.getString("availability_status"),
                        rs.getString("title")
                );
            }
        }
    }

    public static long insertLoan(Connection connection, long memberId, long processedBy) throws SQLException {
        String sql = "INSERT INTO loans (member_id, processed_by, borrowed_at, status) VALUES (?, ?, NOW(), 'ACTIVE')";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, memberId);
            ps.setLong(2, processedBy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Gagal memperoleh ID transaksi peminjaman.");
                return keys.getLong(1);
            }
        }
    }

    public static long insertLoanItem(Connection connection, long loanId, long copyId, LocalDate dueDate)
            throws SQLException {
        String sql = "INSERT INTO loan_items (loan_id, copy_id, due_date, returned_at, status) VALUES (?, ?, ?, NULL, 'BORROWED')";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, loanId);
            ps.setLong(2, copyId);
            ps.setDate(3, Date.valueOf(dueDate));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Gagal memperoleh ID detail peminjaman.");
                return keys.getLong(1);
            }
        }
    }

    public static boolean changeCopyStatus(Connection connection, long copyId, String expected, String target)
            throws SQLException {
        String sql = "UPDATE item_copies SET status = ? WHERE id = ? AND status = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, target);
            ps.setLong(2, copyId);
            ps.setString(3, expected);
            return ps.executeUpdate() == 1;
        }
    }

    public static LoanItemData lockLoanItemForReturn(Connection connection, long loanItemId) throws SQLException {
        String sql = """
                SELECT li.id AS loan_item_id, li.loan_id, li.copy_id, l.member_id,
                       c.item_id, li.due_date, li.status
                FROM loan_items li
                JOIN loans l ON l.id = li.loan_id
                JOIN item_copies c ON c.id = li.copy_id
                WHERE li.id = ?
                FOR UPDATE
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, loanItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Date due = rs.getDate("due_date");
                return new LoanItemData(
                        rs.getLong("loan_item_id"), rs.getLong("loan_id"),
                        rs.getLong("copy_id"), rs.getLong("member_id"),
                        rs.getLong("item_id"), due.toLocalDate(), rs.getString("status")
                );
            }
        }
    }

    public static void markLoanItemReturned(Connection connection, long loanItemId, LocalDate returnDate)
            throws SQLException {
        String sql = "UPDATE loan_items SET returned_at = ?, status = 'RETURNED' WHERE id = ? AND status = 'BORROWED'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(returnDate));
            ps.setLong(2, loanItemId);
            if (ps.executeUpdate() != 1) throw new SQLException("Status detail peminjaman berubah. Muat ulang transaksi.");
        }
    }

    public static void completeLoanIfNoOpenItems(Connection connection, long loanId) throws SQLException {
        String sql = """
                UPDATE loans
                SET status = 'COMPLETED'
                WHERE id = ?
                  AND NOT EXISTS (
                    SELECT 1 FROM loan_items
                    WHERE loan_id = ? AND status = 'BORROWED'
                  )
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, loanId);
            ps.setLong(2, loanId);
            ps.executeUpdate();
        }
    }
}
