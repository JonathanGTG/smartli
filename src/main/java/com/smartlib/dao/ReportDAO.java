package com.smartlib.dao;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.exception.DataAccessException;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Query agregat untuk laporan dashboard:
 * - statistik umum (jumlah buku, anggota, peminjaman aktif, buku terlambat)
 * - laporan buku paling sering dipinjam
 * - laporan aktivitas anggota
 */
public class ReportDAO {

    // ─────────────────────── Statistik Umum ───────────────────────

    public int countTotalItems() {
        return queryCount("SELECT COUNT(*) FROM library_items");
    }

    public int countTotalCopies() {
        return queryCount("SELECT COUNT(*) FROM book_copies");
    }

    public int countAvailableCopies() {
        return queryCount("SELECT COUNT(*) FROM book_copies WHERE status = 'AVAILABLE'");
    }

    public int countTotalMembers() {
        return queryCount(
            "SELECT COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id WHERE r.name = 'ANGGOTA'");
    }

    public int countActiveLoans() {
        return queryCount("SELECT COUNT(*) FROM loans WHERE status = 'ACTIVE'");
    }

    public int countOverdueLoans() {
        return queryCount("SELECT COUNT(*) FROM loans WHERE status = 'ACTIVE' AND due_date < CURDATE()");
    }

    // ─────────────────────── Laporan Buku Terlambat ───────────────────────

    /**
     * Mengembalikan daftar peminjaman yang terlambat.
     * Setiap baris: [username, judul_buku, due_date, hari_terlambat]
     */
    public List<Object[]> overdueLoansReport() {
        String sql =
            "SELECT COALESCE(u.username, m.member_code) AS username, li.title, l.due_date, " +
            "       DATEDIFF(CURDATE(), l.due_date) AS overdue_days " +
            "FROM loans l " +
            "LEFT JOIN users u ON u.id = l.user_id " +
            "LEFT JOIN members m ON m.id = l.member_id " +
            "JOIN book_copies bc ON bc.id = l.copy_id " +
            "JOIN library_items li ON li.id = bc.item_id " +
            "WHERE l.status = 'ACTIVE' AND l.due_date < CURDATE() " +
            "ORDER BY overdue_days DESC";
        return queryRows(sql);
    }

    // ─────────────────────── Laporan Buku Paling Sering Dipinjam ─────────────

    /**
     * Mengembalikan top-N buku paling sering dipinjam.
     * Setiap baris: [judul_buku, jumlah_pinjam]
     */
    public List<Object[]> mostBorrowedBooksReport(int topN) {
        String sql =
            "SELECT li.title, COUNT(l.id) AS borrow_count " +
            "FROM loans l " +
            "JOIN book_copies bc ON bc.id = l.copy_id " +
            "JOIN library_items li ON li.id = bc.item_id " +
            "GROUP BY li.id, li.title " +
            "ORDER BY borrow_count DESC " +
            "LIMIT " + topN;
        return queryRows(sql);
    }

    // ─────────────────────── Laporan Aktivitas Anggota ───────────────────────

    /**
     * Merangkum aktivitas per anggota: total pinjam, masih aktif, terlambat.
     * Setiap baris: [username, nama_lengkap, total_pinjam, aktif, terlambat]
     */
    public List<Object[]> memberActivityReport() {
        String sql =
            "SELECT u.username, u.full_name, " +
            "       COUNT(l.id)                                                  AS total, " +
            "       SUM(CASE WHEN l.status = 'ACTIVE' THEN 1 ELSE 0 END)        AS aktif, " +
            "       SUM(CASE WHEN l.status = 'ACTIVE' AND l.due_date < CURDATE() THEN 1 ELSE 0 END) AS terlambat " +
            "FROM users u " +
            "JOIN roles r ON r.id = u.role_id " +
            "LEFT JOIN loans l ON l.user_id = u.id " +
            "WHERE r.name = 'ANGGOTA' " +
            "GROUP BY u.id, u.username, u.full_name " +
            "ORDER BY total DESC";
        return queryRows(sql);
    }

    // ─────────────────────── Statistik per Bulan ───────────────────────

    /**
     * Peminjaman per bulan dalam N bulan terakhir.
     * Mengembalikan Map<"YYYY-MM", jumlah>.
     */
    public Map<String, Integer> loansByMonth(int lastNMonths) {
        String sql =
            "SELECT DATE_FORMAT(borrow_date, '%Y-%m') AS ym, COUNT(*) AS cnt " +
            "FROM loans " +
            "WHERE borrow_date >= DATE_SUB(CURDATE(), INTERVAL " + lastNMonths + " MONTH) " +
            "GROUP BY ym ORDER BY ym";
        Map<String, Integer> map = new LinkedHashMap<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) map.put(rs.getString("ym"), rs.getInt("cnt"));
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca statistik bulanan.", e);
        }
        return map;
    }

    // ─────────────────────── Ringkasan Pribadi Anggota ───────────────────────

    /** Jumlah eksemplar yang sedang dipinjam anggota. */
    public int countMemberActiveLoans(int memberId) {
        return queryCount(
            "SELECT COUNT(*) FROM loan_items li JOIN loans l ON l.id = li.loan_id " +
            "WHERE l.member_id = ? AND li.status = 'BORROWED'", memberId);
    }

    /** Jumlah antrean aktif (WAITING + OFFERED) milik anggota. */
    public int countMemberQueue(int memberId) {
        return queryCount(
            "SELECT COUNT(*) FROM reservations WHERE member_id = ? AND status IN ('WAITING','OFFERED')", memberId);
    }

    /** Jumlah antrean yang sudah ditawarkan (buku siap diambil). */
    public int countMemberOffered(int memberId) {
        return queryCount(
            "SELECT COUNT(*) FROM reservations WHERE member_id = ? AND status = 'OFFERED'", memberId);
    }

    /** Total nominal denda UNPAID milik anggota yang sudah tercatat di tabel fines. */
    public java.math.BigDecimal sumMemberUnpaidFines(int memberId) {
        String sql = "SELECT COALESCE(SUM(f.amount), 0) FROM fines f " +
                     "JOIN loan_items li ON li.id = f.loan_item_id " +
                     "JOIN loans l ON l.id = li.loan_id " +
                     "WHERE l.member_id = ? AND f.status = 'UNPAID'";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : java.math.BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menghitung denda anggota.", e);
        }
    }

    /** Daftar due_date dari pinjaman aktif (BORROWED) milik anggota yang sudah melewati jatuh tempo. */
    public List<java.time.LocalDate> findMemberOverdueDueDates(int memberId) {
        String sql = "SELECT li.due_date FROM loan_items li " +
                     "JOIN loans l ON l.id = li.loan_id " +
                     "WHERE l.member_id = ? AND li.status = 'BORROWED' AND li.due_date < CURDATE() " +
                     "AND NOT EXISTS (SELECT 1 FROM fines f WHERE f.loan_item_id = li.id)";
        List<java.time.LocalDate> dueDates = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate(1);
                    if (d != null) dueDates.add(d.toLocalDate());
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal memeriksa pinjaman terlambat anggota.", e);
        }
        return dueDates;
    }

    /** Pinjaman berjalan milik anggota. Setiap baris: [judul, jatuh_tempo, status]. */
    public List<Object[]> memberActiveLoansReport(int memberId) {
        String sql = "SELECT item.title, li.due_date, " +
                     "       CASE WHEN li.due_date < CURDATE() THEN 'Terlambat' ELSE 'Dipinjam' END AS state " +
                     "FROM loan_items li " +
                     "JOIN loans l ON l.id = li.loan_id " +
                     "JOIN item_copies c ON c.id = li.copy_id " +
                     "JOIN library_items item ON item.id = c.item_id " +
                     "WHERE l.member_id = ? AND li.status = 'BORROWED' " +
                     "ORDER BY li.due_date ASC";
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, memberId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{rs.getString(1), rs.getDate(2), rs.getString(3)});
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca pinjaman anggota.", e);
        }
        return rows;
    }

    // ─────────────────────── helper ───────────────────────────────────────────

    private int queryCount(String sql, int param) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menghitung data anggota.", e);
        }
    }

    private int queryCount(String sql) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menghitung data: " + sql, e);
        }
    }

    private List<Object[]> queryRows(String sql) {
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            while (rs.next()) {
                Object[] row = new Object[cols];
                for (int i = 0; i < cols; i++) row[i] = rs.getObject(i + 1);
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca laporan.", e);
        }
        return rows;
    }
}
