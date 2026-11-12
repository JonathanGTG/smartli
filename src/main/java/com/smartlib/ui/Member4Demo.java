package com.smartlib.ui;

import javax.swing.SwingUtilities;

/** Jalankan class ini untuk melihat menu demo modul Anggota 4. */
public class Member4Demo {
    private Member4Demo() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Member4MenuFrame().setVisible(true));
    }
}
