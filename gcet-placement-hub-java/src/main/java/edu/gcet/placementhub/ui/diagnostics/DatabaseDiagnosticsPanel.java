package edu.gcet.placementhub.ui.diagnostics;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.GovernanceController;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.GovernanceModels.ExtendedDbStatus;
import edu.gcet.placementhub.models.GovernanceModels.TableDiagnostic;
import edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Phase 5 Non-Destructive Read-Only MySQL Database Diagnostics Panel.
 * Displays live connection status, host/port (without credentials), MySQL version,
 * 17-table availability check, live record counts, ping latency, and error diagnostics.
 */
public class DatabaseDiagnosticsPanel extends JPanel {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss a").withZone(ZoneId.systemDefault());

    private final GovernanceController controller = new GovernanceController();
    private final Consumer<DbStatus> onStatusRefreshed;

    private final BadgeLabel connBadge = new BadgeLabel("CHECKING...", ThemeManager.SLATE_200, ThemeManager.SLATE_800, null, 999);
    private final JLabel dbNameVal = new JLabel("-");
    private final JLabel hostPortVal = new JLabel("-");
    private final JLabel versionVal = new JLabel("-");
    private final JLabel tablesVal = new JLabel("0 / 17");
    private final JLabel latencyVal = new JLabel("-");
    private final JLabel lastConnVal = new JLabel("Never");
    private final JLabel sourceVal = new JLabel("-");

    private final JLabel diagnosticBanner = new JLabel("Running non-destructive JDBC inspection...");
    private final DefaultTableModel tableModel;
    private ExtendedDbStatus latest;

    public DatabaseDiagnosticsPanel(Consumer<DbStatus> onStatusRefreshed) {
        this.onStatusRefreshed = onStatusRefreshed;
        setLayout(new BorderLayout(0, 12));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(16, 18, 16, 18));

        // 1. Header Card
        RoundedCard hdrCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        hdrCard.setLayout(new BorderLayout(12, 0));
        hdrCard.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel hdrLeft = new JPanel();
        hdrLeft.setOpaque(false);
        hdrLeft.setLayout(new BoxLayout(hdrLeft, BoxLayout.Y_AXIS));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel title = new JLabel("DB  MySQL 8 Database Diagnostics (Read-Only)");
        title.setFont(ThemeManager.font(Font.BOLD, 17));
        title.setForeground(ThemeManager.primaryText());
        titleRow.add(title);
        titleRow.add(connBadge);

        JLabel sub = new JLabel("Verifies live JDBC connectivity, MySQL server version, 17 required tables, and actual row counts without modifying data.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sub.setForeground(ThemeManager.mutedText());
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        hdrLeft.add(titleRow);
        hdrLeft.add(Box.createVerticalStrut(4));
        hdrLeft.add(sub);

        ActionButton testBtn = new ActionButton("↻  Run Live JDBC Connection Test", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        testBtn.addActionListener(e -> loadData());

        hdrCard.add(hdrLeft, BorderLayout.CENTER);
        hdrCard.add(testBtn, BorderLayout.EAST);

        // 2. Metadata Grid (No DB password ever displayed)
        JPanel metaGrid = new JPanel(new GridLayout(2, 4, 10, 10));
        metaGrid.setOpaque(false);
        metaGrid.add(infoTile("DATABASE NAME", dbNameVal));
        metaGrid.add(infoTile("HOST & PORT", hostPortVal));
        metaGrid.add(infoTile("MYSQL SERVER VERSION", versionVal));
        metaGrid.add(infoTile("REQUIRED TABLES", tablesVal));
        metaGrid.add(infoTile("JDBC PING LATENCY", latencyVal));
        metaGrid.add(infoTile("LAST SUCCESSFUL CONNECTION", lastConnVal));
        metaGrid.add(infoTile("CONFIG SOURCE", sourceVal));
        metaGrid.add(infoTile("CREDENTIAL SAFETY", new JLabel("Masked (Never Exposed)")));

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        hdrCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(hdrCard);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(metaGrid);

        add(topStack, BorderLayout.NORTH);

        // 3. 17-Table Availability & Live Row Counts Table
        RoundedCard tableCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel tblHdr = new JLabel("17 REQUIRED RELATIONAL TABLES — SCHEMA AVAILABILITY & LIVE RECORD COUNTS");
        tblHdr.setFont(ThemeManager.font(Font.BOLD, 11));
        tblHdr.setForeground(ThemeManager.CYAN_DARK);
        tableCard.add(tblHdr, BorderLayout.NORTH);

        String[] cols = {"#", "MySQL Table Name", "Availability", "Live Row Count", "Diagnostic Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(30);
        table.setFont(ThemeManager.monoFont(Font.PLAIN, 12));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11));
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.getColumnModel().getColumn(0).setMaxWidth(45);
        table.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(ThemeManager.font(Font.BOLD, 11));
                if (!isSelected) {
                    lbl.setForeground("AVAILABLE".equals(String.valueOf(value)) ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY);
                }
                return lbl;
            }
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new MatteBorder(1, 1, 1, 1, ThemeManager.cardBorder()));
        tableCard.add(sp, BorderLayout.CENTER);
        add(tableCard, BorderLayout.CENTER);

        // 4. Diagnostic Message / Error Output Box
        RoundedCard msgCard = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        msgCard.setLayout(new BorderLayout());
        msgCard.setBorder(new EmptyBorder(12, 16, 12, 16));
        diagnosticBanner.setFont(ThemeManager.font(Font.PLAIN, 12));
        diagnosticBanner.setForeground(ThemeManager.secondaryText());
        msgCard.add(diagnosticBanner, BorderLayout.CENTER);
        add(msgCard, BorderLayout.SOUTH);
    }

    public void loadData() {
        connBadge.setText("TESTING...");
        diagnosticBanner.setText("Executing read-only SELECT 1 AS ping, SELECT VERSION(), and INFORMATION_SCHEMA check...");
        controller.loadExtendedDbDiagnosticsAsync(ext -> {
            this.latest = ext;
            applyDiagnostics(ext);
            if (onStatusRefreshed != null && ext != null) {
                onStatusRefreshed.accept(ext.baseStatus());
            }
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        if (latest != null) applyDiagnostics(latest);
    }

    private void applyDiagnostics(ExtendedDbStatus ext) {
        if (ext == null || ext.baseStatus() == null) return;
        DbStatus st = ext.baseStatus();

        dbNameVal.setText(nonBlank(st.getDatabase(), "gcet_placement_hub"));
        hostPortVal.setText(nonBlank(st.getHost(), "localhost") + ":" + st.getPort());
        versionVal.setText(nonBlank(ext.mysqlVersion(), "Unavailable"));
        tablesVal.setText(ext.availableTableCount() + " / " + ext.totalRequiredTables() + " Verified");
        latencyVal.setText(ext.pingLatencyMs() >= 0 ? ext.pingLatencyMs() + " ms" : "Unreachable");
        lastConnVal.setText(formatTs(st.getLastSuccessfulConnectionIso()));
        sourceVal.setText(nonBlank(st.getConfigSource(), "Environment"));

        if (st.isConnected()) {
            connBadge.setText("CONNECTED");
            connBadge.setBadgeColors(new Color(16, 185, 129, 32), ThemeManager.EMERALD_PRIMARY, new Color(16, 185, 129, 95));
            diagnosticBanner.setForeground(ThemeManager.EMERALD_PRIMARY);
            diagnosticBanner.setText("<html><b>JDBC Diagnostic Passed:</b> " + esc(st.getMessage())
                    + " · MySQL Server " + esc(ext.mysqlVersion())
                    + " · Schema tables verified: " + ext.availableTableCount() + "/" + ext.totalRequiredTables() + ".</html>");
        } else {
            connBadge.setText("DISCONNECTED");
            connBadge.setBadgeColors(new Color(225, 29, 72, 32), ThemeManager.ROSE_PRIMARY, new Color(225, 29, 72, 95));
            diagnosticBanner.setForeground(ThemeManager.ROSE_PRIMARY);
            diagnosticBanner.setText("<html><b>Connection Diagnostic Error:</b> "
                    + esc(nonBlank(st.getLastError(), st.getMessage()))
                    + "<br/>Verify local MySQL Server is running on " + esc(st.getHost()) + ":" + st.getPort()
                    + " and update DB credentials via the top-bar MySQL Diagnostics dialog or `.env`.</html>");
        }

        tableModel.setRowCount(0);
        int idx = 1;
        for (TableDiagnostic td : ext.requiredTables()) {
            tableModel.addRow(new Object[]{
                    idx++,
                    td.tableName(),
                    td.exists() ? "AVAILABLE" : "MISSING",
                    td.exists() ? td.rowCount() : "-",
                    td.statusNote()
            });
        }
    }

    private RoundedCard infoTile(String title, JLabel valLabel) {
        RoundedCard c = new RoundedCard(11, ThemeManager.cardBg(), ThemeManager.cardBorder());
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.font(Font.BOLD, 9.5f));
        t.setForeground(ThemeManager.mutedText());
        valLabel.setFont(ThemeManager.monoFont(Font.BOLD, 12));
        valLabel.setForeground(ThemeManager.primaryText());
        c.add(t);
        c.add(Box.createVerticalStrut(4));
        c.add(valLabel);
        return c;
    }

    private String formatTs(String iso) {
        if (iso == null || iso.isBlank()) return "Not yet connected";
        try {
            return TS_FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
