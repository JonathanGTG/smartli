package com.smartlib.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Pemeriksa struktur database. Tabel peminjaman (loans) dibuat oleh modul lain dan bisa saja
 * belum ada di database lokal; DAO memakai class ini agar pengecekan "apakah buku/anggota punya
 * riwayat peminjaman" tidak error ketika tabelnya belum ada.
 */
final class SchemaInspector {

    private SchemaInspector() { }

    static boolean columnExists(Connection c, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** Escape karakter khusus LIKE (% _ \) lalu bungkus dengan % di kiri-kanan. */
    static String contains(String keyword) {
        String k = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + k + "%";
    }
}