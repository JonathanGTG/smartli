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
 * Unit-test LoanService menggunakan DAO palsu (in-memory).
 * Tidak membutuhkan koneksi MySQL.
 */
class LoanServiceTest {

    // ─────────────────────── DAO palsu ───────────────────────

    static class FakeLoanDAO extends LoanDAO {
        final Map<Integer, Loan> data = new LinkedHashMap<>();
        int nextId = 1;

        @Override public int insert(int userId, int copyId, LocalDate borrowDate, LocalDate dueDate) {
            Loan loan = new Loan(nextId, userId, "u" + userId, copyId, "Buku " + copyId,
                borrowDate, dueDate, null, LoanStatus.ACTIVE);
            data.put(nextId, loan);
            return nextId++;
        }

        @Override public Optional<Loan> findById(int id) { return Optional.ofNullable(data.get(id)); }

        @Override public void markReturned(int loanId, LocalDate returnDate) {
            Loan loan = data.get(loanId);
            if (loan != null) loan.markReturned(returnDate);
        }

        @Override public List<Loan> findActive() {
            List<Loan> list = new ArrayList<>();
            data.values().stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE)
                .forEach(list::add);
            return list;
        }

        @Override public List<Loan> findAll() { return new ArrayList<>(data.values()); }

        @Override public List<Loan> findOverdue() {
            List<Loan> list = new ArrayList<>();
            data.values().stream()
                .filter(l -> l.getStatus() == LoanStatus.ACTIVE && l.getDueDate().isBefore(LocalDate.now()))
                .forEach(list::add);
            return list;
        }

        @Override public List<Loan> findByUser(int userId) {
            List<Loan> list = new ArrayList<>();
            data.values().stream().filter(l -> l.getUserId() == userId).forEach(list::add);
            return list;
        }

        @Override public int countActive()  { return (int) data.values().stream().filter(l -> l.getStatus() == LoanStatus.ACTIVE).count(); }
        @Override public int countOverdue() { return (int) data.values().stream().filter(Loan::isOverdue).count(); }
        @Override public int countTotal()   { return data.size(); }
    }

    static class FakeFineDAO extends FineDAO {
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

    // ─────────────────────── Setup ───────────────────────

    private FakeLoanDAO loanDAO;
    private FakeFineDAO fineDAO;
    private LoanService loanService;

    private static final BigDecimal FINE_PER_DAY = new BigDecimal("1000");
    private static final FinePolicy FINE_POLICY  = days -> FINE_PER_DAY.multiply(BigDecimal.valueOf(days));

    @BeforeEach
    void setUp() {
        loanDAO     = new FakeLoanDAO();
        fineDAO     = new FakeFineDAO();
        loanService = new LoanService(loanDAO, fineDAO, FINE_POLICY);

        setAuthServiceInstance(loginAsPetugas());
    }

    @AfterEach
    void tearDown() {
        resetAuthService();
    }

    // ─────────────────────── Test: borrow ───────────────────────

    @Test
    @DisplayName("Petugas dapat membuat peminjaman baru")
    void petugasDapatBorrow() {
        int loanId = loanService.borrow(1, 10);
        assertTrue(loanId > 0);
        Loan loan = loanDAO.findById(loanId).orElseThrow();
        assertEquals(LoanStatus.ACTIVE, loan.getStatus());
        assertEquals(1,  loan.getUserId());
        assertEquals(10, loan.getCopyId());
    }

    @Test
    @DisplayName("Peminjaman memiliki due_date = hari ini + 7 hari")
    void dueDateAdaTujuhHariDariSekarang() {
        int loanId = loanService.borrow(1, 10);
        Loan loan  = loanDAO.findById(loanId).orElseThrow();
        assertEquals(LocalDate.now().plusDays(7), loan.getDueDate());
    }

    @Test
    @DisplayName("Anggota tidak bisa memanggil borrow")
    void anggotaTidakBisaBorrow() {
        setAuthServiceInstance(loginAsAnggota());
        assertThrows(AccessDeniedException.class, () -> loanService.borrow(1, 10));
    }

    // ─────────────────────── Test: returnBook ───────────────────────

    @Test
    @DisplayName("Pengembalian tepat waktu tidak menghasilkan denda")
    void pengembalianTepatWaktuTanpaDenda() {
        int loanId = loanService.borrow(1, 10);
        Optional<Fine> fine = loanService.returnBook(loanId);
        assertFalse(fine.isPresent());
        Loan loan = loanDAO.findById(loanId).orElseThrow();
        assertEquals(LoanStatus.COMPLETED, loan.getStatus());
    }

    @Test
    @DisplayName("Pengembalian terlambat menghasilkan denda yang benar")
    void pengembalianTerlambatHasilkanDenda() {
        LocalDate past    = LocalDate.now().minusDays(3);
        LocalDate dueDate = LocalDate.now().minusDays(1); // terlambat 1 hari
        Loan overdueLoan  = new Loan(99, 1, "anggota1", 10, "Clean Code",
            past, dueDate, null, LoanStatus.ACTIVE);
        loanDAO.data.put(99, overdueLoan);

        Optional<Fine> fine = loanService.returnBook(99);
        assertTrue(fine.isPresent());
        assertEquals(0, new BigDecimal("1000").compareTo(fine.get().getAmount()));
    }

    @Test
    @DisplayName("Tidak bisa mengembalikan peminjaman yang sudah selesai")
    void tidakBisaKembalikanYangSudahSelesai() {
        int loanId = loanService.borrow(1, 10);
        loanService.returnBook(loanId);
        assertThrows(IllegalStateException.class, () -> loanService.returnBook(loanId));
    }

    @Test
    @DisplayName("Peminjaman tidak ditemukan lempar IllegalArgumentException")
    void loanTidakDitemukanLemparException() {
        assertThrows(IllegalArgumentException.class, () -> loanService.returnBook(999));
    }

    // ─────────────────────── Test: query ───────────────────────

    @Test
    @DisplayName("getActiveLoans mengembalikan hanya yang ACTIVE")
    void getActiveLoansCumaActive() {
        int id1 = loanService.borrow(1, 10);
        int id2 = loanService.borrow(2, 11);
        loanService.returnBook(id1);

        List<Loan> active = loanService.getActiveLoans();
        assertEquals(1, active.size());
        assertEquals(id2, active.get(0).getId());
    }

    @Test
    @DisplayName("getOverdueLoans mengembalikan yang sudah lewat jatuh tempo")
    void getOverdueLoansMengembalikanYangTerlambat() {
        Loan late = new Loan(50, 1, "u1", 5, "Buku X",
            LocalDate.now().minusDays(10),
            LocalDate.now().minusDays(3), null, LoanStatus.ACTIVE);
        loanDAO.data.put(50, late);

        List<Loan> overdue = loanService.getOverdueLoans();
        assertEquals(1, overdue.size());
        assertEquals(50, overdue.get(0).getId());
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

    private void resetAuthService() {
        try {
            var field = AuthService.class.getDeclaredField("instance");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
