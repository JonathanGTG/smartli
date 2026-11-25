# Dokumentasi Dashboard, Laporan & Integrasi — SmartLib (Anggota 5)

## 1. Komponen yang Dibuat

| Lapisan   | Class                              | Tugas                                                               |
|-----------|------------------------------------|---------------------------------------------------------------------|
| model     | `Loan`, `Fine`                     | Data peminjaman dan denda (dengan kalkulasi overdue)                |
| dao       | `LoanDAO`, `FineDAO`, `ReportDAO`  | Query CRUD + agregat SQL (COUNT, SUM, GROUP BY)                     |
| service   | `LoanService`, `ReportService`     | Logika bisnis peminjaman, denda, dan statistik laporan              |
| ui        | `DashboardFrame`, `ReportFrame` | UI utama dan laporan lengkap (JTabbedPane)                       |
| ui/components | `StatCard`, `ReportTablePanel` | Komponen reusable: kartu statistik & tabel laporan                  |
| sql       | `loan_fine_schema.sql`             | Skema tabel loans, fines, library_items, book_copies                |
| test      | `LoanServiceTest`, `FineServiceTest` | 13 unit-test (7 + 6); jumlah seluruh proyek ada di bagian 6       |

## 2. Arsitektur Navigasi

```
Main.java
  └─► LoginFrame
        └─► DashboardRouter.open(user)
              └─► DashboardFrame(user)
                    ├─ ringkasan (StatCard x4), tabel terlambat, tabel buku populer
                    └─ menu bar (hanya item yang diizinkan role):
                         ├─ Data      : Katalog, Kelola Buku, Kategori & Penulis, Anggota
                         ├─ Transaksi : Peminjaman, Pengembalian, Antrean, Denda
                         ├─ Laporan   : Laporan Lengkap (ReportFrame)
                         └─ Akun      : Riwayat/Denda/Antrean Saya, Kelola Pengguna, Keluar
```

## 3. Fitur Dashboard

| Fitur | Tersedia |
|-------|----------|
| Statistik total judul buku | Ya |
| Statistik total anggota | Ya |
| Statistik peminjaman aktif | Ya |
| Statistik buku terlambat | Ya |
| Daftar buku terlambat (tabel) | Ya |
| Top-5 buku paling sering dipinjam | Ya |
| Navigasi berdasarkan role (RBAC) | Ya |
| Tombol Logout | Ya |
| Auto-refresh data | Ya |

## 4. Fitur Laporan (`ReportFrame`)

| Tab | Konten |
|-----|--------|
| Terlambat | Daftar peminjaman yang melewati jatuh tempo |
| Buku Populer | Top-10 buku paling sering dipinjam |
| Aktivitas Anggota | Rangkuman per anggota: total, aktif, terlambat |
| Bulanan | Jumlah peminjaman 12 bulan terakhir |

## 5. SQL Tambahan

Jalankan `sql/loan_fine_schema.sql` **setelah** `sql/auth_schema.sql`:

```sql
-- Tabel baru:
library_items  -- judul buku/ebook/jurnal
book_copies    -- eksemplar fisik
loans          -- transaksi peminjaman
fines          -- denda keterlambatan
```

## 6. Unit Test Baru

| Test Class | Jumlah @Test | Coverage |
|------------|--------------|----------|
| `LoanServiceTest` | 7 | aturan peminjaman, model Loan, receipt |
| `FineServiceTest` | 6 | FinePolicy, perhitungan denda |
| `ServiceAccessTest` | 6 | hak akses service Anggota 4 |
| `MyAccountServiceTest` | 3 | hak akses layanan mandiri anggota |
| `MemberServiceTest` | 20 | CRUD anggota (DAO palsu) |
| `BookServiceMasterDataTest` | 12 | CRUD kategori dan penulis (DAO palsu) |
| `AuthServiceTest` | 16 | login, role, kelola akun |
| `OopConceptsTest` | 6 | konsep PBO |
| `PasswordHasherTest` | 3 | hash password |
| **Total** | **79** | hasil lulus/gagal: jalankan `mvn test` dan isi di sini |

## 7. Cara Menjalankan

1. Jalankan `sql/auth_schema.sql` di MySQL Workbench  
2. Jalankan `sql/loan_fine_schema.sql` di MySQL Workbench  
3. Sesuaikan `DatabaseConnection.java` (user/password MySQL)  
4. Jalankan `Main.java`  
5. Login: `admin / Admin#123` atau `petugas1 / Petugas#123`

> [!TIP]
> `mvn test` untuk menjalankan seluruh unit-test (tidak perlu MySQL).

## 8. Hak Akses per Role

| Fitur Dashboard | Admin | Petugas | Anggota |
|-----------------|:-----:|:-------:|:-------:|
| Lihat statistik | Ya | Ya | Tidak |
| Lihat laporan | Ya | Ya | Tidak |
| Proses peminjaman | Ya | Ya | Tidak |
| Kelola pengguna | Ya | Tidak | Tidak |
| Riwayat sendiri | Tidak | Tidak | Ya |
