package com.smartlib.ui;

import com.smartlib.config.DatabaseConnection;
import com.smartlib.exception.BusinessRuleException;
import com.smartlib.model.ReturnReceipt;
import com.smartlib.service.LoanService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Form pengembalian buku berbasis tabel interaktif untuk Petugas.
 * Menampilkan daftar peminjaman aktif, fitur pencarian cepat,
 * deteksi keterlambatan, dan kalkulasi denda otomatis.
 * Dikerjakan oleh: Deby Yasmin Nabila (abcdeby)
 */
public class ReturnFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID Detail", "Barcode", "Judul Buku", "Peminjam", "Tgl Pinjam", "Jatuh Tempo", "Status Keterlambatan"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
    private final JTextField tfSearch = new JTextField(18);
    private final JLabel lblSelected = new JLabel("Pilih baris pada tabel untuk memproses pengembalian.");
    private final LoanService loanService = new LoanService();

    public ReturnFrame() {
        super("SmartLib — Pengembalian Buku");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(860, 500);
        setLocationRelativeTo(null);

        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.add(new JLabel("Cari Peminjaman:"));
        topPanel.add(tfSearch);
        JButton btnSearch = new JButton("Filter");
        btnSearch.addActionListener(e -> applyFilter());
        JButton btnReset = new JButton("Reset");
        btnReset.addActionListener(e -> {
            tfSearch.setText("");
            sorter.setRowFilter(null);
        });
        JButton btnRefresh = new JButton("⟳ Muat Ulang");
        btnRefresh.addActionListener(e -> loadActiveLoans());

        topPanel.add(btnSearch);
        topPanel.add(btnReset);
        topPanel.add(btnRefresh);

        tfSearch.addActionListener(e -> applyFilter());

        JPanel bottomPanel = new JPanel(new BorderLayout(12, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        lblSelected.setFont(lblSelected.getFont().deriveFont(Font.BOLD, 12f));
        bottomPanel.add(lblSelected, BorderLayout.CENTER);

        JButton btnProcess = new JButton("↩ Proses Pengembalian Buku");
        btnProcess.setFont(btnProcess.getFont().deriveFont(Font.BOLD, 13f));
        btnProcess.setBackground(new Color(46, 125, 50));
        btnProcess.setForeground(Color.WHITE);
        btnProcess.setPreferredSize(new Dimension(250, 40));
        btnProcess.addActionListener(e -> processReturn());
        bottomPanel.add(btnProcess, BorderLayout.EAST);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    int modelRow = table.convertRowIndexToModel(row);
                    String barcode = (String) tableModel.getValueAt(modelRow, 1);
                    String title = (String) tableModel.getValueAt(modelRow, 2);
                    String borrower = (String) tableModel.getValueAt(modelRow, 3);
                    String status = (String) tableModel.getValueAt(modelRow, 6);
                    lblSelected.setText("<html>Buku terpilih: <b>[" + barcode + "] " + title + "</b> (Peminjam: " + borrower + ")<br>Status: " + status + "</html>");
                } else {
                    lblSelected.setText("Pilih baris pada tabel untuk memproses pengembalian.");
                }
            }
        });

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        loadActiveLoans();
    }

    private void applyFilter() {
        String text = tfSearch.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        }
    }

    private void loadActiveLoans() {
        tableModel.setRowCount(0);
        String sql = "SELECT li.id AS loan_item_id, c.barcode, i.title, m.member_code, m.full_name, "
                + "       l.borrow_date, li.due_date, "
                + "       DATEDIFF(CURDATE(), li.due_date) AS overdue_days "
                + "FROM loan_items li "
                + "JOIN loans l ON l.id = li.loan_id "
                + "JOIN item_copies c ON c.id = li.copy_id "
                + "JOIN library_items i ON i.id = c.item_id "
                + "JOIN members m ON m.id = l.member_id "
                + "WHERE li.status = 'BORROWED' "
                + "ORDER BY li.due_date ASC, l.borrow_date ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long loanItemId = rs.getLong("loan_item_id");
                String barcode = rs.getString("barcode");
                String title = rs.getString("title");
                String borrower = rs.getString("member_code") + " - " + rs.getString("full_name");
                String borrowDate = rs.getString("borrow_date");
                String dueDate = rs.getString("due_date");
                int overdueDays = rs.getInt("overdue_days");

                String status;
                if (overdueDays > 0) {
                    long fineEst = (long) overdueDays * LoanService.getDailyFineRate().longValue();
                    status = "⚠️ Terlambat " + overdueDays + " hari (Denda Rp " + fineEst + ")";
                } else {
                    status = "✓ Tepat Waktu (Bebas Denda)";
                }

                tableModel.addRow(new Object[]{loanItemId, barcode, title, borrower, borrowDate, dueDate, status});
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat daftar peminjaman: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void processReturn() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Silakan pilih buku yang akan dikembalikan dari tabel di atas.", "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int modelRow = table.convertRowIndexToModel(selectedRow);
        long loanItemId = (Long) tableModel.getValueAt(modelRow, 0);
        String title = (String) tableModel.getValueAt(modelRow, 2);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Proses pengembalian untuk buku:\n\"" + title + "\"?",
                "Konfirmasi Pengembalian", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            ReturnReceipt receipt = loanService.returnBook(loanItemId);
            NumberFormat currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
            String amount = currency.format(receipt.fineAmount());

            StringBuilder msg = new StringBuilder();
            msg.append("Pengembalian buku berhasil diproses!\n\n");
            msg.append("Hari Keterlambatan : ").append(receipt.overdueDays()).append(" hari\n");
            msg.append("Nominal Denda       : ").append(amount).append("\n");

            if (receipt.copyReservedForNextMember()) {
                msg.append("\n📌 INFO ANTREAN:\nEksemplar langsung dialihkan dan ditawarkan kepada antrean FIFO berikutnya (ID Antrean: ")
                        .append(receipt.nextReservationId()).append(").");
            } else {
                msg.append("\nTidak ada antrean untuk buku ini. Eksemplar kembali berstatus AVAILABLE.");
            }

            JOptionPane.showMessageDialog(this, msg.toString(), "Sukses", JOptionPane.INFORMATION_MESSAGE);
            loadActiveLoans();
        } catch (BusinessRuleException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validasi Gagal", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Gagal memproses pengembalian: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}