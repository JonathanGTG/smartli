package com.smartlib.ui.components;

import com.smartlib.ui.Theme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * Panel siap pakai yang membungkus JTable di dalam JScrollPane dengan judul.
 * Dipakai di DashboardFrame dan ReportFrame.
 */
public class ReportTablePanel extends Theme.CardPanel {

    private final DefaultTableModel model;
    private final JTable table;

    public ReportTablePanel(String title, String[] columns) {
        super(new BorderLayout(0, 0), 14);
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.font(Font.BOLD, 14f));
        heading.setForeground(Theme.NAVY_800);
        heading.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));

        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(model);
        Theme.styleTable(table);

        // Rata kanan untuk kolom angka (kolom terakhir)
        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(SwingConstants.RIGHT);
        rightRenderer.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        if (columns.length > 0) {
            table.getColumnModel().getColumn(columns.length - 1).setCellRenderer(rightRenderer);
        }

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
        sp.getViewport().setBackground(Color.WHITE);

        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder(0, 6, 6, 6));
        inner.add(heading, BorderLayout.NORTH);
        inner.add(sp, BorderLayout.CENTER);
        add(inner, BorderLayout.CENTER);
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
