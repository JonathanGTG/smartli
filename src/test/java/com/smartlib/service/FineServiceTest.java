package com.smartlib.service;

import com.smartlib.dao.FineDAO;
import com.smartlib.dao.LoanDAO;
import com.smartlib.enums.AccountStatus;
import com.smartlib.enums.LoanStatus;
import com.smartlib.exception.AccessDeniedException;
import com.smartlib.interfaces.FinePolicy;
import com.smartlib.model.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-test kebijakan denda (FinePolicy) dan alur pembayaran.
 * Tidak membutuhkan koneksi MySQL.
 */
class FineServiceTest {

    // ─────────────────────── DAO palsu lokal ───────────────────────

    static class LocalFakeLoanDAO extends LoanDAO {
        final Map<Integer, Loan> data = new LinkedHashMap<>();
        int nextId = 1;

        @Override public int insert(int userId, int copyId, LocalDate borrowDate, LocalDate dueDate) {
            Loan loan = new Loan(nextId, userId, "u" + userId, copyId, "Buku " + copyId,
                borrowDate, dueDate, null, LoanStatus.ACTIVE);
            data.put(nextId, loan);
            return nextId++;
        }
        @Override public Optional<Loan> findById(int id) { return Optional.ofNullable(data.get(id)); }
        @Override public void markReturned(int loanId, LocalDate d) {
            Loan l = data.get(loanId); if (l != null) l.markReturned(d);
        }
        @Override public List<Loan> findActive()  { return new ArrayList<>(data.values()); }
        @Override public List<Loan> findAll()     { return new ArrayList<>(data.values()); }
        @Override public List<Loan> findOverdue() { return new ArrayList<>(); }
        @Override public List<Loan> findByUser(int userId) { return new ArrayList<>(data.values()); }
        @Override public int countActive()  { return data.size(); }
        @Override public int countOverdue() { return 0; }
        @Override public int countTotal()   { return data.size(); }
    }

    static class LocalFakeFineDAO extends FineDAO {
        final Map<Integer, Fine> data = new LinkedHashMap<>();
        int nextId = 1;

        @Override public int insert(int loanId, BigDecimal amount) {
            Fine fine = new Fine(nextId, loanId, "user", "buku", amount, false, null);
            data.put(nextId, fine);
            return nextId++;
        }
        @Override public Optional<Fine> findByLoanId(int loanId) {
            return data.values().stream().filter(f -> f.getLoanId() == loanId).findFirst();
        }
        @Override public void markPaid(int fineId) {
            Fine f = data.get(fineId);
            if (f != null) f.markPaid(LocalDateTime.now());
        }
        @Override public List<Fine> findUnpaid() {
            List<Fine> list = new ArrayList<>();
            data.values().stream().filter(f -> !f.isPaid()).forEach(list::add);
            return list;
        }
        @Override public List<Fine> findAll()          { return new ArrayList<>(data.values()); }
        @Override public List<Fine> findByUser(int id) { return new ArrayList<>(data.values()); }
        @Override public BigDecimal totalUnpaid() {
            return data.values().stream().filter(f -> !f.isPaid())
                .map(Fine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    // ─────────────────────── FinePolicy: kalkulasi denda ───────────────────────

    @Test
    @DisplayName("FinePolicy default: 0 hari = Rp 0")
    void nolHariNolDenda() {
        FinePolicy policy = days -> LoanService.DEFAULT_FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
        assertEquals(BigDecimal.ZERO, policy.calculateFine(0));
    }

    @Test
    @DisplayName("FinePolicy default: 5 hari = Rp 5.000")
    void limaHariLimaRibu() {
        FinePolicy policy = days -> LoanService.DEFAULT_FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
        assertEquals(0, new BigDecimal("5000").compareTo(policy.calculateFine(5)));
    }

    @Test
    @DisplayName("FinePolicy custom: tarif Rp 500 per hari")
    void customPolicyDapatDiganti() {
        FinePolicy cheapPolicy = days -> new BigDecimal("500").multiply(BigDecimal.valueOf(days));
        assertEquals(0, new BigDecimal("1500").compareTo(cheapPolicy.calculateFine(3)));
    }

    // ─────────────────────── Loan.isOverdue ───────────────────────

    @Test
    @DisplayName("Loan terlambat: isOverdue = true dan overdueDays benar")
    void loanTerlambatIsOverdueTrue() {
        Loan late = new Loan(1, 1, "u1", 1, "Buku",
            LocalDate.now().minusDays(10),
            LocalDate.now().minusDays(2), null, LoanStatus.ACTIVE);
        assertTrue(late.isOverdue());
        assertEquals(2, late.getOverdueDays());
    }

    @Test
    @DisplayName("Loan belum jatuh tempo: isOverdue = false")
    void loanBelumJatuhTempoIsOverdueFalse() {
        Loan ok = new Loan(2, 1, "u1", 1, "Buku",
            LocalDate.now().minusDays(2),
            LocalDate.now().plusDays(5), null, LoanStatus.ACTIVE);
        assertFalse(ok.isOverdue());
        assertEquals(0, ok.getOverdueDays());
    }

    @Test
    @DisplayName("Loan yang sudah COMPLETED tidak dihitung terlambat")
    void loanCompletedTidakIsOverdue() {
        Loan done = new Loan(3, 1, "u1", 1, "Buku",
            LocalDate.now().minusDays(10),
            LocalDate.now().minusDays(2),
            LocalDate.now().minusDays(1), LoanStatus.COMPLETED);
        assertFalse(done.isOverdue());
    }

    // ─────────────────────── Pembayaran denda ───────────────────────

    @Test
    @DisplayName("Petugas dapat membayar denda yang belum lunas")
    void petugasBayarDenda() {
        LocalFakeLoanDAO fakeLoan = new LocalFakeLoanDAO();
        LocalFakeFineDAO fakeFine = new LocalFakeFineDAO();
        FinePolicy policy = days -> LoanService.DEFAULT_FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
        LoanService svc = new LoanService(fakeLoan, fakeFine, policy);

        setAuthServiceInstance(loginAsPetugas());

        int fineId = fakeFine.insert(1, new BigDecimal("3000"));
        assertFalse(fakeFine.findByLoanId(1).orElseThrow().isPaid());

        svc.payFine(fineId);
        assertTrue(fakeFine.data.get(fineId).isPaid());
    }

    @Test
    @DisplayName("Anggota tidak bisa memanggil payFine")
    void anggotaTidakBisaBayarDenda() {
        LocalFakeLoanDAO fakeLoan = new LocalFakeLoanDAO();
        LocalFakeFineDAO fakeFine = new LocalFakeFineDAO();
        FinePolicy policy = days -> LoanService.DEFAULT_FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
        LoanService svc = new LoanService(fakeLoan, fakeFine, policy);

        setAuthServiceInstance(loginAsAnggota());

        assertThrows(AccessDeniedException.class, () -> svc.payFine(1));
    }

    // ─────────────────────── Statistik denda ───────────────────────

    @Test
    @DisplayName("totalUnpaid mengembalikan jumlah denda yang belum lunas")
    void totalUnpaidBenar() {
        LocalFakeFineDAO fakeFine = new LocalFakeFineDAO();
        fakeFine.insert(1, new BigDecimal("2000"));
        fakeFine.insert(2, new BigDecimal("3000"));
        fakeFine.markPaid(1); // bayar yang pertama
        assertEquals(0, new BigDecimal("3000").compareTo(fakeFine.totalUnpaid()));
    }

    @Test
    @DisplayName("getUnpaidFines mengembalikan hanya yang belum dibayar")
    void getUnpaidFinesHanyaBelumBayar() {
        LocalFakeLoanDAO fakeLoan = new LocalFakeLoanDAO();
        LocalFakeFineDAO fakeFine = new LocalFakeFineDAO();
        FinePolicy policy = days -> LoanService.DEFAULT_FINE_PER_DAY.multiply(BigDecimal.valueOf(days));
        LoanService svc = new LoanService(fakeLoan, fakeFine, policy);

        setAuthServiceInstance(loginAsPetugas());

        fakeFine.insert(1, new BigDecimal("1000"));
        fakeFine.insert(2, new BigDecimal("2000"));
        fakeFine.markPaid(1);

        var unpaid = svc.getUnpaidFines();
        assertEquals(1, unpaid.size());
        assertEquals(0, new BigDecimal("2000").compareTo(unpaid.get(0).getAmount()));
    }

    // ─────────────────────── helper ───────────────────────

    private AuthService loginAsPetugas() {
        AuthService.FakeUserDAOHelper dao = new AuthService.FakeUserDAOHelper();
        dao.addUser("petugas1", "Petugas1#123", Role.PETUGAS, AccountStatus.ACTIVE);
        AuthService svc = new AuthService(dao.getDAO());
        svc.login("petugas1", "Petugas1#123".toCharArray());
        return svc;
    }

    private AuthService loginAsAnggota() {
        AuthService.FakeUserDAOHelper dao = new AuthService.FakeUserDAOHelper();
        dao.addUser("anggota1", "Anggota1#123", Role.ANGGOTA, AccountStatus.ACTIVE);
        AuthService svc = new AuthService(dao.getDAO());
        svc.login("anggota1", "Anggota1#123".toCharArray());
        return svc;
    }

    private void setAuthServiceInstance(AuthService svc) {
        try {
            var field = AuthService.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, svc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        try {
            var field = AuthService.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
