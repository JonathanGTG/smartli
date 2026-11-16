package com.smartlib.dao;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.enums.ItemStatus;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Author;
import com.smartlib.model.Book;
import com.smartlib.model.BookCopy;
import com.smartlib.model.BookFilter;
import com.smartlib.model.BookStockSummary;
import com.smartlib.model.Category;

import java.sql.Connection;
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
 * Akses data buku (tabel library_items dengan item_type = 'BUKU'), relasi penulis (item_authors),
 * dan eksemplar (book_copies). Semua query memakai PreparedStatement.
 * Penyimpanan buku + penulisnya dilakukan dalam satu transaksi: berhasil semua atau batal semua.
 */
public class BookDAO {

    /** Pekerjaan database yang dijalankan di dalam satu transaksi. */
    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    // =====================================================================
    // BUKU: membaca dan mencari
    // =====================================================================

    /** Mencari buku beserta ringkasan stok eksemplarnya sesuai filter. */
    public List<BookStockSummary> search(BookFilter filter) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (filter.hasTitle()) {
            conditions.add("(i.title LIKE ? OR i.isbn LIKE ?)");
            params.add(SchemaInspector.contains(filter.titleOrIsbn()));
            params.add(SchemaInspector.contains(filter.titleOrIsbn()));
        }
        if (filter.categoryId() != null) {
            conditions.add("i.category_id = ?");
            params.add(filter.categoryId());
        }
        if (filter.hasAuthor()) {
            conditions.add("EXISTS (SELECT 1 FROM item_authors ia JOIN authors a ON a.id = ia.author_id "
                    + "WHERE ia.item_id = i.id AND a.name LIKE ?)");
            params.add(SchemaInspector.contains(filter.authorName()));
        }
        String having = filter.onlyAvailable() ? " HAVING SUM(bc.status = 'AVAILABLE') > 0" : "";
        return querySummaries(conditions, params, having);
    }

    public List<BookStockSummary> findAll() {
        return search(BookFilter.none());
    }

    public Optional<BookStockSummary> findSummaryById(int id) {
        return querySummaries(List.of("i.id = ?"), List.of(id), "").stream().findFirst();
    }

    public Optional<Book> findById(int id) {
        return findSummaryById(id).map(BookStockSummary::book);
    }

    private List<BookStockSummary> querySummaries(List<String> conditions, List<Object> params, String having) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.title, i.isbn, i.publisher, i.publication_year, i.description, i.edition,
                       c.id AS cat_id, c.name AS cat_name, c.description AS cat_desc,
                       COUNT(bc.id) AS total_copies,
                       COALESCE(SUM(bc.status = 'AVAILABLE'), 0) AS available_copies,
                       COALESCE(SUM(bc.status = 'BORROWED'), 0) AS borrowed_copies
                FROM library_items i
                LEFT JOIN categories c ON c.id = i.category_id
                LEFT JOIN book_copies bc ON bc.item_id = i.id
                WHERE i.item_type = 'BUKU'
                """);
        for (String condition : conditions) sql.append(" AND ").append(condition);
        sql.append(" GROUP BY i.id, c.id").append(having).append(" ORDER BY i.title, i.id");

        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            List<int[]> counts = new ArrayList<>();
            Map<Integer, Book> books = new HashMap<>();
            List<Integer> order = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Book book = mapBook(rs);
                    books.put(book.getId(), book);
                    order.add(book.getId());
                    counts.add(new int[]{rs.getInt("total_copies"), rs.getInt("available_copies"),
                            rs.getInt("borrowed_copies")});
                }
            }
            attachAuthors(c, books);
            List<BookStockSummary> result = new ArrayList<>();
            for (int i = 0; i < order.size(); i++) {
                int[] n = counts.get(i);
                result.add(new BookStockSummary(books.get(order.get(i)), n[0], n[1], n[2]));
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data buku.", e);
        }
    }

    private Book mapBook(ResultSet rs) throws SQLException {
        int catId = rs.getInt("cat_id");
        // Data lama tanpa kategori ditampilkan sebagai "Tanpa Kategori" (id 0 = belum ada di database)
        Category category = rs.wasNull()
                ? new Category(0, "Tanpa Kategori", "")
                : new Category(catId, rs.getString("cat_name"), rs.getString("cat_desc"));
        int year = rs.getInt("publication_year");
        Integer publicationYear = rs.wasNull() ? null : year;
        return new Book(rs.getInt("id"), rs.getString("title"), rs.getString("isbn"),
                rs.getString("publisher"), publicationYear, category,
                rs.getString("description"), List.of(), rs.getString("edition"));
    }

    /** Mengisi daftar penulis semua buku sekaligus dengan satu query (hindari query per buku). */
    private void attachAuthors(Connection c, Map<Integer, Book> books) throws SQLException {
        if (books.isEmpty()) return;
        String marks = String.join(",", books.keySet().stream().map(id -> "?").toList());
        String sql = "SELECT ia.item_id, a.id, a.name, a.biography FROM item_authors ia "
                + "JOIN authors a ON a.id = ia.author_id WHERE ia.item_id IN (" + marks + ") ORDER BY a.name";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int index = 1;
            for (Integer id : books.keySet()) ps.setInt(index++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    books.get(rs.getInt("item_id")).addAuthor(
                            new Author(rs.getInt("id"), rs.getString("name"), rs.getString("biography")));
                }
            }
        }
    }

    // =====================================================================
    // BUKU: menyimpan, mengubah, menghapus
    // =====================================================================

    /** Menyimpan buku baru beserta penulisnya; ID dari database dipasang lewat assignId(). */
    public Book insert(Book book) {
        int newId = inTransaction("Gagal menyimpan buku.", c -> {
            String sql = "INSERT INTO library_items (item_type, title, isbn, publisher, publication_year, "
                    + "category_id, description, edition) VALUES ('BUKU', ?, ?, ?, ?, ?, ?, ?)";
            int id;
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bindBook(ps, book);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("ID buku baru tidak dikembalikan database.");
                    id = keys.getInt(1);
                }
            }
            replaceAuthors(c, id, book);
            return id;
        });
        book.assignId(newId);
        return book;
    }

    public void update(Book book) {
        inTransaction("Gagal mengubah buku.", c -> {
            String sql = "UPDATE library_items SET title = ?, isbn = ?, publisher = ?, publication_year = ?, "
                    + "category_id = ?, description = ?, edition = ? WHERE id = ? AND item_type = 'BUKU'";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bindBook(ps, book);
                ps.setInt(8, book.getId());
                ps.executeUpdate();
            }
            replaceAuthors(c, book.getId(), book);
            return null;
        });
    }

    /** Menghapus buku beserta SEMUA eksemplarnya (dalam satu transaksi). Penulis tidak ikut terhapus. */
    public void delete(int itemId) {
        inTransaction("Gagal menghapus buku.", c -> {
            execute(c, "DELETE FROM book_copies WHERE item_id = ?", itemId);
            execute(c, "DELETE FROM library_items WHERE id = ? AND item_type = 'BUKU'", itemId);
            return null;
        });
    }

    private void bindBook(PreparedStatement ps, Book book) throws SQLException {
        ps.setString(1, book.getTitle());
        ps.setString(2, book.getIsbn());                       // null -> NULL (kolom ISBN unik)
        ps.setString(3, book.getPublisher());
        ps.setObject(4, book.getPublicationYear());
        ps.setInt(5, book.getCategory().getId());
        ps.setString(6, book.getDescription());
        ps.setString(7, book.getEdition().isEmpty() ? null : book.getEdition());
    }

    /** Mengganti seluruh relasi penulis sebuah buku dengan daftar penulis pada objek Book. */
    private void replaceAuthors(Connection c, int itemId, Book book) throws SQLException {
        execute(c, "DELETE FROM item_authors WHERE item_id = ?", itemId);
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO item_authors (item_id, author_id) VALUES (?, ?)")) {
            for (Author author : book.getAuthors()) {
                ps.setInt(1, itemId);
                ps.setInt(2, author.getId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // =====================================================================
    // BUKU: pengecekan duplikat dan riwayat
    // =====================================================================

    /** ID buku (jenis apa pun) yang memakai ISBN ini; spasi dan tanda hubung diabaikan. */
    public Optional<Integer> findIdByIsbn(String isbn) {
        String normalized = isbn.replace("-", "").replace(" ", "");
        String sql = "SELECT id FROM library_items "
                + "WHERE REPLACE(REPLACE(isbn, '-', ''), ' ', '') = ? LIMIT 1";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal memeriksa ISBN.", e);
        }
    }

    /** Apakah sudah ada buku lain dengan judul dan edisi yang sama (tidak peka huruf besar/kecil)? */
    public boolean existsByTitleAndEdition(String title, String edition, int excludeId) {
        return count("SELECT COUNT(*) FROM library_items WHERE item_type = 'BUKU' "
                        + "AND title = ? AND COALESCE(edition, '') = ? AND id <> ?",
                title.trim(), edition == null ? "" : edition.trim(), excludeId) > 0;
    }

    public int countCopies(int itemId) {
        return count("SELECT COUNT(*) FROM book_copies WHERE item_id = ?", itemId);
    }

    /** Jumlah transaksi peminjaman (riwayat, apa pun statusnya) untuk seluruh eksemplar sebuah buku. */
    public int countLoansForItem(int itemId) {
        return countLoans("SELECT COUNT(*) FROM loans l JOIN book_copies bc ON bc.id = l.copy_id "
                + "WHERE bc.item_id = ?", itemId);
    }

    // =====================================================================
    // EKSEMPLAR
    // =====================================================================

    public List<BookCopy> findCopies(int itemId) {
        return queryCopies("WHERE item_id = ?", itemId);
    }

    public Optional<BookCopy> findCopyById(int copyId) {
        return queryCopies("WHERE id = ?", copyId).stream().findFirst();
    }

    private List<BookCopy> queryCopies(String where, Object... params) {
        String sql = "SELECT id, item_id, barcode, status, shelf_location FROM book_copies "
                + where + " ORDER BY barcode, id";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                List<BookCopy> result = new ArrayList<>();
                while (rs.next()) {
                    String barcode = rs.getString("barcode");
                    if (barcode == null || barcode.isBlank()) barcode = "COPY-" + rs.getInt("id"); // data lama
                    result.add(new BookCopy(rs.getInt("id"), rs.getInt("item_id"), barcode,
                            ItemStatus.fromDatabase(rs.getString("status")), rs.getString("shelf_location")));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data eksemplar.", e);
        }
    }

    public BookCopy insertCopy(BookCopy copy) {
        String sql = "INSERT INTO book_copies (item_id, barcode, status, shelf_location) VALUES (?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, copy.getItemId());
            ps.setString(2, copy.getBarcode());
            ps.setString(3, copy.getStatus().name());
            ps.setString(4, copy.getShelfLocation().isEmpty() ? null : copy.getShelfLocation());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    copy.assignId(keys.getInt(1));
                    return copy;
                }
            }
            throw new DataAccessException("ID eksemplar baru tidak dikembalikan database.", null);
        } catch (SQLException e) {
            throw new DataAccessException("Gagal menyimpan eksemplar.", e);
        }
    }

    public void updateCopy(BookCopy copy) {
        execute("UPDATE book_copies SET barcode = ?, status = ?, shelf_location = ? WHERE id = ?",
                "Gagal mengubah eksemplar.", copy.getBarcode(), copy.getStatus().name(),
                copy.getShelfLocation().isEmpty() ? null : copy.getShelfLocation(), copy.getId());
    }

    public void deleteCopy(int copyId) {
        execute("DELETE FROM book_copies WHERE id = ?", "Gagal menghapus eksemplar.", copyId);
    }

    /** Apakah barcode sudah dipakai eksemplar lain? */
    public boolean barcodeExists(String barcode, int excludeCopyId) {
        return count("SELECT COUNT(*) FROM book_copies WHERE barcode = ? AND id <> ?", barcode, excludeCopyId) > 0;
    }

    public int countLoansForCopy(int copyId) {
        return countLoans("SELECT COUNT(*) FROM loans WHERE copy_id = ?", copyId);
    }

    // =====================================================================
    // helper
    // =====================================================================

    private int count(String sql, Object... params) {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal membaca data dari database.", e);
        }
    }

    /** Menghitung riwayat peminjaman; bernilai 0 bila tabel loans (modul lain) belum ada. */
    private int countLoans(String sql, int id) {
        try (Connection c = DatabaseConnection.getConnection()) {
            if (!SchemaInspector.columnExists(c, "loans", "copy_id")) return 0;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Gagal memeriksa riwayat peminjaman.", e);
        }
    }

    private void execute(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        }
    }

    private void execute(String sql, String errorMessage, Object... params) {
        try (Connection c = DatabaseConnection.getConnection()) {
            execute(c, sql, params);
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    private <T> T inTransaction(String errorMessage, SqlWork<T> work) {
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }
}