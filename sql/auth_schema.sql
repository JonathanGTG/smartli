-- SmartLib - skema autentikasi (Anggota 2)
-- Jalankan di MySQL Workbench. Database: smartlib
CREATE DATABASE IF NOT EXISTS smartlib CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smartlib;

CREATE TABLE IF NOT EXISTS roles (
    id   INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS users (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,       -- hash PBKDF2, bukan teks biasa
    full_name     VARCHAR(100) NOT NULL,
    role_id       INT          NOT NULL,
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    nim           VARCHAR(30)  NULL UNIQUE,
    study_program VARCHAR(100) NULL,
    email         VARCHAR(100) NULL UNIQUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME     NULL,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

-- Bila tabel users sudah dibuat sebelumnya tanpa kolom baru:
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS nim VARCHAR(30) NULL UNIQUE;
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS study_program VARCHAR(100) NULL;
-- ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(100) NULL UNIQUE;

INSERT IGNORE INTO roles (name) VALUES ('ADMIN'), ('PETUGAS'), ('ANGGOTA');

-- Akun contoh UNTUK PENGEMBANGAN SAJA (ganti/hapus sebelum demo akhir).
--   admin / Admin#123   petugas1 / Petugas#123   anggota1 / Anggota#123
INSERT IGNORE INTO users (username, password_hash, full_name, role_id, status, nim, study_program, email) VALUES
('admin',    'pbkdf2$120000$MQOxu4SQd1Vnu3cRM1x77A==$YPlW+7f0btNdSV9P5d4EPQnZJCT38QTP4SfAQqIX6E4=',
             'Administrator', (SELECT id FROM roles WHERE name='ADMIN'),   'ACTIVE', NULL, NULL, 'admin@smartlib.ac.id'),
('petugas1', 'pbkdf2$120000$jtzUX6uBaWSfClV4IEnCYg==$O5gogG/x6McUo15Y2COq5GyxPyz0vVj6erWnM2YMaY0=',
             'Petugas Satu',  (SELECT id FROM roles WHERE name='PETUGAS'), 'ACTIVE', NULL, NULL, 'petugas1@smartlib.ac.id'),
('anggota1', 'pbkdf2$120000$w/xL04VPxT1xIMAjKw8RFA==$jUxt8XIuf6yLH0hL8XKKZr13DQicoDx/dpxBXlwTARY=',
             'Anggota Satu',  (SELECT id FROM roles WHERE name='ANGGOTA'), 'ACTIVE', '210001', 'Teknik Informatika', 'anggota1@smartlib.ac.id'),
('anggota2', 'pbkdf2$120000$w/xL04VPxT1xIMAjKw8RFA==$jUxt8XIuf6yLH0hL8XKKZr13DQicoDx/dpxBXlwTARY=',
             'Anggota Nonaktif', (SELECT id FROM roles WHERE name='ANGGOTA'), 'INACTIVE', '210002', 'Sistem Informasi', 'anggota2@smartlib.ac.id');

