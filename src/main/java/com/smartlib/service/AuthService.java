package com.smartlib.service;

import com.smartlib.dao.UserDAO;
import com.smartlib.enums.AccountStatus;
import com.smartlib.exception.AccessDeniedException;
import com.smartlib.exception.AuthenticationException;
import com.smartlib.model.Permission;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.util.PasswordHasher;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Logika autentikasi, session, RBAC, dan manajemen akun.
 * Hak akses diperiksa di sini (bukan hanya di UI) sesuai PRD bagian 6 dan NFR-02.
 */
public class AuthService {

    private static final Pattern USERNAME_RULE = Pattern.compile("^[a-zA-Z0-9_.]{4,30}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String LOGIN_FAILED = "Username atau password salah.";
    /** Dipakai agar waktu respons sama ketika username tidak ditemukan. */
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-password".toCharArray());

    private static AuthService instance;

    private final UserDAO userDAO;
    private volatile User currentUser;   // session: pengguna yang sedang login

    public AuthService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /** Satu instance bersama agar seluruh modul membaca session yang sama. */
    public static synchronized AuthService getInstance() {
        if (instance == null) instance = new AuthService(new UserDAO());
        return instance;
    }

    // ================= Autentikasi & session =================

    public User login(String username, char[] password) {
        if (username == null || username.isBlank()) {
            throw new AuthenticationException("Username tidak boleh kosong.");
        }
        if (password == null || password.length == 0) {
            throw new AuthenticationException("Password tidak boleh kosong.");
        }

        Optional<User> found = userDAO.findByUsername(username.trim().toLowerCase());
        boolean passwordOk;
        if (found.isPresent()) {
            passwordOk = PasswordHasher.verify(password, found.get().getPasswordHash());
        } else {
            PasswordHasher.verify(password, DUMMY_HASH);
            passwordOk = false;
        }
        if (!passwordOk) throw new AuthenticationException(LOGIN_FAILED);

        User user = found.get();
        if (!user.isActive()) {
            throw new AuthenticationException("Akun Anda tidak aktif. Hubungi Admin.");
        }
        userDAO.updateLastLogin(user.getId());
        currentUser = user;
        return user;
    }

    public void logout() { currentUser = null; }

    public boolean isLoggedIn() { return currentUser != null; }

    public Optional<User> getCurrentUser() { return Optional.ofNullable(currentUser); }

    // ================= RBAC =================

    public User requireLogin() {
        User user = currentUser;
        if (user == null) throw new AuthenticationException("Silakan login terlebih dahulu.");
        return user;
    }

    public User requirePermission(Permission permission) {
        User user = requireLogin();
        if (!user.hasPermission(permission)) {
            throw new AccessDeniedException("Role " + user.getRole().getLabel()
                    + " tidak berhak menjalankan operasi ini.");
        }
        return user;
    }

    public User requireRole(Role... allowed) {
        User user = requireLogin();
        if (!Arrays.asList(allowed).contains(user.getRole())) {
            throw new AccessDeniedException("Role " + user.getRole().getLabel()
                    + " tidak berhak menjalankan operasi ini.");
        }
        return user;
    }

    // ================= Manajemen akun (khusus Admin) =================

    public List<User> listUsers() {
        requirePermission(Permission.MANAGE_USERS);
        return userDAO.findAll();
    }

    public User createUser(String username, String fullName, char[] password, Role role) {
        requirePermission(Permission.MANAGE_USERS);
        String uname = validateUsername(username);
        validatePassword(password);
        if (userDAO.existsByUsername(uname)) {
            throw new IllegalArgumentException("Username '" + uname + "' sudah dipakai.");
        }
        User user = new User(0, uname, PasswordHasher.hash(password), fullName, role, AccountStatus.ACTIVE);
        int id = userDAO.insert(user);
        return new User(id, user.getUsername(), user.getPasswordHash(), user.getFullName(),
                user.getRole(), user.getStatus());
    }

    public void updateUser(int userId, String fullName, Role role) {
        requirePermission(Permission.MANAGE_USERS);
        User target = findTarget(userId);
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Nama lengkap tidak boleh kosong.");
        }
        if (role == null) throw new IllegalArgumentException("Role tidak boleh kosong.");
        if (target.getRole() == Role.ADMIN && role != Role.ADMIN && target.isActive()
                && userDAO.countActiveAdmins() <= 1) {
            throw new IllegalStateException("Tidak dapat mengubah role Admin aktif terakhir.");
        }
        userDAO.update(userId, fullName.trim(), role);
    }

    public void setActive(int userId, boolean active) {
        User actor = requirePermission(Permission.MANAGE_USERS);
        User target = findTarget(userId);
        if (!active) {
            if (actor.getId() == userId) {
                throw new IllegalStateException("Anda tidak dapat menonaktifkan akun sendiri.");
            }
            if (target.getRole() == Role.ADMIN && target.isActive() && userDAO.countActiveAdmins() <= 1) {
                throw new IllegalStateException("Tidak dapat menonaktifkan Admin aktif terakhir.");
            }
        }
        userDAO.updateStatus(userId, active ? AccountStatus.ACTIVE : AccountStatus.INACTIVE);
    }

    public void resetPassword(int userId, char[] newPassword) {
        requirePermission(Permission.MANAGE_USERS);
        findTarget(userId);
        validatePassword(newPassword);
        userDAO.updatePasswordHash(userId, PasswordHasher.hash(newPassword));
    }

    // ================= helper =================

    private User findTarget(int userId) {
        return userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Pengguna tidak ditemukan."));
    }

    private String validateUsername(String username) {
        if (username == null || !USERNAME_RULE.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Username 4-30 karakter, hanya huruf, angka, titik, atau underscore.");
        }
        return username.trim().toLowerCase();
    }

    private void validatePassword(char[] password) {
        if (password == null || password.length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password minimal " + MIN_PASSWORD_LENGTH + " karakter.");
        }
    }
}
