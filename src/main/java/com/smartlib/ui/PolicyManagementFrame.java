package com.smartlib.ui;

import com.smartlib.model.Permission;
import com.smartlib.policy.DailyFinePolicy;
import com.smartlib.policy.GracePeriodFinePolicy;
import com.smartlib.service.AuthService;
import com.smartlib.service.LoanService;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;

/**
 * Form pengaturan kebijakan operasional perpustakaan khusus Admin.
 * Mengizinkan pengaturan kuota pinjam, masa pinjam, tarif denda, dan strategi denda.
 */
public class PolicyManagementFrame extends JFrame {

    // 1. Dideklarasikan sebagai atribut/field kelas agar bisa diakses oleh savePolicy()
    private final JSpinner spMaxBooks = new JSpinner(new SpinnerNumberModel(LoanService.getMaxActiveBooks(), 1, 10, 1));
    private final JSpinner spLoanDays = new JSpinner(new SpinnerNumberModel(LoanService.getLoanPeriodDays(), 1, 30, 1));
    private final JTextField tfFineRate = new JTextField(String.valueOf(LoanService.getDailyFineRate()), 12);
    private final JComboBox<String> cbPolicyStrategy = new JComboBox<>(new String[]{
            "Denda Harian Langsung (Daily Fine Policy)",
            "Masa Tenggang 2 Hari Bebas Denda (Grace Period Policy)"
    });

    public PolicyManagementFrame() {
        super("SmartLib — Pengaturan Kebijakan Perpustakaan");

        // Memastikan hanya Admin yang memiliki hak akses
        AuthService.getInstance().requirePermission(Permission.CHANGE_LOAN_RULES);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(520, 340);
        setLocationRelativeTo(null);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(20, 24, 15, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        addRow(form, c, 0, "Batas Maksimal Buku Dipinjam:", spMaxBooks);
        addRow(form, c, 1, "Masa Peminjaman Buku (Hari):", spLoanDays);
        addRow(form, c, 2, "Tarif Denda Keterlambatan (Rp/Hari):", tfFineRate);
        addRow(form, c, 3, "Strategi Perhitungan Denda:", cbPolicyStrategy);

        JButton btnSave = UiKit.accentButton("Simpan Kebijakan Baru", new Color(0x1976D2));
        btnSave.addActionListener(e -> savePolicy());

        c.gridx = 0; c.gridy = 4; c.gridwidth = 2;
        form.add(btnSave, c);
        add(form, BorderLayout.CENTER);
    }

    private void addRow(JPanel p, GridBagConstraints c, int row, String label, JComponent comp) {
        c.gridwidth = 1; c.gridy = row; c.gridx = 0;
        p.add(new JLabel(label), c);
        c.gridx = 1; p.add(comp, c);
    }

    private void savePolicy() {
        try {
            int maxBooks = (Integer) spMaxBooks.getValue();
            int loanDays = (Integer) spLoanDays.getValue();
            BigDecimal fineRate = new BigDecimal(tfFineRate.getText().trim());

            if (fineRate.signum() < 0) {
                JOptionPane.showMessageDialog(this,
                        "Tarif denda tidak boleh bernilai negatif.",
                        "Validasi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Memperbarui variabel di LoanService
            LoanService.setMaxActiveBooks(maxBooks);
            LoanService.setLoanPeriodDays(loanDays);
            LoanService.setDailyFineRate(fineRate);

            // Menerapkan Strategy Pattern sesuai pilihan Admin
            if (cbPolicyStrategy.getSelectedIndex() == 0) {
                LoanService.setFinePolicy(new DailyFinePolicy(fineRate));
            } else {
                LoanService.setFinePolicy(new GracePeriodFinePolicy(fineRate, 2));
            }

            JOptionPane.showMessageDialog(this,
                    "Kebijakan berhasil diperbarui dan langsung aktif di seluruh sistem:\n"
                            + "• Batas Maksimal Pinjam : " + maxBooks + " Eksemplar\n"
                            + "• Masa Peminjaman       : " + loanDays + " Hari\n"
                            + "• Tarif Denda/Hari       : Rp " + fineRate + "\n"
                            + "• Strategi Denda         : " + cbPolicyStrategy.getSelectedItem(),
                    "Pengaturan Disimpan", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Format tarif denda harus berupa angka desimal valid.",
                    "Kesalahan", JOptionPane.ERROR_MESSAGE);
        }
    }
}