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
    private static final Pattern NIM_RULE = Pattern.compile("^[a-zA-Z0-9.-]{4,30}$");
    private static final Pattern EMAIL_RULE = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
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

    /** Hanya untuk pengujian: mengganti instance bersama (mis. dengan DAO palsu). */
    static synchronized void setInstanceForTesting(AuthService replacement) { instance = replacement; }

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

    // ================= Registrasi Anggota Mandiri =================

    public User registerMember(String nim, String fullName, String studyProgram, String email,
                               String username, char[] password) {
        String cleanNim = validateNim(nim);
        if (userDAO.existsByNim(cleanNim)) {
            throw new IllegalArgumentException("NIM '" + cleanNim + "' sudah terdaftar.");
        }

        if (fullName == null || fullName.trim().isBlank()) {
            throw new IllegalArgumentException("Nama lengkap tidak boleh kosong.");
        }
        if (fullName.trim().length() < 2) {
            throw new IllegalArgumentException("Nama lengkap minimal 2 karakter.");
        }

        if (studyProgram == null || studyProgram.trim().isBlank()) {
            throw new IllegalArgumentException("Program studi tidak boleh kosong.");
        }

        String cleanEmail = validateEmail(email);
        if (userDAO.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("Email '" + cleanEmail + "' sudah terdaftar.");
        }

        String rawUsername = (username == null || username.isBlank()) ? cleanNim : username.trim();
        String uname = validateUsername(rawUsername);
        if (userDAO.existsByUsername(uname)) {
            throw new IllegalArgumentException("Username '" + uname + "' sudah dipakai.");
        }

        validatePassword(password);

        User newUser = new User(0, uname, PasswordHasher.hash(password), fullName.trim(),
                Role.ANGGOTA, AccountStatus.ACTIVE, cleanNim, studyProgram.trim(), cleanEmail);
        int id = userDAO.insert(newUser);
// OTOMATIS DAFTARKAN JUGA KE TABEL MEMBERS:
        try {
            com.smartlib.dao.MemberDAO memberDAO = new com.smartlib.dao.MemberDAO();
            String memberCode = memberDAO.nextMemberCode(); // menghasilkan otomatis misal: AGT-0005
            com.smartlib.model.Member newMember = new com.smartlib.model.Member(
                    0,
                    memberCode,
                    fullName.trim(),
                    cleanEmail,
                    null,                  // telepon (bisa diisi nanti oleh anggota)
                    studyProgram.trim(),   // alamat / program studi
                    java.time.LocalDate.now(),
                    AccountStatus.ACTIVE,
                    id                     // foreign key: user_id terhubung!
            );
            memberDAO.insert(newMember);
        } catch (Exception e) {
            System.err.println("Peringatan: Gagal membuat profil member otomatis: " + e.getMessage());
        }

        return new User(id, newUser.getUsername(), newUser.getPasswordHash(), newUser.getFullName(),
                newUser.getRole(), newUser.getStatus(), newUser.getNim(), newUser.getStudyProgram(), newUser.getEmail());
    public User registerMember(String nim, String fullName, String studyProgram, String email, char[] password) {
        return registerMember(nim, fullName, studyProgram, email, null, password);
    }

    // ================= Kelola Akun Saya (Self-Service) =================

    public User updateMyProfile(String fullName, String studyProgram, String email) {
        User user = requireLogin();
        if (fullName == null || fullName.trim().isBlank()) {
            throw new IllegalArgumentException("Nama lengkap tidak boleh kosong.");
        }
        if (fullName.trim().length() < 2) {
            throw new IllegalArgumentException("Nama lengkap minimal 2 karakter.");
        }

        String cleanProdi = (studyProgram == null || studyProgram.trim().isBlank())
                ? user.getStudyProgram() : studyProgram.trim();

        String cleanEmail = validateEmail(email);
        Optional<User> existingEmailUser = userDAO.findByEmail(cleanEmail);
        if (existingEmailUser.isPresent() && existingEmailUser.get().getId() != user.getId()) {
            throw new IllegalArgumentException("Email '" + cleanEmail + "' sudah digunakan oleh akun lain.");
        }

        userDAO.updateProfile(user.getId(), fullName.trim(), cleanProdi, cleanEmail);
        user.setFullName(fullName.trim());
        user.setStudyProgram(cleanProdi);
        user.setEmail(cleanEmail);
        return user;
    }

    public void changePassword(char[] oldPassword, char[] newPassword) {
        User user = requireLogin();
        if (oldPassword == null || oldPassword.length == 0) {
            throw new AuthenticationException("Password saat ini tidak boleh kosong.");
        }
        if (!PasswordHasher.verify(oldPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Password saat ini salah.");
        }
        validatePassword(newPassword);
        String newHash = PasswordHasher.hash(newPassword);
        userDAO.updatePasswordHash(user.getId(), newHash);
        user.setPasswordHash(newHash);
    }

    // ================= Manajemen akun (khusus Admin) =================

    public List<User> listUsers() {
        requirePermission(Permission.MANAGE_USERS);
        return userDAO.findAll();
    }

    public User createUser(String username, String fullName, char[] password, Role role,
                           String nim, String studyProgram, String email) {
        requirePermission(Permission.MANAGE_USERS);
        String uname = validateUsername(username);
        validatePassword(password);
        if (userDAO.existsByUsername(uname)) {
            throw new IllegalArgumentException("Username '" + uname + "' sudah dipakai.");
        }

        String cleanNim = (nim != null && !nim.isBlank()) ? validateNim(nim) : null;
        if (cleanNim != null && userDAO.existsByNim(cleanNim)) {
            throw new IllegalArgumentException("NIM '" + cleanNim + "' sudah terdaftar.");
        }

        String cleanEmail = (email != null && !email.isBlank()) ? validateEmail(email) : null;
        if (cleanEmail != null && userDAO.existsByEmail(cleanEmail)) {
            throw new IllegalArgumentException("Email '" + cleanEmail + "' sudah terdaftar.");
        }

        String cleanProdi = (studyProgram != null && !studyProgram.isBlank()) ? studyProgram.trim() : null;

        User user = new User(0, uname, PasswordHasher.hash(password), fullName.trim(), role,
                AccountStatus.ACTIVE, cleanNim, cleanProdi, cleanEmail);
        int id = userDAO.insert(user);
        return new User(id, user.getUsername(), user.getPasswordHash(), user.getFullName(),
                user.getRole(), user.getStatus(), user.getNim(), user.getStudyProgram(), user.getEmail());
    }

    public User createUser(String username, String fullName, char[] password, Role role) {
        return createUser(username, fullName, password, role, null, null, null);
    }

    public void updateUser(int userId, String fullName, Role role,
                           String nim, String studyProgram, String email) {
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

        String cleanNim = (nim != null && !nim.isBlank()) ? validateNim(nim) : null;
        if (cleanNim != null) {
            Optional<User> foundNim = userDAO.findByNim(cleanNim);
            if (foundNim.isPresent() && foundNim.get().getId() != userId) {
                throw new IllegalArgumentException("NIM '" + cleanNim + "' sudah digunakan oleh akun lain.");
            }
        }

        String cleanEmail = (email != null && !email.isBlank()) ? validateEmail(email) : null;
        if (cleanEmail != null) {
            Optional<User> foundEmail = userDAO.findByEmail(cleanEmail);
            if (foundEmail.isPresent() && foundEmail.get().getId() != userId) {
                throw new IllegalArgumentException("Email '" + cleanEmail + "' sudah digunakan oleh akun lain.");
            }
        }

        String cleanProdi = (studyProgram != null && !studyProgram.isBlank()) ? studyProgram.trim() : null;

        userDAO.update(userId, fullName.trim(), role, cleanNim, cleanProdi, cleanEmail);
        if (currentUser != null && currentUser.getId() == userId) {
            currentUser.setFullName(fullName.trim());
            currentUser.setRole(role);
            currentUser.setNim(cleanNim);
            currentUser.setStudyProgram(cleanProdi);
            currentUser.setEmail(cleanEmail);
        }
    }

    public void updateUser(int userId, String fullName, Role role) {
        updateUser(userId, fullName, role, null, null, null);
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

    private String validateNim(String nim) {
        if (nim == null || !NIM_RULE.matcher(nim.trim()).matches()) {
            throw new IllegalArgumentException("NIM minimal 4 karakter (hanya huruf, angka, titik, atau strip).");
        }
        return nim.trim();
    }

    private String validateEmail(String email) {
        if (email == null || !EMAIL_RULE.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Format email tidak valid.");
        }
        return email.trim().toLowerCase();
    }
}
