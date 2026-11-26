package com.smartlib.ui;

import com.smartlib.model.User;

import javax.swing.*;

/**
 * Pintu masuk setelah login. Semua role (Admin, Petugas, Anggota) memakai DashboardFrame
 * yang sama; isi sidebar dan ringkasannya menyesuaikan hak akses masing-masing role.
 */
public final class DashboardRouter {

    private DashboardRouter() { }

    public static void open(User user) {
        SwingUtilities.invokeLater(() -> new DashboardFrame(user).setVisible(true));
    }
}
