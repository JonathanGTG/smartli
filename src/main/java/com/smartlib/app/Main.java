package com.smartlib.app;

import com.smartlib.ui.LoginFrame;
import com.smartlib.ui.Theme;

import javax.swing.*;

public class Main {

    public   static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            new LoginFrame().setVisible(true);
        });
    }
}
