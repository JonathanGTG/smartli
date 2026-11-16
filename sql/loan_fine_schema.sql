-- =====================================================================
-- SmartLib - skema Peminjaman, Detail Item, Antrean & Denda (Anggota 4 & 5)
-- Database : smartlib
--
-- URUTAN MENJALANKAN (PENTING, jangan dibalik):
--   1. sql/auth_schema.sql          (membuat tabel roles dan users)
--   2. sql/book_member_schema.sql   (koleksi, penulis, eksemplar, anggota)
--   3. sql/loan_fine_schema.sql     (file ini: loans, loan_items, reservations, fines)
--
-- Sesuai PRD Bagian 7 (FR-06 s/d FR-09), Bagian 8, 9, dan Tabel PRD Bagian 11:
--   - loans        : transaksi peminjaman (header)
--   - loan_items   : detail eksemplar dalam transaksi peminjaman
--   - reservations : antrean peminjaman buku dengan prinsip FIFO
--   - fines        : denda keterlambatan per detail item pinjaman
--
-- File ini AMAN dijalankan berulang kali:
--   - tabel memakai CREATE TABLE IF NOT EXISTS
--   - data contoh memakai INSERT IGNORE
-- =====================================================================
USE smartlib;

-- ---------------------------------------------------------------------
-- 1. loans : transaksi peminjaman (header)
--    Satu transaksi peminjaman diproses oleh Petugas/Admin atas nama Anggota.
--    Kolom copy_id, due_date, return_date disinkronkan otomatis dari
--    loan_items via trigger agar kompatibel dengan query agregat laporan.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS loans (
    id           INT PRIMARY KEY AUTO_INCREMENT,
    member_id    INT NOT NULL,
    user_id      INT NULL,                        -- akun login anggota (disinkronkan otomatis dari members)
    processed_by INT NOT NULL,                    -- user id petugas/admin yang memproses transaksi
    borrow_date  DATE NOT NULL DEFAULT (CURRENT_DATE),
    borrowed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date     DATE NULL,                       -- tanggal jatuh tempo pinjaman (disinkronkan dari loan_items)
    return_date  DATE NULL,                       -- tanggal pengembalian selesai
    copy_id      INT NULL,                        -- id eksemplar (disinkronkan dari loan_items untuk kompatibilitas)
    status       ENUM('ACTIVE','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_member FOREIGN KEY (member_id)
        REFERENCES members(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_loan_user FOREIGN KEY (user_id)
        REFERENCES users(id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_loan_processor FOREIGN KEY (processed_by)
        REFERENCES users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_loan_copy FOREIGN KEY (copy_id)
        REFERENCES item_copies(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_loan_member (member_id),
    INDEX idx_loan_user (user_id),
    INDEX idx_loan_status (status),
    INDEX idx_loan_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 2. loan_items : detail item eksemplar dalam peminjaman
--    Mencatat eksemplar buku spesifik yang dipinjam beserta tanggal jatuh tempo
--    dan pengembalian masing-masing item.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS loan_items (
    id          INT PRIMARY KEY AUTO_INCREMENT,
    loan_id     INT NOT NULL,
    copy_id     INT NOT NULL,
    due_date    DATE NOT NULL,
    returned_at DATE NULL,
    status      ENUM('BORROWED','RETURNED','LOST') NOT NULL DEFAULT 'BORROWED',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_li_loan FOREIGN KEY (loan_id)
        REFERENCES loans(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_li_copy FOREIGN KEY (copy_id)
        REFERENCES item_copies(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_li_loan (loan_id),
    INDEX idx_li_copy (copy_id),
    INDEX idx_li_status (status),
    INDEX idx_li_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 3. reservations : antrean peminjaman buku (FIFO)
--    Mencatat permohonan antrean ketika semua eksemplar suatu judul sedang dipinjam.
--    Ketika eksemplar dikembalikan, sistem menawarkan ke antrean terdepan (OFFERED).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reservations (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    member_id       INT NOT NULL,
    item_id         INT NOT NULL,
    offered_copy_id INT NULL,
    requested_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status          ENUM('WAITING','OFFERED','FULFILLED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'WAITING',
    offered_at      DATETIME NULL,
    expires_at      DATETIME NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_res_member FOREIGN KEY (member_id)
        REFERENCES members(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_res_item FOREIGN KEY (item_id)
        REFERENCES library_items(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_res_copy FOREIGN KEY (offered_copy_id)
        REFERENCES item_copies(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_res_item_status (item_id, status),
    INDEX idx_res_member (member_id),
    INDEX idx_res_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 4. fines : denda keterlambatan
--    Denda dicatat per detail eksemplar pinjaman (loan_items).
--    Rp2.000 per eksemplar per hari keterlambatan (PRD Aturan 8).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fines (
    id           INT PRIMARY KEY AUTO_INCREMENT,
    loan_item_id INT NOT NULL,
    overdue_days INT NOT NULL DEFAULT 0,
    rate_per_day DECIMAL(10,2) NOT NULL DEFAULT 2000.00,
    amount       DECIMAL(10,2) NOT NULL,
    status       ENUM('UNPAID','PAID','WAIVED') NOT NULL DEFAULT 'UNPAID',
    assessed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at      DATETIME NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fine_loan_item FOREIGN KEY (loan_item_id)
        REFERENCES loan_items(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_fine_item (loan_item_id),
    INDEX idx_fine_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- 5. Trigger Sinkronisasi Otomatis Antar-Modul
-- ---------------------------------------------------------------------
DELIMITER //

DROP TRIGGER IF EXISTS trg_loans_before_insert//
CREATE TRIGGER trg_loans_before_insert
BEFORE INSERT ON loans
FOR EACH ROW
BEGIN
    IF NEW.user_id IS NULL AND NEW.member_id IS NOT NULL THEN
        SET NEW.user_id = (SELECT user_id FROM members WHERE id = NEW.member_id LIMIT 1);
    END IF;
    IF (NEW.member_id IS NULL OR NEW.member_id = 0) AND NEW.user_id IS NOT NULL THEN
        SET NEW.member_id = (SELECT id FROM members WHERE user_id = NEW.user_id LIMIT 1);
    END IF;
END//

DROP TRIGGER IF EXISTS trg_loan_items_after_insert//
CREATE TRIGGER trg_loan_items_after_insert
AFTER INSERT ON loan_items
FOR EACH ROW
BEGIN
    UPDATE loans
    SET copy_id = COALESCE(loans.copy_id, NEW.copy_id),
        due_date = COALESCE(loans.due_date, NEW.due_date)
    WHERE loans.id = NEW.loan_id;
END//

DROP TRIGGER IF EXISTS trg_loan_items_after_update//
CREATE TRIGGER trg_loan_items_after_update
AFTER UPDATE ON loan_items
FOR EACH ROW
BEGIN
    IF NEW.status = 'RETURNED' AND (OLD.status <> 'RETURNED' OR OLD.status IS NULL) THEN
        UPDATE loans
        SET return_date = COALESCE(NEW.returned_at, CURRENT_DATE)
        WHERE loans.id = NEW.loan_id
          AND NOT EXISTS (
              SELECT 1 FROM loan_items WHERE loan_id = NEW.loan_id AND status = 'BORROWED'
          );
    END IF;
END//

DELIMITER ;

-- =====================================================================
-- DATA CONTOH TRANSAKSI & DENDA (Mendukung demo dan statistik dashboard)
-- =====================================================================

-- 1. Transaksi Aktif 1: Anggota 1 meminjam Laskar Pelangi (BC-0002) - Masih berjalan lancar
INSERT IGNORE INTO loans (id, member_id, user_id, processed_by, borrow_date, due_date, copy_id, status) VALUES
(1,
 (SELECT id FROM members WHERE member_code = 'AGT-0001'),
 (SELECT id FROM users WHERE username = 'anggota1'),
 (SELECT id FROM users WHERE username = 'petugas1'),
 DATE_SUB(CURDATE(), INTERVAL 2 DAY),
 DATE_ADD(CURDATE(), INTERVAL 5 DAY),
 (SELECT id FROM item_copies WHERE barcode = 'BC-0002'),
 'ACTIVE');

INSERT IGNORE INTO loan_items (id, loan_id, copy_id, due_date, status) VALUES
(1, 1, (SELECT id FROM item_copies WHERE barcode = 'BC-0002'), DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'BORROWED');

-- 2. Transaksi Aktif 2: Anggota 2 (Budi) meminjam Clean Code (BC-0007) - TERLAMBAT 3 HARI
INSERT IGNORE INTO loans (id, member_id, user_id, processed_by, borrow_date, due_date, copy_id, status) VALUES
(2,
 (SELECT id FROM members WHERE member_code = 'AGT-0002'),
 NULL,
 (SELECT id FROM users WHERE username = 'petugas1'),
 DATE_SUB(CURDATE(), INTERVAL 10 DAY),
 DATE_SUB(CURDATE(), INTERVAL 3 DAY),
 (SELECT id FROM item_copies WHERE barcode = 'BC-0007'),
 'ACTIVE');

INSERT IGNORE INTO loan_items (id, loan_id, copy_id, due_date, status) VALUES
(2, 2, (SELECT id FROM item_copies WHERE barcode = 'BC-0007'), DATE_SUB(CURDATE(), INTERVAL 3 DAY), 'BORROWED');

-- 3. Transaksi Selesai 3: Anggota 1 meminjam Bumi Manusia (BC-0004), terlambat 2 hari, denda lunas
INSERT IGNORE INTO loans (id, member_id, user_id, processed_by, borrow_date, due_date, return_date, copy_id, status) VALUES
(3,
 (SELECT id FROM members WHERE member_code = 'AGT-0001'),
 (SELECT id FROM users WHERE username = 'anggota1'),
 (SELECT id FROM users WHERE username = 'petugas1'),
 DATE_SUB(CURDATE(), INTERVAL 20 DAY),
 DATE_SUB(CURDATE(), INTERVAL 13 DAY),
 DATE_SUB(CURDATE(), INTERVAL 11 DAY),
 (SELECT id FROM item_copies WHERE barcode = 'BC-0004'),
 'COMPLETED');

INSERT IGNORE INTO loan_items (id, loan_id, copy_id, due_date, returned_at, status) VALUES
(3, 3, (SELECT id FROM item_copies WHERE barcode = 'BC-0004'), DATE_SUB(CURDATE(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), 'RETURNED');

-- Denda untuk transaksi 3 (sudah dibayar)
INSERT IGNORE INTO fines (id, loan_item_id, overdue_days, rate_per_day, amount, status, assessed_at, paid_at) VALUES
(1, 3, 2, 2000.00, 4000.00, 'PAID', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY));

-- 4. Denda belum dibayar untuk transaksi aktif 2 (keterlambatan 3 hari x Rp2.000 = Rp6.000)
INSERT IGNORE INTO fines (id, loan_item_id, overdue_days, rate_per_day, amount, status, assessed_at, paid_at) VALUES
(2, 2, 3, 2000.00, 6000.00, 'UNPAID', NOW(), NULL);

-- 5. Antrean Peminjaman (FIFO): Siti Aminah mengantre buku Clean Code
INSERT IGNORE INTO reservations (id, member_id, item_id, requested_at, status) VALUES
(1,
 (SELECT id FROM members WHERE member_code = 'AGT-0003'),
 (SELECT id FROM library_items WHERE isbn = '978-000-0000-03-5'),
 DATE_SUB(NOW(), INTERVAL 1 DAY),
 'WAITING');
