package com.smartlib.dao;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.enums.AccountStatus;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Role;
import com.smartlib.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Akses data tabel users (join roles). Semua query memakai PreparedStatement. */
public class UserDAO {

    private static final String SELECT_USER =
            "SELECT u.id, u.username, u.password_hash, u.full_name, u.status, "
          + "u.nim, u.study_program, u.email, r.name AS role_name "
          + "FROM users u JOIN roles r ON r.id = u.role_id ";

    public Optional<User> findByUsername(String username) {
        return findOne(SELECT_USER + "WHERE u.username = ?", username);
    }

    public Optional<User> findById(int id) {
        return findOne(SELECT_USER + "WHERE u.id = ?", id);
    }

    public Optional<User> findByNim(String nim) {
        if (nim == null || nim.isBlank()) return Optional.empty();
        return findOne(SELECT_USER + "WHERE u.nim = ?", nim.trim());
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return findOne(SELECT_USER + "WHERE LOWER(u.email) = ?", email.trim().toLowerCase());
    }

    public List<User> findAll() {
        String sql = SELECT_USER + "ORDER BY u.id";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<User> result = new ArrayList<>();
            while (rs.next()) result.add(map(rs));
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca daftar pengguna.", e);
        }
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    public boolean existsByNim(String nim) {
        return findByNim(nim).isPresent();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    /** Menyimpan akun baru dan mengembalikan ID yang dibuat database. */
    public int insert(User user) {
        String sql = "INSERT INTO users (username, password_hash, full_name, role_id, status, nim, study_program, email) "
                   + "VALUES (?, ?, ?, (SELECT id FROM roles WHERE name = ?), ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getStatus().name());
            ps.setString(6, user.getNim());
            ps.setString(7, user.getStudyProgram());
            ps.setString(8, user.getEmail());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            throw new DataAccessException("ID pengguna baru tidak dikembalikan database.", null);
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menyimpan pengguna.", e);
        }
    }

    public void update(int id, String fullName, Role role) {
        execute("UPDATE users SET full_name = ?, role_id = (SELECT id FROM roles WHERE name = ?) WHERE id = ?",
                "Gagal mengubah pengguna.", fullName, role.name(), id);
    }

    public void update(int id, String fullName, Role role, String nim, String studyProgram, String email) {
        execute("UPDATE users SET full_name = ?, role_id = (SELECT id FROM roles WHERE name = ?), "
              + "nim = ?, study_program = ?, email = ? WHERE id = ?",
                "Gagal mengubah pengguna.", fullName, role.name(), nim, studyProgram, email, id);
    }

    public void updateProfile(int id, String fullName, String studyProgram, String email) {
        execute("UPDATE users SET full_name = ?, study_program = ?, email = ? WHERE id = ?",
                "Gagal mengubah profil pengguna.", fullName, studyProgram, email, id);
    }

    public void updateStatus(int id, AccountStatus status) {
        execute("UPDATE users SET status = ? WHERE id = ?",
                "Gagal mengubah status akun.", status.name(), id);
    }

    public void updatePasswordHash(int id, String passwordHash) {
        execute("UPDATE users SET password_hash = ? WHERE id = ?",
                "Gagal mengubah password.", passwordHash, id);
    }

    public void updateLastLogin(int id) {
        execute("UPDATE users SET last_login_at = NOW() WHERE id = ?",
                "Gagal mencatat waktu login.", id);
    }

    public int countActiveAdmins() {
        String sql = "SELECT COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id "
                   + "WHERE r.name = 'ADMIN' AND u.status = 'ACTIVE'";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menghitung admin aktif.", e);
        }
    }

    // ---------- helper ----------

    private Optional<User> findOne(String sql, Object param) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data pengguna.", e);
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

    private User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("full_name"),
                Role.fromDatabase(rs.getString("role_name")),
                AccountStatus.fromDatabase(rs.getString("status")),
                getNullableString(rs, "nim"),
                getNullableString(rs, "study_program"),
                getNullableString(rs, "email"));
    }

    private String getNullableString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }
}
