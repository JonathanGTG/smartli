package com.smartlib.service;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.dao.FineDAO;
import com.smartlib.model.FineRecord;
import com.smartlib.policy.DailyFinePolicy;
import com.smartlib.policy.FinePolicy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Layanan melihat dan mengelola pembayaran denda. */
public class FineService {
    private final FinePolicy finePolicy;

    public FineService() {
        this(new DailyFinePolicy(LoanService.DAILY_FINE_RATE));
    }

    public FineService(FinePolicy finePolicy) {
        this.finePolicy = java.util.Objects.requireNonNull(finePolicy, "FinePolicy wajib diisi.");
    }

    public int calculateOverdueDays(LocalDate dueDate, LocalDate returnDate) {
        return finePolicy.overdueDays(dueDate, returnDate);
    }

    public BigDecimal calculateFine(int overdueDays) {
        return finePolicy.calculateFine(overdueDays);
    }

    public List<FineRecord> getFinesByMember(long memberId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return FineDAO.findByMember(connection, memberId);
        }
    }

    public void markFineAsPaid(long fineId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                FineDAO.markPaid(connection, fineId);
                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            }
        }
    }
}
