package com.smartlib.dao;

import java.sql.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class ReservationDAO {
    private ReservationDAO() {
    }

    public record ReservationData(long reservationId, long memberId, long itemId,
                           Long offeredCopyId, String status, Timestamp expiresAt) { }
    public record OfferData(long reservationId, long memberId) { }
    public record ExpiredOffer(long reservationId, long itemId, long copyId) { }

    public static boolean hasAvailableCopy(Connection connection, long itemId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM item_copies WHERE item_id = ? AND availability_status = 'AVAILABLE')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }

    public static boolean hasWaitingReservation(Connection connection, long itemId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM reservations WHERE item_id = ? AND status = 'WAITING')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }

    public static boolean hasActiveReservation(Connection connection, long memberId, long itemId) throws SQLException {
        String sql = "SELECT EXISTS(SELECT 1 FROM reservations WHERE member_id = ? AND item_id = ? AND status IN ('WAITING','OFFERED'))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, memberId);
            ps.setLong(2, itemId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBoolean(1); }
        }
    }

    public static long insertWaiting(Connection connection, long memberId, long itemId) throws SQLException {
        String sql = "INSERT INTO reservations (member_id, item_id, offered_copy_id, requested_at, status, offered_at, expires_at) VALUES (?, ?, NULL, NOW(), 'WAITING', NULL, NULL)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, memberId);
            ps.setLong(2, itemId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Gagal memperoleh ID antrean.");
                return keys.getLong(1);
            }
        }
    }

    public static int getQueuePosition(Connection connection, long reservationId, long itemId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM reservations r
                WHERE r.item_id = ?
                  AND r.status = 'WAITING'
                  AND r.requested_at <= (SELECT requested_at FROM reservations WHERE reservation_id = ?)
                  AND (r.requested_at < (SELECT requested_at FROM reservations WHERE reservation_id = ?)
                       OR r.reservation_id <= ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            ps.setLong(2, reservationId);
            ps.setLong(3, reservationId);
            ps.setLong(4, reservationId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    public static ReservationData lockReservation(Connection connection, long reservationId) throws SQLException {
        String sql = "SELECT reservation_id, member_id, item_id, offered_copy_id, status, expires_at FROM reservations WHERE reservation_id = ? FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long offeredId = rs.getLong("offered_copy_id");
                Long offered = rs.wasNull() ? null : offeredId;
                return new ReservationData(rs.getLong("reservation_id"), rs.getLong("member_id"),
                        rs.getLong("item_id"), offered, rs.getString("status"), rs.getTimestamp("expires_at"));
            }
        }
    }

    public static OfferData offerNext(Connection connection, long itemId, long copyId) throws SQLException {
        String select = "SELECT reservation_id, member_id FROM reservations WHERE item_id = ? AND status = 'WAITING' ORDER BY requested_at ASC, reservation_id ASC FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(select)) {
            ps.setLong(1, itemId);
            Queue<OfferData> fifoQueue = new ArrayDeque<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    fifoQueue.offer(new OfferData(rs.getLong("reservation_id"), rs.getLong("member_id")));
                }
            }
            OfferData next = fifoQueue.poll();
            if (next == null) return null;
            String update = "UPDATE reservations SET status = 'OFFERED', offered_copy_id = ?, offered_at = NOW(), expires_at = DATE_ADD(NOW(), INTERVAL 24 HOUR) WHERE reservation_id = ? AND status = 'WAITING'";
            try (PreparedStatement ups = connection.prepareStatement(update)) {
                ups.setLong(1, copyId);
                ups.setLong(2, next.reservationId());
                if (ups.executeUpdate() != 1) throw new SQLException("Gagal menetapkan penawaran antrean.");
            }
            if (!LoanDAO.changeCopyStatus(connection, copyId, "AVAILABLE", "RESERVED")) {
                throw new SQLException("Status eksemplar tidak dapat diubah menjadi RESERVED.");
            }
            return next;
        }
    }

    public static List<ExpiredOffer> findExpiredOffers(Connection connection) throws SQLException {
        String sql = "SELECT reservation_id, item_id, offered_copy_id FROM reservations WHERE status = 'OFFERED' AND expires_at <= NOW() FOR UPDATE";
        List<ExpiredOffer> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new ExpiredOffer(rs.getLong("reservation_id"), rs.getLong("item_id"), rs.getLong("offered_copy_id")));
            }
        }
        return results;
    }

    public static void markExpired(Connection connection, long reservationId) throws SQLException {
        String sql = "UPDATE reservations SET status = 'EXPIRED' WHERE reservation_id = ? AND status = 'OFFERED'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            ps.executeUpdate();
        }
    }

    public static void markCancelled(Connection connection, long reservationId) throws SQLException {
        String sql = "UPDATE reservations SET status = 'CANCELLED' WHERE reservation_id = ? AND status IN ('WAITING','OFFERED')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            if (ps.executeUpdate() != 1) throw new SQLException("Antrean tidak dapat dibatalkan.");
        }
    }


    public static void markFulfilled(Connection connection, long reservationId) throws SQLException {
        String sql = "UPDATE reservations SET status = 'FULFILLED' WHERE reservation_id = ? AND status = 'OFFERED'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            if (ps.executeUpdate() != 1) throw new SQLException("Status antrean tidak dapat diperbarui menjadi FULFILLED.");
        }
    }

    public static boolean isCopyStillReserved(Connection connection, long copyId) throws SQLException {
        String sql = "SELECT availability_status = 'RESERVED' FROM item_copies WHERE copy_id = ? FOR UPDATE";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, copyId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getBoolean(1); }
        }
    }
}
