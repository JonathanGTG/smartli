package com.smartlib.ui;

import com.smartlib.model.FineRecord;
import com.smartlib.service.FineService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Form melihat denda anggota dan mencatat pembayaran oleh petugas. */
public class FineFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private final JTextField memberIdField = new JTextField(10);
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID Denda", "ID Detail", "Judul Buku", "Hari", "Tarif/Hari", "Jumlah", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final FineService fineService = new FineService();

    public FineFrame() {
        super("SmartLib - Denda Anggota");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(850, 420);
        setLocationRelativeTo(null);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("ID Anggota:"));
        top.add(memberIdField);
        JButton loadButton = new JButton("Tampilkan Denda");
        loadButton.addActionListener(e -> loadFines());
        top.add(loadButton);

        JButton paidButton = new JButton("Tandai Denda Lunas");
        paidButton.addActionListener(e -> markSelectedFinePaid());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(paidButton);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private void loadFines() {
        try {
            long memberId = Long.parseLong(memberIdField.getText().trim());
            List<FineRecord> records = fineService.getFinesByMember(memberId);
            tableModel.setRowCount(0);
            NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
            for (FineRecord record : records) {
                tableModel.addRow(new Object[]{record.fineId(), record.loanItemId(), record.bookTitle(),
                        record.overdueDays(), currency.format(record.ratePerDay()),
                        currency.format(record.amount()), record.status()});
            }
            if (records.isEmpty()) JOptionPane.showMessageDialog(this, "Tidak ada data denda untuk anggota ini.");
        } catch (NumberFormatException ex) {
            showError("ID anggota harus berupa angka.");
        } catch (Exception ex) {
            showError("Gagal mengambil data denda: " + ex.getMessage());
        }
    }

    private void markSelectedFinePaid() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Pilih baris denda yang akan ditandai lunas.");
            return;
        }
        try {
            long fineId = Long.parseLong(tableModel.getValueAt(row, 0).toString());
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Tandai denda ID " + fineId + " sebagai PAID?",
                    "Konfirmasi Pembayaran", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                fineService.markFineAsPaid(fineId);
                loadFines();
                JOptionPane.showMessageDialog(this, "Status denda berhasil diperbarui.");
            }
        } catch (Exception ex) {
            showError("Gagal memperbarui pembayaran: " + ex.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Denda", JOptionPane.WARNING_MESSAGE);
    }
}
