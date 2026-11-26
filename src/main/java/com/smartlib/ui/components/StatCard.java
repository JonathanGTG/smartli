package com.smartlib.ui.components;

import com.smartlib.ui.Theme;

import javax.swing.*;
import java.awt.*;

/** Kartu ringkasan: label kecil di atas, angka besar di bawahnya, dengan strip aksen di kiri. */
public class StatCard extends Theme.CardPanel {

    private final JLabel valueLabel = new JLabel("-");
    private final Color accent;

    public StatCard(String title) {
        this(title, Theme.BLUE);
    }

    public StatCard(String title, Color accent) {
        super(new BorderLayout(0, 4), 14);
        this.accent = accent;

        JPanel inner = new JPanel(new BorderLayout(0, 4));
        inner.setOpaque(false);
        inner.setBorder(BorderFactory.createEmptyBorder(14, 22, 14, 14));

        JLabel name = new JLabel(title);
        name.setFont(Theme.font(Font.PLAIN, 12f));
        name.setForeground(Theme.MUTED);

        valueLabel.setFont(Theme.font(Font.BOLD, 28f));
        valueLabel.setForeground(Theme.NAVY_900);

        inner.add(name, BorderLayout.NORTH);
        inner.add(valueLabel, BorderLayout.CENTER);
        add(inner, BorderLayout.CENTER);
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(accent);
        g2.fillRoundRect(3, 14, 5, getHeight() - 36, 5, 5);
        g2.dispose();
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    /** Menandai angka dengan warna merah (mis. ada keterlambatan) atau mengembalikannya ke normal. */
    public void setHighlight(boolean on) {
        valueLabel.setForeground(on ? Theme.DANGER : Theme.NAVY_900);
    }
}
