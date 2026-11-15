package edu.gcet.placementhub.ui.auth;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.BiConsumer;

/**
 * Exact Java Swing recreation of CampusHero.tsx:
 * Official GCET campus building photograph background + multi-stop dark navy gradient overlay +
 * institutional badges, title, tagline, campus address, GCTC portal button, and right-side action controls.
 */
public class CampusHeroPanel extends JPanel {

    private final BufferedImage campusPhoto;

    public CampusHeroPanel(
            BiConsumer<PortalCategory, Boolean> onOpenPortal,
            Runnable onScrollToPortals
    ) {
        this.campusPhoto = ResourceLoader.loadCampusImage();
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(32, 36, 32, 36));
        setMinimumSize(new Dimension(600, 390));
        setPreferredSize(new Dimension(1120, 410));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;

        // Left column: institutional info & highlights
        JPanel leftCol = new JPanel();
        leftCol.setOpaque(false);
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        BadgeLabel instBadge = new BadgeLabel(
                "●  GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY",
                new Color(38, 132, 255, 55),
                ThemeManager.CYAN_ACCENT,
                new Color(50, 197, 244, 90),
                999
        );
        BadgeLabel accBadge = new BadgeLabel(
                "UGC AUTONOMOUS | NAAC A+ & NBA",
                new Color(15, 23, 42, 165),
                new Color(0xE2, 0xE8, 0xF0),
                new Color(255, 255, 255, 45),
                999
        );
        badgeRow.add(instBadge);
        badgeRow.add(accBadge);

        JLabel titleLabel = new JLabel("<html>GCET <span style='color:#32C5F4;'>PLACEMENT HUB</span></html>");
        titleLabel.setFont(ThemeManager.font(Font.BOLD, 34));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Smart Placement Cell & Skill Matrix Portal");
        subtitleLabel.setFont(ThemeManager.font(Font.BOLD, 17));
        subtitleLabel.setForeground(new Color(0xBA, 0xE6, 0xFD));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel taglineLabel = new JLabel("\"Your Skills. Your Opportunities. Your Future.\"");
        taglineLabel.setFont(ThemeManager.font(Font.ITALIC | Font.BOLD, 13.5f));
        taglineLabel.setForeground(new Color(0x67, 0xE8, 0xF9));
        taglineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel(
                "<html><body style='width: 560px; line-height: 1.45;'>"
                        + "Welcome to GCET Placement Hub, a centralized platform connecting students, faculty and "
                        + "recruiting companies through student profiles, skill development, placement drives and career opportunities."
                        + "</body></html>"
        );
        descLabel.setFont(ThemeManager.font(Font.PLAIN, 13));
        descLabel.setForeground(new Color(0xE2, 0xE8, 0xF0));
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel detailsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        detailsRow.setOpaque(false);
        detailsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        BadgeLabel addressChip = new BadgeLabel(
                "Cheeryal, Keesara, Telangana - 501301",
                new Color(0, 0, 0, 120),
                new Color(0xE2, 0xE8, 0xF0),
                new Color(255, 255, 255, 45),
                14
        );
        addressChip.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        addressChip.setBorder(new EmptyBorder(6, 12, 6, 12));

        ActionButton gctcBtn = new ActionButton(
                "Official GCTC Academic Portal  ↗",
                new Color(38, 132, 255, 65),
                new Color(38, 132, 255, 110),
                ThemeManager.CYAN_ACCENT,
                new Color(50, 197, 244, 110),
                12
        );
        gctcBtn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        gctcBtn.setMargin(new Insets(6, 12, 6, 12));
        gctcBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, "https://www.gctcportal.in/"));

        detailsRow.add(addressChip);
        detailsRow.add(gctcBtn);

        leftCol.add(badgeRow);
        leftCol.add(Box.createVerticalStrut(14));
        leftCol.add(titleLabel);
        leftCol.add(Box.createVerticalStrut(4));
        leftCol.add(subtitleLabel);
        leftCol.add(Box.createVerticalStrut(10));
        leftCol.add(taglineLabel);
        leftCol.add(Box.createVerticalStrut(10));
        leftCol.add(descLabel);
        leftCol.add(Box.createVerticalStrut(16));
        leftCol.add(detailsRow);

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        add(leftCol, gbc);

        // Right column: Primary Action Buttons
        JPanel rightCol = new JPanel();
        rightCol.setOpaque(false);
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setPreferredSize(new Dimension(280, 220));

        ActionButton stuSignInBtn = new ActionButton(
                "Student Sign In",
                ThemeManager.BLUE_ELECTRIC,
                ThemeManager.BLUE_HOVER,
                Color.WHITE,
                new Color(96, 165, 250, 130),
                14
        );
        stuSignInBtn.setMaximumSize(new Dimension(280, 42));
        stuSignInBtn.setPreferredSize(new Dimension(280, 42));
        stuSignInBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        stuSignInBtn.addActionListener(e -> onOpenPortal.accept(PortalCategory.STUDENTS, false));

        ActionButton portalAccessBtn = new ActionButton(
                "Portal Access & Role Gateways  →",
                new Color(15, 23, 42, 195),
                new Color(30, 41, 59, 220),
                Color.WHITE,
                new Color(255, 255, 255, 60),
                14
        );
        portalAccessBtn.setMaximumSize(new Dimension(280, 42));
        portalAccessBtn.setPreferredSize(new Dimension(280, 42));
        portalAccessBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        portalAccessBtn.addActionListener(e -> {
            if (onScrollToPortals != null) {
                onScrollToPortals.run();
            } else {
                onOpenPortal.accept(PortalCategory.ADMIN, false);
            }
        });

        ActionButton newRegBtn = new ActionButton(
                "New Student Registration  ›",
                new Color(255, 255, 255, 28),
                new Color(255, 255, 255, 55),
                new Color(0xA5, 0xF3, 0xFC),
                new Color(255, 255, 255, 45),
                14
        );
        newRegBtn.setMaximumSize(new Dimension(280, 38));
        newRegBtn.setPreferredSize(new Dimension(280, 38));
        newRegBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        newRegBtn.addActionListener(e -> onOpenPortal.accept(PortalCategory.STUDENTS, true));

        JLabel stripLabel = new JLabel("●  4 Portals: Admin · Faculty · Companies · Students");
        stripLabel.setFont(ThemeManager.font(Font.PLAIN, 11));
        stripLabel.setForeground(new Color(0xCB, 0xD5, 0xE1));
        stripLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        rightCol.add(Box.createVerticalGlue());
        rightCol.add(stuSignInBtn);
        rightCol.add(Box.createVerticalStrut(12));
        rightCol.add(portalAccessBtn);
        rightCol.add(Box.createVerticalStrut(12));
        rightCol.add(newRegBtn);
        rightCol.add(Box.createVerticalStrut(14));
        rightCol.add(stripLabel);
        rightCol.add(Box.createVerticalGlue());

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(0, 28, 0, 0);
        gbc.anchor = GridBagConstraints.EAST;
        add(rightCol, gbc);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int w = getWidth();
        int h = getHeight();
        Shape clip = new RoundRectangle2D.Float(0, 0, w - 1f, h - 1f, 26, 26);
        g2.setClip(clip);

        // Base dark navy background
        g2.setColor(new Color(0x0A, 0x12, 0x26));
        g2.fillRect(0, 0, w, h);

        // Draw GCET campus building photograph scaled like CSS object-cover centered at 35% height
        if (campusPhoto != null) {
            double imgW = campusPhoto.getWidth();
            double imgH = campusPhoto.getHeight();
            double scale = Math.max((double) w / imgW, (double) h / imgH);
            int drawW = (int) Math.round(imgW * scale);
            int drawH = (int) Math.round(imgH * scale);
            int drawX = (w - drawW) / 2;
            int drawY = (int) Math.round((h - drawH) * 0.35);
            g2.drawImage(campusPhoto, drawX, drawY, drawW, drawH, null);
        }

        // Left-to-right gradient overlay matching CampusHero.tsx
        LinearGradientPaint horizOverlay = new LinearGradientPaint(
                0f, 0f, w, 0f,
                new float[]{0.0f, 0.52f, 1.0f},
                new Color[]{
                        new Color(7, 14, 32, 240),
                        new Color(9, 21, 51, 200),
                        new Color(11, 23, 54, 110)
                }
        );
        g2.setPaint(horizOverlay);
        g2.fillRect(0, 0, w, h);

        // Bottom-to-top subtle navy vignette
        GradientPaint vertOverlay = new GradientPaint(
                0f, h, new Color(7, 14, 32, 205),
                0f, 0f, new Color(7, 14, 32, 75)
        );
        g2.setPaint(vertOverlay);
        g2.fillRect(0, 0, w, h);

        g2.setClip(null);
        g2.setColor(new Color(51, 65, 85, 160));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(clip);

        g2.dispose();
        super.paintComponent(g);
    }
}
