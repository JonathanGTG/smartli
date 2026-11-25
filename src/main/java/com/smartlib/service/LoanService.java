package com.smartlib.service;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.model.Permission;
import com.smartlib.dao.FineDAO;
import com.smartlib.dao.LoanDAO;
import com.smartlib.dao.ReservationDAO;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.BorrowReceipt;
import com.smartlib.model.ReturnReceipt;
import com.smartlib.policy.DailyFinePolicy;
import com.smartlib.policy.FinePolicy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/** Logika transaksi peminjaman dan pengembalian. */
public class LoanService {
    // Variabel aturan dinamis (dapat diubah oleh Admin lewat aplikasi)
    private static final int MAX_ACTIVE_BOOKS = 3;
    private static final int LOAN_PERIOD_DAYS = 7;
    private static final BigDecimal DAILY_FINE_RATE = new BigDecimal("2000.00");
    private static int maxActiveBooks = MAX_ACTIVE_BOOKS;
    private static int loanPeriodDays = LOAN_PERIOD_DAYS;
    private static BigDecimal dailyFineRate = DAILY_FINE_RATE;
    private static FinePolicy finePolicy = new DailyFinePolicy(dailyFineRate);
    public static int getMaxActiveBooks() { return maxActiveBooks; }
    public static void setMaxActiveBooks(int max) { maxActiveBooks = max; }
    public static int getLoanPeriodDays() { return loanPeriodDays; }
    public static void setLoanPeriodDays(int days) { loanPeriodDays = days; }
    public static BigDecimal getDailyFineRate() { return dailyFineRate; }
    public static void setDailyFineRate(BigDecimal rate) {
        dailyFineRate = rate;
        finePolicy = new DailyFinePolicy(rate);
    }
    public static FinePolicy getFinePolicy() { return finePolicy; }
    public static void setFinePolicy(FinePolicy policy) { finePolicy = policy; }

    public LoanService() {
    }

    public LoanService(FinePolicy policy) {
        if (policy != null) finePolicy = policy;
    }

    public BorrowReceipt borrow(long memberId, long copyId, long processedByUserId)
            throws SQLException, BusinessRuleException {
        AuthService.getInstance().requirePermission(Permission.PROCESS_LOAN);
        return createBorrow(memberId, copyId, processedByUserId, "AVAILABLE");
    }

    /** Memproses penawaran antrean yang diterima oleh anggota yang berhak. */
    public BorrowReceipt borrowReserved(long memberId, long reservationId, long processedByUserId)
            throws SQLException, BusinessRuleException {
        AuthService.getInstance().requirePermission(Permission.PROCESS_LOAN);
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ReservationDAO.ReservationData reservation =
                        ReservationDAO.lockReservation(connection, reservationId);
                if (reservation == null) throw new BusinessRuleException("Data antrean tidak ditemukan.");
                if (reservation.memberId() != memberId) {
                    throw new BusinessRuleException("Penawaran ini bukan milik anggota tersebut.");
                }
                if (!"OFFERED".equalsIgnoreCase(reservation.status())) {
                    throw new BusinessRuleException("Antrean tidak berada pada status penawaran.");
                }
                if (reservation.expiresAt() == null || reservation.expiresAt().toLocalDateTime().isBefore(java.time.LocalDateTime.now())) {
                    throw new BusinessRuleException("Masa penawaran buku sudah berakhir.");
                }
                if (reservation.offeredCopyId() == null) {
                    throw new BusinessRuleException("Eksemplar yang ditawarkan tidak ditemukan.");
                }

                BorrowReceipt receipt = createBorrowWithinTransaction(connection, memberId,
                        reservation.offeredCopyId(), processedByUserId, "RESERVED");
                ReservationDAO.markFulfilled(connection, reservationId);
                connection.commit();
                return new BorrowReceipt(receipt.loanId(), receipt.loanItemId(), receipt.dueDate(), true);
            } catch (Exception ex) {
                rollback(connection, ex);
                if (ex instanceof BusinessRuleException bre) throw bre;
                if (ex instanceof SQLException sqle) throw sqle;
                throw new SQLException("Gagal memproses penawaran antrean.", ex);
            }
        }
    }

    private BorrowReceipt createBorrow(long memberId, long copyId, long processedByUserId,
                                       String expectedCopyStatus)
            throws SQLException, BusinessRuleException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BorrowReceipt receipt = createBorrowWithinTransaction(connection, memberId,
                        copyId, processedByUserId, expectedCopyStatus);
                connection.commit();
                return receipt;
            } catch (Exception ex) {
                rollback(connection, ex);
                if (ex instanceof BusinessRuleException bre) throw bre;
                if (ex instanceof SQLException sqle) throw sqle;
                throw new SQLException("Gagal menyimpan transaksi peminjaman.", ex);
            }
        }
    }

    private BorrowReceipt createBorrowWithinTransaction(Connection connection, long memberId,
                                                          long copyId, long processedByUserId,
                                                          String expectedCopyStatus)
            throws SQLException, BusinessRuleException {
        LoanDAO.MemberData processor = LoanDAO.validateProcessor(connection, processedByUserId);
        if (!processor.active()) throw new BusinessRuleException(processor.message());

        LoanDAO.MemberData member = LoanDAO.lockAndValidateMember(connection, memberId);
        if (!member.active()) throw new BusinessRuleException(member.message());

        int activeLoans = LoanDAO.countActiveLoans(connection, memberId);
        if (activeLoans >= MAX_ACTIVE_BOOKS) {
            throw new BusinessRuleException("Batas peminjaman tercapai. Maksimal " + MAX_ACTIVE_BOOKS + " eksemplar aktif.");
        }
        if (LoanDAO.hasUnpaidFine(connection, memberId)) {
            throw new BusinessRuleException("Anggota masih memiliki denda yang belum dibayar.");
        }

        LoanDAO.CopyData copy = LoanDAO.lockCopy(connection, copyId);
        if (copy == null) throw new BusinessRuleException("Eksemplar buku tidak ditemukan.");
        if (!expectedCopyStatus.equalsIgnoreCase(copy.status())) {
            throw new BusinessRuleException("Eksemplar tidak tersedia untuk dipinjam. Status saat ini: " + copy.status());
        }
        if ("AVAILABLE".equalsIgnoreCase(expectedCopyStatus)
                && ReservationDAO.hasWaitingReservation(connection, copy.itemId())) {
            throw new BusinessRuleException("Masih ada anggota dalam antrean untuk buku ini. Pemrosesan harus mengikuti urutan FIFO.");
        }

        LocalDate borrowedDate = LocalDate.now();
        LocalDate dueDate = borrowedDate.plusDays(LOAN_PERIOD_DAYS);
        long loanId = LoanDAO.insertLoan(connection, memberId, processedByUserId);
        long loanItemId = LoanDAO.insertLoanItem(connection, loanId, copyId, dueDate);
        if (!LoanDAO.changeCopyStatus(connection, copyId, expectedCopyStatus, "BORROWED")) {
            throw new BusinessRuleException("Stok berubah saat transaksi diproses. Silakan ulangi.");
        }
        return new BorrowReceipt(loanId, loanItemId, dueDate, "RESERVED".equalsIgnoreCase(expectedCopyStatus));
    }

    public ReturnReceipt returnBook(long loanItemId) throws SQLException, BusinessRuleException {
        AuthService.getInstance().requirePermission(Permission.PROCESS_RETURN);
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LoanDAO.LoanItemData item = LoanDAO.lockLoanItemForReturn(connection, loanItemId);
                if (item == null) throw new BusinessRuleException("Detail peminjaman tidak ditemukan.");
                if (!"BORROWED".equalsIgnoreCase(item.status())) {
                    throw new BusinessRuleException("Buku ini sudah dikembalikan atau status pinjam tidak aktif.");
                }

                LocalDate returnDate = LocalDate.now();
                int overdueDays = finePolicy.overdueDays(item.dueDate(), returnDate);
                BigDecimal fineAmount = finePolicy.calculateFine(overdueDays);

                if (overdueDays > 0) {
                    FineDAO.insertFine(connection, loanItemId, overdueDays, finePolicy.ratePerDay(), fineAmount);
                }

                LoanDAO.markLoanItemReturned(connection, loanItemId, returnDate);
                LoanDAO.completeLoanIfNoOpenItems(connection, item.loanId());

                // Ubah menjadi AVAILABLE dahulu; jika ada antrean, eksemplar akan langsung RESERVED.
                if (!LoanDAO.changeCopyStatus(connection, item.copyId(), "BORROWED", "AVAILABLE")) {
                    throw new BusinessRuleException("Status eksemplar gagal diperbarui saat pengembalian.");
                }

                ReservationDAO.OfferData nextOffer =
                        ReservationDAO.offerNext(connection, item.itemId(), item.copyId());
                connection.commit();

                return new ReturnReceipt(loanItemId, overdueDays, fineAmount,
                        nextOffer != null, nextOffer == null ? null : nextOffer.reservationId());
            } catch (Exception ex) {
                rollback(connection, ex);
                if (ex instanceof BusinessRuleException bre) throw bre;
                if (ex instanceof SQLException sqle) throw sqle;
                throw new SQLException("Gagal memproses pengembalian buku.", ex);
            }
        }
    }

    private static void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
