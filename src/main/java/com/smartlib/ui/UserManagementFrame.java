package com.smartlib.ui;

import com.smartlib.exception.AccessDeniedException;
import com.smartlib.exception.DataAccessException;
import com.smartlib.model.Permission;
import com.smartlib.model.Role;
import com.smartlib.model.User;
import com.smartlib.service.AuthService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/** Manajemen akun pengguna. Hanya dapat dibuka oleh role dengan Permission.MANAGE_USERS (Admin). */
public class UserManagementFrame extends JFrame {

    private final AuthService authService = AuthService.getInstance();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"ID", "Username", "Nama Lengkap", "Role", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(tableModel);
    private List<User> users = List.of();

    public UserManagementFrame() {
        super("SmartLib - Manajemen Pengguna");
        authService.requirePermission(Permission.MANAGE_USERS);   // cek di service, bukan hanya tombol

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(button("Tambah", this::onAdd));
        buttons.add(button("Ubah", this::onEdit));
        buttons.add(button("Aktifkan / Nonaktifkan", this::onToggleActive));
        buttons.add(button("Reset Password", this::onResetPassword));
        buttons.add(button("Refresh", this::reload));

        add(buttons, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        setSize(720, 420);
        setLocationRelativeTo(null);
        reload();
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void reload() {
        run(() -> {
            users = authService.listUsers();
            tableModel.setRowCount(0);
            for (User u : users) {
                tableModel.addRow(new Object[]{u.getId(), u.getUsername(), u.getFullName(),
                        u.getRole().getLabel(), u.getStatus().getLabel()});
            }
        });
    }

    private User selected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Pilih pengguna pada tabel terlebih dahulu.");
            return null;
        }
        return users.get(row);
    }

    private void onAdd() {
        JTextField tfUser = new JTextField(18);
        JTextField tfName = new JTextField(18);
        JPasswordField pfPass = new JPasswordField(18);
        JComboBox<Role> cbRole = new JComboBox<>(Role.values());
        JPanel form = form("Username", tfUser, "Nama lengkap", tfName, "Password", pfPass, "Role", cbRole);

        if (JOptionPane.showConfirmDialog(this, form, "Tambah Pengguna",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] pass = pfPass.getPassword();
        try {
            run(() -> {
                authService.createUser(tfUser.getText(), tfName.getText(), pass, (Role) cbRole.getSelectedItem());
                reload();
            });
        } finally {
            Arrays.fill(pass, '\0');
        }
    }

    private void onEdit() {
        User u = selected();
        if (u == null) return;
        JTextField tfName = new JTextField(u.getFullName(), 18);
        JComboBox<Role> cbRole = new JComboBox<>(Role.values());
        cbRole.setSelectedItem(u.getRole());
        JPanel form = form("Username", new JLabel(u.getUsername()), "Nama lengkap", tfName, "Role", cbRole);

        if (JOptionPane.showConfirmDialog(this, form, "Ubah Pengguna",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        run(() -> {
            authService.updateUser(u.getId(), tfName.getText(), (Role) cbRole.getSelectedItem());
            reload();
        });
    }

    private void onToggleActive() {
        User u = selected();
        if (u == null) return;
        boolean activate = !u.isActive();
        String verb = activate ? "mengaktifkan" : "menonaktifkan";
        if (JOptionPane.showConfirmDialog(this, "Yakin " + verb + " akun '" + u.getUsername() + "'?",
                "Konfirmasi", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        run(() -> {
            authService.setActive(u.getId(), activate);
            reload();
        });
    }

    private void onResetPassword() {
        User u = selected();
        if (u == null) return;
        JPasswordField pf = new JPasswordField(18);
        JPanel form = form("Password baru (" + u.getUsername() + ")", pf);
        if (JOptionPane.showConfirmDialog(this, form, "Reset Password",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        char[] pass = pf.getPassword();
        try {
            run(() -> {
                authService.resetPassword(u.getId(), pass);
                JOptionPane.showMessageDialog(this, "Password berhasil direset.");
            });
        } finally {
            Arrays.fill(pass, '\0');
        }
    }

    /** Menjalankan aksi dan menampilkan pesan error yang ramah bila gagal. */
    private void run(Runnable action) {
        try {
            action.run();
        } catch (AccessDeniedException | IllegalArgumentException | IllegalStateException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Tidak dapat diproses", JOptionPane.WARNING_MESSAGE);
        } catch (DataAccessException e) {
            JOptionPane.showMessageDialog(this, e.getMessage() + "\nPeriksa koneksi MySQL.",
                    "Kesalahan database", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Membuat panel form: pasangan (label, komponen) berurutan. */
    private static JPanel form(Object... labelAndField) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < labelAndField.length; i += 2) {
            gc.gridy = i / 2;
            gc.gridx = 0;
            p.add(new JLabel((String) labelAndField[i]), gc);
            gc.gridx = 1;
            p.add((Component) labelAndField[i + 1], gc);
        }
        return p;
    }
}
