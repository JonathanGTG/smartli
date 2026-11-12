package com.smartlib.service;

import com.smartlib.dao.ReportDAO;
import com.smartlib.model.Permission;

import java.util.List;
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
}
