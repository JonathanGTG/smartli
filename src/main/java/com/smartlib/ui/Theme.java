package com.smartlib.ui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Pusat tema tampilan SmartLib (navy). Semua warna, font, dan komponen visual bersama
 * ada di sini supaya setiap layar terlihat seragam. Hanya urusan tampilan.
 */
public final class Theme {

    private Theme() { }

    // ---------- Palet ----------
    public static final Color NAVY_950 = new Color(0x081428);
    public static final Color NAVY_900 = new Color(0x0D1F3F);
    public static final Color NAVY_800 = new Color(0x14305E);
    public static final Color NAVY_700 = new Color(0x1D4380);
    public static final Color BLUE     = new Color(0x2F6FDE);
    public static final Color SKY      = new Color(0xDCE7FA);
    public static final Color BG       = new Color(0xF0F3F9);
    public static final Color CARD     = Color.WHITE;
    public static final Color TEXT     = new Color(0x16243D);
    public static final Color MUTED    = new Color(0x6A7A93);
    public static final Color LINE     = new Color(0xDAE1EE);
    public static final Color ROW_ALT  = new Color(0xF6F8FD);
    public static final Color GOLD     = new Color(0xF2B134);
    public static final Color DANGER   = new Color(0xC62828);
    public static final Color SUCCESS  = new Color(0x2E7D32);

    private static String fontFamily = Font.SANS_SERIF;

    /** Dipanggil sekali dari Main sebelum jendela apa pun dibuat. */
    public static void install() {
        fontFamily = pickFont("Segoe UI", "Inter", "Roboto", "Helvetica Neue", "SF Pro Text");
        Font base = new Font(fontFamily, Font.PLAIN, 13);

        UIManager.put("nimbusBase", NAVY_800);
        UIManager.put("nimbusBlueGrey", new Color(0x8DA2C6));
        UIManager.put("control", BG);
        UIManager.put("nimbusLightBackground", Color.WHITE);
        UIManager.put("text", TEXT);
        UIManager.put("nimbusSelectionBackground", BLUE);
        UIManager.put("nimbusSelectedText", Color.WHITE);
        UIManager.put("nimbusFocus", BLUE);
        UIManager.put("nimbusInfoBlue", NAVY_700);
        UIManager.put("defaultFont", base);

        UIManager.put("Table.alternateRowColor", ROW_ALT);
        UIManager.put("Table.showGrid", Boolean.FALSE);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
        UIManager.put("OptionPane.background", BG);
        UIManager.put("Panel.background", BG);
        UIManager.put("ToolTip.background", NAVY_900);
        UIManager.put("ToolTip.foreground", Color.WHITE);

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    // Nimbus menimpa sebagian key saat dipasang; terapkan ulang yang penting.
                    UIManager.put("Table.alternateRowColor", ROW_ALT);
                    UIManager.put("Table.showGrid", Boolean.FALSE);
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // pakai tampilan bawaan Swing
        }
    }

    private static String pickFont(String... names) {
        java.util.Set<String> have = new java.util.HashSet<>(java.util.Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String n : names) if (have.contains(n)) return n;
        return Font.SANS_SERIF;
    }

    public static Font font(int style, float size) {
        return new Font(fontFamily, style, 13).deriveFont(style, size);
    }

    // ---------- Komponen ----------

    /** Panel berlatar gradasi navy dengan lingkaran dekoratif tipis. */
    public static class HeroPanel extends JPanel {
        private final Color from, to;
        private final boolean decor;

        public HeroPanel(Color from, Color to, boolean decor) {
            this.from = from;
            this.to = to;
            this.decor = decor;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g2.setPaint(new GradientPaint(0, 0, from, w, h, to));
            g2.fillRect(0, 0, w, h);
            if (decor) {
                g2.setColor(new Color(255, 255, 255, 14));
                g2.fill(new Ellipse2D.Double(w * 0.55, -h * 0.55, h * 1.3, h * 1.3));
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fill(new Ellipse2D.Double(w * 0.78, h * 0.25, h * 1.0, h * 1.0));
                g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40));
                g2.fill(new Ellipse2D.Double(-h * 0.35, h * 0.7, h * 0.7, h * 0.7));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Kartu putih bersudut membulat dengan bayangan halus. */
    public static class CardPanel extends JPanel {
        private final int arc;

        public CardPanel(LayoutManager lm) { this(lm, 14); }

        public CardPanel(LayoutManager lm, int arc) {
            super(lm);
            this.arc = arc;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(3, 3, 5, 3));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            for (int i = 3; i >= 1; i--) {
                g2.setColor(new Color(13, 31, 63, 7 * (4 - i)));
                g2.fill(new RoundRectangle2D.Double(3 - i, 5 - i + 1, w - 6 + 2 * i, h - 8 + 2 * i, arc + i, arc + i));
            }
            g2.setColor(CARD);
            g2.fill(new RoundRectangle2D.Double(3, 3, w - 6, h - 8, arc, arc));
            g2.setColor(LINE);
            g2.draw(new RoundRectangle2D.Double(3.5, 3.5, w - 7, h - 9, arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Border bulat untuk kolom isian; berubah biru saat fokus. */
    public static class FieldBorder extends AbstractBorder {
        private final JComponent owner;

        public FieldBorder(JComponent owner) { this.owner = owner; }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focus = owner.hasFocus();
            g2.setColor(focus ? BLUE : new Color(0xC3CDDF));
            g2.setStroke(new BasicStroke(focus ? 2f : 1f));
            g2.draw(new RoundRectangle2D.Double(x + 1, y + 1, w - 2.5, h - 2.5, 10, 10));
            g2.dispose();
        }

        @Override public Insets getBorderInsets(Component c) { return new Insets(8, 12, 8, 12); }
    }

    /** Menata JTextField / JPasswordField supaya konsisten dengan tema. */
    public static void styleField(JTextField f) {
        f.setFont(font(Font.PLAIN, 14));
        f.setBorder(new FieldBorder(f));
        f.setOpaque(true);
        f.setBackground(Color.WHITE);
        f.setForeground(TEXT);
        f.setCaretColor(NAVY_700);
        f.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { f.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { f.repaint(); }
        });
    }

    // ---------- Tabel ----------

    /** Header tabel navy dengan teks putih tebal. */
    public static void styleTable(JTable t) {
        t.setRowHeight(30);
        t.setShowGrid(false);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(0xE9EEF6));
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setSelectionBackground(SKY);
        t.setSelectionForeground(NAVY_900);
        t.setFillsViewportHeight(true);
        t.setFont(font(Font.PLAIN, 13));
        t.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                                                                     boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                l.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return l;
            }
        });

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setPreferredSize(new Dimension(h.getPreferredSize().width, 34));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                                                                     boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
                l.setOpaque(true);
                l.setBackground(NAVY_800);
                l.setForeground(Color.WHITE);
                l.setFont(font(Font.BOLD, 12));
                l.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 2, 0, GOLD),
                        BorderFactory.createEmptyBorder(0, 10, 0, 10)));
                javax.swing.table.TableCellRenderer cr = table.getColumnModel().getColumn(column).getCellRenderer();
                boolean right = cr instanceof DefaultTableCellRenderer
                        && ((DefaultTableCellRenderer) cr).getHorizontalAlignment() == SwingConstants.RIGHT;
                l.setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return l;
            }
        });
    }

    /** Scroll pane tanpa bingkai bawaan, dengan garis tipis. */
    public static JScrollPane scroll(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createLineBorder(LINE));
        sp.getViewport().setBackground(Color.WHITE);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }
}
