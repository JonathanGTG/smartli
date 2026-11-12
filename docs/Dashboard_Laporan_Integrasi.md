# Dokumentasi Dashboard, Laporan & Integrasi — SmartLib (Anggota 5)

## 1. Komponen yang Dibuat

| Lapisan   | Class                              | Tugas                                                               |
|-----------|------------------------------------|---------------------------------------------------------------------|
| model     | `Loan`, `Fine`                     | Data peminjaman dan denda (dengan kalkulasi overdue)                |
| dao       | `LoanDAO`, `FineDAO`, `ReportDAO`  | Query CRUD + agregat SQL (COUNT, SUM, GROUP BY)                     |
| service   | `LoanService`, `ReportService`     | Logika bisnis peminjaman, denda, dan statistik laporan              |
| ui        | `DashboardFrame`, `ReportFrame`, `MainFrame` | UI utama, laporan lengkap (JTabbedPane), wrapper navigasi  |
| ui/components | `StatCard`, `ReportTablePanel` | Komponen reusable: kartu statistik & tabel laporan                  |
| sql       | `loan_fine_schema.sql`             | Skema tabel loans, fines, library_items, book_copies                |
| test      | `LoanServiceTest`, `FineServiceTest` | 19 unit-test tambahan, total 38 test lulus                        |

## 2. Arsitektur Navigasi

```
Main.java
  └─► LoginFrame
        └─► DashboardRouter.open(user)
              └─► DashboardFrame(user)        ← BARU
                    ├─ StatCard ×4           ← BARU
                    ├─ overdueTable           ← BARU
                    ├─ mostBorrowedTable      ← BARU
                    └─ Nav Buttons:
                         ├─ Laporan  → ReportFrame  ← BARU
                         └─ Kelola Pengguna → UserManagementFrame
```

## 3. Fitur Dashboard

| Fitur | Tersedia |
|-------|----------|
| Statistik total judul buku | ✅ |
| Statistik total anggota | ✅ |
| Statistik peminjaman aktif | ✅ |
| Statistik buku terlambat | ✅ |
| Daftar buku terlambat (tabel) | ✅ |
| Top-5 buku paling sering dipinjam | ✅ |
| Navigasi berdasarkan role (RBAC) | ✅ |
| Tombol Logout | ✅ |
| Auto-refresh data | ✅ |

## 4. Fitur Laporan (`ReportFrame`)

| Tab | Konten |
|-----|--------|
| ⚠ Terlambat | Daftar peminjaman yang melewati jatuh tempo |
| 🏆 Buku Populer | Top-10 buku paling sering dipinjam |
| 👥 Aktivitas Anggota | Rangkuman per anggota: total, aktif, terlambat |
| 📅 Bulanan | Jumlah peminjaman 12 bulan terakhir |

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

| Test Class | Jumlah Test | Coverage |
|------------|-------------|---------|
| `LoanServiceTest` | 9 | borrow, returnBook, query, RBAC |
| `FineServiceTest` | 10 | FinePolicy, Loan.isOverdue, payFine, statistik |
| **Total** | **38** | 0 Failures |

## 7. Cara Menjalankan

1. Jalankan `sql/auth_schema.sql` di MySQL Workbench  
2. Jalankan `sql/loan_fine_schema.sql` di MySQL Workbench  
3. Sesuaikan `DatabaseConnection.java` (user/password MySQL)  
4. Jalankan `Main.java`  
5. Login: `admin / Admin#123` atau `petugas1 / Petugas#123`

> [!TIP]
> `mvn test` untuk menjalankan semua 38 unit-test (tidak perlu MySQL).

## 8. Hak Akses per Role

| Fitur Dashboard | Admin | Petugas | Anggota |
|-----------------|:-----:|:-------:|:-------:|
| Lihat statistik | ✅ | ✅ | ❌ |
| Lihat laporan | ✅ | ✅ | ❌ |
| Proses peminjaman | ✅ | ✅ | ❌ |
| Kelola pengguna | ✅ | ❌ | ❌ |
| Riwayat sendiri | ❌ | ❌ | ✅ |
