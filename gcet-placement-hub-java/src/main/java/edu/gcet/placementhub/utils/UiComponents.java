package edu.gcet.placementhub.utils;

import edu.gcet.placementhub.config.ThemeManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Reusable Swing + FlatLaf UI primitives matching GCET Placement Hub React cards, buttons, badges, and inputs.
 */
public final class UiComponents {

    private UiComponents() {
    }

    /**
     * Rounded surface card with crisp border and subtle hover border highlight.
     */
    public static class RoundedCard extends JPanel {
        private int radius;
        private Color fillColor;
        private Color borderColor;
        private Color hoverBorderColor;
        private float borderThickness;
        private boolean hovered = false;

        public RoundedCard(int radius, Color fillColor, Color borderColor) {
            this(radius, fillColor, borderColor, null, 1.0f);
        }

        public RoundedCard(int radius, Color fillColor, Color borderColor, Color hoverBorderColor, float borderThickness) {
            this.radius = radius;
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.hoverBorderColor = hoverBorderColor;
            this.borderThickness = borderThickness;
            setOpaque(false);

            if (hoverBorderColor != null) {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }
        }

        public void setColors(Color fillColor, Color borderColor) {
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Shape round = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius, radius);
            if (fillColor != null) {
                g2.setColor(fillColor);
                g2.fill(round);
            }
            Color stroke = (hovered && hoverBorderColor != null) ? hoverBorderColor : borderColor;
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(borderThickness));
                g2.draw(round);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Styled institutional button with rounded corners and hover/press feedback.
     */
    public static class ActionButton extends JButton {
        private Color baseBg;
        private Color hoverBg;
        private Color borderCol;
        private int arc;

        public ActionButton(String text, Color baseBg, Color hoverBg, Color textColor) {
            this(text, baseBg, hoverBg, textColor, null, 12);
        }

        public ActionButton(String text, Color baseBg, Color hoverBg, Color textColor, Color borderCol, int arc) {
            super(text);
            this.baseBg = baseBg;
            this.hoverBg = hoverBg;
            this.borderCol = borderCol;
            this.arc = arc;
            setForeground(textColor);
            setFont(ThemeManager.font(Font.BOLD, 12));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(9, 14, 9, 14));
        }

        public void updateColors(Color baseBg, Color hoverBg, Color textColor, Color borderCol) {
            this.baseBg = baseBg;
            this.hoverBg = hoverBg;
            this.borderCol = borderCol;
            setForeground(textColor);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Color bg = !isEnabled()
                    ? new Color(baseBg.getRed(), baseBg.getGreen(), baseBg.getBlue(), 120)
                    : (getModel().isPressed() || getModel().isRollover()) ? hoverBg : baseBg;
            Shape rect = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc);
            g2.setColor(bg);
            g2.fill(rect);
            if (borderCol != null) {
                g2.setColor(borderCol);
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(rect);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Compact pill/badge label matching Tailwind rounded-full / rounded-md badges.
     */
    public static class BadgeLabel extends JLabel {
        private final int arc;
        private Color bgColor;
        private Color borderColor;

        public BadgeLabel(String text, Color bgColor, Color textColor, Color borderColor, int arc) {
            super(text);
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            this.arc = arc;
            setForeground(textColor);
            setFont(ThemeManager.font(Font.BOLD, 10.5f));
            setOpaque(false);
            setBorder(new EmptyBorder(3, 9, 3, 9));
        }

        public void setBadgeColors(Color bgColor, Color textColor, Color borderColor) {
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            setForeground(textColor);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape rect = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, arc, arc);
            if (bgColor != null) {
                g2.setColor(bgColor);
                g2.fill(rect);
            }
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(1.0f));
                g2.draw(rect);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Rounded icon square badge (e.g. 40x40 / 48x48 portal & feature card icons).
     */
    public static JPanel createIconBadge(String glyph, Color bg, Color fg, int boxSize, int fontSize) {
        RoundedCard box = new RoundedCard(12, bg, null);
        box.setPreferredSize(new Dimension(boxSize, boxSize));
        box.setMinimumSize(new Dimension(boxSize, boxSize));
        box.setMaximumSize(new Dimension(boxSize, boxSize));
        box.setLayout(new GridBagLayout());
        JLabel lbl = new JLabel(glyph);
        lbl.setForeground(fg);
        lbl.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
        box.add(lbl);
        return box;
    }

    /**
     * Styled text input with placeholder support.
     */
    public static JTextField createStyledTextField(String placeholder) {
        JTextField field = new JTextField();
        field.setFont(ThemeManager.font(Font.PLAIN, 13));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.putClientProperty("FlatLaf.style", "arc: 12");
        field.setPreferredSize(new Dimension(200, 38));
        return field;
    }

    /**
     * Password input container that is ALWAYS empty on initialization, masked by default,
     * and includes an Eye / Hide toggle button matching LoginPage.tsx.
     */
    public static class PasswordInputField extends JPanel {
        private final JPasswordField passwordField;
        private final JButton toggleButton;
        private final char defaultEchoChar;
        private boolean visible = false;

        public PasswordInputField(String placeholder) {
            super(new BorderLayout(4, 0));
            setOpaque(false);

            passwordField = new JPasswordField();
            passwordField.setText(""); // Always empty by default
            passwordField.setFont(ThemeManager.font(Font.PLAIN, 13));
            passwordField.putClientProperty("JTextField.placeholderText", placeholder);
            passwordField.putClientProperty("FlatLaf.style", "arc: 12");
            passwordField.setPreferredSize(new Dimension(200, 38));
            defaultEchoChar = passwordField.getEchoChar() == 0 ? '\u2022' : passwordField.getEchoChar();
            passwordField.setEchoChar(defaultEchoChar);

            toggleButton = new JButton("Show");
            toggleButton.setFont(ThemeManager.font(Font.BOLD, 11));
            toggleButton.setFocusPainted(false);
            toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleButton.setPreferredSize(new Dimension(64, 38));
            toggleButton.setToolTipText("Toggle password visibility");
            toggleButton.addActionListener(e -> setPasswordVisible(!visible));

            add(passwordField, BorderLayout.CENTER);
            add(toggleButton, BorderLayout.EAST);
        }

        public void setPasswordVisible(boolean show) {
            this.visible = show;
            passwordField.setEchoChar(show ? (char) 0 : defaultEchoChar);
            toggleButton.setText(show ? "Hide" : "Show");
        }

        public void clearPassword() {
            passwordField.setText("");
            setPasswordVisible(false);
        }

        public String getPasswordText() {
            return new String(passwordField.getPassword());
        }

        public JPasswordField getPasswordField() {
            return passwordField;
        }
    }

    /**
     * Alert banner matching LoginPage.tsx rose error cards and emerald/cyan status callouts.
     */
    public static class AlertBanner extends RoundedCard {
        private final JLabel textLabel;

        public AlertBanner() {
            super(12, ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA));
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(10, 12, 10, 12));

            JLabel icon = new JLabel("!");
            icon.setOpaque(true);
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setFont(ThemeManager.font(Font.BOLD, 11));
            icon.setPreferredSize(new Dimension(18, 18));
            icon.setBackground(ThemeManager.ROSE_PRIMARY);
            icon.setForeground(Color.WHITE);

            JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
            iconWrap.setOpaque(false);
            iconWrap.add(icon);

            textLabel = new JLabel();
            textLabel.setFont(ThemeManager.font(Font.PLAIN, 12));
            textLabel.setForeground(new Color(0x9F, 0x12, 0x39));

            add(iconWrap, BorderLayout.WEST);
            add(textLabel, BorderLayout.CENTER);
            setVisible(false);
        }

        public void showError(String message) {
            if (message == null || message.isBlank()) {
                clear();
                return;
            }
            setColors(ThemeManager.ROSE_SOFT, new Color(0xFC, 0xA5, 0xA5));
            textLabel.setForeground(new Color(0x9F, 0x12, 0x39));
            textLabel.setText("<html><body style='width: 420px'>" + escapeHtml(message) + "</body></html>");
            setVisible(true);
            revalidate();
            repaint();
        }

        public void clear() {
            textLabel.setText("");
            setVisible(false);
            revalidate();
            repaint();
        }

        private String escapeHtml(String s) {
            return s.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\n", "<br/>");
        }
    }
}
