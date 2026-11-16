-- =====================================================================
-- SmartLib — Smart Library Management System
-- Skema Database Lengkap & Data Awal (Master Schema)
-- Sesuai Product Requirements Document (PRD) Versi 1.0
-- =====================================================================

CREATE DATABASE IF NOT EXISTS smartlib
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE smartlib;

-- =====================================================================
-- 1. MODUL AUTENTIKASI & PENGGUNA (Anggota 2)
-- =====================================================================

-- Tabel roles (PRD Bagian 6 & 11)
CREATE TABLE IF NOT EXISTS roles (
    id   INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabel users (PRD FR-01, FR-02 & Tabel 11)
CREATE TABLE IF NOT EXISTS users (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role_id       INT          NOT NULL,
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME     NULL,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id)
        REFERENCES roles(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_users_username (username),
    INDEX idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Role bawaan
INSERT IGNORE INTO roles (id, name) VALUES
(1, 'ADMIN'),
(2, 'PETUGAS'),
(3, 'ANGGOTA');

-- Akun bawaan (password default: Role#123)
INSERT IGNORE INTO users (id, username, password_hash, full_name, role_id, status) VALUES
(1, 'admin',    'pbkdf2$120000$MQOxu4SQd1Vnu3cRM1x77A==$YPlW+7f0btNdSV9P5d4EPQnZJCT38QTP4SfAQqIX6E4=',
                'Administrator', 1, 'ACTIVE'),
(2, 'petugas1', 'pbkdf2$120000$jtzUX6uBaWSfClV4IEnCYg==$O5gogG/x6McUo15Y2COq5GyxPyz0vVj6erWnM2YMaY0=',
                'Petugas Satu', 2, 'ACTIVE'),
(3, 'anggota1', 'pbkdf2$120000$w/xL04VPxT1xIMAjKw8RFA==$jUxt8XIuf6yLH0hL8XKKZr13DQicoDx/dpxBXlwTARY=',
                'Anggota Satu', 3, 'ACTIVE'),
(4, 'anggota2', 'pbkdf2$120000$w/xL04VPxT1xIMAjKw8RFA==$jUxt8XIuf6yLH0hL8XKKZr13DQicoDx/dpxBXlwTARY=',
                'Anggota Nonaktif', 3, 'INACTIVE');

-- =====================================================================
-- 2. MODUL KOLEKSI BUKU & ANGGOTA (Anggota 3)
-- =====================================================================

-- Tabel categories (PRD FR-03 & Tabel 11)
CREATE TABLE IF NOT EXISTS categories (
    id          INT          PRIMARY KEY AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    CONSTRAINT uq_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabel authors (PRD FR-03 & Tabel 11)
CREATE TABLE IF NOT EXISTS authors (
    id        INT          PRIMARY KEY AUTO_INCREMENT,
    name      VARCHAR(100) NOT NULL,
    biography TEXT         NULL,
    CONSTRAINT uq_authors_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabel library_items (PRD FR-03 & Tabel 11)
CREATE TABLE IF NOT EXISTS library_items (
    id               INT          PRIMARY KEY AUTO_INCREMENT,
    item_type        ENUM('BUKU','EBOOK','JURNAL') NOT NULL DEFAULT 'BUKU',
    title            VARCHAR(255) NOT NULL,
    isbn             VARCHAR(20)  NULL,
    publisher        VARCHAR(100) NULL,
    publication_year SMALLINT UNSIGNED NULL,
    category_id      INT          NULL,
    description      TEXT         NULL,
    edition          VARCHAR(50)  NULL,
    file_format      VARCHAR(20)  NULL,
    download_url     VARCHAR(500) NULL,
    volume           VARCHAR(30)  NULL,
    issue            VARCHAR(30)  NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_items_isbn UNIQUE (isbn),
    CONSTRAINT fk_items_category FOREIGN KEY (category_id)
        REFERENCES categories(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_items_year CHECK (publication_year IS NULL OR publication_year BETWEEN 1 AND 9999),
    INDEX idx_items_title (title),
    INDEX idx_items_category (category_id),
    INDEX idx_items_type (item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Tabel item_authors (PRD FR-03 & Tabel 11 - relasi N:M)
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

-- Tabel item_copies (PRD FR-03 & Tabel 11)
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

-- View book_copies alias untuk kompatibilitas kode
CREATE OR REPLACE VIEW book_copies AS SELECT * FROM item_copies;

-- Tabel members (PRD FR-04 & Tabel 11)
CREATE TABLE IF NOT EXISTS members (
    id          INT          PRIMARY KEY AUTO_INCREMENT,
    member_code VARCHAR(20)  NOT NULL,
    full_name   VARCHAR(100) NOT NULL,
    email       VARCHAR(100) NULL,
    phone       VARCHAR(20)  NULL,
    address     VARCHAR(255) NULL,
    join_date   DATE         NOT NULL,
    valid_until DATE         NULL,        -- Masa berlaku keanggotaan (PRD FR-04 & 8.1)
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

-- Kategori contoh
INSERT IGNORE INTO categories (id, name, description) VALUES
(1, 'Fiksi',                'Novel dan cerita fiksi'),
(2, 'Teknologi Informasi',  'Pemrograman, algoritma, dan rekayasa perangkat lunak'),
(3, 'Sejarah',              'Buku sejarah dan biografi'),
(4, 'Sains',                'Ilmu pengetahuan alam');

-- Penulis contoh
INSERT IGNORE INTO authors (id, name, biography) VALUES
(1, 'Andrea Hirata',          'Novelis Indonesia.'),
(2, 'Pramoedya Ananta Toer',  'Sastrawan Indonesia.'),
(3, 'Robert C. Martin',       'Penulis buku rekayasa perangkat lunak.'),
(4, 'Thomas H. Cormen',       'Penulis buku algoritma.'),
(5, 'Charles E. Leiserson',   'Penulis buku algoritma.'),
(6, 'Penulis Contoh',         NULL);

-- Koleksi contoh
INSERT IGNORE INTO library_items
    (id, item_type, title, isbn, publisher, publication_year, category_id, description, edition)
VALUES
(1, 'BUKU', 'Laskar Pelangi', '978-000-0000-01-1', 'Bentang Pustaka', 2005, 1, 'Novel tentang sepuluh anak di Belitung.', NULL),
(2, 'BUKU', 'Bumi Manusia',   '978-000-0000-02-8', 'Hasta Mitra',     1980, 1, 'Novel pertama Tetralogi Buru.', NULL),
(3, 'BUKU', 'Clean Code',     '978-000-0000-03-5', 'Prentice Hall',   2008, 2, 'Panduan menulis kode yang bersih.', NULL),
(4, 'BUKU', 'Introduction to Algorithms', '978-000-0000-04-2', 'MIT Press', 2009, 2, 'Buku acuan algoritma.', 'Edisi ke-3');

INSERT IGNORE INTO library_items
    (id, item_type, title, isbn, publisher, publication_year, category_id, description, file_format, download_url)
VALUES
(5, 'EBOOK', 'Clean Architecture (Digital)', '978-000-0000-05-9', 'Prentice Hall', 2017, 2,
    'Versi digital contoh.', 'PDF', 'https://contoh.local/clean-architecture.pdf');

INSERT IGNORE INTO library_items
    (id, item_type, title, isbn, publisher, publication_year, category_id, description, volume, issue)
VALUES
(6, 'JURNAL', 'Jurnal Teknologi Informasi', '2000-0001', 'Universitas Contoh', 2024, 2,
    'Jurnal contoh, baca di tempat.', '10', '2');

-- Relasi item - author
INSERT IGNORE INTO item_authors (item_id, author_id) VALUES
(1, 1), (2, 2), (3, 3), (4, 4), (4, 5), (5, 3);

-- Eksemplar buku
INSERT IGNORE INTO item_copies (id, item_id, barcode, status, shelf_location) VALUES
(1, 1, 'BC-0001', 'AVAILABLE', 'Rak A1'),
(2, 1, 'BC-0002', 'BORROWED',  'Rak A1'),
(3, 1, 'BC-0003', 'AVAILABLE', 'Rak A1'),
(4, 2, 'BC-0004', 'AVAILABLE', 'Rak A2'),
(5, 2, 'BC-0005', 'DAMAGED',   'Rak A2'),
(6, 3, 'BC-0006', 'AVAILABLE', 'Rak B1'),
(7, 3, 'BC-0007', 'BORROWED',  'Rak B1'),
(8, 4, 'BC-0008', 'AVAILABLE', 'Rak B2');

-- Anggota
INSERT IGNORE INTO members (id, member_code, full_name, email, phone, address, join_date, valid_until, status, user_id) VALUES
(1, 'AGT-0001', 'Anggota Satu',  'anggota1@contoh.local', '081200000001', 'Medan',          '2026-01-10', '2027-01-10', 'ACTIVE',   3),
(2, 'AGT-0002', 'Budi Santoso',  'budi@contoh.local',     '081200000002', 'Medan Baru',     '2026-02-15', '2027-02-15', 'ACTIVE',   NULL),
(3, 'AGT-0003', 'Siti Aminah',   'siti@contoh.local',     '081200000003', 'Medan Johor',    '2026-03-01', '2027-03-01', 'ACTIVE',   NULL),
(4, 'AGT-0004', 'Dewi Lestari',  'dewi@contoh.local',     '081200000004', 'Medan Selayang', '2025-11-20', '2026-01-01', 'INACTIVE', NULL);

-- =====================================================================
-- 3. MODUL TRANSAKSI, ANTREAN & DENDA (Anggota 4 & 5)
-- =====================================================================

-- Tabel loans (PRD FR-06 & Tabel 11)
CREATE TABLE IF NOT EXISTS loans (
    id           INT PRIMARY KEY AUTO_INCREMENT,
    member_id    INT NOT NULL,
    user_id      INT NULL,
    processed_by INT NOT NULL,
    borrow_date  DATE NOT NULL DEFAULT (CURRENT_DATE),
    borrowed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date     DATE NULL,
    return_date  DATE NULL,
    copy_id      INT NULL,
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

-- Tabel loan_items (PRD FR-06, FR-07 & Tabel 11)
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

-- Tabel reservations (PRD FR-08 & Tabel 11)
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

-- Tabel fines (PRD FR-09 & Tabel 11)
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

-- Trigger Sinkronisasi
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

-- Data Contoh Transaksi
INSERT IGNORE INTO loans (id, member_id, user_id, processed_by, borrow_date, due_date, copy_id, status) VALUES
(1, 1, 3, 2, DATE_SUB(CURDATE(), INTERVAL 2 DAY), DATE_ADD(CURDATE(), INTERVAL 5 DAY), 2, 'ACTIVE'),
(2, 2, NULL, 2, DATE_SUB(CURDATE(), INTERVAL 10 DAY), DATE_SUB(CURDATE(), INTERVAL 3 DAY), 7, 'ACTIVE'),
(3, 1, 3, 2, DATE_SUB(CURDATE(), INTERVAL 20 DAY), DATE_SUB(CURDATE(), INTERVAL 13 DAY), 4, 'COMPLETED');

INSERT IGNORE INTO loan_items (id, loan_id, copy_id, due_date, returned_at, status) VALUES
(1, 1, 2, DATE_ADD(CURDATE(), INTERVAL 5 DAY), NULL, 'BORROWED'),
(2, 2, 7, DATE_SUB(CURDATE(), INTERVAL 3 DAY), NULL, 'BORROWED'),
(3, 3, 4, DATE_SUB(CURDATE(), INTERVAL 13 DAY), DATE_SUB(CURDATE(), INTERVAL 11 DAY), 'RETURNED');

UPDATE loans SET return_date = DATE_SUB(CURDATE(), INTERVAL 11 DAY) WHERE id = 3;

INSERT IGNORE INTO fines (id, loan_item_id, overdue_days, rate_per_day, amount, status, assessed_at, paid_at) VALUES
(1, 3, 2, 2000.00, 4000.00, 'PAID', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
(2, 2, 3, 2000.00, 6000.00, 'UNPAID', NOW(), NULL);

INSERT IGNORE INTO reservations (id, member_id, item_id, requested_at, status) VALUES
(1, 3, 3, DATE_SUB(NOW(), INTERVAL 1 DAY), 'WAITING');
