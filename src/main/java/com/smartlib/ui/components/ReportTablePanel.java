package com.smartlib.ui.components;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Panel siap pakai yang membungkus JTable di dalam JScrollPane dengan judul.
 * Dipakai di DashboardFrame dan ReportFrame.
 */
public class ReportTablePanel extends JPanel {

    private final DefaultTableModel model;
    private final JTable table;

    public ReportTablePanel(String title, String[] columns) {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), title,
            javax.swing.border.TitledBorder.LEFT,
            javax.swing.border.TitledBorder.TOP
        ));

        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(24);
        table.setGridColor(new Color(230, 230, 230));
        table.getTableHeader().setFont(table.getFont().deriveFont(Font.BOLD));
        table.setShowVerticalLines(false);

        // Rata kanan untuk kolom angka (kolom terakhir)
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        if (columns.length > 0) {
            table.getColumnModel().getColumn(columns.length - 1).setCellRenderer(rightRenderer);
        }

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /** Mengganti seluruh isi tabel dengan data baru. */
    public void setData(java.util.List<Object[]> rows) {
        model.setRowCount(0);
        rows.forEach(model::addRow);
    }

    /** Menambah satu baris. */
    public void addRow(Object[] row) {
        model.addRow(row);
    }

    /** Menghapus semua baris. */
    public void clearRows() {
        model.setRowCount(0);
    }

    public JTable getTable() { return table; }

    public DefaultTableModel getModel() { return model; }
}
