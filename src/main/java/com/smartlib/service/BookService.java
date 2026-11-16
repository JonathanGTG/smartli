package com.smartlib.service;

import com.smartlib.dao.AuthorDAO;
import com.smartlib.dao.BookDAO;
import com.smartlib.dao.CategoryDAO;
import com.smartlib.enums.ItemStatus;
import com.smartlib.model.Author;
import com.smartlib.model.Book;
import com.smartlib.model.BookCopy;
import com.smartlib.model.BookFilter;
import com.smartlib.model.BookStockSummary;
import com.smartlib.model.Category;
import com.smartlib.model.Permission;

import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * Aturan bisnis untuk katalog buku, kategori, penulis, dan eksemplar.
 *
 * Pembagian tugas:
 *  - UI memanggil class ini (tidak boleh menulis SQL).
 *  - Class ini memeriksa hak akses, memvalidasi data, dan memeriksa aturan relasi.
 *  - BookDAO / CategoryDAO / AuthorDAO yang berbicara dengan database.
 *
 * Kesalahan input   -> IllegalArgumentException (pesan siap ditampilkan ke pengguna)
 * Pelanggaran aturan -> IllegalStateException
 * Tidak berhak       -> AccessDeniedException (dari AuthService)
 * Masalah database   -> DataAccessException (dari DAO)
 */
public class BookService {

    private final AuthService authService;
    private final BookDAO bookDAO;
    private final CategoryDAO categoryDAO;
    private final AuthorDAO authorDAO;

    public BookService() {
        this(AuthService.getInstance(), new BookDAO(), new CategoryDAO(), new AuthorDAO());
    }

    public BookService(AuthService authService, BookDAO bookDAO, CategoryDAO categoryDAO, AuthorDAO authorDAO) {
        this.authService = authService;
        this.bookDAO = bookDAO;
        this.categoryDAO = categoryDAO;
        this.authorDAO = authorDAO;
    }

    // =====================================================================
    // Katalog (boleh dilihat semua role yang punya VIEW_CATALOG)
    // =====================================================================

    public List<BookStockSummary> search(BookFilter filter) {
        authService.requirePermission(Permission.VIEW_CATALOG);
        return bookDAO.search(filter == null ? BookFilter.none() : filter);
    }

    public BookStockSummary getSummary(int bookId) {
        authService.requirePermission(Permission.VIEW_CATALOG);
        return bookDAO.findSummaryById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Buku tidak ditemukan."));
    }

    public List<BookCopy> listCopies(int bookId) {
        authService.requirePermission(Permission.VIEW_CATALOG);
        return bookDAO.findCopies(bookId);
    }

    public List<Category> listCategories() {
        authService.requirePermission(Permission.VIEW_CATALOG);
        return categoryDAO.findAll();
    }

    public List<Author> listAuthors() {
        authService.requirePermission(Permission.VIEW_CATALOG);
        return authorDAO.findAll();
    }

    // =====================================================================
    // Buku (ADMIN / PETUGAS: MANAGE_COLLECTION)
    // =====================================================================

    public Book addBook(Book book) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        validateBook(book);
        return bookDAO.insert(book);
    }

    public void updateBook(Book book) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        if (book.getId() <= 0 || bookDAO.findById(book.getId()).isEmpty())
            throw new IllegalArgumentException("Buku yang diubah tidak ditemukan.");
        validateBook(book);
        bookDAO.update(book);
    }

    /**
     * Menghapus buku beserta eksemplarnya, hanya bila aman:
     * tidak ada eksemplar yang sedang dipinjam/dicadangkan dan tidak ada riwayat peminjaman.
     */
    public void deleteBook(int bookId) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        BookStockSummary summary = bookDAO.findSummaryById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Buku tidak ditemukan."));
        long inUse = bookDAO.findCopies(bookId).stream()
                .filter(c -> c.getStatus() == ItemStatus.BORROWED || c.getStatus() == ItemStatus.RESERVED).count();
        if (inUse > 0) {
            throw new IllegalStateException("Buku \"" + summary.book().getTitle() + "\" tidak dapat dihapus karena "
                    + inUse + " eksemplar sedang dipinjam atau dicadangkan.");
        }
        int loans = bookDAO.countLoansForItem(bookId);
        if (loans > 0) {
            throw new IllegalStateException("Buku \"" + summary.book().getTitle() + "\" memiliki " + loans
                    + " riwayat peminjaman sehingga tidak dapat dihapus. "
                    + "Ubah status eksemplarnya menjadi Hilang atau Rusak bila sudah tidak dipakai.");
        }
        bookDAO.delete(bookId);
    }

    /** Mengubah teks tahun dari form menjadi angka. Kosong = tidak diisi (null). */
    public static Integer parseYear(String text) {
        if (text == null || text.isBlank()) return null;
        String value = text.trim();
        int max = Year.now().getValue() + 1;
        if (!value.matches("\\d{4}"))
            throw new IllegalArgumentException("Tahun terbit harus berupa 4 digit angka, misalnya 2020.");
        int year = Integer.parseInt(value);
        if (year < 1000 || year > max)
            throw new IllegalArgumentException("Tahun terbit harus antara 1000 dan " + max + ".");
        return year;
    }

    private void validateBook(Book book) {
        // 1. Kategori wajib ada di database
        Category category = book.getCategory();
        if (category == null || category.getId() <= 0 || categoryDAO.findById(category.getId()).isEmpty())
            throw new IllegalArgumentException("Pilih kategori yang valid.");

        // 2. Minimal satu penulis, dan semuanya harus ada di database
        if (book.getAuthors().isEmpty())
            throw new IllegalArgumentException("Pilih minimal satu penulis.");
        for (Author author : book.getAuthors()) {
            if (author.getId() <= 0 || authorDAO.findById(author.getId()).isEmpty())
                throw new IllegalArgumentException("Penulis \"" + author.getName() + "\" tidak ditemukan di database.");
        }

        // 3. Format panjang dan ISBN
        if (book.getTitle().length() > 255)
            throw new IllegalArgumentException("Judul maksimal 255 karakter.");
        if (book.getPublisher() != null && book.getPublisher().length() > 100)
            throw new IllegalArgumentException("Penerbit maksimal 100 karakter.");
        if (book.getEdition().length() > 50)
            throw new IllegalArgumentException("Edisi maksimal 50 karakter.");
        if (book.getIsbn() != null) {
            String digits = book.getIsbn().replace("-", "").replace(" ", "");
            if (!digits.matches("\\d{13}|\\d{9}[\\dXx]"))
                throw new IllegalArgumentException("ISBN harus 10 atau 13 digit (tanda hubung boleh dipakai).");
        }
        if (book.getPublicationYear() != null) {
            parseYear(String.valueOf(book.getPublicationYear()));
        }

        // 4. Duplikat
        if (book.getIsbn() != null) {
            Optional<Integer> owner = bookDAO.findIdByIsbn(book.getIsbn());
            if (owner.isPresent() && owner.get() != book.getId())
                throw new IllegalStateException("ISBN " + book.getIsbn() + " sudah dipakai oleh koleksi lain.");
        }
        if (bookDAO.existsByTitleAndEdition(book.getTitle(), book.getEdition(), book.getId()))
            throw new IllegalStateException("Buku dengan judul dan edisi yang sama sudah ada.");
    }

    // =====================================================================
    // Eksemplar
    // =====================================================================

    /** Status yang boleh diatur manual. BORROWED/RESERVED hanya diubah oleh proses peminjaman. */
    private static boolean isManualStatus(ItemStatus status) {
        return status == ItemStatus.AVAILABLE || status == ItemStatus.DAMAGED || status == ItemStatus.LOST;
    }

    public BookCopy addCopy(int bookId, String barcode, ItemStatus status, String shelfLocation) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        if (bookDAO.findById(bookId).isEmpty()) throw new IllegalArgumentException("Buku tidak ditemukan.");
        BookCopy copy = new BookCopy(bookId, barcode, status, shelfLocation);
        if (!isManualStatus(copy.getStatus()))
            throw new IllegalArgumentException("Eksemplar baru hanya boleh berstatus Tersedia, Rusak, atau Hilang.");
        if (bookDAO.barcodeExists(copy.getBarcode(), 0))
            throw new IllegalStateException("Barcode " + copy.getBarcode() + " sudah dipakai eksemplar lain.");
        return bookDAO.insertCopy(copy);
    }

    public void updateCopy(int copyId, String barcode, ItemStatus status, String shelfLocation) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        BookCopy existing = bookDAO.findCopyById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Eksemplar tidak ditemukan."));
        BookCopy updated = new BookCopy(copyId, existing.getItemId(), barcode, status, shelfLocation);
        if (updated.getStatus() != existing.getStatus()) {
            if (!isManualStatus(existing.getStatus()))
                throw new IllegalStateException("Status eksemplar yang sedang "
                        + existing.getStatus().getLabel().toLowerCase()
                        + " diubah otomatis oleh proses pengembalian, bukan manual.");
            if (!isManualStatus(updated.getStatus()))
                throw new IllegalArgumentException("Status Dipinjam/Dicadangkan hanya diatur oleh proses peminjaman.");
        }
        if (bookDAO.barcodeExists(updated.getBarcode(), copyId))
            throw new IllegalStateException("Barcode " + updated.getBarcode() + " sudah dipakai eksemplar lain.");
        bookDAO.updateCopy(updated);
    }

    public void deleteCopy(int copyId) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        BookCopy copy = bookDAO.findCopyById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Eksemplar tidak ditemukan."));
        if (!isManualStatus(copy.getStatus()))
            throw new IllegalStateException("Eksemplar " + copy.getBarcode() + " sedang "
                    + copy.getStatus().getLabel().toLowerCase() + " dan tidak dapat dihapus.");
        int loans = bookDAO.countLoansForCopy(copyId);
        if (loans > 0)
            throw new IllegalStateException("Eksemplar " + copy.getBarcode() + " memiliki " + loans
                    + " riwayat peminjaman sehingga tidak dapat dihapus. Ubah statusnya menjadi Hilang atau Rusak.");
        bookDAO.deleteCopy(copyId);
    }

    // =====================================================================
    // Kategori
    // =====================================================================

    public Category addCategory(String name, String description) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        Category category = new Category(name, description);
        validateCategory(category);
        return categoryDAO.insert(category);
    }

    public void updateCategory(int id, String name, String description) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        if (categoryDAO.findById(id).isEmpty()) throw new IllegalArgumentException("Kategori tidak ditemukan.");
        Category category = new Category(id, name, description);
        validateCategory(category);
        categoryDAO.update(category);
    }

    public void deleteCategory(int id) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        Category category = categoryDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kategori tidak ditemukan."));
        int used = categoryDAO.countItems(id);
        if (used > 0)
            throw new IllegalStateException("Kategori \"" + category.getName() + "\" masih dipakai " + used
                    + " koleksi. Pindahkan koleksinya ke kategori lain terlebih dahulu.");
        categoryDAO.delete(id);
    }

    private void validateCategory(Category category) {
        if (category.getName().length() > 100)
            throw new IllegalArgumentException("Nama kategori maksimal 100 karakter.");
        if (category.getDescription().length() > 255)
            throw new IllegalArgumentException("Deskripsi kategori maksimal 255 karakter.");
        Optional<Category> same = categoryDAO.findByName(category.getName());
        if (same.isPresent() && same.get().getId() != category.getId())
            throw new IllegalStateException("Kategori \"" + category.getName() + "\" sudah ada.");
    }

    // =====================================================================
    // Penulis
    // =====================================================================

    public Author addAuthor(String name, String biography) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        Author author = new Author(name, biography);
        validateAuthor(author);
        return authorDAO.insert(author);
    }

    public void updateAuthor(int id, String name, String biography) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        if (authorDAO.findById(id).isEmpty()) throw new IllegalArgumentException("Penulis tidak ditemukan.");
        Author author = new Author(id, name, biography);
        validateAuthor(author);
        authorDAO.update(author);
    }

    public void deleteAuthor(int id) {
        authService.requirePermission(Permission.MANAGE_COLLECTION);
        Author author = authorDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Penulis tidak ditemukan."));
        int used = authorDAO.countItems(id);
        if (used > 0)
            throw new IllegalStateException("Penulis \"" + author.getName() + "\" masih tercatat pada " + used
                    + " koleksi. Lepaskan dari koleksi tersebut terlebih dahulu.");
        authorDAO.delete(id);
    }

    private void validateAuthor(Author author) {
        if (author.getName().length() > 100)
            throw new IllegalArgumentException("Nama penulis maksimal 100 karakter.");
        Optional<Author> same = authorDAO.findByName(author.getName());
        if (same.isPresent() && same.get().getId() != author.getId())
            throw new IllegalStateException("Penulis \"" + author.getName() + "\" sudah ada.");
    }
}