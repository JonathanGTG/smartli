package com.smartlib.ui;

import com.smartlib.exception.AuthenticationException;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Tampilan untuk pengguna (termasuk Anggota Perpustakaan) mengelola akunnya sendiri:
 * melihat & mengubah profil (Nama, Program Studi, Email) dan mengganti password.
 */
public class ManageAccountFrame extends JFrame {

    private final AuthService authService = AuthService.getInstance();
    private final Runnable onUpdatedCallback;

    private final JTextField txtUsername = new JTextField(20);
    private final JTextField txtRole = new JTextField(20);
    private final JTextField txtStatus = new JTextField(20);
    private final JTextField txtNim = new JTextField(20);
    private final JTextField txtFullName = new JTextField(20);
    private final JTextField txtStudyProgram = new JTextField(20);
    private final JTextField txtEmail = new JTextField(20);

    private final JPasswordField txtOldPassword = new JPasswordField(20);
    private final JPasswordField txtNewPassword = new JPasswordField(20);
    private final JPasswordField txtConfirmNewPassword = new JPasswordField(20);

    public ManageAccountFrame() {
        this(null);
    }

    public ManageAccountFrame(Runnable onUpdatedCallback) {
        super("SmartLib - Kelola Akun Saya");
        this.onUpdatedCallback = onUpdatedCallback;

        authService.requireLogin();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        loadUserData();
        pack();
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        tabbedPane.addTab("Informasi Profil", buildProfilePanel());
        tabbedPane.addTab("Ganti Password", buildPasswordPanel());

        JPanel root = new JPanel(new BorderLayout());
        root.add(tabbedPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        JButton btnClose = new JButton("Tutup");
        btnClose.addActionListener(e -> dispose());
        bottomPanel.add(btnClose);
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildProfilePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 5, 5, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        txtUsername.setEditable(false);
        txtRole.setEditable(false);
        txtStatus.setEditable(false);
        txtNim.setEditable(false);

        // Styling read-only fields
        Color bgDisabled = new Color(245, 245, 245);
        txtUsername.setBackground(bgDisabled);
        txtRole.setBackground(bgDisabled);
        txtStatus.setBackground(bgDisabled);
        txtNim.setBackground(bgDisabled);

        int row = 0;
        addRow(form, gc, row++, "Username:", txtUsername);
        addRow(form, gc, row++, "Role:", txtRole);
        addRow(form, gc, row++, "Status Akun:", txtStatus);
        addRow(form, gc, row++, "NIM:", txtNim);
        addRow(form, gc, row++, "Nama Lengkap *:", txtFullName);
        addRow(form, gc, row++, "Program Studi *:", txtStudyProgram);
        addRow(form, gc, row++, "Email *:", txtEmail);

        panel.add(form, BorderLayout.CENTER);

        JButton btnSaveProfile = new JButton("Simpan Perubahan Profil");
        btnSaveProfile.setFont(btnSaveProfile.getFont().deriveFont(Font.BOLD));
        btnSaveProfile.addActionListener(e -> saveProfile());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.add(btnSaveProfile);
        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildPasswordPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 5, 6, 5);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow(form, gc, row++, "Password Saat Ini *:", txtOldPassword);
        addRow(form, gc, row++, "Password Baru *:", txtNewPassword);
        addRow(form, gc, row++, "Konfirmasi Password Baru *:", txtConfirmNewPassword);

        JLabel hint = new JLabel("Password baru minimal 8 karakter.");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        hint.setForeground(Color.GRAY);
        gc.gridx = 1;
        gc.gridy = row++;
        form.add(hint, gc);

        panel.add(form, BorderLayout.CENTER);

        JButton btnChangePassword = new JButton("Perbarui Password");
        btnChangePassword.setFont(btnChangePassword.getFont().deriveFont(Font.BOLD));
        btnChangePassword.addActionListener(e -> changePassword());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actionPanel.add(btnChangePassword);
        panel.add(actionPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void addRow(JPanel panel, GridBagConstraints gc, int row, String label, JComponent field) {
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0.0;
        panel.add(new JLabel(label), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        panel.add(field, gc);
    }

    private void loadUserData() {
        authService.getCurrentUser().ifPresent(u -> {
            txtUsername.setText(u.getUsername());
            txtRole.setText(u.getRole().getLabel());
            txtStatus.setText(u.getStatus().getLabel());
            txtNim.setText(u.getNim() != null ? u.getNim() : "-");
            txtFullName.setText(u.getFullName());
            txtStudyProgram.setText(u.getStudyProgram() != null ? u.getStudyProgram() : "");
            txtEmail.setText(u.getEmail() != null ? u.getEmail() : "");
        });
    }

    private void saveProfile() {
        final String newName = txtFullName.getText().trim();
        final String newProdi = txtStudyProgram.getText().trim();
        final String newEmail = txtEmail.getText().trim();

        if (newName.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nama lengkap tidak boleh kosong.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            User updated = authService.updateMyProfile(newName, newProdi, newEmail);
            JOptionPane.showMessageDialog(this, "Profil akun berhasil diperbarui!",
                    "Sukses", JOptionPane.INFORMATION_MESSAGE);
            loadUserData();
            if (onUpdatedCallback != null) {
                onUpdatedCallback.run();
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Gagal Menyimpan", JOptionPane.WARNING_MESSAGE);
        } catch (DataAccessException e) {
            JOptionPane.showMessageDialog(this, e.getMessage() + "\nPeriksa koneksi database MySQL.",
                    "Kesalahan Database", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void changePassword() {
        final char[] oldPass = txtOldPassword.getPassword();
        final char[] newPass = txtNewPassword.getPassword();
        final char[] confirmNew = txtConfirmNewPassword.getPassword();

        if (oldPass.length == 0) {
            JOptionPane.showMessageDialog(this, "Masukkan password Anda saat ini.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (newPass.length < 8) {
            JOptionPane.showMessageDialog(this, "Password baru minimal 8 karakter.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!Arrays.equals(newPass, confirmNew)) {
            JOptionPane.showMessageDialog(this, "Konfirmasi password baru tidak cocok.",
                    "Peringatan", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            authService.changePassword(oldPass, newPass);
            JOptionPane.showMessageDialog(this, "Password berhasil diubah!",
                    "Sukses", JOptionPane.INFORMATION_MESSAGE);
            txtOldPassword.setText("");
            txtNewPassword.setText("");
            txtConfirmNewPassword.setText("");
        } catch (AuthenticationException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Gagal Mengubah Password", JOptionPane.WARNING_MESSAGE);
        } catch (DataAccessException e) {
            JOptionPane.showMessageDialog(this, e.getMessage() + "\nPeriksa koneksi database MySQL.",
                    "Kesalahan Database", JOptionPane.ERROR_MESSAGE);
        } finally {
            Arrays.fill(oldPass, '\0');
            Arrays.fill(newPass, '\0');
            Arrays.fill(confirmNew, '\0');
        }
    }
}
