package edu.gcet.placementhub.ui.shell;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;

/**
 * Non-crashing startup dialog shown when the MongoDB cluster (Atlas or local) is unreachable.
 * Explains exactly how to fix the most common causes (Atlas IP whitelist 0.0.0.0/0, wrong
 * MONGO_URI password, offline Wi-Fi, local mongod not running) and offers Retry / Settings /
 * Continue Offline so the Swing app never terminates because of a connection problem.
 */
public final class ConnectionErrorDialog {

    private ConnectionErrorDialog() {
    }

    public static void show(Window owner, DbStatus status, Runnable onRetry, Runnable onOpenSettings) {
        JDialog dlg = new JDialog(owner, "MongoDB Connection Problem — GCET Placement Hub", Dialog.ModalityType.MODELESS);
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setLayout(new BorderLayout());
        dlg.getContentPane().setBackground(Color.WHITE);

        String clusterType = status != null && status.getClusterType() != null ? status.getClusterType() : "MongoDB";
        String error = status != null && status.getLastError() != null ? status.getLastError()
                : (status != null && status.getMessage() != null ? status.getMessage() : "The database did not respond.");
        String hint = status != null && status.getRemediationHint() != null
                ? status.getRemediationHint()
                : DatabaseConnection.getInstance().getLastRemediationHint();
        if (hint == null || hint.isBlank()) {
            hint = "1. Check MONGO_URI in .env (gcet-placement-hub-java/.env).\n"
                    + "2. For MongoDB Atlas: Network Access -> Add IP Address -> 0.0.0.0/0.\n"
                    + "3. For local MongoDB: start the mongod service on localhost:27017.";
        }
        String uri = status != null && status.getMaskedUri() != null ? status.getMaskedUri() : "(unknown)";

        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 12, 24));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("Cannot reach " + clusterType);
        title.setFont(ThemeManager.font(Font.BOLD, 17));
        title.setForeground(ThemeManager.NAVY_MIDNIGHT);
        titleRow.add(title);
        titleRow.add(new BadgeLabel("DISCONNECTED", new Color(0xFF, 0xE4, 0xE6), ThemeManager.ROSE_PRIMARY, new Color(0xFE, 0xCD, 0xD3), 999));
        content.add(titleRow);
        content.add(Box.createVerticalStrut(6));

        JLabel sub = new JLabel("<html><body style='width:520px;'>The desktop app is still usable, but live data, login and seeding need a reachable MongoDB cluster."
                + " Fix the connection below, then click <b>Retry</b>.</body></html>");
        sub.setFont(ThemeManager.font(Font.PLAIN, 12));
        sub.setForeground(ThemeManager.SLATE_600);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sub);
        content.add(Box.createVerticalStrut(12));

        content.add(section("CONNECTION STRING (password masked)", uri, ThemeManager.SLATE_700));
        content.add(Box.createVerticalStrut(8));
        content.add(section("ERROR", error, ThemeManager.ROSE_PRIMARY));
        content.add(Box.createVerticalStrut(8));
        content.add(section("HOW TO FIX", hint, ThemeManager.SLATE_800));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        footer.setBackground(ThemeManager.SLATE_50);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_200));

        ActionButton offlineBtn = new ActionButton("Continue Offline", Color.WHITE, ThemeManager.SLATE_100, ThemeManager.SLATE_700, ThemeManager.SLATE_300, 10);
        offlineBtn.addActionListener(e -> dlg.dispose());

        ActionButton settingsBtn = new ActionButton("Open Connection Settings", ThemeManager.SLATE_800, ThemeManager.SLATE_700, Color.WHITE, null, 10);
        settingsBtn.addActionListener(e -> {
            dlg.dispose();
            if (onOpenSettings != null) onOpenSettings.run();
        });

        ActionButton retryBtn = new ActionButton("Retry Connection", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        retryBtn.addActionListener(e -> {
            dlg.dispose();
            if (onRetry != null) onRetry.run();
        });

        footer.add(offlineBtn);
        footer.add(settingsBtn);
        footer.add(retryBtn);

        JScrollPane sp = new JScrollPane(content);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        dlg.add(sp, BorderLayout.CENTER);
        dlg.add(footer, BorderLayout.SOUTH);
        dlg.setSize(640, 520);
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    private static JPanel section(String heading, String body, Color bodyColor) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel h = new JLabel(heading);
        h.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        h.setForeground(ThemeManager.SLATE_500);
        JTextArea area = new JTextArea(body);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(ThemeManager.monoFont(Font.PLAIN, 12));
        area.setForeground(bodyColor);
        area.setBackground(ThemeManager.SLATE_50);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        p.add(h, BorderLayout.NORTH);
        p.add(area, BorderLayout.CENTER);
        return p;
    }
}
