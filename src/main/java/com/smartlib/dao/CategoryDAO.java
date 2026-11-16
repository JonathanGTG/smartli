package com.smartlib.dao;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Category;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Akses data tabel categories. Semua query memakai PreparedStatement. */
public class CategoryDAO {

    private static final String SELECT = "SELECT id, name, description FROM categories ";

    public List<Category> findAll() {
        return queryList(SELECT + "ORDER BY name");
    }

    /** Mencari kategori yang namanya mengandung kata kunci (tidak peka huruf besar/kecil). */
    public List<Category> searchByName(String keyword) {
        return queryList(SELECT + "WHERE name LIKE ? ORDER BY name", "%" + keyword.trim() + "%");
    }

    public Optional<Category> findById(int id) {
        return queryList(SELECT + "WHERE id = ?", id).stream().findFirst();
    }

    /** Nama dicocokkan persis (tidak peka huruf besar/kecil), dipakai untuk cek duplikat. */
    public Optional<Category> findByName(String name) {
        return queryList(SELECT + "WHERE name = ?", name.trim()).stream().findFirst();
    }

    /** Menyimpan kategori baru; ID dari database dipasang ke objek lewat assignId(). */
    public Category insert(Category category) {
        String sql = "INSERT INTO categories (name, description) VALUES (?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    category.assignId(keys.getInt(1));
                    return category;
                }
            }
            throw new DataAccessException("ID kategori baru tidak dikembalikan database.", null);
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menyimpan kategori.", e);
        }
    }

    public void update(Category category) {
        execute("UPDATE categories SET name = ?, description = ? WHERE id = ?",
                "Gagal mengubah kategori.",
                category.getName(), category.getDescription(), category.getId());
    }

    public void delete(int id) {
        execute("DELETE FROM categories WHERE id = ?", "Gagal menghapus kategori.", id);
    }

    /** Jumlah koleksi yang memakai kategori ini (dipakai sebelum menghapus). */
    public int countItems(int categoryId) {
        String sql = "SELECT COUNT(*) FROM library_items WHERE category_id = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menghitung buku pada kategori.", e);
        }
    }

    // ---------- helper ----------

    private List<Category> queryList(String sql, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<Category> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(new Category(rs.getInt("id"), rs.getString("name"), rs.getString("description")));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data kategori.", e);
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