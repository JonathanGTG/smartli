package com.smartlib.service;

import com.smartlib.dao.ReportDAO;
import com.smartlib.model.Permission;
import com.smartlib.policy.FinePolicy;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Layanan laporan & statistik dashboard.
 * Semua metode publik mengandung pengecekan hak akses sebelum mengambil data.
 */
public class ReportService {

    private static ReportService instance;

    private final ReportDAO reportDAO;

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    public static synchronized ReportService getInstance() {
        if (instance == null) instance = new ReportService(new ReportDAO());
        return instance;
    }

    // ─────────────────────── Statistik Utama (semua role yang VIEW_REPORTS) ───

    public DashboardStats getDashboardStats() {
        AuthService.getInstance().requirePermission(Permission.VIEW_REPORTS);
        return new DashboardStats(
            reportDAO.countTotalItems(),
            reportDAO.countTotalCopies(),
            reportDAO.countAvailableCopies(),
            reportDAO.countTotalMembers(),
            reportDAO.countActiveLoans(),
            reportDAO.countOverdueLoans()
        );
    }

    // ─────────────────────── Laporan ─────────────────────────────────────────

    /** Top-N buku paling sering dipinjam. */
    public List<Object[]> mostBorrowedBooks(int topN) {
        AuthService.getInstance().requirePermission(Permission.VIEW_REPORTS);
        return reportDAO.mostBorrowedBooksReport(topN);
    }

    /** Daftar peminjaman yang terlambat. */
    public List<Object[]> overdueLoans() {
        AuthService.getInstance().requirePermission(Permission.VIEW_REPORTS);
        return reportDAO.overdueLoansReport();
    }

    /** Aktivitas per anggota. */
    public List<Object[]> memberActivity() {
        AuthService.getInstance().requirePermission(Permission.VIEW_REPORTS);
        return reportDAO.memberActivityReport();
    }

    /** Peminjaman per bulan dalam N bulan terakhir. */
    public Map<String, Integer> loansByMonth(int lastNMonths) {
        AuthService.getInstance().requirePermission(Permission.VIEW_REPORTS);
        return reportDAO.loansByMonth(lastNMonths);
    }

    // ─────────────────────── Ringkasan Pribadi Anggota ───────────────────────

    /** Ringkasan pinjaman, antrean, dan total denda belum dibayar (termasuk denda berjalan) milik anggota. */
    public MemberStats getMemberStats(int memberId) {
        AuthService.getInstance().requirePermission(Permission.VIEW_OWN_HISTORY);
        BigDecimal totalUnpaid = reportDAO.sumMemberUnpaidFines(memberId);
        FinePolicy policy = LoanService.getFinePolicy();
        LocalDate today = LocalDate.now();
        for (LocalDate dueDate : reportDAO.findMemberOverdueDueDates(memberId)) {
            int overdueDays = policy.overdueDays(dueDate, today);
            if (overdueDays > 0) {
                totalUnpaid = totalUnpaid.add(policy.calculateFine(overdueDays));
            }
        }
        return new MemberStats(
            reportDAO.countMemberActiveLoans(memberId),
            reportDAO.countMemberQueue(memberId),
            reportDAO.countMemberOffered(memberId),
            totalUnpaid
        );
    }

    /** Pinjaman berjalan milik anggota: [judul, jatuh_tempo, status (dengan info denda berjalan jika terlambat)]. */
    public List<Object[]> memberActiveLoans(int memberId) {
        AuthService.getInstance().requirePermission(Permission.VIEW_OWN_HISTORY);
        List<Object[]> rows = reportDAO.memberActiveLoansReport(memberId);
        FinePolicy policy = LoanService.getFinePolicy();
        LocalDate today = LocalDate.now();
        NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));

        for (Object[] row : rows) {
            if (row[1] instanceof java.sql.Date sqlDate) {
                LocalDate dueDate = sqlDate.toLocalDate();
                if (dueDate.isBefore(today)) {
                    long rawDays = ChronoUnit.DAYS.between(dueDate, today);
                    int billableDays = policy.overdueDays(dueDate, today);
                    if (billableDays > 0) {
                        BigDecimal fine = policy.calculateFine(billableDays);
                        row[2] = "Terlambat " + rawDays + " hari (Denda berjalan: " + currency.format(fine) + ")";
                    } else {
                        row[2] = "Terlambat " + rawDays + " hari (Masa tenggang)";
                    }
                }
            }
        }
        return rows;
    }

    // ─────────────────────── Inner DTO ───────────────────────────────────────

    /** Objek data ringkasan statistik untuk dashboard. */
    public static final class DashboardStats {
        public final int totalItems;
        public final int totalCopies;
        public final int availableCopies;
        public final int totalMembers;
        public final int activeLoans;
        public final int overdueLoans;

        DashboardStats(int totalItems, int totalCopies, int availableCopies,
                       int totalMembers, int activeLoans, int overdueLoans) {
            this.totalItems      = totalItems;
            this.totalCopies     = totalCopies;
            this.availableCopies = availableCopies;
            this.totalMembers    = totalMembers;
            this.activeLoans     = activeLoans;
            this.overdueLoans    = overdueLoans;
        }
    }

    /** Ringkasan pribadi anggota untuk dashboard Anggota. */
    public static final class MemberStats {
        public final int activeLoans;
        public final int queued;
        public final int readyToPickup;
        public final java.math.BigDecimal unpaidFines;

        MemberStats(int activeLoans, int queued, int readyToPickup, java.math.BigDecimal unpaidFines) {
            this.activeLoans   = activeLoans;
            this.queued        = queued;
            this.readyToPickup = readyToPickup;
            this.unpaidFines   = unpaidFines;
        }
    }
}
