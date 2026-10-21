package dev.mx.launcher;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 深色主题的 ComboBox UI：自绘箭头、去掉系统默认的白底，配色与启动器一致。
 */
final class DarkComboUI extends BasicComboBoxUI {

    private static final Color BG     = new Color(0x18, 0x1B, 0x23);
    private static final Color BG_HI  = new Color(0x22, 0x27, 0x31);
    private static final Color TEXT   = new Color(0x84, 0x8F, 0xA0);
    private static final Color LINE   = new Color(0x28, 0x2D, 0x38);

    @Override
    protected JButton createArrowButton() {
        JButton b = new JButton() {
            private boolean hover;

            {
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorder(null);
                setOpaque(false);
                setPreferredSize(new Dimension(30, 30));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // 分隔线
                g2.setColor(LINE);
                g2.drawLine(0, 6, 0, h - 6);

                // 箭头（向下三角）
                g2.setColor(hover ? new Color(0xEC, 0xF1, 0xF8) : TEXT);
                int cx = w / 2;
                int cy = h / 2;
                int[] xs = {cx - 5, cx + 5, cx};
                int[] ys = {cy - 2, cy - 2, cy + 4};
                g2.fillPolygon(xs, ys, 3);

                g2.dispose();
            }
        };
        b.setBackground(BG);
        return b;
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds,
                                            boolean hasFocus) {
        g.setColor(BG);
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    @Override
    protected ComboPopup createPopup() {
        BasicComboPopup popup = new BasicComboPopup(comboBox) {
            @Override
            protected JList<Object> createList() {
                JList<Object> list = super.createList();
                list.setBackground(BG_HI);
                list.setForeground(Color.WHITE);
                list.setSelectionBackground(new Color(0x5C, 0xC8, 0xA4));
                list.setSelectionForeground(new Color(0x0B, 0x14, 0x10));
                list.setBorder(null);
                return list;
            }
        };
        popup.setBorder(javax.swing.BorderFactory.createLineBorder(LINE));
        return popup;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        // 自己画背景与边框，避免系统 LAF 覆盖
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(c.getBackground() == null ? BG : BG);
        g2.fillRect(0, 0, c.getWidth(), c.getHeight());
        g2.setColor(LINE);
        g2.drawRect(0, 0, c.getWidth() - 1, c.getHeight() - 1);
        g2.dispose();

        // 再画当前选中值
        super.paint(g, c);
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        comboBox.setOpaque(true);
        comboBox.setBackground(BG);
        comboBox.setForeground(new Color(0xEC, 0xF1, 0xF8));
        comboBox.setBorder(null);
    }

    @Override
    protected void uninstallDefaults() {
        comboBox.setBorder(null);
        super.uninstallDefaults();
    }
}
