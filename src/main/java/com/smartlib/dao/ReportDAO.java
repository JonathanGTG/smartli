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
            "SELECT u.username, li.title, l.due_date, " +
            "       DATEDIFF(CURDATE(), l.due_date) AS overdue_days " +
            "FROM loans l " +
            "JOIN users u ON u.id = l.user_id " +
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

    // ─────────────────────── helper ───────────────────────────────────────────

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
