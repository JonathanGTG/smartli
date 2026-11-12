package com.smartlib.service;

import com.smartlib.dao.UserDAO;
import com.smartlib.enums.AccountStatus;
import com.smartlib.exception.AccessDeniedException;
import com.smartlib.exception.AuthenticationException;
import com.smartlib.model.Permission;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Pengujian AuthService memakai DAO palsu di memori, jadi tidak membutuhkan MySQL. */
class AuthServiceTest {

    /** DAO palsu: menyimpan user di Map. */
    static class FakeUserDAO extends UserDAO {
        final Map<Integer, User> data = new LinkedHashMap<>();
        int nextId = 1;

        User add(String username, String password, Role role, AccountStatus status) {
            User u = new User(nextId, username, PasswordHasher.hash(password.toCharArray()),
                    username, role, status);
            data.put(nextId++, u);
            return u;
        }

        @Override public Optional<User> findByUsername(String n) {
            return data.values().stream().filter(u -> u.getUsername().equals(n)).findFirst();
        }
        @Override public Optional<User> findById(int id) { return Optional.ofNullable(data.get(id)); }
        @Override public List<User> findAll() { return new ArrayList<>(data.values()); }
        @Override public int insert(User u) {
            User saved = new User(nextId, u.getUsername(), u.getPasswordHash(), u.getFullName(), u.getRole(), u.getStatus());
            data.put(nextId, saved);
            return nextId++;
        }
        @Override public void update(int id, String name, Role role) {
            User u = data.get(id); u.setFullName(name); u.setRole(role);
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
}
