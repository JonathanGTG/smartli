-- SmartLib - skema peminjaman & denda (Anggota 5)
-- Jalankan setelah auth_schema.sql. Database: smartlib
USE smartlib;

-- ─────────────────────── Koleksi (sederhana) ───────────────────────
CREATE TABLE IF NOT EXISTS library_items (
    id               INT PRIMARY KEY AUTO_INCREMENT,
    title            VARCHAR(200) NOT NULL,
    isbn             VARCHAR(20)  NULL UNIQUE,
    publisher        VARCHAR(100) NULL,
    publication_year INT          NULL,
    item_type        ENUM('BUKU','EBOOK','JURNAL') NOT NULL DEFAULT 'BUKU',
    description      TEXT         NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Eksemplar fisik buku
CREATE TABLE IF NOT EXISTS book_copies (
    id       INT PRIMARY KEY AUTO_INCREMENT,
    item_id  INT NOT NULL,
    barcode  VARCHAR(50) NULL UNIQUE,
    status   ENUM('AVAILABLE','BORROWED','RESERVED','DAMAGED','LOST') NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT fk_copy_item FOREIGN KEY (item_id) REFERENCES library_items(id)
);

-- ─────────────────────── Peminjaman ───────────────────────
CREATE TABLE IF NOT EXISTS loans (
    id          INT PRIMARY KEY AUTO_INCREMENT,
    user_id     INT  NOT NULL,
    copy_id     INT  NOT NULL,
    borrow_date DATE NOT NULL,
    due_date    DATE NOT NULL,
    return_date DATE NULL,
    status      ENUM('ACTIVE','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_loan_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_loan_copy FOREIGN KEY (copy_id) REFERENCES book_copies(id)
);

-- ─────────────────────── Denda ───────────────────────
CREATE TABLE IF NOT EXISTS fines (
    id          INT PRIMARY KEY AUTO_INCREMENT,
    loan_id     INT            NOT NULL,
    amount      DECIMAL(10,2)  NOT NULL,
    paid        BOOLEAN        NOT NULL DEFAULT FALSE,
    paid_at     DATETIME       NULL,
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fine_loan FOREIGN KEY (loan_id) REFERENCES loans(id)
);

-- ─────────────────────── Data Contoh ───────────────────────
INSERT IGNORE INTO library_items (title, isbn, publisher, publication_year, item_type) VALUES
('Clean Code',              '9780132350884', 'Prentice Hall', 2008, 'BUKU'),
('The Pragmatic Programmer','9780201616224', 'Addison-Wesley', 1999, 'BUKU'),
('Design Patterns',         '9780201633610', 'Addison-Wesley', 1994, 'BUKU'),
('Effective Java',          '9780134685991', 'Addison-Wesley', 2018, 'BUKU'),
('Introduction to Algorithms','9780262033848','MIT Press', 2009, 'BUKU');

INSERT IGNORE INTO book_copies (item_id, barcode, status) VALUES
(1, 'BC-0001', 'AVAILABLE'),
(1, 'BC-0002', 'AVAILABLE'),
(2, 'BC-0003', 'AVAILABLE'),
(3, 'BC-0004', 'AVAILABLE'),
(4, 'BC-0005', 'AVAILABLE'),
(5, 'BC-0006', 'AVAILABLE');
