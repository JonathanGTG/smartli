package com.smartlib.model;

import com.smartlib.enums.AccountStatus;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Anggota perpustakaan (tabel members).
 * BUKAN akun login: akun login ada di class User. Anggota boleh dihubungkan
 * ke satu akun lewat userId (opsional, boleh null).
 */
public class Member {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");

    private int id;                 // 0 = belum disimpan ke database
    private String memberCode;
    private String fullName;
    private String email;           // boleh kosong ("")
    private String phone;           // boleh kosong ("")
    private String address;         // boleh kosong ("")
    private LocalDate joinDate;
    private AccountStatus status;
    private Integer userId;         // null = belum punya akun login

    public Member(int id, String memberCode, String fullName, String email, String phone,
                  String address, LocalDate joinDate, AccountStatus status, Integer userId) {
        if (id < 0) throw new IllegalArgumentException("ID tidak boleh negatif.");
        this.id = id;
        setMemberCode(memberCode);
        setFullName(fullName);
        setEmail(email);
        setPhone(phone);
        setAddress(address);
        setJoinDate(joinDate);
        setStatus(status);
        setUserId(userId);
    }

    /** Untuk anggota baru yang belum disimpan (id = 0, status aktif, belum punya akun). */
    public Member(String memberCode, String fullName, String email, String phone,
                  String address, LocalDate joinDate) {
        this(0, memberCode, fullName, email, phone, address, joinDate, AccountStatus.ACTIVE, null);
    }

    public int getId() { return id; }
    public String getMemberCode() { return memberCode; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public LocalDate getJoinDate() { return joinDate; }
    public AccountStatus getStatus() { return status; }
    public Integer getUserId() { return userId; }

    public boolean isActive() { return status == AccountStatus.ACTIVE; }

    public void assignId(int generatedId) {
        if (id != 0) throw new IllegalStateException("ID sudah ditetapkan.");
        if (generatedId <= 0) throw new IllegalArgumentException("ID hasil generate harus positif.");
        this.id = generatedId;
    }

    public final void setMemberCode(String memberCode) {
        if (memberCode == null || memberCode.isBlank())
            throw new IllegalArgumentException("Kode anggota wajib diisi.");
        String code = memberCode.trim().toUpperCase();
        if (code.length() > 20)
            throw new IllegalArgumentException("Kode anggota maksimal 20 karakter.");
        this.memberCode = code;
    }

    public final void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank())
            throw new IllegalArgumentException("Nama anggota wajib diisi.");
        String name = fullName.trim();
        if (name.length() > 100)
            throw new IllegalArgumentException("Nama anggota maksimal 100 karakter.");
        this.fullName = name;
    }

    public final void setEmail(String email) {
        if (email == null || email.isBlank()) {
            this.email = "";
            return;
        }
        String value = email.trim().toLowerCase();
        if (value.length() > 100 || !EMAIL.matcher(value).matches())
            throw new IllegalArgumentException("Format email tidak valid.");
        this.email = value;
    }

    public final void setPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            this.phone = "";
            return;
        }
        String value = phone.trim().replaceAll("[\\s-]", "");
        if (!PHONE.matcher(value).matches())
            throw new IllegalArgumentException("Nomor telepon harus 8-15 digit (boleh diawali +).");
        this.phone = value;
    }

    public final void setAddress(String address) {
        String value = (address == null) ? "" : address.trim();
        if (value.length() > 255)
            throw new IllegalArgumentException("Alamat maksimal 255 karakter.");
        this.address = value;
    }

    public final void setJoinDate(LocalDate joinDate) {
        if (joinDate == null) throw new IllegalArgumentException("Tanggal bergabung wajib diisi.");
        if (joinDate.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Tanggal bergabung tidak boleh di masa depan.");
        this.joinDate = joinDate;
    }

    public final void setStatus(AccountStatus status) {
        if (status == null) throw new IllegalArgumentException("Status anggota wajib diisi.");
        this.status = status;
    }

    public final void setUserId(Integer userId) {
        if (userId != null && userId <= 0)
            throw new IllegalArgumentException("ID akun tidak valid.");
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Member other)) return false;
        return id != 0 && id == other.id;
    }

    @Override
    public int hashCode() { return id != 0 ? Integer.hashCode(id) : System.identityHashCode(this); }

    @Override
    public String toString() { return memberCode + " - " + fullName; }
}