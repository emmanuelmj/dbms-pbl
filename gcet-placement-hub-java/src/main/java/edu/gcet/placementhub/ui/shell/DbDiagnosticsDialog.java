package edu.gcet.placementhub.ui.shell;

import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.AuthController;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.utils.UiComponents.*;
import static edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Recreation of HeaderBar.tsx MySQL 8 Database Diagnostics Modal,
 * plus runtime JDBC connection testing against local MySQL `gcet_placement_hub`.
 */
public class DbDiagnosticsDialog extends JDialog {

    private final AuthController authController = new AuthController();
    private final Consumer<DbStatus> onStatusUpdated;

    private BadgeLabel statusBadge;
    private JLabel subtitleLabel;
    private JLabel messageBannerLabel;
    private JPanel countsGrid;
    private JTextField hostField, portField, dbField, userField;
    private PasswordInputField passField;

    public DbDiagnosticsDialog(Window owner, DbStatus initialStatus, Consumer<DbStatus> onStatusUpdated) {
        super(owner, "MySQL 8 Database Diagnostics — GCET Placement Hub", ModalityType.APPLICATION_MODAL);
        this.onStatusUpdated = onStatusUpdated;

        setSize(640, 620);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(ThemeManager.NAVY_MIDNIGHT);
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        if (initialStatus != null) {
            applyStatus(initialStatus);
        }
        refreshPing();
    }

    private JPanel buildHeader() {
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setBackground(ThemeManager.NAVY_MIDNIGHT);
        hdr.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.NAVY_DEEP),
                new EmptyBorder(16, 20, 14, 20)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(createIconBadge("DB", new Color(38, 132, 255, 50), ThemeManager.CYAN_ACCENT, 40, 13));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topLine.setOpaque(false);
        JLabel t = new JLabel("MySQL 8 Database Diagnostics");
        t.setFont(ThemeManager.font(Font.BOLD, 16));
        t.setForeground(Color.WHITE);
        statusBadge = new BadgeLabel("CHECKING...", new Color(51, 65, 85), Color.WHITE, null, 999);
        topLine.add(t);
        topLine.add(statusBadge);

        subtitleLabel = new JLabel("Checking JDBC connectivity...");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        subtitleLabel.setForeground(ThemeManager.SLATE_400);
        subtitleLabel.setBorder(new EmptyBorder(0, 8, 0, 0));

        titles.add(topLine);
        titles.add(subtitleLabel);
        left.add(titles);

        hdr.add(left, BorderLayout.WEST);
        return hdr;
    }

    private JScrollPane buildBody() {
        JPanel body = new JPanel();
        body.setBackground(ThemeManager.NAVY_MIDNIGHT);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(16, 20, 16, 20));

        DatabaseConfig cfg = DatabaseConfig.getInstance();

        // Runtime Connection Parameters (never hardcoded password)
        RoundedCard connCard = new RoundedCard(14, new Color(23, 43, 80, 140), ThemeManager.NAVY_DEEP);
        connCard.setLayout(new BoxLayout(connCard, BoxLayout.Y_AXIS));
        connCard.setBorder(new EmptyBorder(14, 14, 14, 14));
        connCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel connHdr = new JLabel("JDBC CONNECTION SETTINGS (SOURCE: " + cfg.getSourceDescription() + ")");
        connHdr.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        connHdr.setForeground(ThemeManager.CYAN_ACCENT);
        connHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        connCard.add(connHdr);
        connCard.add(Box.createVerticalStrut(10));

        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 8));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        hostField = createDarkTextField(cfg.getHost());
        portField = createDarkTextField(String.valueOf(cfg.getPort()));
        dbField = createDarkTextField(cfg.getDatabase());
        userField = createDarkTextField(cfg.getUser());

        grid.add(labeledDarkField("Host", hostField));
        grid.add(labeledDarkField("Port", portField));
        grid.add(labeledDarkField("Database Name", dbField));
        grid.add(labeledDarkField("MySQL User", userField));
        connCard.add(grid);
        connCard.add(Box.createVerticalStrut(8));

        passField = new PasswordInputField("Enter MySQL password to test connection");
        passField.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (cfg.getPassword() != null && !cfg.getPassword().isEmpty()) {
            passField.getPasswordField().setText(cfg.getPassword());
        }
        JPanel passWrap = labeledDarkField("MySQL Password (for runtime JDBC session)", passField);
        passWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        connCard.add(passWrap);
        connCard.add(Box.createVerticalStrut(10));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ActionButton testBtn = new ActionButton("Apply & Test Live JDBC Ping (SELECT 1)", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        testBtn.addActionListener(e -> {
            applyFieldsToConfig();
            refreshPing();
        });

        ActionButton saveBtn = new ActionButton("Save Settings to .env", ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57), Color.WHITE, null, 10);
        saveBtn.addActionListener(e -> {
            applyFieldsToConfig();
            try {
                java.nio.file.Path saved = DatabaseConfig.getInstance().saveToLocalDotEnv();
                refreshPing();
                JOptionPane.showMessageDialog(
                        this,
                        "MySQL JDBC configuration saved to:\n" + saved,
                        "Configuration Saved",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Could not write .env file: " + ex.getMessage(),
                        "Save Failed",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        btnRow.add(testBtn);
        btnRow.add(saveBtn);
        connCard.add(btnRow);

        body.add(connCard);
        body.add(Box.createVerticalStrut(14));

        JLabel tblTitle = new JLabel("LIVE RELATIONAL TABLE RECORDS");
        tblTitle.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        tblTitle.setForeground(ThemeManager.SLATE_400);
        tblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(tblTitle);
        body.add(Box.createVerticalStrut(8));

        countsGrid = new JPanel(new GridLayout(0, 3, 8, 8));
        countsGrid.setOpaque(false);
        countsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(countsGrid);
        body.add(Box.createVerticalStrut(14));

        RoundedCard statusBox = new RoundedCard(12, new Color(15, 23, 42), ThemeManager.NAVY_DEEP);
        statusBox.setLayout(new BorderLayout());
        statusBox.setBorder(new EmptyBorder(12, 14, 12, 14));
        statusBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageBannerLabel = new JLabel("Testing MySQL JDBC connection...");
        messageBannerLabel.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        messageBannerLabel.setForeground(ThemeManager.SLATE_200);
        statusBox.add(messageBannerLabel, BorderLayout.CENTER);
        body.add(statusBox);

        JScrollPane sp = new JScrollPane(body);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        return sp;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(ThemeManager.NAVY_MIDNIGHT);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, ThemeManager.NAVY_DEEP));

        ActionButton closeBtn = new ActionButton("Close Diagnostics", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        return footer;
    }

    private JTextField createDarkTextField(String val) {
        JTextField tf = new JTextField(val);
        tf.setFont(ThemeManager.monoFont(Font.BOLD, 12));
        tf.setPreferredSize(new Dimension(160, 32));
        return tf;
    }

    private JPanel labeledDarkField(String label, Component comp) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
        l.setForeground(ThemeManager.SLATE_400);
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private void applyFieldsToConfig() {
        int p = 3306;
        try {
            p = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        DatabaseConfig.getInstance().updateRuntimeCredentials(
                hostField.getText(),
                p,
                dbField.getText(),
                userField.getText(),
                passField.getPasswordText()
        );
    }

    private void refreshPing() {
        statusBadge.setText("PINGING...");
        authController.checkDatabaseHealthAsync(status -> {
            applyStatus(status);
            if (onStatusUpdated != null) {
                onStatusUpdated.accept(status);
            }
        });
    }

    private void applyStatus(DbStatus st) {
        subtitleLabel.setText("Host: " + st.getHost() + ":" + st.getPort() + " · Database: " + st.getDatabase());
        if (st.isConnected()) {
            statusBadge.setText("CONNECTED");
            statusBadge.setBadgeColors(new Color(16, 185, 129, 45), new Color(0x34, 0xD3, 0x99), new Color(16, 185, 129, 100));
            messageBannerLabel.setForeground(new Color(0xA7, 0xF3, 0xD0));
            messageBannerLabel.setText("<html><body style='width:530px;'><b>Connected:</b> Local MySQL database responded to <code>SELECT 1 AS ping</code> via JDBC."
                    + (st.getLastSuccessfulConnectionIso() != null ? "<br/>Last successful ping: " + st.getLastSuccessfulConnectionIso() : "")
                    + "</body></html>");
        } else {
            statusBadge.setText("DISCONNECTED");
            statusBadge.setBadgeColors(new Color(244, 63, 94, 45), new Color(0xFB, 0x71, 0x85), new Color(244, 63, 94, 100));
            messageBannerLabel.setForeground(new Color(0xFE, 0xCD, 0xD3));
            messageBannerLabel.setText("<html><body style='width:530px;'><b>Disconnected:</b> "
                    + (st.getLastError() != null ? st.getLastError() : "Could not connect to MySQL")
                    + "<br/>Ensure MySQL80 is running on your laptop and enter your DB password above or in <code>.env</code>.</body></html>");
        }

        countsGrid.removeAll();
        for (Map.Entry<String, Integer> entry : st.getTableCounts().entrySet()) {
            RoundedCard cell = new RoundedCard(8, new Color(23, 43, 80, 90), ThemeManager.NAVY_DEEP);
            cell.setLayout(new BorderLayout());
            cell.setBorder(new EmptyBorder(6, 10, 6, 10));
            JLabel tbl = new JLabel(entry.getKey());
            tbl.setFont(ThemeManager.monoFont(Font.PLAIN, 11));
            tbl.setForeground(ThemeManager.SLATE_300);
            JLabel cnt = new JLabel(String.valueOf(entry.getValue()));
            cnt.setFont(ThemeManager.font(Font.BOLD, 12));
            cnt.setForeground(ThemeManager.CYAN_ACCENT);
            cell.add(tbl, BorderLayout.WEST);
            cell.add(cnt, BorderLayout.EAST);
            countsGrid.add(cell);
        }
        countsGrid.revalidate();
        countsGrid.repaint();
    }
}
