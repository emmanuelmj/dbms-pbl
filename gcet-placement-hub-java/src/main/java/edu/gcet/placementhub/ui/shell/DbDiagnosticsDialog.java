package edu.gcet.placementhub.ui.shell;

import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.AuthController;
import edu.gcet.placementhub.database.DatabaseConnection;
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
 * MongoDB Diagnostics & Connection Settings Modal.
 * Supports Atlas SRV cloud connections, local MongoDB community fallback,
 * runtime URI testing, and 10-collection document verification.
 */
public class DbDiagnosticsDialog extends JDialog {

    private final AuthController authController = new AuthController();
    private final Consumer<DbStatus> onStatusUpdated;

    private BadgeLabel statusBadge;
    private JLabel subtitleLabel;
    private JLabel messageBannerLabel;
    private JPanel countsGrid;
    private JTextField uriField;
    private JTextField dbField;

    public DbDiagnosticsDialog(Window owner, DbStatus initialStatus, Consumer<DbStatus> onStatusUpdated) {
        super(owner, "MongoDB Atlas & Database Diagnostics — GCET Placement Hub", ModalityType.APPLICATION_MODAL);
        this.onStatusUpdated = onStatusUpdated;

        setSize(700, 680);
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
        JLabel t = new JLabel("MongoDB Database Diagnostics & Settings");
        t.setFont(ThemeManager.font(Font.BOLD, 16));
        t.setForeground(Color.WHITE);
        statusBadge = new BadgeLabel("CHECKING...", new Color(51, 65, 85), Color.WHITE, null, 999);
        topLine.add(t);
        topLine.add(statusBadge);

        subtitleLabel = new JLabel("Checking MongoDB Atlas / Local connectivity...");
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

        // Runtime Connection Parameters
        RoundedCard connCard = new RoundedCard(14, new Color(23, 43, 80, 140), ThemeManager.NAVY_DEEP);
        connCard.setLayout(new BoxLayout(connCard, BoxLayout.Y_AXIS));
        connCard.setBorder(new EmptyBorder(14, 14, 14, 14));
        connCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel connHdr = new JLabel("MONGODB CONNECTION SETTINGS (SOURCE: " + cfg.getSourceDescription() + ")");
        connHdr.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        connHdr.setForeground(ThemeManager.CYAN_ACCENT);
        connHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        connCard.add(connHdr);
        connCard.add(Box.createVerticalStrut(10));

        uriField = createDarkTextField(cfg.getMongoUri());
        dbField = createDarkTextField(cfg.getDatabaseName());

        connCard.add(labeledDarkField("MongoDB Connection URI (Atlas SRV or Local localhost:27017)", uriField));
        connCard.add(Box.createVerticalStrut(8));
        connCard.add(labeledDarkField("Database Name", dbField));
        connCard.add(Box.createVerticalStrut(10));

        // Quick template buttons
        JPanel quickBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickBtnRow.setOpaque(false);
        quickBtnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ActionButton atlasTplBtn = new ActionButton("Paste Atlas Cloud Template", new Color(0x33, 0x41, 0x55), new Color(0x47, 0x55, 0x69), Color.WHITE, null, 6);
        atlasTplBtn.setFont(ThemeManager.font(Font.PLAIN, 11));
        atlasTplBtn.addActionListener(e -> {
            uriField.setText("mongodb+srv://<username>:<password>@cluster0.abcde.mongodb.net/gcet_placement_hub?retryWrites=true&w=majority");
            dbField.setText("gcet_placement_hub");
        });

        ActionButton localTplBtn = new ActionButton("Use Local (localhost:27017)", new Color(0x33, 0x41, 0x55), new Color(0x47, 0x55, 0x69), Color.WHITE, null, 6);
        localTplBtn.setFont(ThemeManager.font(Font.PLAIN, 11));
        localTplBtn.addActionListener(e -> {
            uriField.setText("mongodb://localhost:27017");
            dbField.setText("gcet_placement_hub");
        });

        quickBtnRow.add(atlasTplBtn);
        quickBtnRow.add(localTplBtn);
        connCard.add(quickBtnRow);
        connCard.add(Box.createVerticalStrut(12));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ActionButton testBtn = new ActionButton("Apply & Test MongoDB Ping", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
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
                        "MongoDB configuration saved to:\n" + saved,
                        "Configuration Saved",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(
                        this,
                        "Failed to save .env file: " + ex.getMessage(),
                        "Save Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        btnRow.add(testBtn);
        btnRow.add(saveBtn);
        connCard.add(btnRow);

        body.add(connCard);
        body.add(Box.createVerticalStrut(14));

        // Diagnostics Banner Card
        RoundedCard bannerCard = new RoundedCard(14, new Color(23, 43, 80, 140), ThemeManager.NAVY_DEEP);
        bannerCard.setLayout(new BorderLayout());
        bannerCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        bannerCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        messageBannerLabel = new JLabel("Running connection diagnostics...");
        messageBannerLabel.setFont(ThemeManager.font(Font.PLAIN, 12));
        messageBannerLabel.setForeground(ThemeManager.SLATE_200);
        bannerCard.add(messageBannerLabel, BorderLayout.CENTER);

        body.add(bannerCard);
        body.add(Box.createVerticalStrut(14));

        // 10 Collections live count grid
        JLabel colHdr = new JLabel("COLLECTION VERIFICATION & DOCUMENT COUNTS");
        colHdr.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        colHdr.setForeground(ThemeManager.CYAN_ACCENT);
        colHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(colHdr);
        body.add(Box.createVerticalStrut(8));

        countsGrid = new JPanel(new GridLayout(0, 2, 10, 8));
        countsGrid.setOpaque(false);
        countsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        buildInitialCountsPlaceholder();
        body.add(countsGrid);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        return scroll;
    }

    private void applyFieldsToConfig() {
        String u = uriField.getText() != null ? uriField.getText().trim() : "";
        String d = dbField.getText() != null ? dbField.getText().trim() : "gcet_placement_hub";
        DatabaseConfig.getInstance().updateRuntimeConnection(u, d);
        DatabaseConnection.getInstance().reconnect();
    }

    private JPanel labeledDarkField(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.BOLD, 11));
        l.setForeground(ThemeManager.SLATE_300);
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private JTextField createDarkTextField(String val) {
        JTextField f = new JTextField(val != null ? val : "");
        f.setFont(ThemeManager.monoFont(Font.PLAIN, 12));
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setBackground(new Color(11, 20, 38));
        f.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 1, 1, 1, ThemeManager.NAVY_DEEP),
                new EmptyBorder(6, 10, 6, 10)
        ));
        return f;
    }

    private void buildInitialCountsPlaceholder() {
        countsGrid.removeAll();
        for (String col : DatabaseConnection.REQUIRED_COLLECTIONS) {
            countsGrid.add(createCountBadge(col, -1));
        }
        countsGrid.revalidate();
        countsGrid.repaint();
    }

    private JPanel createCountBadge(String colName, int count) {
        RoundedCard card = new RoundedCard(10, new Color(15, 23, 42), ThemeManager.NAVY_DEEP);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel nameLbl = new JLabel(colName);
        nameLbl.setFont(ThemeManager.monoFont(Font.PLAIN, 11.5f));
        nameLbl.setForeground(ThemeManager.SLATE_200);

        JLabel countLbl = new JLabel(count >= 0 ? count + " docs" : "...");
        countLbl.setFont(ThemeManager.monoFont(Font.BOLD, 11.5f));
        countLbl.setForeground(count > 0 ? ThemeManager.CYAN_ACCENT : (count == 0 ? ThemeManager.SLATE_400 : ThemeManager.SLATE_500));

        card.add(nameLbl, BorderLayout.WEST);
        card.add(countLbl, BorderLayout.EAST);
        return card;
    }

    private JPanel buildFooter() {
        JPanel ftr = new JPanel(new BorderLayout());
        ftr.setBackground(ThemeManager.NAVY_MIDNIGHT);
        ftr.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.NAVY_DEEP),
                new EmptyBorder(12, 20, 12, 20)
        ));

        JLabel hint = new JLabel("Atlas Tip: Whitelist IP 0.0.0.0/0 in Atlas Network Access for testing.");
        hint.setFont(ThemeManager.font(Font.ITALIC, 11));
        hint.setForeground(ThemeManager.SLATE_400);

        ActionButton closeBtn = new ActionButton("Close Diagnostics", new Color(51, 65, 85), new Color(71, 85, 105), Color.WHITE, null, 8);
        closeBtn.addActionListener(e -> dispose());

        ftr.add(hint, BorderLayout.WEST);
        ftr.add(closeBtn, BorderLayout.EAST);
        return ftr;
    }

    public void refreshPing() {
        statusBadge.setText("PINGING...");
        statusBadge.setBadgeColors(new Color(51, 65, 85), Color.WHITE, null);
        messageBannerLabel.setText("Checking connection to " + DatabaseConfig.getInstance().getHostLabel() + "...");

        authController.checkDatabaseHealthAsync(status -> {
            applyStatus(status);
            if (onStatusUpdated != null) {
                onStatusUpdated.accept(status);
            }
        });
    }

    private void applyStatus(DbStatus status) {
        if (status == null) return;

        if (status.isConnected()) {
            statusBadge.setText("CONNECTED");
            statusBadge.setBadgeColors(new Color(16, 185, 129, 40), ThemeManager.EMERALD_PRIMARY, new Color(16, 185, 129, 90));
            subtitleLabel.setText(status.getClusterType() + " · " + status.getHost() + " · Latency: " + status.getPingLatencyMs() + " ms");
            messageBannerLabel.setForeground(ThemeManager.EMERALD_PRIMARY);
            messageBannerLabel.setText("<html><b>Cluster Connected:</b> " + esc(status.getMessage())
                    + (status.isUsingLocalFallback() ? "<br/><i>Notice: Running on local MongoDB fallback server (Atlas was unreachable).</i>" : "")
                    + "</html>");
        } else {
            statusBadge.setText("DISCONNECTED");
            statusBadge.setBadgeColors(new Color(225, 29, 72, 40), ThemeManager.ROSE_PRIMARY, new Color(225, 29, 72, 90));
            subtitleLabel.setText("Failed to connect to " + DatabaseConfig.getInstance().getHostLabel());
            messageBannerLabel.setForeground(ThemeManager.ROSE_PRIMARY);
            messageBannerLabel.setText("<html><b>Connection Failed:</b> " + esc(status.getLastError() != null ? status.getLastError() : status.getMessage())
                    + "<br/><b>Remediation:</b> Verify MongoDB is running or check Atlas IP whitelist (0.0.0.0/0).</html>");
        }

        if (status.getTableCounts() != null && !status.getTableCounts().isEmpty()) {
            countsGrid.removeAll();
            for (Map.Entry<String, Integer> e : status.getTableCounts().entrySet()) {
                countsGrid.add(createCountBadge(e.getKey(), e.getValue()));
            }
            countsGrid.revalidate();
            countsGrid.repaint();
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
