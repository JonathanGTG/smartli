package com.smartlib.ui.components;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Kartu statistik kecil yang menampilkan satu angka dengan label dan warna.
 * Dipakai di DashboardFrame untuk menampilkan ringkasan data.
 */
public class StatCard extends JPanel {

    private final JLabel valueLabel;
    private final JLabel titleLabel;

    public StatCard(String title, String value, Color accentColor) {
        setLayout(new BorderLayout(4, 4));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accentColor, 2, true),
            BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));
        setBackground(lighten(accentColor, 0.92f));

        valueLabel = new JLabel(value, SwingConstants.CENTER);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 36f));
        valueLabel.setForeground(accentColor.darker());

        titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 13f));
        titleLabel.setForeground(Color.DARK_GRAY);

        add(valueLabel, BorderLayout.CENTER);
        add(titleLabel, BorderLayout.SOUTH);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    /** Membuat warna yang lebih terang (blend ke putih) dengan faktor 0–1. */
    private static Color lighten(Color c, float factor) {
        int r = (int) (c.getRed()   + (255 - c.getRed())   * factor);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * factor);
        int b = (int) (c.getBlue()  + (255 - c.getBlue())  * factor);
        return new Color(
            Math.min(r, 255),
            Math.min(g, 255),
            Math.min(b, 255)
        );
    }
}
