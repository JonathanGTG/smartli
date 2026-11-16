-- SmartLib - skema peminjaman & denda (Anggota 5)
-- Jalankan setelah auth_schema.sql. Database: smartlib
USE smartlib;

-- Tabel library_items dan book_copies TIDAK dibuat di sini lagi.
-- Keduanya dibuat oleh sql/book_member_schema.sql (Anggota 3), jadi jalankan
-- file tersebut LEBIH DULU. Mendefinisikan tabel yang sama di dua file membuat

-- kolom category_id, barcode, dan relasi penulis tidak terbentuk.

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


-- Data contoh buku dan eksemplar sekarang ada di sql/book_member_schema.sql.