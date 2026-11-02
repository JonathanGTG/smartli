package com.smartlib.app;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {

        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame("SmartLib - Sistem Manajemen Perpustakaan");

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1000, 650);
            frame.setLocationRelativeTo(null);

            JLabel label = new JLabel(
                    "Selamat Datang di SmartLib",
                    SwingConstants.CENTER
            );

            label.setFont(new Font("Arial", Font.BOLD, 24));

            frame.add(label);
            frame.setVisible(true);

        });
    }
}
