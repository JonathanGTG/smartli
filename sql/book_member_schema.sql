-- =====================================================================
-- SmartLib - skema Manajemen Buku dan Anggota (Anggota 3)
-- Database : smartlib
--
-- URUTAN MENJALANKAN (PENTING, jangan dibalik):
--   1. sql/auth_schema.sql          (membuat tabel roles dan users)
--   2. sql/book_member_schema.sql   (file ini: koleksi, penulis, eksemplar, anggota)
--   3. sql/loan_fine_schema.sql     (peminjaman & denda, memakai book_copies dari file ini)
--
-- Nilai item_type dan nama kolom barcode sengaja disamakan dengan yang dipakai
-- skema peminjaman/laporan (Anggota 4 dan 5) agar semua modul memakai tabel yang sama.
--
-- File ini AMAN dijalankan berulang kali:
--   - tabel memakai CREATE TABLE IF NOT EXISTS
--   - data contoh memakai INSERT IGNORE (tidak akan menggandakan data)
-- File ini TIDAK menghapus atau mengubah tabel roles dan users.
-- =====================================================================
USE smartlib;

-- ---------------------------------------------------------------------
-- 1. categories : kategori buku (Fiksi, Teknologi Informasi, dst.)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
                                          id          INT          PRIMARY KEY AUTO_INCREMENT,
                                          name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    CONSTRAINT uq_categories_name UNIQUE (name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. authors : penulis
--    Nama dibuat UNIQUE agar penulis yang sama tidak tercatat dua kali.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS authors (
                                       id        INT          PRIMARY KEY AUTO_INCREMENT,
                                       name      VARCHAR(100) NOT NULL,
    biography TEXT         NULL,
    CONSTRAINT uq_authors_name UNIQUE (name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. library_items : koleksi (Buku, E-Book, Jurnal) dalam satu tabel.
--    Kolom item_type menentukan jenisnya; kolom khusus tiap jenis
--    (edition, file_format, download_url, volume, issue) boleh NULL
--    untuk jenis lain. Sesuai class LibraryItem, Book, EBook, Journal.
--    category_id boleh NULL hanya agar data lama tanpa kategori tetap bisa
--    disimpan; aplikasi (BookService) tetap mewajibkan kategori untuk buku baru.
--    isbn UNIQUE; MySQL mengizinkan banyak nilai NULL pada kolom UNIQUE.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS library_items (
                                             id               INT          PRIMARY KEY AUTO_INCREMENT,
                                             item_type        ENUM('BUKU','EBOOK','JURNAL') NOT NULL DEFAULT 'BUKU',
    title            VARCHAR(255) NOT NULL,
    isbn             VARCHAR(20)  NULL,
    publisher        VARCHAR(100) NULL,
    publication_year SMALLINT UNSIGNED NULL,
    category_id      INT          NULL,
    description      TEXT         NULL,
    edition          VARCHAR(50)  NULL,       -- khusus BOOK
    file_format      VARCHAR(20)  NULL,       -- khusus EBOOK
    download_url     VARCHAR(500) NULL,       -- khusus EBOOK
    volume           VARCHAR(30)  NULL,       -- khusus JOURNAL
    issue            VARCHAR(30)  NULL,       -- khusus JOURNAL
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_items_isbn UNIQUE (isbn),
    CONSTRAINT fk_items_category FOREIGN KEY (category_id)
    REFERENCES categories(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_items_year CHECK (publication_year IS NULL OR publication_year BETWEEN 1 AND 9999),
    CONSTRAINT ck_items_ebook_format CHECK (item_type <> 'EBOOK' OR file_format IS NOT NULL),
    INDEX idx_items_title (title),
    INDEX idx_items_category (category_id),
    INDEX idx_items_type (item_type)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. item_authors : tabel penghubung banyak-ke-banyak
--    Hapus item  -> baris penghubungnya ikut terhapus (CASCADE).
--    Hapus author yang masih dipakai -> DITOLAK (RESTRICT).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS item_authors (
                                            item_id   INT NOT NULL,
                                            author_id INT NOT NULL,
                                            PRIMARY KEY (item_id, author_id),
    CONSTRAINT fk_ia_item FOREIGN KEY (item_id)
    REFERENCES library_items(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ia_author FOREIGN KEY (author_id)
    REFERENCES authors(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_ia_author (author_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. item_copies : eksemplar fisik (PRD Tabel 8).
--    item_copies adalah tabel utama (sesuai PRD & relasi FK loan/reservasi).
--    book_copies disediakan sebagai VIEW alias agar kompatibel dengan BookDAO & ReportDAO.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS item_copies (
    id             INT         PRIMARY KEY AUTO_INCREMENT,
    item_id        INT         NOT NULL,
    barcode        VARCHAR(50) NULL,
    status         ENUM('AVAILABLE','BORROWED','RESERVED','DAMAGED','LOST')
                   NOT NULL DEFAULT 'AVAILABLE',
    shelf_location VARCHAR(50) NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_copies_barcode UNIQUE (barcode),
    CONSTRAINT fk_copies_item FOREIGN KEY (item_id)
        REFERENCES library_items(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_copies_item_status (item_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE OR REPLACE VIEW book_copies AS SELECT * FROM item_copies;

-- ---------------------------------------------------------------------
-- 6. members : data anggota perpustakaan (BUKAN akun login).
--    user_id bersifat opsional: menghubungkan anggota ke akun di tabel
--    users (role ANGGOTA). UNIQUE agar satu akun hanya untuk satu anggota.
--    valid_until: masa berlaku keanggotaan (sesuai PRD FR-04 & Aturan 8.1).
--    Anggota yang punya riwayat peminjaman dinonaktifkan, bukan dihapus.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS members (
    id          INT          PRIMARY KEY AUTO_INCREMENT,
    member_code VARCHAR(20)  NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(100) NULL,
    phone       VARCHAR(20)  NULL,
    address     VARCHAR(255) NULL,
    join_date   DATE         NOT NULL,
    valid_until DATE         NULL,
    status      ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    user_id     INT          NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_members_code  UNIQUE (member_code),
    CONSTRAINT uq_members_email UNIQUE (email),
    CONSTRAINT uq_members_user  UNIQUE (user_id),
    CONSTRAINT fk_members_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_members_name (full_name),
    INDEX idx_members_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- DATA CONTOH UNTUK PENGUJIAN (hapus/ganti sebelum demo akhir bila perlu)
-- ISBN di bawah ini hanya contoh, bukan ISBN resmi.
-- =====================================================================

-- Kategori ("Sejarah" sengaja belum dipakai agar bisa dipakai menguji hapus kategori)
INSERT IGNORE INTO categories (name, description) VALUES
('Fiksi',                'Novel dan cerita fiksi'),
('Teknologi Informasi',  'Pemrograman, algoritma, dan rekayasa perangkat lunak'),
('Sejarah',              'Buku sejarah dan biografi'),
('Sains',                'Ilmu pengetahuan alam');

-- Penulis
INSERT IGNORE INTO authors (name, biography) VALUES
('Andrea Hirata',          'Novelis Indonesia.'),
('Pramoedya Ananta Toer',  'Sastrawan Indonesia.'),
('Robert C. Martin',       'Penulis buku rekayasa perangkat lunak.'),
('Thomas H. Cormen',       'Penulis buku algoritma.'),
('Charles E. Leiserson',   'Penulis buku algoritma.'),
('Penulis Contoh',         NULL);   -- belum dipakai buku mana pun (untuk uji hapus penulis)

-- Koleksi (4 buku, 1 e-book, 1 jurnal)
INSERT IGNORE INTO library_items
    (item_type, title, isbn, publisher, publication_year, category_id, description, edition)
VALUES
('BUKU', 'Laskar Pelangi', '978-000-0000-01-1', 'Bentang Pustaka', 2005,
    (SELECT id FROM categories WHERE name = 'Fiksi'),
    'Novel tentang sepuluh anak di Belitung.', NULL),
('BUKU', 'Bumi Manusia', '978-000-0000-02-8', 'Hasta Mitra', 1980,
    (SELECT id FROM categories WHERE name = 'Fiksi'),
    'Novel pertama Tetralogi Buru.', NULL),
('BUKU', 'Clean Code', '978-000-0000-03-5', 'Prentice Hall', 2008,
    (SELECT id FROM categories WHERE name = 'Teknologi Informasi'),
    'Panduan menulis kode yang bersih.', NULL),
('BUKU', 'Introduction to Algorithms', '978-000-0000-04-2', 'MIT Press', 2009,
    (SELECT id FROM categories WHERE name = 'Teknologi Informasi'),
    'Buku acuan algoritma dan struktur data.', 'Edisi ke-3');

INSERT IGNORE INTO library_items
    (item_type, title, isbn, publisher, publication_year, category_id, description, file_format, download_url)
VALUES
('EBOOK', 'Clean Architecture (Digital)', '978-000-0000-05-9', 'Prentice Hall', 2017,
    (SELECT id FROM categories WHERE name = 'Teknologi Informasi'),
    'Versi digital contoh.', 'PDF', 'https://contoh.local/clean-architecture.pdf');

INSERT IGNORE INTO library_items
    (item_type, title, isbn, publisher, publication_year, category_id, description, volume, issue)
VALUES
('JURNAL', 'Jurnal Teknologi Informasi', '2000-0001', 'Universitas Contoh', 2024,
    (SELECT id FROM categories WHERE name = 'Teknologi Informasi'),
    'Jurnal contoh, baca di tempat.', '10', '2');

-- Relasi item - penulis (Introduction to Algorithms punya 2 penulis)
INSERT IGNORE INTO item_authors (item_id, author_id) VALUES
((SELECT id FROM library_items WHERE isbn = '978-000-0000-01-1'), (SELECT id FROM authors WHERE name = 'Andrea Hirata')),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-02-8'), (SELECT id FROM authors WHERE name = 'Pramoedya Ananta Toer')),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-03-5'), (SELECT id FROM authors WHERE name = 'Robert C. Martin')),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-04-2'), (SELECT id FROM authors WHERE name = 'Thomas H. Cormen')),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-04-2'), (SELECT id FROM authors WHERE name = 'Charles E. Leiserson')),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-05-9'), (SELECT id FROM authors WHERE name = 'Robert C. Martin'));

-- Eksemplar fisik (hanya untuk BOOK). Ada yang BORROWED/DAMAGED untuk uji hitung stok.
INSERT IGNORE INTO item_copies (item_id, barcode, status, shelf_location) VALUES
((SELECT id FROM library_items WHERE isbn = '978-000-0000-01-1'), 'BC-0001', 'AVAILABLE', 'Rak A1'),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-01-1'), 'BC-0002', 'BORROWED',  'Rak A1'),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-01-1'), 'BC-0003', 'AVAILABLE', 'Rak A1'),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-02-8'), 'BC-0004', 'AVAILABLE', 'Rak A2'),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-02-8'), 'BC-0005', 'DAMAGED',   'Rak A2'),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-03-5'), 'BC-0006', 'AVAILABLE', 'Rak B1'),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-03-5'), 'BC-0007', 'BORROWED',  'Rak B1'),
((SELECT id FROM library_items WHERE isbn = '978-000-0000-04-2'), 'BC-0008', 'AVAILABLE', 'Rak B2');

-- Anggota perpustakaan (AGT-0001 dihubungkan ke akun login anggota1)
INSERT IGNORE INTO members (member_code, full_name, email, phone, address, join_date, valid_until, status, user_id) VALUES
('AGT-0001', 'Anggota Satu',  'anggota1@contoh.local', '081200000001', 'Medan',          '2026-01-10', '2027-01-10', 'ACTIVE',
    (SELECT id FROM users WHERE username = 'anggota1')),
('AGT-0002', 'Budi Santoso',  'budi@contoh.local',     '081200000002', 'Medan Baru',     '2026-02-15', '2027-02-15', 'ACTIVE',   NULL),
('AGT-0003', 'Siti Aminah',   'siti@contoh.local',     '081200000003', 'Medan Johor',    '2026-03-01', '2027-03-01', 'ACTIVE',   NULL),
('AGT-0004', 'Dewi Lestari',  'dewi@contoh.local',     '081200000004', 'Medan Selayang', '2025-11-20', '2026-01-01', 'INACTIVE', NULL);

-- =====================================================================
-- QUERY PEMERIKSAAN (jalankan manual setelah file ini selesai)
-- =====================================================================
-- SHOW TABLES;
-- SELECT 'categories' t, COUNT(*) n FROM categories UNION ALL
-- SELECT 'authors', COUNT(*) FROM authors UNION ALL
-- SELECT 'library_items', COUNT(*) FROM library_items UNION ALL
-- SELECT 'item_authors', COUNT(*) FROM item_authors UNION ALL
-- SELECT 'book_copies', COUNT(*) FROM book_copies UNION ALL
-- SELECT 'members', COUNT(*) FROM members;
-- Hasil yang diharapkan: 4, 6, 6, 6, 8, 4
--
-- Stok per judul (contoh query yang nanti dipakai BookDAO):
-- SELECT i.title,
--        COUNT(c.id)                          AS total,
--        SUM(c.status = 'AVAILABLE')          AS tersedia,
--        SUM(c.status = 'BORROWED')           AS dipinjam
-- FROM library_items i LEFT JOIN book_copies c ON c.item_id = i.id
-- WHERE i.item_type = 'BUKU' GROUP BY i.id, i.title;