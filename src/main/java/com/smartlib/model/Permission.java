package com.smartlib.model;

/** Hak akses per fitur, mengikuti tabel "Role dan Hak Akses" pada PRD bagian 6. */
public enum Permission {
    MANAGE_USERS,            // Mengelola akun dan role
    MANAGE_COLLECTION,       // Kategori, koleksi, penulis, eksemplar
    MANAGE_MEMBERS,          // Data anggota
    VIEW_CATALOG,            // Melihat katalog
    PROCESS_LOAN,            // Memproses peminjaman
    PROCESS_RETURN,          // Memproses pengembalian
    VIEW_ALL_TRANSACTIONS,   // Melihat seluruh transaksi
    VIEW_OWN_HISTORY,        // Riwayat sendiri (anggota)
    REQUEST_QUEUE,           // Mengajukan / membatalkan antrean (anggota)
    VIEW_OWN_FINE,           // Melihat denda sendiri (anggota)
    RECORD_FINE_PAYMENT,     // Mencatat pembayaran denda
    CHANGE_LOAN_RULES,       // Mengubah aturan peminjaman
    VIEW_REPORTS             // Melihat laporan keseluruhan
}
