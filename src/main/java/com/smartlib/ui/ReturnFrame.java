package com.smartlib.ui;

import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.ReturnReceipt;
import com.smartlib.service.LoanService;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.Locale;

/** Form sederhana pengembalian buku untuk Petugas. */
public class ReturnFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JTextField loanItemIdField = new JTextField(16);
    private final LoanService loanService = new LoanService();

    public ReturnFrame() {
        super("SmartLib - Pengembalian Buku");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(430, 190);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0;
        panel.add(new JLabel("ID Detail Peminjaman"), c);
        c.gridx = 1;
        panel.add(loanItemIdField, c);

        JButton returnButton = new JButton("Proses Pengembalian");
        returnButton.addActionListener(e -> returnBook());
        c.gridx = 0; c.gridy = 1; c.gridwidth = 2;
        panel.add(returnButton, c);
        add(panel);
    }

    private void returnBook() {
        try {
            long loanItemId = Long.parseLong(loanItemIdField.getText().trim());
            ReturnReceipt receipt = loanService.returnBook(loanItemId);
            String amount = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
                    .format(receipt.fineAmount());
            String message = "Pengembalian berhasil.\nHari terlambat: " + receipt.overdueDays()
                    + "\nDenda: " + amount;
            if (receipt.copyReservedForNextMember()) {
                message += "\nEksemplar ditawarkan kepada antrean berikutnya. ID antrean: "
                        + receipt.nextReservationId();
            } else {
                message += "\nTidak ada antrean. Buku tersedia kembali.";
            }
            JOptionPane.showMessageDialog(this, message, "Pengembalian Berhasil", JOptionPane.INFORMATION_MESSAGE);
            loanItemIdField.setText("");
        } catch (NumberFormatException ex) {
            showError("ID detail peminjaman harus berupa angka.");
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal memproses pengembalian: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Pengembalian", JOptionPane.WARNING_MESSAGE);
    }
}
