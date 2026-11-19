package edu.gcet.placementhub.ui.dashboard;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Unified Dashboard Campus Hero reproducing Section 1 of DashboardPage.tsx:
 * Campus photograph background, institutional badges, live MySQL connection pill + Refresh,
 * and quick portal navigation CTAs.
 */
public class DashboardHeroPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("hh:mm:ss a").withZone(ZoneId.systemDefault());

    private final BufferedImage campusPhoto;
    private final JLabel dbDotLabel;
    private final JLabel dbTextLabel;
    private final JButton refreshBtn;
    private final ActionButton smartMatchingBtn;

    public DashboardHeroPanel(Consumer<String> onNavigate, Runnable onRefresh) {
        this.campusPhoto = ResourceLoader.loadCampusImage();
        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(26, 30, 26, 30));
        setMinimumSize(new Dimension(700, 290));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;

        // Left institutional identity column
        JPanel leftCol = new JPanel();
        leftCol.setOpaque(false);
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        badgeRow.add(new BadgeLabel(
                "●  GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY",
                new Color(38, 132, 255, 65),
                ThemeManager.CYAN_ACCENT,
                new Color(50, 197, 244, 95),
                999
        ));
        badgeRow.add(new BadgeLabel(
                "UGC AUTONOMOUS | NAAC A+ & NBA",
                new Color(15, 23, 42, 165),
                new Color(0xE2, 0xE8, 0xF0),
                new Color(255, 255, 255, 45),
                999
        ));

        JLabel titleLabel = new JLabel("<html>GCET <span style='color:#32C5F4;'>PLACEMENT HUB</span></html>");
        titleLabel.setFont(ThemeManager.font(Font.BOLD, 30));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Smart Placement Cell & Skill Matrix Portal");
        subtitleLabel.setFont(ThemeManager.font(Font.BOLD, 15.5f));
        subtitleLabel.setForeground(new Color(0xBA, 0xE6, 0xFD));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel taglineLabel = new JLabel("\"Your Skills. Your Opportunities. Your Future.\"");
        taglineLabel.setFont(ThemeManager.font(Font.ITALIC | Font.BOLD, 13));
        taglineLabel.setForeground(new Color(0x67, 0xE8, 0xF9));
        taglineLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel(
                "<html><body style='width:520px; line-height:1.45;'>"
                        + "Welcome to GCET Placement Hub. Connecting student talent with career opportunities "
                        + "across premier engineering disciplines."
                        + "</body></html>"
        );
        descLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
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
                12
        );
        addressChip.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        addressChip.setBorder(new EmptyBorder(6, 12, 6, 12));

        ActionButton gctcBtn = new ActionButton(
                "Official GCTC Academic Portal  ›",
                new Color(38, 132, 255, 65),
                new Color(38, 132, 255, 110),
                ThemeManager.CYAN_ACCENT,
                new Color(50, 197, 244, 110),
                12
        );
        gctcBtn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        gctcBtn.setMargin(new Insets(6, 12, 6, 12));
        gctcBtn.addActionListener(e -> onNavigate.accept("campus-connect"));

        detailsRow.add(addressChip);
        detailsRow.add(gctcBtn);

        leftCol.add(badgeRow);
        leftCol.add(Box.createVerticalStrut(10));
        leftCol.add(titleLabel);
        leftCol.add(Box.createVerticalStrut(4));
        leftCol.add(subtitleLabel);
        leftCol.add(Box.createVerticalStrut(8));
        leftCol.add(taglineLabel);
        leftCol.add(Box.createVerticalStrut(8));
        leftCol.add(descLabel);
        leftCol.add(Box.createVerticalStrut(12));
        leftCol.add(detailsRow);

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        add(leftCol, gbc);

        // Right column: Live MySQL pill + action CTAs
        JPanel rightCol = new JPanel();
        rightCol.setOpaque(false);
        rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
        rightCol.setPreferredSize(new Dimension(285, 240));

        RoundedCard dbStrip = new RoundedCard(
                12,
                new Color(0, 0, 0, 130),
                new Color(255, 255, 255, 45)
        );
        dbStrip.setLayout(new BorderLayout(8, 0));
        dbStrip.setBorder(new EmptyBorder(7, 12, 7, 8));
        dbStrip.setMaximumSize(new Dimension(285, 36));
        dbStrip.setPreferredSize(new Dimension(285, 36));
        dbStrip.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel dbLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dbLeft.setOpaque(false);
        dbDotLabel = new JLabel("●");
        dbDotLabel.setFont(ThemeManager.font(Font.BOLD, 10));
        dbDotLabel.setForeground(new Color(0xF4, 0x3F, 0x5E));
        dbTextLabel = new JLabel("Checking MongoDB Atlas...");
        dbTextLabel.setFont(ThemeManager.monoFont(Font.BOLD, 11));
        dbTextLabel.setForeground(new Color(0xE2, 0xE8, 0xF0));
        dbLeft.add(dbDotLabel);
        dbLeft.add(dbTextLabel);

        refreshBtn = new JButton("↻");
        refreshBtn.setToolTipText("Refresh Live MongoDB Database Statistics & Test Connection");
        refreshBtn.setFont(ThemeManager.font(Font.BOLD, 14));
        refreshBtn.setForeground(ThemeManager.CYAN_ACCENT);
        refreshBtn.setOpaque(false);
        refreshBtn.setContentAreaFilled(false);
        refreshBtn.setBorderPainted(false);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> {
            setRefreshing(true);
            onRefresh.run();
        });

        dbStrip.add(dbLeft, BorderLayout.CENTER);
        dbStrip.add(refreshBtn, BorderLayout.EAST);
        dbStrip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dbStrip.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onNavigate.accept("diagnostics");
            }
        });

        ActionButton facultyConsoleBtn = new ActionButton(
                "⚡  DBMS Faculty Console  ›",
                new Color(16, 185, 129, 210),
                new Color(5, 150, 105, 230),
                Color.WHITE,
                new Color(52, 211, 153, 110),
                12
        );
        facultyConsoleBtn.setFont(ThemeManager.font(Font.BOLD, 12));
        facultyConsoleBtn.setMaximumSize(new Dimension(285, 40));
        facultyConsoleBtn.setPreferredSize(new Dimension(285, 40));
        facultyConsoleBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        facultyConsoleBtn.addActionListener(e -> onNavigate.accept("diagnostics"));

        ActionButton campusBtn = new ActionButton(
                "Campus Connect Portal",
                ThemeManager.BLUE_ELECTRIC,
                ThemeManager.BLUE_HOVER,
                Color.WHITE,
                new Color(96, 165, 250, 110),
                12
        );
        campusBtn.setMaximumSize(new Dimension(285, 38));
        campusBtn.setPreferredSize(new Dimension(285, 38));
        campusBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        campusBtn.addActionListener(e -> onNavigate.accept("campus-connect"));

        ActionButton createDriveBtn = new ActionButton(
                "Create Placement Drive",
                new Color(15, 23, 42, 200),
                new Color(30, 41, 59, 225),
                Color.WHITE,
                new Color(255, 255, 255, 55),
                12
        );
        createDriveBtn.setMaximumSize(new Dimension(285, 38));
        createDriveBtn.setPreferredSize(new Dimension(285, 38));
        createDriveBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        createDriveBtn.addActionListener(e -> onNavigate.accept("drives"));

        ActionButton eligibilityBtn = new ActionButton(
                "Eligibility Engine",
                new Color(255, 255, 255, 28),
                new Color(255, 255, 255, 55),
                Color.WHITE,
                new Color(255, 255, 255, 55),
                12
        );
        eligibilityBtn.setMaximumSize(new Dimension(285, 36));
        eligibilityBtn.setPreferredSize(new Dimension(285, 36));
        eligibilityBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        eligibilityBtn.addActionListener(e -> onNavigate.accept("eligibility"));

        smartMatchingBtn = new ActionButton(
                "Smart Company Matching",
                new Color(30, 58, 138, 165),
                new Color(30, 64, 175, 210),
                new Color(0xA5, 0xF3, 0xFC),
                new Color(50, 197, 244, 90),
                12
        );
        smartMatchingBtn.setMaximumSize(new Dimension(285, 36));
        smartMatchingBtn.setPreferredSize(new Dimension(285, 36));
        smartMatchingBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        smartMatchingBtn.addActionListener(e -> onNavigate.accept("matching"));
        smartMatchingBtn.setVisible(false);

        rightCol.add(Box.createVerticalGlue());
        rightCol.add(dbStrip);
        rightCol.add(Box.createVerticalStrut(10));
        rightCol.add(facultyConsoleBtn);
        rightCol.add(Box.createVerticalStrut(8));
        rightCol.add(campusBtn);
        rightCol.add(Box.createVerticalStrut(8));
        rightCol.add(createDriveBtn);
        rightCol.add(Box.createVerticalStrut(8));
        rightCol.add(eligibilityBtn);
        rightCol.add(Box.createVerticalStrut(8));
        rightCol.add(smartMatchingBtn);
        rightCol.add(Box.createVerticalGlue());

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(0, 24, 0, 0);
        gbc.anchor = GridBagConstraints.EAST;
        add(rightCol, gbc);
    }

    public void updateState(User currentUser, DbStatus status) {
        smartMatchingBtn.setVisible(currentUser != null && currentUser.getRole() == UserRole.STUDENT);
        setRefreshing(false);
        if (status != null && status.isConnected()) {
            dbDotLabel.setForeground(new Color(0x34, 0xD3, 0x99));
            dbTextLabel.setForeground(new Color(0x6E, 0xE7, 0xB7));
            String ts = "";
            if (status.getLastSuccessfulConnectionIso() != null) {
                try {
                    ts = " · " + TIME_FMT.format(Instant.parse(status.getLastSuccessfulConnectionIso()));
                } catch (Exception ignored) {
                }
            }
            String clusterType = (status.getClusterType() != null && !status.getClusterType().isBlank())
                    ? status.getClusterType() : "MongoDB";
            dbTextLabel.setText(clusterType + " Connected" + ts);
        } else {
            dbDotLabel.setForeground(new Color(0xF4, 0x3F, 0x5E));
            dbTextLabel.setForeground(new Color(0xFD, 0xA4, 0xAF));
            dbTextLabel.setText("MongoDB Disconnected");
        }
    }

    public void setRefreshing(boolean loading) {
        refreshBtn.setEnabled(!loading);
        if (loading) {
            dbTextLabel.setText("Refreshing MongoDB Atlas...");
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int w = getWidth();
        int h = getHeight();
        Shape clip = new RoundRectangle2D.Float(0, 0, w - 1f, h - 1f, 22, 22);
        g2.setClip(clip);

        g2.setColor(new Color(0x0A, 0x12, 0x26));
        g2.fillRect(0, 0, w, h);

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

        LinearGradientPaint horizOverlay = new LinearGradientPaint(
                0f, 0f, w, 0f,
                new float[]{0.0f, 0.55f, 1.0f},
                new Color[]{
                        new Color(7, 14, 32, 240),
                        new Color(9, 21, 51, 198),
                        new Color(11, 23, 54, 125)
                }
        );
        g2.setPaint(horizOverlay);
        g2.fillRect(0, 0, w, h);

        GradientPaint vertOverlay = new GradientPaint(
                0f, h, new Color(7, 14, 32, 200),
                0f, 0f, new Color(7, 14, 32, 70)
        );
        g2.setPaint(vertOverlay);
        g2.fillRect(0, 0, w, h);

        g2.setClip(null);
        g2.setColor(new Color(51, 65, 85, 160));
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(clip);
        g2.dispose();
    }
}
