package com.smartlib.dao;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Author;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Akses data tabel authors. Semua query memakai PreparedStatement. */
public class AuthorDAO {

    private static final String SELECT = "SELECT id, name, biography FROM authors ";

    public List<Author> findAll() {
        return queryList(SELECT + "ORDER BY name");
    }

    /** Mencari penulis yang namanya mengandung kata kunci (tidak peka huruf besar/kecil). */
    public List<Author> searchByName(String keyword) {
        return queryList(SELECT + "WHERE name LIKE ? ORDER BY name", "%" + keyword.trim() + "%");
    }

    public Optional<Author> findById(int id) {
        return queryList(SELECT + "WHERE id = ?", id).stream().findFirst();
    }

    /** Nama dicocokkan persis (tidak peka huruf besar/kecil), dipakai untuk cek duplikat. */
    public Optional<Author> findByName(String name) {
        return queryList(SELECT + "WHERE name = ?", name.trim()).stream().findFirst();
    }

    /** Menyimpan penulis baru; ID dari database dipasang ke objek lewat assignId(). */
    public Author insert(Author author) {
        String sql = "INSERT INTO authors (name, biography) VALUES (?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, author.getName());
            ps.setString(2, author.getBiography());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    author.assignId(keys.getInt(1));
                    return author;
                }
            }
            throw new DataAccessException("ID penulis baru tidak dikembalikan database.", null);
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menyimpan penulis.", e);
        }
    }

    public void update(Author author) {
        execute("UPDATE authors SET name = ?, biography = ? WHERE id = ?",
                "Gagal mengubah penulis.",
                author.getName(), author.getBiography(), author.getId());
    }

    public void delete(int id) {
        execute("DELETE FROM authors WHERE id = ?", "Gagal menghapus penulis.", id);
    }

    /** Jumlah koleksi yang ditulis penulis ini (dipakai sebelum menghapus). */
    public int countItems(int authorId) {
        String sql = "SELECT COUNT(*) FROM item_authors WHERE author_id = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, authorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menghitung buku milik penulis.", e);
        }
    }

    // ---------- helper ----------

    private List<Author> queryList(String sql, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Author> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new Author(rs.getInt("id"), rs.getString("name"), rs.getString("biography")));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data penulis.", e);
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