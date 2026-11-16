package com.smartlib.dao;

import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Author;
import com.smartlib.model.Category;

/**
 * Pengecekan MANUAL untuk DAO (bukan JUnit). Jalankan lewat tombol panah hijau di samping main().
 * Memerlukan MySQL menyala dan kedua file SQL sudah dijalankan.
 * Data uji yang dibuat langsung dihapus kembali, data contoh tidak berubah.
 */
public class DaoManualCheck {

    private static int pass = 0;
    private static int fail = 0;

    public static void main(String[] args) {
        checkCategory();
        checkAuthor();
        System.out.println();
        System.out.println("HASIL: " + pass + " lulus, " + fail + " gagal");
    }

    private static void checkCategory() {
        System.out.println("== CategoryDAO ==");
        CategoryDAO dao = new CategoryDAO();

        check("findAll membaca data contoh (>= 3 kategori)", dao.findAll().size() >= 3);
        check("findByName 'fiksi' (huruf kecil) ketemu", dao.findByName("fiksi").isPresent());

        Category baru = dao.insert(new Category("Kategori Uji", "dibuat oleh DaoManualCheck"));
        check("insert memberi ID dari database", baru.getId() > 0);
        check("findById mengembalikan data yang sama",
                dao.findById(baru.getId()).map(Category::getName).orElse("").equals("Kategori Uji"));

        baru.setDescription("sudah diubah");
        dao.update(baru);
        check("update tersimpan",
                dao.findById(baru.getId()).map(Category::getDescription).orElse("").equals("sudah diubah"));
        check("searchByName 'uji' ketemu", dao.searchByName("uji").size() == 1);

        try {
            dao.insert(new Category("Kategori Uji", ""));
            check("insert nama ganda harus ditolak database", false);
        } catch (DataAccessException e) {
            check("insert nama ganda ditolak database", true);
        }

        int fiksiId = dao.findByName("Fiksi").orElseThrow().getId();
        check("countItems 'Fiksi' >= 2", dao.countItems(fiksiId) >= 2);
        try {
            dao.delete(fiksiId);
            check("hapus kategori yang dipakai harus ditolak", false);
        } catch (DataAccessException e) {
            check("hapus kategori yang dipakai ditolak database", true);
        }

        dao.delete(baru.getId());
        check("delete kategori uji berhasil", dao.findById(baru.getId()).isEmpty());
    }

    private static void checkAuthor() {
        System.out.println("== AuthorDAO ==");
        AuthorDAO dao = new AuthorDAO();

        check("findAll membaca data contoh (>= 5 penulis)", dao.findAll().size() >= 5);
        check("findByName 'andrea hirata' ketemu", dao.findByName("andrea hirata").isPresent());

        Author baru = dao.insert(new Author("Penulis Uji", "dibuat oleh DaoManualCheck"));
        check("insert memberi ID dari database", baru.getId() > 0);

        baru.setBiography("sudah diubah");
        dao.update(baru);
        check("update tersimpan",
                dao.findById(baru.getId()).map(Author::getBiography).orElse("").equals("sudah diubah"));
        check("searchByName 'uji' ketemu", dao.searchByName("uji").size() == 1);

        try {
            dao.insert(new Author("Penulis Uji", ""));
            check("insert nama ganda harus ditolak database", false);
        } catch (DataAccessException e) {
            check("insert nama ganda ditolak database", true);
        }

        int hirataId = dao.findByName("Andrea Hirata").orElseThrow().getId();
        check("countItems 'Andrea Hirata' >= 1", dao.countItems(hirataId) >= 1);
        try {
            dao.delete(hirataId);
            check("hapus penulis yang dipakai harus ditolak", false);
        } catch (DataAccessException e) {
            check("hapus penulis yang dipakai ditolak database", true);
        }

        dao.delete(baru.getId());
        check("delete penulis uji berhasil", dao.findById(baru.getId()).isEmpty());
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "  [LULUS] " : "  [GAGAL] ") + name);
    }
}