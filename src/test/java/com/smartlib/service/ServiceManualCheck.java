package com.smartlib.service;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.dao.MemberDAO;
import com.smartlib.enums.AccountStatus;
import com.smartlib.enums.ItemStatus;
import com.smartlib.exception.AccessDeniedException;
import com.smartlib.model.Author;
import com.smartlib.model.Book;
import com.smartlib.model.BookCopy;
import com.smartlib.model.BookFilter;
import com.smartlib.model.BookStockSummary;
import com.smartlib.model.Category;
import com.smartlib.model.Member;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

/**
 * Pengecekan MANUAL untuk BookService dan MemberService terhadap database MySQL yang sebenarnya
 * (bukan JUnit). Jalankan lewat tombol panah hijau di samping main().
 *
 * Syarat: MySQL menyala dan tiga file SQL sudah dijalankan berurutan
 * (auth_schema, book_member_schema, loan_fine_schema).
 * Semua data uji dihapus kembali di akhir, data contoh tidak berubah.
 */
public class ServiceManualCheck {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) throws Exception {
        AuthService auth = AuthService.getInstance();
        BookService books = new BookService();
        MemberService members = new MemberService();

        auth.login("admin", "Admin#123".toCharArray());
        System.out.println("== Hak akses ==");
        auth.logout();
        auth.login("anggota1", "Anggota#123".toCharArray());
        check("Anggota boleh melihat katalog", !books.search(BookFilter.none()).isEmpty());
        throwsType("Anggota TIDAK boleh menambah kategori", AccessDeniedException.class,
                () -> books.addCategory("X", ""));
        throwsType("Anggota TIDAK boleh membuka data anggota", AccessDeniedException.class,
                () -> members.search("", null));
        auth.logout();
        auth.login("petugas1", "Petugas#123".toCharArray());
        check("Petugas boleh membuka data anggota", !members.search("", null).isEmpty());

        checkSearch(books);
        checkBookCrud(books);
        checkCategoryAuthor(books);
        checkMembers(members);
        checkLoanRules(books, members);

        System.out.println();
        System.out.println("HASIL: " + pass + " lulus, " + fail + " gagal");
    }

    // ------------------------------------------------------------------ pencarian
    private static void checkSearch(BookService books) {
        System.out.println("== Pencarian dan filter katalog ==");
        List<BookStockSummary> all = books.search(BookFilter.none());
        check("Katalog memuat >= 4 buku (hanya jenis Buku)", all.size() >= 4);
        BookStockSummary laskar = find(all, "Laskar Pelangi");
        check("Stok Laskar Pelangi: total 3, tersedia 2, dipinjam 1",
                laskar.totalCopies() == 3 && laskar.availableCopies() == 2 && laskar.borrowedCopies() == 1);
        check("Cari judul 'laskar' (huruf kecil)",
                books.search(new BookFilter("laskar", "", null, false)).size() == 1);
        check("Cari penulis 'hirata'",
                books.search(new BookFilter("", "hirata", null, false)).size() == 1);
        BookStockSummary algo = find(all, "Introduction to Algorithms");
        check("Buku dengan 2 penulis menampilkan keduanya", algo.book().getAuthors().size() == 2);
        check("Cari penulis 'cormen' menemukan Introduction to Algorithms",
                books.search(new BookFilter("", "cormen", null, false)).get(0).book().getTitle()
                        .equals("Introduction to Algorithms"));
        int tiId = books.listCategories().stream().filter(c -> c.getName().equals("Teknologi Informasi"))
                .findFirst().orElseThrow().getId();
        check("Filter kategori Teknologi Informasi >= 2 buku",
                books.search(new BookFilter("", "", tiId, false)).size() >= 2);
        check("Gabungan filter (judul + kategori Fiksi) tanpa hasil",
                books.search(new BookFilter("clean", "", books.listCategories().stream()
                        .filter(c -> c.getName().equals("Fiksi")).findFirst().orElseThrow().getId(), false)).isEmpty());
        check("Cari dengan karakter khusus '%' tidak error dan tanpa hasil",
                books.search(new BookFilter("%", "", null, false)).isEmpty());
    }

    // ------------------------------------------------------------------ buku + eksemplar
    private static void checkBookCrud(BookService books) {
        System.out.println("== Buku dan eksemplar ==");
        Category ti = books.listCategories().stream().filter(c -> c.getName().equals("Teknologi Informasi"))
                .findFirst().orElseThrow();
        Author martin = books.listAuthors().stream().filter(a -> a.getName().equals("Robert C. Martin"))
                .findFirst().orElseThrow();
        Author cormen = books.listAuthors().stream().filter(a -> a.getName().equals("Thomas H. Cormen"))
                .findFirst().orElseThrow();

        Book uji = new Book(0, "Buku Uji Service", "9781234567897", "Penerbit Uji", 2020, ti,
                "dibuat oleh ServiceManualCheck", List.of(martin), "Edisi 1");
        books.addBook(uji);
        check("addBook memberi ID", uji.getId() > 0);
        BookStockSummary s = books.getSummary(uji.getId());
        check("Buku baru belum punya eksemplar (stok 0)", s.totalCopies() == 0 && s.availableCopies() == 0);
        check("Buku tanpa eksemplar tidak muncul pada filter 'hanya tersedia'",
                books.search(new BookFilter("Buku Uji Service", "", null, true)).isEmpty());

        throwsType("ISBN ganda ditolak", IllegalStateException.class, () -> books.addBook(
                new Book(0, "Judul Lain", "978-1-2345-6789-7", null, 2020, ti, "", List.of(martin), "")));
        throwsType("Judul + edisi ganda ditolak", IllegalStateException.class, () -> books.addBook(
                new Book(0, "buku uji service", null, null, 2020, ti, "", List.of(martin), "Edisi 1")));
        throwsType("ISBN salah format ditolak", IllegalArgumentException.class,
                () -> books.addBook(new Book(0, "Judul Z", "123", null, 2020, ti, "", List.of(martin), "")));
        throwsType("Buku tanpa penulis ditolak", IllegalArgumentException.class,
                () -> books.addBook(new Book(0, "Judul Z", null, null, 2020, ti, "", List.of(), "")));
        throwsType("Kategori belum tersimpan (id 0) ditolak", IllegalArgumentException.class,
                () -> books.addBook(new Book(0, "Judul Z", null, null, 2020, new Category(0, "Palsu", ""),
                        "", List.of(martin), "")));
        throwsType("Judul kosong ditolak oleh model", IllegalArgumentException.class,
                () -> new Book(0, " ", null, null, 2020, ti, "", List.of(martin), ""));
        throwsType("Tahun 'abcd' ditolak", IllegalArgumentException.class, () -> BookService.parseYear("abcd"));
        throwsType("Tahun terlalu jauh di masa depan ditolak", IllegalArgumentException.class,
                () -> BookService.parseYear("2999"));
        check("Tahun kosong = null", BookService.parseYear("  ") == null);

        Book edit = books.getSummary(uji.getId()).book();
        edit.setTitle("Buku Uji Service (Revisi)");
        edit.setAuthors(List.of(martin, cormen));
        books.updateBook(edit);
        Book reloaded = books.getSummary(uji.getId()).book();
        check("updateBook: judul berubah", reloaded.getTitle().equals("Buku Uji Service (Revisi)"));
        check("updateBook: penulis diganti menjadi 2", reloaded.getAuthors().size() == 2);

        BookCopy c1 = books.addCopy(uji.getId(), "uji-001", ItemStatus.AVAILABLE, "Rak Uji");
        books.addCopy(uji.getId(), "UJI-002", ItemStatus.AVAILABLE, "");
        check("Barcode disimpan huruf besar", c1.getBarcode().equals("UJI-001"));
        throwsType("Barcode ganda ditolak", IllegalStateException.class,
                () -> books.addCopy(uji.getId(), "UJI-001", ItemStatus.AVAILABLE, ""));
        throwsType("Eksemplar baru berstatus Dipinjam ditolak", IllegalArgumentException.class,
                () -> books.addCopy(uji.getId(), "UJI-003", ItemStatus.BORROWED, ""));
        throwsType("Eksemplar untuk buku yang tidak ada ditolak", IllegalArgumentException.class,
                () -> books.addCopy(999999, "UJI-009", ItemStatus.AVAILABLE, ""));
        check("Stok setelah 2 eksemplar: 2/2/0", sameStock(books.getSummary(uji.getId()), 2, 2, 0));
        books.updateCopy(c1.getId(), "UJI-001", ItemStatus.DAMAGED, "Rak Uji");
        check("Eksemplar rusak tidak dihitung tersedia: 2/1/0", sameStock(books.getSummary(uji.getId()), 2, 1, 0));

        BookCopy borrowed = books.listCopies(find(books.search(BookFilter.none()), "Laskar Pelangi").book().getId())
                .stream().filter(c -> c.getStatus() == ItemStatus.BORROWED).findFirst().orElseThrow();
        throwsType("Status eksemplar yang sedang dipinjam tidak boleh diubah manual", IllegalStateException.class,
                () -> books.updateCopy(borrowed.getId(), borrowed.getBarcode(), ItemStatus.AVAILABLE, ""));
        throwsType("Eksemplar yang sedang dipinjam tidak boleh dihapus", IllegalStateException.class,
                () -> books.deleteCopy(borrowed.getId()));
        throwsType("Buku yang eksemplarnya sedang dipinjam tidak boleh dihapus", IllegalStateException.class,
                () -> books.deleteBook(find(books.search(BookFilter.none()), "Laskar Pelangi").book().getId()));

        books.deleteCopy(c1.getId());
        check("deleteCopy berhasil: total 1", sameStock(books.getSummary(uji.getId()), 1, 1, 0));
        books.deleteBook(uji.getId());
        throwsType("Buku sudah terhapus", IllegalArgumentException.class, () -> books.getSummary(uji.getId()));
        check("Eksemplar buku yang dihapus ikut terhapus", books.listCopies(uji.getId()).isEmpty());
    }

    // ------------------------------------------------------------------ kategori dan penulis
    private static void checkCategoryAuthor(BookService books) {
        System.out.println("== Kategori dan penulis ==");
        Category k = books.addCategory("Kategori Uji Service", "uji");
        throwsType("Kategori ganda (beda huruf) ditolak", IllegalStateException.class,
                () -> books.addCategory("kategori uji service", ""));
        throwsType("Nama kategori kosong ditolak", IllegalArgumentException.class, () -> books.addCategory(" ", ""));
        books.updateCategory(k.getId(), "Kategori Uji Service 2", "diubah");
        check("updateCategory tersimpan", books.listCategories().stream()
                .anyMatch(c -> c.getName().equals("Kategori Uji Service 2")));
        int fiksi = books.listCategories().stream().filter(c -> c.getName().equals("Fiksi")).findFirst().orElseThrow().getId();
        throwsType("Hapus kategori yang dipakai buku ditolak", IllegalStateException.class,
                () -> books.deleteCategory(fiksi));
        books.deleteCategory(k.getId());
        check("Hapus kategori kosong berhasil", books.listCategories().stream()
                .noneMatch(c -> c.getName().startsWith("Kategori Uji Service")));

        Author a = books.addAuthor("Penulis Uji Service", "uji");
        throwsType("Penulis ganda ditolak", IllegalStateException.class, () -> books.addAuthor("penulis uji service", ""));
        books.updateAuthor(a.getId(), "Penulis Uji Service 2", "diubah");
        int hirata = books.listAuthors().stream().filter(x -> x.getName().equals("Andrea Hirata")).findFirst().orElseThrow().getId();
        throwsType("Hapus penulis yang dipakai buku ditolak", IllegalStateException.class,
                () -> books.deleteAuthor(hirata));
        books.deleteAuthor(a.getId());
        check("Hapus penulis kosong berhasil", books.listAuthors().stream()
                .noneMatch(x -> x.getName().startsWith("Penulis Uji Service")));
    }

    // ------------------------------------------------------------------ anggota
    private static void checkMembers(MemberService members) {
        System.out.println("== Anggota ==");
        check("Cari 'budi' menemukan 1 anggota", members.search("budi", null).size() == 1);
        check("Filter status Nonaktif menemukan anggota nonaktif",
                members.search("", AccountStatus.INACTIVE).stream().allMatch(m -> !m.isActive())
                        && !members.search("", AccountStatus.INACTIVE).isEmpty());
        check("Cari dengan '%' tanpa hasil", members.search("%", null).isEmpty());

        String suggested = members.suggestMemberCode();
        check("Saran kode berikutnya AGT-0005", suggested.equals("AGT-0005"));
        Member m = members.addMember("", "Anggota Uji", "uji@contoh.local", "0812-9999-0001", "Medan",
                LocalDate.now(), "");
        check("addMember: kode otomatis + ID dari database", m.getMemberCode().equals("AGT-0005") && m.getId() > 0);
        throwsType("Email ganda ditolak", IllegalStateException.class, () -> members.addMember("AGT-0090", "Z",
                "UJI@contoh.local", "", "", LocalDate.now(), ""));
        throwsType("Telepon ganda ditolak", IllegalStateException.class, () -> members.addMember("AGT-0091", "Z",
                "", "081299990001", "", LocalDate.now(), ""));
        throwsType("Kode ganda ditolak", IllegalStateException.class, () -> members.addMember("agt-0005", "Z",
                "", "", "", LocalDate.now(), ""));
        throwsType("Email salah format ditolak", IllegalArgumentException.class, () -> members.addMember("AGT-0092",
                "Z", "bukan-email", "", "", LocalDate.now(), ""));
        throwsType("Telepon salah format ditolak", IllegalArgumentException.class, () -> members.addMember("AGT-0093",
                "Z", "", "12ab", "", LocalDate.now(), ""));
        throwsType("Nama kosong ditolak", IllegalArgumentException.class, () -> members.addMember("AGT-0094",
                " ", "", "", "", LocalDate.now(), ""));
        throwsType("Format tanggal salah ditolak", IllegalArgumentException.class, () -> MemberService.parseDate("12/11/2026"));
        throwsType("Akun ber-role Admin tidak boleh dihubungkan", IllegalArgumentException.class,
                () -> members.addMember("AGT-0095", "Z", "", "", "", LocalDate.now(), "admin"));
        throwsType("Akun yang sudah dihubungkan ke anggota lain ditolak", IllegalStateException.class,
                () -> members.addMember("AGT-0096", "Z", "", "", "", LocalDate.now(), "anggota1"));
        Member tanpaEmail1 = members.addMember("AGT-0097", "Tanpa Email 1", "", "", "", LocalDate.now(), "");
        Member tanpaEmail2 = members.addMember("AGT-0098", "Tanpa Email 2", "", "", "", LocalDate.now(), "");
        check("Dua anggota tanpa email boleh (disimpan NULL)", tanpaEmail1.getId() > 0 && tanpaEmail2.getId() > 0);

        members.updateMember(m.getId(), "AGT-0005", "Anggota Uji Revisi", "uji@contoh.local", "081299990001",
                "Alamat baru", LocalDate.now(), "anggota2");
        Member after = new MemberDAO().findById(m.getId()).orElseThrow();
        check("updateMember: nama dan akun tersambung", after.getFullName().equals("Anggota Uji Revisi")
                && after.getUserId() != null);
        members.setActive(m.getId(), false);
        check("Nonaktifkan anggota", !new MemberDAO().findById(m.getId()).orElseThrow().isActive());
        members.setActive(m.getId(), true);
        check("Aktifkan kembali", new MemberDAO().findById(m.getId()).orElseThrow().isActive());

        check("Hapus anggota yang tidak pernah meminjam = DELETED",
                members.deleteMember(m.getId()) == MemberService.DeleteResult.DELETED);
        members.deleteMember(tanpaEmail1.getId());
        members.deleteMember(tanpaEmail2.getId());
        check("Anggota uji sudah bersih", members.search("Uji", null).isEmpty()
                && members.search("Tanpa Email", null).isEmpty());
    }

    // ------------------------------------------------------------------ relasi ke peminjaman
    private static void checkLoanRules(BookService books, MemberService members) throws Exception {
        System.out.println("== Relasi dengan transaksi peminjaman ==");
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM information_schema.columns "
                     + "WHERE table_schema = DATABASE() AND table_name = 'loans' AND column_name = 'copy_id'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            if (rs.getInt(1) == 0) {
                System.out.println("  [LEWATI] tabel loans belum ada (jalankan loan_fine_schema.sql untuk menguji bagian ini)");
                return;
            }
        }
        int anggota1 = scalar("SELECT id FROM users WHERE username = 'anggota1'");
        int member1 = scalar("SELECT id FROM members WHERE user_id = " + anggota1);
        int copyId = scalar("SELECT id FROM book_copies WHERE barcode = 'BC-0001'");
        int loanId = -1;
        try {
            exec("INSERT INTO loans (user_id, copy_id, borrow_date, due_date, status) VALUES ("
                    + anggota1 + ", " + copyId + ", CURRENT_DATE, CURRENT_DATE + INTERVAL 7 DAY, 'ACTIVE')");
            loanId = scalar("SELECT MAX(id) FROM loans");
            int bookId = scalar("SELECT item_id FROM book_copies WHERE id = " + copyId);

            throwsType("Hapus eksemplar yang punya riwayat pinjam ditolak", IllegalStateException.class,
                    () -> books.deleteCopy(copyId));
            throwsType("Hapus buku yang punya riwayat pinjam ditolak", IllegalStateException.class,
                    () -> books.deleteBook(bookId));
            throwsType("Hapus anggota yang masih meminjam ditolak", IllegalStateException.class,
                    () -> members.deleteMember(member1));
            throwsType("Nonaktifkan anggota yang masih meminjam ditolak", IllegalStateException.class,
                    () -> members.setActive(member1, false));

            exec("UPDATE loans SET status = 'COMPLETED', return_date = CURRENT_DATE WHERE id = " + loanId);
            throwsType("Setelah dikembalikan, hapus eksemplar tetap ditolak (riwayat)", IllegalStateException.class,
                    () -> books.deleteCopy(copyId));
            check("Anggota dengan riwayat pinjam hanya DINONAKTIFKAN, bukan dihapus",
                    members.deleteMember(member1) == MemberService.DeleteResult.DEACTIVATED);
            check("Data anggota tetap ada setelah 'dihapus'", new MemberDAO().findById(member1).isPresent());
        } finally {
            if (loanId > 0) exec("DELETE FROM loans WHERE id = " + loanId);
            exec("UPDATE members SET status = 'ACTIVE' WHERE id = " + member1);
        }
    }

    // ------------------------------------------------------------------ helper
    private static BookStockSummary find(List<BookStockSummary> list, String title) {
        return list.stream().filter(s -> s.book().getTitle().equals(title)).findFirst().orElseThrow();
    }

    private static boolean sameStock(BookStockSummary s, int total, int available, int borrowed) {
        return s.totalCopies() == total && s.availableCopies() == available && s.borrowedCopies() == borrowed;
    }

    private static int scalar(String sql) throws Exception {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void exec(String sql) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private static void throwsType(String name, Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            check(name + " (seharusnya error)", false);
        } catch (Throwable t) {
            boolean ok = type.isInstance(t);
            if (!ok) System.out.println("      error yang muncul: " + t);
            check(name, ok);
        }
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  [LULUS] " : "  [GAGAL] ") + name);
    }
}