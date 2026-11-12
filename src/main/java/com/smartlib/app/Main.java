package com.smartlib.app;

import com.smartlib.ui.LoginFrame;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // pakai tampilan bawaan Swing
            }
            new LoginFrame().setVisible(true);
        });
    }
}
