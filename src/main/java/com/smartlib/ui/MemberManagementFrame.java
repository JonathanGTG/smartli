package com.smartlib.ui;

import com.smartlib.enums.AccountStatus;
import com.smartlib.model.Member;
import com.smartlib.model.Permission;
import com.smartlib.service.AuthService;
import com.smartlib.service.MemberService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Manajemen anggota perpustakaan: lihat, cari, tambah, ubah, hapus, aktif/nonaktif.
 * Hanya untuk role dengan Permission.MANAGE_MEMBERS (Admin dan Petugas).
 * Anggota di sini adalah data keanggotaan, BUKAN akun login (akun login dikelola di Manajemen Pengguna).
 */
public class MemberManagementFrame extends JFrame {

    private static final String ALL_STATUS = "Semua status";

    private final MemberService service = new MemberService();
    private final JTextField tfSearch = new JTextField(20);
    private final JComboBox<Object> cbStatus = new JComboBox<>(
            new Object[]{ALL_STATUS, AccountStatus.ACTIVE, AccountStatus.INACTIVE});
    private final DefaultTableModel model = UiKit.model("Kode", "Nama", "Email", "Telepon", "Alamat",
            "Bergabung", "Status", "Akun Login");
    private final JTable table = UiKit.table(model);
    private final JLabel statusLabel = new JLabel(" ");
    private List<Member> members = List.of();

    public MemberManagementFrame() {
        super("SmartLib - Manajemen Anggota");
        AuthService.getInstance().requirePermission(Permission.MANAGE_MEMBERS);   // cek di service, bukan hanya tombol

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        search.setBorder(BorderFactory.createTitledBorder("Pencarian (kode, nama, email, telepon, alamat)"));
        search.add(tfSearch);
        search.add(cbStatus);
        search.add(UiKit.button("Cari", this::reload));
        search.add(UiKit.button("Reset", () -> {
            tfSearch.setText("");
            cbStatus.setSelectedIndex(0);
            reload();
        }));
        tfSearch.addActionListener(e -> reload());

        int[] widths = {80, 160, 210, 110, 150, 90, 70, 110};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.add(UiKit.button("Tambah Anggota", this::onAdd));
        buttons.add(UiKit.button("Ubah", this::onEdit));
        buttons.add(UiKit.button("Hapus", this::onDelete));
        buttons.add(UiKit.button("Aktifkan / Nonaktifkan", this::onToggleActive));
        buttons.add(UiKit.button("Refresh", this::reload));

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        center.add(search, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(south, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        setSize(1050, 520);
        setLocationRelativeTo(null);
        reload();
    }

    private void reload() {
        AccountStatus status = cbStatus.getSelectedItem() instanceof AccountStatus s ? s : null;
        List<Member> result = UiKit.call(this, () -> service.search(tfSearch.getText(), status));
        Map<Integer, String> usernames = UiKit.call(this, service::linkedUsernames);
        if (result == null || usernames == null) return;
        members = result;
        model.setRowCount(0);
        for (Member m : members) {
            model.addRow(new Object[]{m.getMemberCode(), m.getFullName(), m.getEmail(), m.getPhone(),
                    m.getAddress(), m.getJoinDate().toString(), m.getStatus().getLabel(),
                    m.getUserId() == null ? "-" : usernames.getOrDefault(m.getUserId(), "-")});
        }
        statusLabel.setText(" " + members.size() + " anggota ditemukan.");
    }

    private Member selected() {
        int row = UiKit.selectedModelRow(table);
        if (row < 0) {
            UiKit.info(this, "Pilih anggota pada tabel terlebih dahulu.");
            return null;
        }
        return members.get(row);
    }

    // ------------------------------------------------------------------ aksi tombol

    private void onAdd() {
        String suggested = UiKit.call(this, service::suggestMemberCode);
        if (suggested == null) return;
        if (showForm(null, suggested)) reload();
    }

    private void onEdit() {
        Member m = selected();
        if (m != null && showForm(m, m.getMemberCode())) reload();
    }

    private void onDelete() {
        Member m = selected();
        if (m == null) return;
        String message = "Hapus anggota " + m.getMemberCode() + " - " + m.getFullName() + "?\n"
                + "Jika anggota ini pernah meminjam buku, anggota hanya akan dinonaktifkan agar riwayat tetap utuh.\n"
                + "Anggota yang masih memiliki pinjaman aktif tidak dapat dihapus.";
        if (!UiKit.confirm(this, message)) return;
        MemberService.DeleteResult result = UiKit.call(this, () -> service.deleteMember(m.getId()));
        if (result == null) return;
        UiKit.info(this, result == MemberService.DeleteResult.DELETED
                ? "Anggota berhasil dihapus."
                : "Anggota memiliki riwayat peminjaman, sehingga hanya dinonaktifkan (data tetap tersimpan).");
        reload();
    }

    private void onToggleActive() {
        Member m = selected();
        if (m == null) return;
        boolean activate = !m.isActive();
        if (!UiKit.confirm(this, "Yakin " + (activate ? "mengaktifkan" : "menonaktifkan") + " anggota "
                + m.getFullName() + "?")) return;
        if (UiKit.run(this, () -> service.setActive(m.getId(), activate))) reload();
    }

    // ------------------------------------------------------------------ form tambah / ubah

    /**
     * Form anggota. existing = null untuk anggota baru. Mengembalikan true bila tersimpan.
     */
    private boolean showForm(Member existing, String code) {
        Map<Integer, String> usernames = UiKit.call(this, service::linkedUsernames);
        if (usernames == null) return false;

        JTextField tfCode = new JTextField(code, 20);
        JTextField tfName = new JTextField(existing == null ? "" : existing.getFullName(), 24);
        JTextField tfEmail = new JTextField(existing == null ? "" : existing.getEmail(), 24);
        JTextField tfPhone = new JTextField(existing == null ? "" : existing.getPhone(), 24);
        JTextField tfAddress = new JTextField(existing == null ? "" : existing.getAddress(), 24);
        JTextField tfJoin = new JTextField(existing == null ? LocalDate.now().toString()
                : existing.getJoinDate().toString(), 12);
        String currentUser = existing == null || existing.getUserId() == null ? ""
                : usernames.getOrDefault(existing.getUserId(), "");
        JTextField tfUsername = new JTextField(currentUser, 20);
        JCheckBox chkAutoUser = new JCheckBox("Buatkan Akun Login Otomatis (Password: SmartLib#123)", true);

        JPanel form = existing == null
                ? UiKit.form(
                        "NIM / Kode Anggota *", tfCode,
                        "Nama lengkap *", tfName,
                        "Email", tfEmail,
                        "Telepon", tfPhone,
                        "Alamat", tfAddress,
                        "Tanggal bergabung (yyyy-MM-dd) *", tfJoin,
                        "Username akun login (opsional)", tfUsername,
                        "", chkAutoUser)
                : UiKit.form(
                        "NIM / Kode Anggota *", tfCode,
                        "Nama lengkap *", tfName,
                        "Email", tfEmail,
                        "Telepon", tfPhone,
                        "Alamat", tfAddress,
                        "Tanggal bergabung (yyyy-MM-dd) *", tfJoin);

        return UiKit.editDialog(this, existing == null ? "Tambah Anggota" : "Ubah Anggota", form, () -> {
            LocalDate joinDate = MemberService.parseDate(tfJoin.getText());
            if (existing == null) {
                String uname = tfUsername.getText().trim();
                if (uname.isEmpty() && chkAutoUser.isSelected()) {
                    uname = tfCode.getText().toLowerCase().replaceAll("[^a-z0-9]", "");
                }
                service.addMember(tfCode.getText(), tfName.getText(), tfEmail.getText(), tfPhone.getText(),
                        tfAddress.getText(), joinDate, uname, chkAutoUser.isSelected());
            } else {
                service.updateMember(existing.getId(), tfCode.getText(), tfName.getText(), tfEmail.getText(),
                        tfPhone.getText(), tfAddress.getText(), joinDate, currentUser);
            }
        });
    }
}