package com.smartlib.service;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.dao.LoanDAO;
import com.smartlib.dao.ReservationDAO;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.ReservationReceipt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Mengelola antrean buku dengan prinsip FIFO. */
public class ReservationService {

    public ReservationReceipt requestReservation(long memberId, long itemId)
            throws SQLException, BusinessRuleException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LoanDAO.MemberData member = LoanDAO.lockAndValidateMember(connection, memberId);
                if (!member.active()) throw new BusinessRuleException(member.message());
                if (ReservationDAO.hasAvailableCopy(connection, itemId)) {
                    throw new BusinessRuleException("Buku masih tersedia. Silakan ajukan peminjaman langsung.");
                }
                if (ReservationDAO.hasActiveReservation(connection, memberId, itemId)) {
                    throw new BusinessRuleException("Anda sudah memiliki antrean aktif untuk judul buku ini.");
                }

                long reservationId = ReservationDAO.insertWaiting(connection, memberId, itemId);
                int position = ReservationDAO.getQueuePosition(connection, reservationId, itemId);
                connection.commit();
                return new ReservationReceipt(reservationId, position, "WAITING");
            } catch (Exception ex) {
                rollback(connection, ex);
                if (ex instanceof BusinessRuleException bre) throw bre;
                if (ex instanceof SQLException sqle) throw sqle;
                throw new SQLException("Gagal membuat antrean peminjaman.", ex);
            }
        }
    }

    public void cancelReservation(long memberId, long reservationId)
            throws SQLException, BusinessRuleException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ReservationDAO.ReservationData reservation =
                        ReservationDAO.lockReservation(connection, reservationId);
                if (reservation == null) throw new BusinessRuleException("Data antrean tidak ditemukan.");
                if (reservation.memberId() != memberId) {
                    throw new BusinessRuleException("Anda tidak berhak membatalkan antrean milik anggota lain.");
                }
                if (!"WAITING".equalsIgnoreCase(reservation.status())
                        && !"OFFERED".equalsIgnoreCase(reservation.status())) {
                    throw new BusinessRuleException("Antrean tidak dapat dibatalkan karena statusnya " + reservation.status() + ".");
                }

                ReservationDAO.markCancelled(connection, reservationId);
                if ("OFFERED".equalsIgnoreCase(reservation.status()) && reservation.offeredCopyId() != null) {
                    long copyId = reservation.offeredCopyId();
                    if (ReservationDAO.isCopyStillReserved(connection, copyId)) {
                        if (!LoanDAO.changeCopyStatus(connection, copyId, "RESERVED", "AVAILABLE")) {
                            throw new SQLException("Gagal melepaskan eksemplar dari penawaran antrean.");
                        }
                        ReservationDAO.offerNext(connection, reservation.itemId(), copyId);
                    }
                }
                connection.commit();
            } catch (Exception ex) {
                rollback(connection, ex);
                if (ex instanceof BusinessRuleException bre) throw bre;
                if (ex instanceof SQLException sqle) throw sqle;
                throw new SQLException("Gagal membatalkan antrean.", ex);
            }
        }
    }

    /**
     * Jalankan saat aplikasi dibuka dan secara berkala ketika menu antrean dibuka.
     * Penawaran yang lewat dari 24 jam diubah menjadi EXPIRED dan eksemplarnya
     * ditawarkan kepada anggota WAITING berikutnya.
     */
    public int processExpiredOffers() throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<ReservationDAO.ExpiredOffer> expired = ReservationDAO.findExpiredOffers(connection);
                int processed = 0;
                for (ReservationDAO.ExpiredOffer offer : expired) {
                    ReservationDAO.markExpired(connection, offer.reservationId());
                    if (ReservationDAO.isCopyStillReserved(connection, offer.copyId())) {
                        if (!LoanDAO.changeCopyStatus(connection, offer.copyId(), "RESERVED", "AVAILABLE")) {
                            throw new SQLException("Gagal melepaskan eksemplar dari penawaran kedaluwarsa.");
                        }
                        ReservationDAO.offerNext(connection, offer.itemId(), offer.copyId());
                    }
                    processed++;
                }
                connection.commit();
                return processed;
            } catch (Exception ex) {
                rollback(connection, ex);
                if (ex instanceof SQLException sqle) throw sqle;
                throw new SQLException("Gagal memproses penawaran antrean yang kedaluwarsa.", ex);
            }
        }
    }

    public int getQueuePosition(long reservationId, long itemId) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return ReservationDAO.getQueuePosition(connection, reservationId, itemId);
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
