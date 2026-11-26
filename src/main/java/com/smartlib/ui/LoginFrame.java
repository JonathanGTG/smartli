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
    private final JButton btnLogin = UiKit.accentButton("Masuk", Theme.NAVY_700);
    private final JButton btnRegister = new JButton("Belum punya akun? Daftar sebagai anggota");
    private final JLabel lblMessage = new JLabel(" ");

    public LoginFrame() {
        super("SmartLib - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUi();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildBrandSide(), BorderLayout.WEST);
        root.add(buildFormSide(), BorderLayout.CENTER);
        setContentPane(root);
        getRootPane().setDefaultButton(btnLogin);   // tekan Enter = login
        btnLogin.addActionListener(e -> doLogin());
        btnRegister.addActionListener(e -> openRegister());
        setSize(820, 480);
    }

    /** Sisi kiri: gradasi navy, nama aplikasi, dan deretan punggung buku. */
    private JPanel buildBrandSide() {
        JPanel p = new BrandPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setPreferredSize(new Dimension(340, 480));
        p.setBorder(BorderFactory.createEmptyBorder(48, 36, 36, 36));

        JLabel name = new JLabel("SmartLib");
        name.setFont(Theme.font(Font.BOLD, 38f));
        name.setForeground(Color.WHITE);
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel bar = new JLabel(" ");
        bar.setOpaque(true);
        bar.setBackground(Theme.GOLD);
        bar.setMaximumSize(new Dimension(48, 4));
        bar.setPreferredSize(new Dimension(48, 4));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tag = new JLabel("<html>Katalog, peminjaman, antrean, dan denda perpustakaan dalam satu aplikasi.</html>");
        tag.setFont(Theme.font(Font.PLAIN, 14f));
        tag.setForeground(new Color(0xC9D6EE));
        tag.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(name);
        p.add(Box.createVerticalStrut(12));
        p.add(bar);
        p.add(Box.createVerticalStrut(16));
        p.add(tag);
        return p;
    }

    /** Sisi kanan: form masuk. */
    private JPanel buildFormSide() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 48, 24, 48));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.weightx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;

        JLabel title = new JLabel("Masuk");
        title.setFont(Theme.font(Font.BOLD, 26f));
        title.setForeground(Theme.NAVY_900);
        gc.gridy = 0; gc.insets = new Insets(0, 0, 4, 0); panel.add(title, gc);

        JLabel sub = new JLabel("Gunakan akun yang terdaftar di perpustakaan.");
        sub.setFont(Theme.font(Font.PLAIN, 13f));
        sub.setForeground(Theme.MUTED);
        gc.gridy = 1; gc.insets = new Insets(0, 0, 24, 0); panel.add(sub, gc);

        gc.gridy = 2; gc.insets = new Insets(0, 0, 6, 0); panel.add(fieldLabel("Username"), gc);
        Theme.styleField(txtUsername);
        gc.gridy = 3; gc.insets = new Insets(0, 0, 16, 0); panel.add(txtUsername, gc);

        gc.gridy = 4; gc.insets = new Insets(0, 0, 6, 0); panel.add(fieldLabel("Password"), gc);
        Theme.styleField(txtPassword);
        gc.gridy = 5; gc.insets = new Insets(0, 0, 8, 0); panel.add(txtPassword, gc);

        lblMessage.setForeground(Theme.DANGER);
        lblMessage.setFont(Theme.font(Font.PLAIN, 12f));
        gc.gridy = 6; gc.insets = new Insets(0, 0, 10, 0); panel.add(lblMessage, gc);

        btnLogin.setFont(Theme.font(Font.BOLD, 14f));
        btnLogin.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        gc.gridy = 7; gc.insets = new Insets(0, 0, 10, 0); panel.add(btnLogin, gc);

        btnRegister.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        btnRegister.setContentAreaFilled(false);
        btnRegister.setBorderPainted(false);
        btnRegister.setFocusPainted(false);
        btnRegister.setForeground(Theme.BLUE);
        btnRegister.setFont(Theme.font(Font.PLAIN, 12f));
        btnRegister.setCursor(new Cursor(Cursor.HAND_CURSOR));
        gc.gridy = 8; gc.insets = new Insets(0, 0, 0, 0); gc.anchor = GridBagConstraints.CENTER;
        panel.add(btnRegister, gc);
        return panel;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 12f));
        l.setForeground(Theme.NAVY_800);
        return l;
    }

    /** Gradasi navy dengan punggung buku sebagai dekorasi di bagian bawah. */
    private static final class BrandPanel extends Theme.HeroPanel {
        BrandPanel() { super(Theme.NAVY_950, Theme.NAVY_700, true); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] heights = {120, 150, 100, 170, 130, 110, 160};
            Color[] cols = {new Color(255, 255, 255, 36), new Color(242, 177, 52, 170),
                    new Color(255, 255, 255, 56), new Color(255, 255, 255, 28),
                    new Color(47, 111, 222, 190), new Color(255, 255, 255, 44),
                    new Color(255, 255, 255, 24)};
            int x = 36, w = 30, gap = 6, base = getHeight() - 28;
            for (int i = 0; i < heights.length; i++) {
                g2.setColor(cols[i]);
                g2.fillRoundRect(x, base - heights[i], w, heights[i], 6, 6);
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fillRect(x + 6, base - heights[i] + 14, w - 12, 2);
                x += w + gap;
            }
            g2.setColor(new Color(255, 255, 255, 70));
            g2.fillRect(28, base, x - 28, 2);
            g2.dispose();
        }
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
