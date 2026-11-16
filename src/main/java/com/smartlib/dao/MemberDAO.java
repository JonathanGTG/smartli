package com.smartlib.dao;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.enums.AccountStatus;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Member;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Akses data anggota perpustakaan (tabel members). Semua query memakai PreparedStatement.
 * Email, telepon, dan alamat yang kosong disimpan sebagai NULL agar kolom email yang unik
 * tidak bentrok antar anggota yang belum punya email.
 */
public class MemberDAO {

    private static final String SELECT =
            "SELECT id, member_code, full_name, email, phone, address, join_date, status, user_id FROM members ";

    // ---------- membaca ----------

    public List<Member> findAll() {
        return search("", null);
    }

    /**
     * Mencari anggota. Kata kunci dicocokkan ke kode, nama, email, telepon, dan alamat.
     *
     * @param status null = semua status
     */
    public List<Member> search(String keyword, AccountStatus status) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (member_code LIKE ? OR full_name LIKE ? OR email LIKE ? OR phone LIKE ? OR address LIKE ?)");
            String like = SchemaInspector.contains(keyword);
            for (int i = 0; i < 5; i++) params.add(like);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status.name());
        }
        sql.append(" ORDER BY member_code");
        return queryList(sql.toString(), params.toArray());
    }

    public Optional<Member> findById(int id) {
        return queryList(SELECT + "WHERE id = ?", id).stream().findFirst();
    }

    /** Peta user_id -> username untuk anggota yang terhubung ke akun login (untuk ditampilkan di tabel). */
    public Map<Integer, String> findLinkedUsernames() {
        String sql = "SELECT u.id, u.username FROM users u JOIN members m ON m.user_id = u.id";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            Map<Integer, String> result = new HashMap<>();
            while (rs.next()) result.put(rs.getInt(1), rs.getString(2));
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca akun anggota.", e);
        }
    }

    /** ID akun login ber-role ANGGOTA dengan username ini, kosong bila tidak ada / bukan role ANGGOTA. */
    public Optional<Integer> findAnggotaUserId(String username) {
        String sql = "SELECT u.id FROM users u JOIN roles r ON r.id = u.role_id "
                + "WHERE u.username = ? AND r.name = 'ANGGOTA'";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal memeriksa akun anggota.", e);
        }
    }

    // ---------- pengecekan duplikat (excludeId = id anggota yang sedang diedit, 0 untuk anggota baru) ----------

    public boolean existsByCode(String memberCode, int excludeId) {
        return count("SELECT COUNT(*) FROM members WHERE member_code = ? AND id <> ?", memberCode, excludeId) > 0;
    }

    public boolean existsByEmail(String email, int excludeId) {
        return count("SELECT COUNT(*) FROM members WHERE email = ? AND id <> ?", email, excludeId) > 0;
    }

    public boolean existsByPhone(String phone, int excludeId) {
        return count("SELECT COUNT(*) FROM members WHERE phone = ? AND id <> ?", phone, excludeId) > 0;
    }

    public boolean existsByUserId(int userId, int excludeId) {
        return count("SELECT COUNT(*) FROM members WHERE user_id = ? AND id <> ?", userId, excludeId) > 0;
    }

    /** Kode anggota berikutnya, misalnya AGT-0005 (angka terbesar yang ada + 1). */
    public String nextMemberCode() {
        String sql = "SELECT COALESCE(MAX(CAST(SUBSTRING(member_code, 5) AS UNSIGNED)), 0) "
                + "FROM members WHERE member_code REGEXP '^AGT-[0-9]+$'";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int last = rs.next() ? rs.getInt(1) : 0;
            return String.format("AGT-%04d", last + 1);
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membuat kode anggota.", e);
        }
    }

    // ---------- menulis ----------

    /** Menyimpan anggota baru; ID dari database dipasang ke objek lewat assignId(). */
    public Member insert(Member m) {
        String sql = "INSERT INTO members (member_code, full_name, email, phone, address, join_date, status, user_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, m.getMemberCode());
            ps.setString(2, m.getFullName());
            ps.setString(3, nullIfEmpty(m.getEmail()));
            ps.setString(4, nullIfEmpty(m.getPhone()));
            ps.setString(5, nullIfEmpty(m.getAddress()));
            ps.setDate(6, Date.valueOf(m.getJoinDate()));
            ps.setString(7, m.getStatus().name());
            ps.setObject(8, m.getUserId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    m.assignId(keys.getInt(1));
                    return m;
                }
            }
            throw new DataAccessException("ID anggota baru tidak dikembalikan database.", null);
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menyimpan anggota.", e);
        }
    }

    public void update(Member m) {
        execute("UPDATE members SET member_code = ?, full_name = ?, email = ?, phone = ?, address = ?, "
                        + "join_date = ?, status = ?, user_id = ? WHERE id = ?",
                "Gagal mengubah anggota.",
                m.getMemberCode(), m.getFullName(), nullIfEmpty(m.getEmail()), nullIfEmpty(m.getPhone()),
                nullIfEmpty(m.getAddress()), Date.valueOf(m.getJoinDate()), m.getStatus().name(),
                m.getUserId(), m.getId());
    }

    public void updateStatus(int id, AccountStatus status) {
        execute("UPDATE members SET status = ? WHERE id = ?", "Gagal mengubah status anggota.", status.name(), id);
    }

    public void delete(int id) {
        execute("DELETE FROM members WHERE id = ?", "Gagal menghapus anggota.", id);
    }

    // ---------- relasi transaksi peminjaman ----------
    // Riwayat peminjaman seorang anggota dicari lewat members.id (member_id) atau members.user_id.

    /** Jumlah seluruh transaksi peminjaman anggota (riwayat). 0 bila belum ada tabel loans / akun. */
    public int countLoans(int memberId) {
        return countLoans("SELECT COUNT(*) FROM loans l JOIN members m ON (m.id = l.member_id OR m.user_id = l.user_id) WHERE m.id = ?",
                memberId);
    }

    /** Jumlah peminjaman yang masih berjalan (belum dikembalikan). */
    public int countActiveLoans(int memberId) {
        return countLoans("SELECT COUNT(*) FROM loans l JOIN members m ON (m.id = l.member_id OR m.user_id = l.user_id) "
                + "WHERE m.id = ? AND l.status = 'ACTIVE'", memberId);
    }

    private int countLoans(String sql, int memberId) {
        try (Connection c = DatabaseConnection.getConnection()) {
            if (!SchemaInspector.columnExists(c, "loans", "user_id") && !SchemaInspector.columnExists(c, "loans", "member_id")) return 0;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, memberId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal memeriksa riwayat peminjaman anggota.", e);
        }
    }

    // ---------- helper ----------

    private static String nullIfEmpty(String value) {
        return (value == null || value.isEmpty()) ? null : value;
    }

    private List<Member> queryList(String sql, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Member> result = new ArrayList<>();
                while (rs.next()) {
                    int userId = rs.getInt("user_id");
                    Integer linkedUser = rs.wasNull() ? null : userId;
                    result.add(new Member(rs.getInt("id"), rs.getString("member_code"), rs.getString("full_name"),
                            rs.getString("email"), rs.getString("phone"), rs.getString("address"),
                            rs.getDate("join_date").toLocalDate(),
                            AccountStatus.fromDatabase(rs.getString("status")), linkedUser));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data anggota.", e);
        }
    }

    private int count(String sql, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data anggota.", e);
        }
    }

    private void execute(String sql, String errorMessage, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }
}