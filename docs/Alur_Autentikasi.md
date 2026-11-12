# Dokumentasi Alur Autentikasi — SmartLib (Anggota 2)

## 1. Komponen
| Lapisan | Class | Tugas |
|---|---|---|
| model | `User`, `Role`, `Permission` | Data akun, role, dan pemetaan hak akses (RBAC) |
| dao | `UserDAO` | Query `users` + `roles` memakai `PreparedStatement` |
| service | `AuthService` | Login/logout, session, pengecekan hak akses, manajemen akun |
| util | `PasswordHasher` | Hash PBKDF2-HMAC-SHA256 + salt acak |
| ui | `LoginFrame`, `DashboardRouter`, `UserManagementFrame` | Form login, arah dashboard per role, kelola akun |

## 2. Alur login
```mermaid
sequenceDiagram
    participant U as Pengguna
    participant L as LoginFrame
    participant S as AuthService
    participant D as UserDAO
    participant DB as MySQL
    U->>L: isi username + password, klik Login
    L->>S: login(username, password) [SwingWorker]
    S->>D: findByUsername(username)
    D->>DB: SELECT ... WHERE username = ?
    DB-->>D: baris user / kosong
    S->>S: PasswordHasher.verify(password, hash)
    alt username/password salah
        S-->>L: AuthenticationException "Username atau password salah."
    else akun INACTIVE
        S-->>L: AuthenticationException "Akun Anda tidak aktif."
    else berhasil
        S->>S: simpan currentUser (session)
        S-->>L: User
        L->>L: DashboardRouter.open(user) sesuai role
    end
```
Logout: `AuthService.logout()` mengosongkan `currentUser`, lalu `LoginFrame` ditampilkan lagi.

## 3. Keamanan
- Password disimpan sebagai `pbkdf2$iterasi$salt$hash`, tidak pernah teks biasa.
- Pesan gagal sama untuk username salah dan password salah (tidak membocorkan keberadaan akun).
- Hak akses dicek di `AuthService.requirePermission()`, bukan hanya dengan menyembunyikan tombol.
- Admin tidak bisa menonaktifkan akunnya sendiri / admin aktif terakhir.

## 4. Matriks hak akses (sesuai PRD bagian 6)
Didefinisikan di `Role.java`. Modul lain cukup memanggil, misalnya:
`AuthService.getInstance().requirePermission(Permission.PROCESS_LOAN);`

## 5. Skenario pengujian
| No | Skenario | Hasil diharapkan | Tes |
|---|---|---|---|
| 1 | Login admin/petugas/anggota benar | Masuk, role sesuai | `loginBerhasilSetiapRole` |
| 2 | Password salah | Ditolak, pesan umum | `passwordSalahDitolak` |
| 3 | Username tidak ada | Ditolak, pesan sama | `usernameTidakAdaPesannyaSamaDenganPasswordSalah` |
| 4 | Akun nonaktif | Ditolak | `akunNonaktifTidakBisaLogin` |
| 5 | Input kosong | Ditolak | `inputKosongDitolak` |
| 6 | Petugas/anggota buka kelola pengguna | `AccessDeniedException` | `petugasDanAnggotaTidakBolehKelolaPengguna` |
| 7 | Logout | Session kosong | `sessionTersimpanLaluHilangSetelahLogout` |
| 8 | Admin buat akun | Password tersimpan hash | `adminMembuatAkunDenganPasswordTerhash` |

Pengujian manual (isi screenshot): login `admin` / `Admin#123`, `petugas1` / `Petugas#123`, `anggota1` / `Anggota#123`, `anggota2` (nonaktif), dan password salah.

## 6. Cara menjalankan
1. Jalankan `sql/auth_schema.sql` di MySQL Workbench.
2. Sesuaikan user/password MySQL di `DatabaseConnection.java`.
3. Jalankan `Main.java`; tes otomatis: `mvn test`.
