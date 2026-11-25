package com.smartlib.model;

import com.smartlib.enums.AccountStatus;

/** Akun pengguna aplikasi. Password hanya disimpan dalam bentuk hash. */
public class User {
    private int id;                 // 0 = belum disimpan ke database
    private String username;
    private String passwordHash;
    private String fullName;
    private Role role;
    private AccountStatus status;
    private String nim;
    private String studyProgram;
    private String email;

    public User(int id, String username, String passwordHash, String fullName,
                Role role, AccountStatus status) {
        this(id, username, passwordHash, fullName, role, status, null, null, null);
    }

    public User(int id, String username, String passwordHash, String fullName,
                Role role, AccountStatus status, String nim, String studyProgram, String email) {
        if (id < 0) throw new IllegalArgumentException("ID tidak boleh negatif.");
        this.id = id;
        setUsername(username);
        setPasswordHash(passwordHash);
        setFullName(fullName);
        setRole(role);
        setStatus(status);
        setNim(nim);
        setStudyProgram(studyProgram);
        setEmail(email);
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public AccountStatus getStatus() { return status; }
    public String getNim() { return nim; }
    public String getStudyProgram() { return studyProgram; }
    public String getEmail() { return email; }

    public boolean isActive() { return status == AccountStatus.ACTIVE; }

    public boolean hasPermission(Permission permission) { return role.can(permission); }

    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username tidak boleh kosong.");
        }
        this.username = username.trim();
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Hash password tidak boleh kosong.");
        }
        this.passwordHash = passwordHash;
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Nama lengkap tidak boleh kosong.");
        }
        this.fullName = fullName.trim();
    }

    public void setRole(Role role) {
        if (role == null) throw new IllegalArgumentException("Role tidak boleh kosong.");
        this.role = role;
    }

    public void setStatus(AccountStatus status) {
        if (status == null) throw new IllegalArgumentException("Status akun tidak boleh kosong.");
        this.status = status;
    }

    public void setNim(String nim) {
        this.nim = (nim == null || nim.isBlank()) ? null : nim.trim();
    }

    public void setStudyProgram(String studyProgram) {
        this.studyProgram = (studyProgram == null || studyProgram.isBlank()) ? null : studyProgram.trim();
    }

    public void setEmail(String email) {
        this.email = (email == null || email.isBlank()) ? null : email.trim().toLowerCase();
    }

    @Override
    public String toString() {   // sengaja tanpa passwordHash
        return "User{id=" + id + ", username='" + username + "', role=" + role + ", status=" + status
                + ", nim='" + nim + "', email='" + email + "'}";
    }
}
