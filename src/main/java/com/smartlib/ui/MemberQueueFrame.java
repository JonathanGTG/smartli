package com.smartlib.ui;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.Member;
import com.smartlib.model.User;
import com.smartlib.service.MemberService;
import com.smartlib.service.ReservationService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Antarmuka pemantauan antrean peminjaman buku khusus Anggota.
 * Dikerjakan oleh: Royceyana Yosephine Silaban (Royceyana)
 */
public class MemberQueueFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final User user;
    private final MemberService memberService = new MemberService();
    private final ReservationService reservationService = new ReservationService();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID Antrean", "Judul Koleksi Buku", "Waktu Pengajuan", "Posisi Antrean", "Status", "Batas Waktu Pengambilan"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final JPanel bannerPanel = new JPanel(new BorderLayout(10, 0));
    private final JLabel lblBannerText = new JLabel();

    private Member currentMember = null;

    public MemberQueueFrame(User user) {
        super("SmartLib — Status Antrean Buku Saya (" + user.getFullName() + ")");
        this.user = user;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(840, 480);
        setLocationRelativeTo(null);

        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 16));
        JLabel title = new JLabel("Daftar Antrean Peminjaman Buku Saya");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JLabel sub = new JLabel("Urutan antrean diatur secara First In, First Out (FIFO). Buku ditahan 24 jam saat siap.");
        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);

        bannerPanel.setBackground(new Color(254, 243, 199));
        bannerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(245, 158, 11), 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        JLabel iconLbl = new JLabel("🎉 ");
        iconLbl.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));
        lblBannerText.setFont(lblBannerText.getFont().deriveFont(Font.BOLD, 12f));
        lblBannerText.setForeground(new Color(146, 64, 14));
        bannerPanel.add(iconLbl, BorderLayout.WEST);
        bannerPanel.add(lblBannerText, BorderLayout.CENTER);
        bannerPanel.setVisible(false);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(header, BorderLayout.NORTH);
        topContainer.add(bannerPanel, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        JButton btnRefresh = new JButton("⟳ Refresh");
        btnRefresh.addActionListener(e -> loadQueueData());

        JButton btnCancel = UiKit.accentButton("✕ Batalkan Antrean Terpilih", new Color(198, 40, 40));
        btnCancel.addActionListener(e -> cancelSelectedQueue());

        bottom.add(btnRefresh);
        bottom.add(btnCancel);

        add(topContainer, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        Optional<Member> mOpt = memberService.getMemberByUserId(user.getId());
        if (mOpt.isPresent()) {
            currentMember = mOpt.get();
            loadQueueData();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Akun Anda belum tertaut ke profil Anggota perpustakaan. Hubungi petugas.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void loadQueueData() {
        if (currentMember == null) return;
        tableModel.setRowCount(0);
        bannerPanel.setVisible(false);

        String sql = "SELECT r.id, i.title, r.requested_at, r.status, r.expires_at, "
                   + "       (SELECT COUNT(*) FROM reservations w "
                   + "        WHERE w.item_id = r.item_id AND w.status = 'WAITING' "
                   + "          AND (w.requested_at < r.requested_at OR (w.requested_at = r.requested_at AND w.id <= r.id))) AS queue_pos "
                   + "FROM reservations r "
                   + "JOIN library_items i ON i.id = r.item_id "
                   + "WHERE r.member_id = ? AND r.status IN ('WAITING', 'OFFERED') "
                   + "ORDER BY r.requested_at ASC";

        boolean hasOffered = false;
        String offeredBookTitle = "";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, currentMember.getId());
            try (ResultSet rs = ps.executeQuery()) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
                while (rs.next()) {
                    long resId = rs.getLong("id");
                    String title = rs.getString("title");
                    Timestamp reqAt = rs.getTimestamp("requested_at");
                    String status = rs.getString("status");
                    Timestamp expAt = rs.getTimestamp("expires_at");
                    int pos = rs.getInt("queue_pos");

                    String posText = "WAITING".equalsIgnoreCase(status) ? "Urutan ke-" + pos : "⭐ Giliran Anda (Terdepan)";
                    String expText = expAt != null ? expAt.toLocalDateTime().format(fmt) + " WIB" : "Menunggu giliran";

                    if ("OFFERED".equalsIgnoreCase(status)) {
                        hasOffered = true;
                        offeredBookTitle = title;
                    }

                    tableModel.addRow(new Object[]{resId, title, reqAt != null ? reqAt.toLocalDateTime().format(fmt) : "-", posText, status, expText});
                }
            }

            if (hasOffered) {
                lblBannerText.setText("<html>BUKU TELAH SIAP DIAMBIL: <b>\"" + offeredBookTitle + "\"</b><br>Silakan ambil buku fisik di Meja Sirkulasi Perpustakaan sebelum batas waktu kedaluwarsa (24 jam).</html>");
                bannerPanel.setVisible(true);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat antrean: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void cancelSelectedQueue() {
        if (currentMember == null) return;
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Pilih baris antrean pada tabel yang ingin dibatalkan.", "Perhatian", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long resId = (Long) tableModel.getValueAt(row, 0);
        String title = (String) tableModel.getValueAt(row, 1);
        String status = (String) tableModel.getValueAt(row, 4);

        if ("OFFERED".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this,
                    "Antrean untuk buku \"" + title + "\" sudah berstatus DITAWARKAN (OFFERED).\n"
                            + "Buku sudah disiapkan di meja sirkulasi. Silakan hubungi petugas jika ingin melepaskannya.",
                    "Tidak Dapat Dibatalkan Mandiri", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Batalkan antrean untuk buku:\n\"" + title + "\"?",
                "Konfirmasi Pembatalan", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            reservationService.cancelReservation(currentMember.getId(), resId);
            JOptionPane.showMessageDialog(this, "Antrean berhasil dibatalkan.", "Sukses", JOptionPane.INFORMATION_MESSAGE);
            loadQueueData();
        } catch (BusinessRuleException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Gagal", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gagal membatalkan antrean: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
