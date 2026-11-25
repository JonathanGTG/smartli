package com.smartlib.ui;

import com.smartlib.model.*;
import com.smartlib.service.AuthService;
import com.smartlib.service.BookService;
import com.smartlib.service.MemberService;
import com.smartlib.service.ReservationService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Optional;

final class BookDetailDialog {

    private BookDetailDialog() { }

    static void show(Component parent, BookService service, int bookId) {
        BookStockSummary summary = UiKit.call(parent, () -> service.getSummary(bookId));
        List<BookCopy> copies = UiKit.call(parent, () -> service.listCopies(bookId));
        if (summary == null || copies == null) return;

        Book b = summary.book();
        StringBuilder text = new StringBuilder();
        text.append("Judul        : ").append(b.getTitle()).append('\n');
        text.append("Penulis      : ").append(b.getAuthorNames().isEmpty() ? "-" : b.getAuthorNames()).append('\n');
        text.append("Kategori     : ").append(b.getCategory().getName()).append('\n');
        text.append("ISBN         : ").append(b.getIsbn() == null ? "-" : b.getIsbn()).append('\n');
        text.append("Penerbit     : ").append(b.getPublisher() == null ? "-" : b.getPublisher()).append('\n');
        text.append("Tahun terbit : ").append(b.getPublicationYear() == null ? "-" : b.getPublicationYear()).append('\n');
        text.append("Jenis        : ").append(b.getDetails()).append('\n');
        text.append("Deskripsi    : ").append(b.getDescription().isEmpty() ? "-" : b.getDescription()).append('\n');
        text.append('\n');
        text.append("Stok: Total ").append(summary.totalCopies())
                .append(" | Tersedia ").append(summary.availableCopies())
                .append(" | Dipinjam ").append(summary.borrowedCopies())
                .append(" | Cadangan/Rusak ").append(summary.otherCopies());

        JTextArea area = new JTextArea(text.toString());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        DefaultTableModel model = UiKit.model("Barcode", "Status", "Lokasi Rak");
        for (BookCopy c : copies) {
            model.addRow(new Object[]{c.getBarcode(), c.getStatus().getLabel(),
                    c.getShelfLocation().isEmpty() ? "-" : c.getShelfLocation()});
        }
        JScrollPane copyScroll = new JScrollPane(UiKit.table(model));
        copyScroll.setBorder(BorderFactory.createTitledBorder("Eksemplar Fisik"));
        copyScroll.setPreferredSize(new Dimension(560, 140));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Detail Koleksi Buku",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(copyScroll, BorderLayout.CENTER);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

        // Tombol Reservasi / Antrean jika stok habis
        if (summary.availableCopies() == 0 && summary.totalCopies() > 0) {
            JButton btnQueue = new JButton("📌 Ajukan Antrean (FIFO)");
            btnQueue.setBackground(new Color(0xE65100));
            btnQueue.setForeground(Color.WHITE);
            btnQueue.addActionListener(e -> {
                Optional<User> currentUser = AuthService.getInstance().getCurrentUser();
                if (currentUser.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog, "Silakan login terlebih dahulu.");
                    return;
                }
                User u = currentUser.get();
                MemberService memberService = new MemberService();
                Optional<Member> memberOpt = memberService.getMemberByUserId(u.getId());
                if (memberOpt.isEmpty()) {
                    JOptionPane.showMessageDialog(dialog,
                            "Akun Anda belum ditautkan ke profil Anggota perpustakaan. Hubungi petugas.",
                            "Perhatian", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                int confirm = JOptionPane.showConfirmDialog(dialog,
                        "Buku saat ini sedang habis dipinjam.\nApakah Anda ingin masuk ke dalam antrean (FIFO) untuk judul:\n\""
                                + b.getTitle() + "\"?",
                        "Konfirmasi Antrean", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    try {
                        ReservationService reservationService = new ReservationService();
                        ReservationReceipt receipt = reservationService.requestReservation(memberOpt.get().getId(), b.getId());
                        JOptionPane.showMessageDialog(dialog,
                                "Berhasil masuk antrean!\nNomor ID Antrean: " + receipt.reservationId()
                                        + "\nPosisi antrean saat ini: " + receipt.queuePosition(),
                                "Sukses", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dialog, "Gagal masuk antrean: " + ex.getMessage(),
                                "Peringatan", JOptionPane.WARNING_MESSAGE);
                    }
                }
            });
            actionRow.add(btnQueue);
        }

        JButton close = UiKit.button("Tutup", dialog::dispose);
        actionRow.add(close);

        south.add(actionRow, BorderLayout.SOUTH);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(620, 540);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}