package edu.gcet.placementhub.ui.components;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.utils.UiComponents;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Institutional KPI Card reproducing `StatCard.tsx` with a crisp colored top stripe,
 * uppercase title, square icon badge, bold metric value, optional trend pill, and subtitle.
 */
public class StatCardPanel extends RoundedCard {

    public enum Accent {
        BLUE(ThemeManager.BLUE_ELECTRIC, new Color(38, 132, 255, 32), new Color(38, 132, 255, 80)),
        CYAN(ThemeManager.CYAN_ACCENT, new Color(50, 197, 244, 32), new Color(50, 197, 244, 80)),
        EMERALD(ThemeManager.EMERALD_PRIMARY, new Color(16, 185, 129, 32), new Color(16, 185, 129, 80)),
        AMBER(ThemeManager.AMBER_PRIMARY, new Color(245, 158, 11, 32), new Color(245, 158, 11, 80)),
        ROSE(ThemeManager.ROSE_PRIMARY, new Color(225, 29, 72, 32), new Color(225, 29, 72, 80)),
        SLATE(ThemeManager.SLATE_500, new Color(148, 163, 184, 35), new Color(148, 163, 184, 80));

        final Color topColor;
        final Color iconBg;
        final Color iconBorder;

        Accent(Color topColor, Color iconBg, Color iconBorder) {
            this.topColor = topColor;
            this.iconBg = iconBg;
            this.iconBorder = iconBorder;
        }
    }

    private final Accent accent;
    private final JLabel titleLabel;
    private final JLabel valueLabel;
    private final BadgeLabel trendBadge;
    private final JLabel subtitleLabel;

    public StatCardPanel(String title, String value, String subtitle, String iconGlyph, Accent accent) {
        this(title, value, subtitle, iconGlyph, null, accent);
    }

    public StatCardPanel(String title, String value, String subtitle, String iconGlyph, String trendText, Accent accent) {
        super(14, ThemeManager.cardBg(), ThemeManager.cardBorder(), accent.topColor, 1.0f);
        this.accent = accent;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(18, 18, 18, 18));

        // Top row: uppercase title + icon square badge
        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        titleLabel.setForeground(ThemeManager.mutedText());

        JPanel iconBox = UiComponents.createIconBadge(iconGlyph, accent.iconBg, accent.topColor, 34, 15);
        topRow.add(titleLabel, BorderLayout.CENTER);
        topRow.add(iconBox, BorderLayout.EAST);

        // Middle row: metric value + optional trend pill
        JPanel midRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        midRow.setOpaque(false);
        midRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        valueLabel = new JLabel(value);
        valueLabel.setFont(ThemeManager.font(Font.BOLD, 24));
        valueLabel.setForeground(ThemeManager.primaryText());
        midRow.add(valueLabel);

        trendBadge = new BadgeLabel(
                trendText != null ? trendText : "",
                new Color(16, 185, 129, 28),
                ThemeManager.EMERALD_PRIMARY,
                new Color(16, 185, 129, 85),
                999
        );
        trendBadge.setVisible(trendText != null && !trendText.isBlank());
        midRow.add(trendBadge);

        // Subtitle
        subtitleLabel = new JLabel("<html><body style='width:210px;'>" + (subtitle != null ? subtitle : "") + "</body></html>");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        add(topRow);
        add(Box.createVerticalStrut(10));
        add(midRow);
        add(Box.createVerticalStrut(6));
        add(subtitleLabel);
    }

    public void updateValues(String value, String subtitle, String trendText) {
        valueLabel.setText(value);
        subtitleLabel.setText("<html><body style='width:210px;'>" + (subtitle != null ? subtitle : "") + "</body></html>");
        if (trendText != null && !trendText.isBlank()) {
            trendBadge.setText(trendText);
            trendBadge.setVisible(true);
        } else {
            trendBadge.setVisible(false);
        }
        refreshTheme();
    }

    public void refreshTheme() {
        setColors(ThemeManager.cardBg(), ThemeManager.cardBorder());
        titleLabel.setForeground(ThemeManager.mutedText());
        valueLabel.setForeground(ThemeManager.primaryText());
        subtitleLabel.setForeground(ThemeManager.mutedText());
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape clip = new RoundRectangle2D.Float(1, 1, getWidth() - 2f, getHeight() - 2f, 14, 14);
        g2.setClip(clip);
        g2.setColor(accent.topColor);
        g2.fillRect(0, 0, getWidth(), 3);
        g2.dispose();
    }
}
