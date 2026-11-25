package com.smartlib.ui;

import com.smartlib.exception.AccessDeniedException;
import com.smartlib.exception.DataAccessException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.function.Supplier;

/**
 * Perangkat bantu kecil untuk form-form modul koleksi dan anggota, supaya kode yang sama
 * (membuat tombol, menampilkan pesan error, membuat form, membuat tabel) tidak ditulis berulang.
 * Class ini hanya berisi urusan tampilan; tidak ada query dan tidak ada aturan bisnis.
 */
final class UiKit {

    private UiKit() { }

    /**
     * Tombol berwarna yang menggambar latar sendiri, sehingga warnanya tetap tampil di semua
     * Look and Feel (Windows LAF mengabaikan setBackground dan membuat teks putih tak terbaca).
     */
    static JButton accentButton(String text, Color bg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = !isEnabled() ? new Color(176, 176, 176)
                        : getModel().isPressed() ? bg.darker().darker()
                        : getModel().isRollover() ? bg.darker() : bg;
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setForeground(Color.WHITE);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    static JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    /**
     * Menjalankan aksi dan menampilkan pesan yang ramah bila gagal.
     *
     * @return true bila aksi berhasil tanpa error
     */
    static boolean run(Component parent, Runnable action) {
        try {
            action.run();
            return true;
        } catch (AccessDeniedException | IllegalArgumentException | IllegalStateException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Tidak dapat diproses", JOptionPane.WARNING_MESSAGE);
        } catch (DataAccessException e) {
            JOptionPane.showMessageDialog(parent, e.getMessage() + "\nPeriksa koneksi MySQL dan struktur database.",
                    "Kesalahan database", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    /** Seperti run(), tetapi mengembalikan hasil; null bila terjadi error (pesan sudah ditampilkan). */
    static <T> T call(Component parent, Supplier<T> action) {
        Object[] holder = new Object[1];
        boolean ok = run(parent, () -> holder[0] = action.get());
        @SuppressWarnings("unchecked")
        T result = ok ? (T) holder[0] : null;
        return result;
    }

    /**
     * Menampilkan form di dalam dialog OK/Batal. Bila data tidak valid, pesan error ditampilkan dan
     * dialog dibuka lagi dengan isian yang sama, sehingga pengguna tidak perlu mengetik ulang.
     *
     * @return true bila data berhasil disimpan, false bila pengguna membatalkan
     */
    static boolean editDialog(Component parent, String title, JComponent form, Runnable save) {
        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, form, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return false;
            if (run(parent, save)) return true;
        }
    }

    static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Konfirmasi",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message);
    }

    /** Panel form: pasangan (label, komponen) berurutan. */
    static JPanel form(Object... labelAndField) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.NORTHWEST;
        for (int i = 0; i < labelAndField.length; i += 2) {
            gc.gridy = i / 2;
            gc.gridx = 0;
            p.add(new JLabel((String) labelAndField[i]), gc);
            gc.gridx = 1;
            p.add((Component) labelAndField[i + 1], gc);
        }
        return p;
    }

    /** Model tabel yang tidak bisa diedit langsung dan mengurutkan angka sebagai angka. */
    static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }

            @Override public Class<?> getColumnClass(int column) {
                Object first = getRowCount() > 0 ? getValueAt(0, column) : null;
                return first == null ? Object.class : first.getClass();
            }
        };
    }

    /** Tabel satu-baris-terpilih; klik judul kolom untuk mengurutkan. */
    static JTable table(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setRowHeight(24);
        t.setAutoCreateRowSorter(true);
        return t;
    }

    /** Indeks baris pada model untuk baris terpilih (memperhitungkan pengurutan), atau -1. */
    static int selectedModelRow(JTable table) {
        int row = table.getSelectedRow();
        return row < 0 ? -1 : table.convertRowIndexToModel(row);
    }
}