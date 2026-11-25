package com.smartlib.ui.components;

import javax.swing.*;
import java.awt.*;

/** Kotak ringkasan sederhana: label kecil di atas, angka di bawahnya. Dipakai di DashboardFrame. */
public class StatCard extends JPanel {

    private static final Color WARNING = new Color(0xB71C1C);

    private final JLabel valueLabel = new JLabel("-");
    private final Color normalColor;

    public StatCard(String title) {
        setLayout(new BorderLayout(0, 2));
        Color line = UIManager.getColor("Separator.foreground");
        if (line == null) line = Color.LIGHT_GRAY;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(line),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 20f));
        normalColor = valueLabel.getForeground();

        add(new JLabel(title), BorderLayout.NORTH);
        add(valueLabel, BorderLayout.CENTER);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    /** Menandai angka dengan warna merah (mis. ada keterlambatan) atau mengembalikannya ke normal. */
    public void setHighlight(boolean on) {
        valueLabel.setForeground(on ? WARNING : normalColor);
    }
}
