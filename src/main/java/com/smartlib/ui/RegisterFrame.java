package com.smartlib.ui;

import com.smartlib.exception.DataAccessException;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

/**
 * Form registrasi mandiri untuk calon anggota perpustakaan.
 * Meminta input: Nama Lengkap, Program Studi, NIM, Email, serta Password.
 */
public class RegisterFrame extends JFrame {

    private final AuthService authService = AuthService.getInstance();
    private final LoginFrame parentLoginFrame;

    private final JTextField txtNim = new JTextField(22);
    private final JTextField txtFullName = new JTextField(22);
    private final JComboBox<String> cbStudyProgram;
    private final JTextField txtEmail = new JTextField(22);
    private final JTextField txtUsername = new JTextField(22);
    private final JPasswordField txtPassword = new JPasswordField(22);
    private final JPasswordField txtConfirmPassword = new JPasswordField(22);

    private final JButton btnRegister = new JButton("Daftar Sekarang");
    private final JButton btnCancel = new JButton("Kembali ke Login");
    private final JLabel lblMessage = new JLabel(" ");

    private static final String[] POPULAR_STUDY_PROGRAMS = {
            "Teknik Informatika",
            "Sistem Informasi",
            "Teknik Komputer",
            "Teknik Elektro",
            "Teknik Mesin",
            "Teknik Industri",
            "Teknik Sipil",
            "Manajemen",
            "Akuntansi",
            "Ilmu Komunikasi",
            "Hukum",
            "Psikologi",
            "Pendidikan Bahasa",
            "Desain Komunikasi Visual"
    };

    public RegisterFrame() {
        this(null);
    }

    public RegisterFrame(LoginFrame parentLoginFrame) {
        super("SmartLib - Registrasi Anggota Perpustakaan");
        this.parentLoginFrame = parentLoginFrame;

        cbStudyProgram = new JComboBox<>(POPULAR_STUDY_PROGRAMS);
        cbStudyProgram.setEditable(true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Pendaftaran Anggota Perpustakaan");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("Lengkapi data di bawah ini untuk mendaftarkan akun SmartLib");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        subtitle.setForeground(Color.DARK_GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerPanel.add(title);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subtitle);
        headerPanel.add(Box.createVerticalStrut(12));

        root.add(headerPanel, BorderLayout.NORTH);

        // Form Fields
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 6, 5, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addFormRow(form, gc, row++, "NIM/NIP *", txtNim, "Contoh: 210001 (minimal 4 karakter)");
        addFormRow(form, gc, row++, "Nama Lengkap *", txtFullName, "Nama lengkap sesuai kartu mahasiswa");
        addFormRow(form, gc, row++, "Program Studi *", cbStudyProgram, "Pilih atau ketik program studi Anda");
        addFormRow(form, gc, row++, "Email *", txtEmail, "Contoh: nama@gmail.com");
        addFormRow(form, gc, row++, "Username", txtUsername, "Opsional (otomatis terisi NIM bila kosong)");
        addFormRow(form, gc, row++, "Password *", txtPassword, "Minimal 8 karakter");
        addFormRow(form, gc, row++, "Konfirmasi Password *", txtConfirmPassword, "Ulangi password di atas");

        lblMessage.setForeground(new Color(0xB00020));
        lblMessage.setHorizontalAlignment(SwingConstants.CENTER);
        gc.gridx = 0;
        gc.gridy = row++;
        gc.gridwidth = 2;
        form.add(lblMessage, gc);

        root.add(form, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRegister.setFont(btnRegister.getFont().deriveFont(Font.BOLD));
        buttonPanel.add(btnCancel);
        buttonPanel.add(btnRegister);

        root.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(btnRegister);

        btnRegister.addActionListener(e -> doRegister());
        btnCancel.addActionListener(e -> onCancel());
    }

    private void addFormRow(JPanel panel, GridBagConstraints gc, int row,
                            String labelText, JComponent field, String hintText) {
        gc.gridwidth = 1;
        gc.gridx = 0;
        gc.gridy = row;
        gc.weightx = 0.0;
        JLabel lbl = new JLabel(labelText);
        panel.add(lbl, gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        JPanel fieldBox = new JPanel(new BorderLayout(0, 2));
        fieldBox.add(field, BorderLayout.CENTER);
        if (hintText != null) {
            JLabel hint = new JLabel(hintText);
            hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
            hint.setForeground(Color.GRAY);
            fieldBox.add(hint, BorderLayout.SOUTH);
        }
        panel.add(fieldBox, gc);
    }

    private void doRegister() {
        final String nim = txtNim.getText().trim();
        final String fullName = txtFullName.getText().trim();
        Object prodiObj = cbStudyProgram.getSelectedItem();
        final String studyProgram = (prodiObj != null) ? prodiObj.toString().trim() : "";
        final String email = txtEmail.getText().trim();
        final String username = txtUsername.getText().trim();
        final char[] password = txtPassword.getPassword();
        final char[] confirmPassword = txtConfirmPassword.getPassword();

        // Validasi awal di UI
        if (nim.isEmpty()) {
            showError("NIM/NIP tidak boleh kosong.");
            txtNim.requestFocusInWindow();
            return;
        }
        if (fullName.isEmpty()) {
            showError("Nama lengkap tidak boleh kosong.");
            txtFullName.requestFocusInWindow();
            return;
        }
        if (studyProgram.isEmpty()) {
            showError("Program studi tidak boleh kosong.");
            cbStudyProgram.requestFocusInWindow();
            return;
        }
        if (email.isEmpty()) {
            showError("Email tidak boleh kosong.");
            txtEmail.requestFocusInWindow();
            return;
        }
        if (password.length == 0) {
            showError("Password tidak boleh kosong.");
            txtPassword.requestFocusInWindow();
            return;
        }
        if (password.length < 8) {
            showError("Password minimal 8 karakter.");
            txtPassword.requestFocusInWindow();
            return;
        }
        if (!Arrays.equals(password, confirmPassword)) {
            showError("Konfirmasi password tidak cocok.");
            txtConfirmPassword.requestFocusInWindow();
            return;
        }

        setBusy(true);

        new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                return authService.registerMember(nim, fullName, studyProgram, email, username, password);
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                Arrays.fill(confirmPassword, '\0');
                setBusy(false);
                try {
                    User registered = get();
                    JOptionPane.showMessageDialog(RegisterFrame.this,
                            "Pendaftaran akun anggota perpustakaan berhasil!\n\n"
                                    + "Username: " + registered.getUsername() + "\n"
                                    + "Nama: " + registered.getFullName() + "\n"
                                    + "NIM: " + registered.getNim() + "\n"
                                    + "Program Studi: " + registered.getStudyProgram() + "\n\n"
                                    + "Silakan login menggunakan username dan password Anda.",
                            "Registrasi Berhasil", JOptionPane.INFORMATION_MESSAGE);

                    dispose();
                    if (parentLoginFrame != null) {
                        parentLoginFrame.setPrefilledUsername(registered.getUsername());
                        parentLoginFrame.setVisible(true);
                    } else {
                        new LoginFrame().setVisible(true);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    showError(messageFor(e.getCause()));
                }
            }
        }.execute();
    }

    private String messageFor(Throwable cause) {
        if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
            return cause.getMessage();
        }
        if (cause instanceof DataAccessException) {
            return "Tidak dapat terhubung ke database. Pastikan MySQL berjalan.";
        }
        return "Terjadi kesalahan: " + cause.getMessage();
    }

    private void showError(String message) {
        lblMessage.setText(message);
    }

    private void onCancel() {
        dispose();
        if (parentLoginFrame != null) {
            parentLoginFrame.setVisible(true);
        } else {
            new LoginFrame().setVisible(true);
        }
    }

    private void setBusy(boolean busy) {
        btnRegister.setEnabled(!busy);
        btnCancel.setEnabled(!busy);
        txtNim.setEnabled(!busy);
        txtFullName.setEnabled(!busy);
        cbStudyProgram.setEnabled(!busy);
        txtEmail.setEnabled(!busy);
        txtUsername.setEnabled(!busy);
        txtPassword.setEnabled(!busy);
        txtConfirmPassword.setEnabled(!busy);
        if (busy) lblMessage.setText(" ");
        setCursor(busy ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }
}
