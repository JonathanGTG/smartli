package com.smartlib.ui;

import com.smartlib.exception.AuthenticationException;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/** Form login SmartLib. Query database dijalankan di SwingWorker agar UI tidak membeku. */
public class LoginFrame extends JFrame {

    private final AuthService authService = AuthService.getInstance();
    private final JTextField txtUsername = new JTextField(20);
    private final JPasswordField txtPassword = new JPasswordField(20);
    private final JButton btnLogin = new JButton("Login");
    private final JButton btnRegister = new JButton("Daftar Akun Anggota");
    private final JLabel lblMessage = new JLabel(" ");

    public LoginFrame() {
        super("SmartLib - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUi();
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("SmartLib", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        JLabel subtitle = new JLabel("Smart Library Management System", SwingConstants.CENTER);

        gc.gridx = 0; gc.gridy = 0; gc.gridwidth = 2; panel.add(title, gc);
        gc.gridy = 1; panel.add(subtitle, gc);

        gc.gridwidth = 1;
        gc.gridy = 2; gc.gridx = 0; panel.add(new JLabel("Username"), gc);
        gc.gridx = 1; panel.add(txtUsername, gc);
        gc.gridy = 3; gc.gridx = 0; panel.add(new JLabel("Password"), gc);
        gc.gridx = 1; panel.add(txtPassword, gc);

        lblMessage.setForeground(new Color(0xB00020));
        lblMessage.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridy = 4; gc.gridx = 0; gc.gridwidth = 2; panel.add(lblMessage, gc);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        btnPanel.add(btnLogin);
        btnPanel.add(btnRegister);

        gc.gridy = 5; panel.add(btnPanel, gc);

        setContentPane(panel);
        getRootPane().setDefaultButton(btnLogin);   // tekan Enter = login
        btnLogin.addActionListener(e -> doLogin());
        btnRegister.addActionListener(e -> openRegister());
    }

    private void openRegister() {
        new RegisterFrame(this).setVisible(true);
        setVisible(false);
    }

    public void setPrefilledUsername(String username) {
        txtUsername.setText(username);
        txtPassword.setText("");
        txtPassword.requestFocusInWindow();
    }

    private void doLogin() {
        final String username = txtUsername.getText();
        final char[] password = txtPassword.getPassword();
        setBusy(true);

        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                return authService.login(username, password);
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                setBusy(false);
                try {
                    User user = get();
                    txtPassword.setText("");
                    dispose();
                    DashboardRouter.open(user);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(messageFor(e.getCause()));
                    txtPassword.setText("");
                    txtPassword.requestFocusInWindow();
                }
            }
        }.execute();
    }

    private String messageFor(Throwable cause) {
        if (cause instanceof AuthenticationException) return cause.getMessage();
        if (cause instanceof DataAccessException) {
            return "Tidak dapat terhubung ke database. Pastikan MySQL berjalan.";
        }
        return "Terjadi kesalahan: " + cause.getMessage();
    }

    private void showError(String message) { lblMessage.setText(message); }

    private void setBusy(boolean busy) {
        btnLogin.setEnabled(!busy);
        btnRegister.setEnabled(!busy);
        txtUsername.setEnabled(!busy);
        txtPassword.setEnabled(!busy);
        if (busy) lblMessage.setText(" ");
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }
}
