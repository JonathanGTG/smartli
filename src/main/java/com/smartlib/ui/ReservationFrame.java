package com.smartlib.ui;

import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.ReservationReceipt;
import com.smartlib.service.ReservationService;
import com.smartlib.service.LoanService;

import javax.swing.*;
import java.awt.*;

/** Form untuk membuat/membatalkan antrean dan memproses penawaran yang kedaluwarsa. */
public class ReservationFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JTextField memberIdField = new JTextField(12);
    private final JTextField itemIdField = new JTextField(12);
    private final JTextField reservationIdField = new JTextField(12);
    private final JTextField staffUserIdField = new JTextField(12);
    private final ReservationService reservationService = new ReservationService();
    private final LoanService loanService = new LoanService();

    public ReservationFrame() {
        super("SmartLib - Antrean Peminjaman");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(520, 300);
        setLocationRelativeTo(null);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, c, 0, "ID Anggota", memberIdField);
        addRow(form, c, 1, "ID Judul/Koleksi (item_id)", itemIdField);
        addRow(form, c, 2, "ID Antrean", reservationIdField);
        addRow(form, c, 3, "ID User Petugas (konfirmasi penawaran)", staffUserIdField);

        JButton joinButton = new JButton("Masuk Antrean");
        joinButton.addActionListener(e -> joinQueue());
        JButton cancelButton = new JButton("Batalkan Antrean");
        cancelButton.addActionListener(e -> cancelQueue());
        JButton acceptButton = new JButton("Terima Penawaran dan Pinjam");
        acceptButton.addActionListener(e -> acceptOffer());
        JButton expireButton = new JButton("Proses Penawaran Kedaluwarsa");
        expireButton.addActionListener(e -> processExpired());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttons.add(joinButton); buttons.add(cancelButton);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        form.add(buttons, c);
        c.gridy = 5;
        form.add(acceptButton, c);
        c.gridy = 6;
        form.add(expireButton, c);
        add(form);
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridwidth = 1; c.gridy = row; c.gridx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1; panel.add(field, c);
    }

    private void joinQueue() {
        try {
            long memberId = Long.parseLong(memberIdField.getText().trim());
            long itemId = Long.parseLong(itemIdField.getText().trim());
            ReservationReceipt receipt = reservationService.requestReservation(memberId, itemId);
            JOptionPane.showMessageDialog(this,
                    "Berhasil masuk antrean.\nID antrean: " + receipt.reservationId()
                            + "\nPosisi antrean: " + receipt.queuePosition(),
                    "Antrean Berhasil", JOptionPane.INFORMATION_MESSAGE);
            reservationIdField.setText(String.valueOf(receipt.reservationId()));
        } catch (NumberFormatException ex) {
            showError("ID anggota dan ID koleksi harus berupa angka.");
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal membuat antrean: " + ex.getMessage());
        }
    }

    private void cancelQueue() {
        try {
            long memberId = Long.parseLong(memberIdField.getText().trim());
            long reservationId = Long.parseLong(reservationIdField.getText().trim());
            reservationService.cancelReservation(memberId, reservationId);
            JOptionPane.showMessageDialog(this, "Antrean berhasil dibatalkan.");
        } catch (NumberFormatException ex) {
            showError("Masukkan ID anggota dan ID antrean yang valid.");
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal membatalkan antrean: " + ex.getMessage());
        }
    }

    private void acceptOffer() {
        try {
            long memberId = Long.parseLong(memberIdField.getText().trim());
            long reservationId = Long.parseLong(reservationIdField.getText().trim());
            long staffId = Long.parseLong(staffUserIdField.getText().trim());
            var receipt = loanService.borrowReserved(memberId, reservationId, staffId);
            JOptionPane.showMessageDialog(this,
                    "Penawaran berhasil diproses menjadi peminjaman.\nID transaksi: " + receipt.loanId()
                            + "\nJatuh tempo: " + receipt.dueDate(),
                    "Penawaran Diterima", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            showError("ID anggota, antrean, dan petugas harus berupa angka.");
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal memproses penawaran: " + ex.getMessage());
        }
    }

    private void processExpired() {
        try {
            int total = reservationService.processExpiredOffers();
            JOptionPane.showMessageDialog(this, total + " penawaran kedaluwarsa diproses.");
        } catch (Exception ex) {
            showError("Gagal memproses antrean: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Antrean", JOptionPane.WARNING_MESSAGE);
    }
}
