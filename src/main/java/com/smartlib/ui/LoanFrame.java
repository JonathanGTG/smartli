package com.smartlib.ui;

import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.BorrowReceipt;
import com.smartlib.service.LoanService;

import javax.swing.*;
import java.awt.*;

/** Form sederhana peminjaman untuk dihubungkan dari Dashboard Petugas. */
public class LoanFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JTextField memberIdField = new JTextField(14);
    private final JTextField copyIdField = new JTextField(14);
    private final JTextField staffUserIdField = new JTextField(14);
    private final LoanService loanService = new LoanService();

    public LoanFrame() {
        super("SmartLib - Peminjaman Buku");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(460, 300);
        setLocationRelativeTo(null);
        com.smartlib.service.AuthService.getInstance().getCurrentUser()
                .ifPresent(u -> staffUserIdField.setText(String.valueOf(u.getId())));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 20, 10, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 7, 7, 7);
        c.fill = GridBagConstraints.HORIZONTAL;
        addRow(form, c, 0, "ID Anggota", memberIdField);
        addRow(form, c, 1, "ID Eksemplar Buku", copyIdField);
        addRow(form, c, 2, "ID User Petugas", staffUserIdField);

        JButton borrowButton = new JButton("Proses Peminjaman");
        borrowButton.addActionListener(e -> borrowBook());
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2;
        form.add(borrowButton, c);
        add(form, BorderLayout.CENTER);
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, JTextField field) {
        c.gridwidth = 1; c.gridy = row; c.gridx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1; panel.add(field, c);
    }

    private void borrowBook() {
        try {
            long memberId = Long.parseLong(memberIdField.getText().trim());
            long copyId = Long.parseLong(copyIdField.getText().trim());
            long staffId = Long.parseLong(staffUserIdField.getText().trim());
            BorrowReceipt receipt = loanService.borrow(memberId, copyId, staffId);
            JOptionPane.showMessageDialog(this,
                    "Peminjaman berhasil.\nID transaksi: " + receipt.loanId()
                            + "\nID detail: " + receipt.loanItemId()
                            + "\nJatuh tempo: " + receipt.dueDate(),
                    "Berhasil", JOptionPane.INFORMATION_MESSAGE);
            memberIdField.setText(""); copyIdField.setText("");
        } catch (NumberFormatException ex) {
            showError("Semua ID harus berupa angka.");
        } catch (BusinessRuleException ex) {
            showError(ex.getMessage());
        } catch (Exception ex) {
            showError("Gagal memproses peminjaman: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Peminjaman", JOptionPane.WARNING_MESSAGE);
    }
}
