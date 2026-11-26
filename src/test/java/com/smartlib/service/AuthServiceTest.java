package com.smartlib.service;

import com.smartlib.dao.MemberDAO;
import com.smartlib.dao.UserDAO;
import com.smartlib.enums.AccountStatus;
import com.smartlib.exception.AccessDeniedException;
import com.smartlib.exception.AuthenticationException;
import com.smartlib.model.Member;
import com.smartlib.model.Permission;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Pengujian AuthService memakai DAO palsu di memori, jadi tidak membutuhkan MySQL. */
class AuthServiceTest {

    /** DAO palsu: menyimpan user di Map. */
    static class FakeUserDAO extends UserDAO {
        final Map<Integer, User> data = new LinkedHashMap<>();
        int nextId = 1;

        User add(String username, String password, Role role, AccountStatus status) {
            return add(username, password, role, status, null, null, null);
        }

        User add(String username, String password, Role role, AccountStatus status,
                 String nim, String studyProgram, String email) {
            User u = new User(nextId, username, PasswordHasher.hash(password.toCharArray()),
                    username, role, status, nim, studyProgram, email);
            data.put(nextId++, u);
            return u;
        }

        @Override public Optional<User> findByUsername(String n) {
            return data.values().stream().filter(u -> u.getUsername().equalsIgnoreCase(n)).findFirst();
        }
        @Override public Optional<User> findById(int id) { return Optional.ofNullable(data.get(id)); }
        @Override public Optional<User> findByNim(String nim) {
            if (nim == null || nim.isBlank()) return Optional.empty();
            return data.values().stream().filter(u -> nim.equalsIgnoreCase(u.getNim())).findFirst();
        }
        @Override public Optional<User> findByEmail(String email) {
            if (email == null || email.isBlank()) return Optional.empty();
            return data.values().stream().filter(u -> email.equalsIgnoreCase(u.getEmail())).findFirst();
        }
        @Override public boolean existsByUsername(String n) { return findByUsername(n).isPresent(); }
        @Override public boolean existsByNim(String nim) { return findByNim(nim).isPresent(); }
        @Override public boolean existsByEmail(String email) { return findByEmail(email).isPresent(); }

        @Override public List<User> findAll() { return new ArrayList<>(data.values()); }
        @Override public int insert(User u) {
            User saved = new User(nextId, u.getUsername(), u.getPasswordHash(), u.getFullName(),
                    u.getRole(), u.getStatus(), u.getNim(), u.getStudyProgram(), u.getEmail());
            data.put(nextId, saved);
            return nextId++;
        }
        @Override public void update(int id, String name, Role role) {
            User u = data.get(id);
            if (u != null) {
                u.setFullName(name);
                u.setRole(role);
            }
        }
        @Override public void update(int id, String name, Role role, String nim, String studyProgram, String email) {
            User u = data.get(id);
            if (u != null) {
                u.setFullName(name);
                u.setRole(role);
                u.setNim(nim);
                u.setStudyProgram(studyProgram);
                u.setEmail(email);
            }
        }
        @Override public void updateProfile(int id, String name, String studyProgram, String email) {
            User u = data.get(id);
            if (u != null) {
                u.setFullName(name);
                u.setStudyProgram(studyProgram);
                u.setEmail(email);
            }
        }
        @Override public void updateStatus(int id, AccountStatus s) { data.get(id).setStatus(s); }
        @Override public void updatePasswordHash(int id, String h) { data.get(id).setPasswordHash(h); }
        @Override public void updateLastLogin(int id) { }
        @Override public int countActiveAdmins() {
            return (int) data.values().stream()
                    .filter(u -> u.getRole() == Role.ADMIN && u.isActive()).count();
        }
    }

    private FakeUserDAO dao;
    private AuthService service;
    private User admin;

    @BeforeEach
    void setUp() {
        dao = new FakeUserDAO();
        admin = dao.add("admin", "Admin#123", Role.ADMIN, AccountStatus.ACTIVE);
        dao.add("petugas1", "Petugas#123", Role.PETUGAS, AccountStatus.ACTIVE);
        dao.add("anggota1", "Anggota#123", Role.ANGGOTA, AccountStatus.ACTIVE);
        dao.add("nonaktif", "Anggota#123", Role.ANGGOTA, AccountStatus.INACTIVE);
        service = new AuthService(dao);
    }

    // ---------- login berhasil ----------
    @Test
    void loginBerhasilSetiapRole() {
        assertEquals(Role.ADMIN, service.login("admin", "Admin#123".toCharArray()).getRole());
        service.logout();
        assertEquals(Role.PETUGAS, service.login("petugas1", "Petugas#123".toCharArray()).getRole());
        service.logout();
        assertEquals(Role.ANGGOTA, service.login("anggota1", "Anggota#123".toCharArray()).getRole());
    }

    @Test
    void usernameTidakPeduliHurufBesarDanSpasi() {
        assertNotNull(service.login("  ADMIN ", "Admin#123".toCharArray()));
    }

    @Test
    void sessionTersimpanLaluHilangSetelahLogout() {
        service.login("admin", "Admin#123".toCharArray());
        assertTrue(service.isLoggedIn());
        service.logout();
        assertFalse(service.isLoggedIn());
    }

    // ---------- login gagal ----------
    @Test
    void passwordSalahDitolak() {
        AuthenticationException e = assertThrows(AuthenticationException.class,
                () -> service.login("admin", "salah".toCharArray()));
        assertEquals("Username atau password salah.", e.getMessage());
        assertFalse(service.isLoggedIn());
    }

    @Test
    void usernameTidakAdaPesannyaSamaDenganPasswordSalah() {
        AuthenticationException e = assertThrows(AuthenticationException.class,
                () -> service.login("hantu", "apa-saja".toCharArray()));
        assertEquals("Username atau password salah.", e.getMessage());
    }

    @Test
    void akunNonaktifTidakBisaLogin() {
        assertThrows(AuthenticationException.class,
                () -> service.login("nonaktif", "Anggota#123".toCharArray()));
    }

    @Test
    void inputKosongDitolak() {
        assertThrows(AuthenticationException.class, () -> service.login("", "x".toCharArray()));
        assertThrows(AuthenticationException.class, () -> service.login("admin", new char[0]));
        assertThrows(AuthenticationException.class, () -> service.login(null, null));
    }

    // ---------- RBAC ----------
    @Test
    void matriksHakAksesSesuaiPrd() {
        assertTrue(Role.ADMIN.can(Permission.MANAGE_USERS));
        assertTrue(Role.ADMIN.can(Permission.CHANGE_LOAN_RULES));
        assertFalse(Role.PETUGAS.can(Permission.MANAGE_USERS));
        assertFalse(Role.PETUGAS.can(Permission.CHANGE_LOAN_RULES));
        assertTrue(Role.PETUGAS.can(Permission.PROCESS_LOAN));
        assertFalse(Role.ANGGOTA.can(Permission.PROCESS_LOAN));
        assertTrue(Role.ANGGOTA.can(Permission.REQUEST_QUEUE));
        assertFalse(Role.ADMIN.can(Permission.REQUEST_QUEUE));
    }

    @Test
    void operasiTanpaLoginDitolak() {
        assertThrows(AuthenticationException.class, () -> service.listUsers());
    }

    @Test
    void petugasDanAnggotaTidakBolehKelolaPengguna() {
        service.login("petugas1", "Petugas#123".toCharArray());
        assertThrows(AccessDeniedException.class, () -> service.listUsers());
        assertThrows(AccessDeniedException.class,
                () -> service.createUser("userbaru", "User Baru", "Password1".toCharArray(), Role.ANGGOTA));
        service.logout();
        service.login("anggota1", "Anggota#123".toCharArray());
        assertThrows(AccessDeniedException.class, () -> service.setActive(admin.getId(), false));
    }

    // ---------- manajemen akun ----------
    @Test
    void adminMembuatAkunDenganPasswordTerhash() {
        service.login("admin", "Admin#123".toCharArray());
        User baru = service.createUser("Budi_01", "Budi", "Password1".toCharArray(), Role.PETUGAS);
        assertEquals("budi_01", baru.getUsername());
        assertNotEquals("Password1", baru.getPasswordHash());
        assertTrue(PasswordHasher.verify("Password1".toCharArray(), baru.getPasswordHash()));
        service.logout();
        assertEquals(Role.PETUGAS, service.login("budi_01", "Password1".toCharArray()).getRole());
    }

    @Test
    void validasiUsernameDanPassword() {
        service.login("admin", "Admin#123".toCharArray());
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("ab", "X", "Password1".toCharArray(), Role.ANGGOTA));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("user baru", "X", "Password1".toCharArray(), Role.ANGGOTA));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("userbaru", "X", "pendek".toCharArray(), Role.ANGGOTA));
        assertThrows(IllegalArgumentException.class,
                () -> service.createUser("anggota1", "Dobel", "Password1".toCharArray(), Role.ANGGOTA));
    }

    @Test
    void akunYangDinonaktifkanTidakBisaLogin() {
        service.login("admin", "Admin#123".toCharArray());
        int idAnggota = dao.findByUsername("anggota1").orElseThrow().getId();
        service.setActive(idAnggota, false);
        service.logout();
        assertThrows(AuthenticationException.class,
                () -> service.login("anggota1", "Anggota#123".toCharArray()));
    }

    @Test
    void adminTidakBolehMenonaktifkanDiriSendiri() {
        service.login("admin", "Admin#123".toCharArray());
        assertThrows(IllegalStateException.class, () -> service.setActive(admin.getId(), false));
    }

    @Test
    void adminAktifTerakhirTidakBolehDinonaktifkanAtauDiturunkanRole() {
        service.login("admin", "Admin#123".toCharArray());
        User admin2 = service.createUser("admin2", "Admin Dua", "Password1".toCharArray(), Role.ADMIN);
        service.logout();
        service.login("admin2", "Password1".toCharArray());
        service.setActive(admin.getId(), false);                      // masih ada admin2: boleh
        assertThrows(IllegalStateException.class,
                () -> service.updateUser(admin2.getId(), "Admin Dua", Role.PETUGAS)); // tinggal satu admin
    }

    @Test
    void resetPasswordMengubahKredensial() {
        service.login("admin", "Admin#123".toCharArray());
        int id = dao.findByUsername("anggota1").orElseThrow().getId();
        service.resetPassword(id, "PasswordBaru1".toCharArray());
        service.logout();
        assertThrows(AuthenticationException.class,
                () -> service.login("anggota1", "Anggota#123".toCharArray()));
        assertNotNull(service.login("anggota1", "PasswordBaru1".toCharArray()));
    }

    // ---------- Registrasi Mandiri Anggota Perpustakaan ----------

    @Test
    void registrasiAnggotaMandiriBerhasil() {
        User registered = service.registerMember(
                "211401001",
                "Budi Pratama",
                "Teknik Informatika",
                "budi.pratama@smartlib.ac.id",
                "budi_p",
                "Budi#12345".toCharArray()
        );

        assertNotNull(registered);
        assertEquals("budi_p", registered.getUsername());
        assertEquals("Budi Pratama", registered.getFullName());
        assertEquals("211401001", registered.getNim());
        assertEquals("Teknik Informatika", registered.getStudyProgram());
        assertEquals("budi.pratama@smartlib.ac.id", registered.getEmail());
        assertEquals(Role.ANGGOTA, registered.getRole());
        assertEquals(AccountStatus.ACTIVE, registered.getStatus());

        // Login menggunakan akun yang baru terdaftar
        User loggedIn = service.login("budi_p", "Budi#12345".toCharArray());
        assertEquals("Budi Pratama", loggedIn.getFullName());
        assertEquals("211401001", loggedIn.getNim());
    }

    @Test
    void registrasiAnggotaDenganUsernameDefaultDariNim() {
        User registered = service.registerMember(
                "211402002",
                "Siti Rahma",
                "Sistem Informasi",
                "siti@smartlib.ac.id",
                "Siti#12345".toCharArray()
        );

        assertEquals("211402002", registered.getUsername());
        assertEquals("211402002", registered.getNim());

        User loggedIn = service.login("211402002", "Siti#12345".toCharArray());
        assertEquals("Siti Rahma", loggedIn.getFullName());
    }

    @Test
    void registrasiAnggotaNimDuplikatDitolak() {
        service.registerMember("211401001", "Budi Pratama", "Teknik Informatika",
                "budi@smartlib.ac.id", "Budi#12345".toCharArray());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registerMember("211401001", "Budi Lain", "Teknik Informatika",
                        "budi2@smartlib.ac.id", "Budi#12345".toCharArray()));
        assertTrue(ex.getMessage().contains("NIM '211401001' sudah terdaftar"));
    }

    @Test
    void registrasiAnggotaEmailTidakValidAtauDuplikatDitolak() {
        // Email tidak valid formatnya
        assertThrows(IllegalArgumentException.class, () ->
                service.registerMember("211401003", "Nama User", "Teknik Elektro",
                        "email-salah", "Password123".toCharArray()));

        // Email sudah terdaftar
        service.registerMember("211401004", "User A", "Teknik Elektro",
                "usera@smartlib.ac.id", "Password123".toCharArray());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.registerMember("211401005", "User B", "Teknik Elektro",
                        "usera@smartlib.ac.id", "Password123".toCharArray()));
        assertTrue(ex.getMessage().contains("Email 'usera@smartlib.ac.id' sudah terdaftar"));
    }

    @Test
    void registrasiAnggotaValidasiInputLainnya() {
        // NIM kosong
        assertThrows(IllegalArgumentException.class, () ->
                service.registerMember("", "Nama", "Prodi", "email@mail.com", "Password123".toCharArray()));

        // Nama kosong
        assertThrows(IllegalArgumentException.class, () ->
                service.registerMember("211401099", "", "Prodi", "email@mail.com", "Password123".toCharArray()));

        // Prodi kosong
        assertThrows(IllegalArgumentException.class, () ->
                service.registerMember("211401099", "Nama", " ", "email@mail.com", "Password123".toCharArray()));

        // Password kurang dari 8 karakter
        assertThrows(IllegalArgumentException.class, () ->
                service.registerMember("211401099", "Nama", "Prodi", "email@mail.com", "pendek".toCharArray()));
    }

    // ---------- Kelola Akun Saya (Self-Service) ----------

    @Test
    void kelolaAkunPerbaruiProfilSendiri() {
        User registered = service.registerMember("211401010", "Dedi Siregar", "Ilmu Komputer",
                "dedi@smartlib.ac.id", "Dedi#12345".toCharArray());

        service.login(registered.getUsername(), "Dedi#12345".toCharArray());

        // Update profil
        User updated = service.updateMyProfile("Dedi Siregar S.Kom", "Sistem Informasi", "dedi.baru@smartlib.ac.id");
        assertEquals("Dedi Siregar S.Kom", updated.getFullName());
        assertEquals("Sistem Informasi", updated.getStudyProgram());
        assertEquals("dedi.baru@smartlib.ac.id", updated.getEmail());

        // Cek bahwa currentUser dan DAO sama-sama terupdate
        User current = service.getCurrentUser().orElseThrow();
        assertEquals("Dedi Siregar S.Kom", current.getFullName());
        assertEquals("dedi.baru@smartlib.ac.id", current.getEmail());

        User fromDao = dao.findById(registered.getId()).orElseThrow();
        assertEquals("Dedi Siregar S.Kom", fromDao.getFullName());
        assertEquals("dedi.baru@smartlib.ac.id", fromDao.getEmail());
    }

    @Test
    void kelolaAkunGantiPassword() {
        User registered = service.registerMember("211401020", "Rina Marlina", "Akuntansi",
                "rina@smartlib.ac.id", "Rina#12345".toCharArray());

        service.login(registered.getUsername(), "Rina#12345".toCharArray());

        // Password lama salah
        assertThrows(AuthenticationException.class, () ->
                service.changePassword("Salah#123".toCharArray(), "RinaBaru#999".toCharArray()));

        // Password baru terlalu pendek
        assertThrows(IllegalArgumentException.class, () ->
                service.changePassword("Rina#12345".toCharArray(), "pendek".toCharArray()));

        // Ganti password berhasil
        service.changePassword("Rina#12345".toCharArray(), "RinaBaru#999".toCharArray());

        service.logout();

        // Login dengan password lama gagal
        assertThrows(AuthenticationException.class, () ->
                service.login(registered.getUsername(), "Rina#12345".toCharArray()));

        // Login dengan password baru sukses
        User loggedIn = service.login(registered.getUsername(), "RinaBaru#999".toCharArray());
        assertEquals("Rina Marlina", loggedIn.getFullName());
    }

    @Test
    void kelolaAkunTanpaLoginDitolak() {
        service.logout();
        assertThrows(AuthenticationException.class, () ->
                service.updateMyProfile("Nama", "Prodi", "test@smartlib.ac.id"));
        assertThrows(AuthenticationException.class, () ->
                service.changePassword("pass1".toCharArray(), "pass2".toCharArray()));
    }

    @Test
    void adminBuatDanUbahUserDenganDataMahasiswa() {
        service.login("admin", "Admin#123".toCharArray());

        User mhs = service.createUser("mhs_baru", "Mahasiswa Baru", "Password123".toCharArray(),
                Role.ANGGOTA, "211401099", "Manajemen", "mhs@smartlib.ac.id");

        assertEquals("211401099", mhs.getNim());
        assertEquals("Manajemen", mhs.getStudyProgram());
        assertEquals("mhs@smartlib.ac.id", mhs.getEmail());

        // Admin mengubah data mahasiswa
        service.updateUser(mhs.getId(), "Mahasiswa Baru Edit", Role.ANGGOTA,
                "211401099", "Ilmu Komunikasi", "mhs.edit@smartlib.ac.id");

        User updatedMhs = dao.findById(mhs.getId()).orElseThrow();
        assertEquals("Mahasiswa Baru Edit", updatedMhs.getFullName());
        assertEquals("Ilmu Komunikasi", updatedMhs.getStudyProgram());
        assertEquals("mhs.edit@smartlib.ac.id", updatedMhs.getEmail());
    }

    // ---------- Sinkronisasi Status User & Member ----------

    static class FakeMemberDAO extends MemberDAO {
        final Map<Integer, Member> data = new LinkedHashMap<>();
        int nextId = 1;

        Member add(String code, String fullName, AccountStatus status, Integer userId) {
            Member m = new Member(nextId, code, fullName, "", "", "", LocalDate.now(), status, userId);
            data.put(nextId++, m);
            return m;
        }

        @Override public Optional<Member> findById(int id) { return Optional.ofNullable(data.get(id)); }
        @Override public Optional<Member> findByUserId(int userId) {
            return data.values().stream().filter(m -> Objects.equals(m.getUserId(), userId)).findFirst();
        }
        @Override public void updateStatus(int id, AccountStatus status) {
            Member existing = data.get(id);
            if (existing != null) {
                data.put(id, new Member(existing.getId(), existing.getMemberCode(), existing.getFullName(),
                        existing.getEmail(), existing.getPhone(), existing.getAddress(),
                        existing.getJoinDate(), status, existing.getUserId()));
            }
        }
        @Override public int countActiveLoans(int memberId) { return 0; }
        @Override public int countLoans(int memberId) { return 0; }
    }

    @Test
    void nonaktifkanAnggotaIkutMenonaktifkanAkunLoginDanMenolakLogin() {
        FakeMemberDAO memberDAO = new FakeMemberDAO();
        AuthService authWithMember = new AuthService(dao, memberDAO);
        MemberService memberService = new MemberService(authWithMember, memberDAO, dao);

        User anggota = dao.findByUsername("anggota1").orElseThrow();
        Member member = memberDAO.add("AGT-0001", "Anggota Satu", AccountStatus.ACTIVE, anggota.getId());

        authWithMember.login("admin", "Admin#123".toCharArray());
        memberService.setActive(member.getId(), false);
        authWithMember.logout();

        assertEquals(AccountStatus.INACTIVE, memberDAO.findById(member.getId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.INACTIVE, dao.findById(anggota.getId()).orElseThrow().getStatus());
        assertThrows(AuthenticationException.class,
                () -> authWithMember.login("anggota1", "Anggota#123".toCharArray()));

        // Aktifkan kembali -> bisa login lagi
        authWithMember.login("admin", "Admin#123".toCharArray());
        memberService.setActive(member.getId(), true);
        authWithMember.logout();

        assertEquals(AccountStatus.ACTIVE, dao.findById(anggota.getId()).orElseThrow().getStatus());
        assertNotNull(authWithMember.login("anggota1", "Anggota#123".toCharArray()));
    }

    @Test
    void nonaktifkanUserIkutMenonaktifkanMemberDanLoginDitolakJikaMemberNonaktif() {
        FakeMemberDAO memberDAO = new FakeMemberDAO();
        AuthService authWithMember = new AuthService(dao, memberDAO);

        User anggota = dao.findByUsername("anggota1").orElseThrow();
        Member member = memberDAO.add("AGT-0001", "Anggota Satu", AccountStatus.ACTIVE, anggota.getId());

        // Jika di DB member sudah INACTIVE padahal user masih ACTIVE, login tetap ditolak
        memberDAO.updateStatus(member.getId(), AccountStatus.INACTIVE);
        assertThrows(AuthenticationException.class,
                () -> authWithMember.login("anggota1", "Anggota#123".toCharArray()));

        // Aktifkan kembali lalu nonaktifkan dari AuthService (Kelola Pengguna)
        memberDAO.updateStatus(member.getId(), AccountStatus.ACTIVE);
        authWithMember.login("admin", "Admin#123".toCharArray());
        authWithMember.setActive(anggota.getId(), false);
        authWithMember.logout();

        assertEquals(AccountStatus.INACTIVE, memberDAO.findById(member.getId()).orElseThrow().getStatus());
        assertEquals(AccountStatus.INACTIVE, dao.findById(anggota.getId()).orElseThrow().getStatus());
    }
}
