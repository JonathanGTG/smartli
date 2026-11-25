package com.smartlib.service;

import com.smartlib.dao.MemberDAO;
import com.smartlib.enums.AccountStatus;
import com.smartlib.model.Member;
import com.smartlib.model.Permission;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Aturan bisnis untuk data anggota perpustakaan (hanya role dengan MANAGE_MEMBERS: Admin dan Petugas).
 * Anggota BUKAN akun login. Satu anggota boleh dihubungkan ke satu akun login ber-role ANGGOTA
 * lewat username (opsional); hubungan inilah yang dipakai untuk menelusuri riwayat peminjaman.
 */
public class MemberService {

    /** Hasil penghapusan: dihapus permanen, atau hanya dinonaktifkan karena punya riwayat peminjaman. */
    public enum DeleteResult { DELETED, DEACTIVATED }

    private final AuthService authService;
    private final MemberDAO memberDAO;

    public MemberService() {
        this(AuthService.getInstance(), new MemberDAO());
    }

    public MemberService(AuthService authService, MemberDAO memberDAO) {
        this.authService = authService;
        this.memberDAO = memberDAO;

    }


    // ---------- membaca ----------

    /** Daftar anggota. keyword kosong = semua; status null = semua status. */
    public List<Member> search(String keyword, AccountStatus status) {
        authService.requirePermission(Permission.MANAGE_MEMBERS);
        return memberDAO.search(keyword, status);
    }

    public Optional<Member> getMemberByUserId(int userId) {
        return memberDAO.findByUserId(userId);
    }

    public Map<Integer, String> linkedUsernames() {
        authService.requirePermission(Permission.MANAGE_MEMBERS);
        return memberDAO.findLinkedUsernames();
    }

    public String suggestMemberCode() {
        authService.requirePermission(Permission.MANAGE_MEMBERS);
        return memberDAO.nextMemberCode();
    }

    /** Mengubah teks tanggal dari form (yyyy-MM-dd). Kosong = hari ini. */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) return LocalDate.now();
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Tanggal bergabung harus berformat yyyy-MM-dd, misalnya 2026-11-12.");
        }
    }

    // ---------- menulis ----------

    public Member addMember(String memberCode, String fullName, String email, String phone,
                            String address, LocalDate joinDate, String username) {
        authService.requirePermission(Permission.MANAGE_MEMBERS);
        String code = (memberCode == null || memberCode.isBlank()) ? memberDAO.nextMemberCode() : memberCode;
        Member member = new Member(code, fullName, email, phone, address, joinDate);
        member.setUserId(resolveUserId(username));
        checkDuplicates(member);
        return memberDAO.insert(member);
    }

    public void updateMember(int id, String memberCode, String fullName, String email, String phone,
                             String address, LocalDate joinDate, String username) {
        authService.requirePermission(Permission.MANAGE_MEMBERS);
        Member existing = memberDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anggota tidak ditemukan."));
        Member member = new Member(id, memberCode, fullName, email, phone, address, joinDate,
                existing.getStatus(), resolveUserId(username));
        checkDuplicates(member);
        memberDAO.update(member);
    }

    /** Mengaktifkan atau menonaktifkan anggota. Anggota yang masih meminjam tidak boleh dinonaktifkan. */
    public void setActive(int id, boolean active) {
        authService.requirePermission(Permission.MANAGE_MEMBERS);
        Member member = memberDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anggota tidak ditemukan."));
        if (!active) requireNoActiveLoans(member);
        memberDAO.updateStatus(id, active ? AccountStatus.ACTIVE : AccountStatus.INACTIVE);
    }

    /**
     * Menghapus anggota dengan mempertimbangkan transaksi peminjaman:
     *  - masih ada pinjaman aktif      -> ditolak
     *  - punya riwayat peminjaman      -> hanya dinonaktifkan (riwayat tetap utuh)
     *  - tidak pernah meminjam         -> dihapus permanen
     */
    public DeleteResult deleteMember(int id) {
        authService.requirePermission(Permission.MANAGE_MEMBERS);
        Member member = memberDAO.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anggota tidak ditemukan."));
        requireNoActiveLoans(member);
        if (memberDAO.countLoans(id) > 0) {
            memberDAO.updateStatus(id, AccountStatus.INACTIVE);
            return DeleteResult.DEACTIVATED;
        }
        memberDAO.delete(id);
        return DeleteResult.DELETED;
    }

    // ---------- helper ----------

    private void requireNoActiveLoans(Member member) {
        int active = memberDAO.countActiveLoans(member.getId());
        if (active > 0)
            throw new IllegalStateException(member.getFullName() + " masih memiliki " + active
                    + " peminjaman aktif. Selesaikan pengembalian terlebih dahulu.");
    }

    private Integer resolveUserId(String username) {
        if (username == null || username.isBlank()) return null;
        return memberDAO.findAnggotaUserId(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Akun \"" + username.trim() + "\" tidak ditemukan atau bukan akun ber-role Anggota."));
    }

    private void checkDuplicates(Member m) {
        if (memberDAO.existsByCode(m.getMemberCode(), m.getId()))
            throw new IllegalStateException("Kode anggota " + m.getMemberCode() + " sudah dipakai anggota lain.");
        if (!m.getEmail().isEmpty() && memberDAO.existsByEmail(m.getEmail(), m.getId()))
            throw new IllegalStateException("Email " + m.getEmail() + " sudah terdaftar pada anggota lain.");
        if (!m.getPhone().isEmpty() && memberDAO.existsByPhone(m.getPhone(), m.getId()))
            throw new IllegalStateException("Nomor telepon " + m.getPhone() + " sudah terdaftar pada anggota lain.");
        if (m.getUserId() != null && memberDAO.existsByUserId(m.getUserId(), m.getId()))
            throw new IllegalStateException("Akun login tersebut sudah dihubungkan ke anggota lain.");
    }
}