package edu.gcet.placementhub.ui.audit;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.GovernanceController;
import edu.gcet.placementhub.models.AuditLog;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.services.GovernanceService.AuditLogBundle;
import edu.gcet.placementhub.utils.ReportExporter;
import edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Phase 5 Audit Logs Viewer Panel.
 * Enforces RBAC: Leadership & Admins see full institutional `audit_logs`;
 * non-privileged users see strictly their own actor trail.
 */
public class AuditLogsPanel extends JPanel {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final GovernanceController controller = new GovernanceController();

    private final JTextField searchField;
    private final JComboBox<String> actionFilterBox;
    private final JComboBox<String> roleFilterBox;
    private final JTextField fromDateField;
    private final JTextField toDateField;
    private final JLabel scopeNoticeLabel = new JLabel("Loading audit trail...");
    private final AlertBanner alertBanner = new AlertBanner();

    private final DefaultTableModel tableModel;
    private final JTextArea detailInspector = new JTextArea(4, 40);
    private List<AuditLog> currentLogs = List.of();

    public AuditLogsPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));

        // Header
        RoundedCard hdrCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        hdrCard.setLayout(new BorderLayout(12, 0));
        hdrCard.setBorder(new EmptyBorder(14, 18, 14, 18));
        hdrCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("∿  Institutional Security & Audit Logs (`audit_logs`)");
        title.setFont(ThemeManager.font(Font.BOLD, 17));
        title.setForeground(ThemeManager.primaryText());
        scopeNoticeLabel.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        scopeNoticeLabel.setForeground(ThemeManager.CYAN_DARK);
        left.add(title);
        left.add(Box.createVerticalStrut(3));
        left.add(scopeNoticeLabel);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        right.setOpaque(false);
        ActionButton csvBtn = new ActionButton("⇩  Export CSV", ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57), Color.WHITE, null, 10);
        csvBtn.addActionListener(e -> exportCsv());
        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        refreshBtn.addActionListener(e -> loadData());
        right.add(csvBtn);
        right.add(refreshBtn);

        hdrCard.add(left, BorderLayout.CENTER);
        hdrCard.add(right, BorderLayout.EAST);
        topStack.add(hdrCard);
        topStack.add(Box.createVerticalStrut(10));

        // Filters
        RoundedCard filterCard = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField = createStyledTextField("Search user, action, or details...");
        searchField.setPreferredSize(new Dimension(210, 32));
        searchField.addActionListener(e -> loadData());

        actionFilterBox = new JComboBox<>(new String[]{"ALL"});
        actionFilterBox.setPreferredSize(new Dimension(170, 32));

        roleFilterBox = new JComboBox<>(new String[]{
                "ALL", "PRINCIPAL", "CHAIRMAN", "VICE_CHAIRMAN", "ADMIN", "PLACEMENT_OFFICER", "FACULTY", "STUDENT", "COMPANY"
        });
        roleFilterBox.setPreferredSize(new Dimension(145, 32));

        fromDateField = createStyledTextField("YYYY-MM-DD");
        fromDateField.setPreferredSize(new Dimension(105, 32));
        toDateField = createStyledTextField("YYYY-MM-DD");
        toDateField.setPreferredSize(new Dimension(105, 32));

        ActionButton applyBtn = new ActionButton("Apply", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 9);
        applyBtn.addActionListener(e -> loadData());
        ActionButton clearBtn = new ActionButton("Clear", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.secondaryText(), ThemeManager.cardBorder(), 9);
        clearBtn.addActionListener(e -> {
            searchField.setText("");
            actionFilterBox.setSelectedIndex(0);
            roleFilterBox.setSelectedIndex(0);
            fromDateField.setText("");
            toDateField.setText("");
            loadData();
        });

        filterCard.add(inlineLbl("Search:"));
        filterCard.add(searchField);
        filterCard.add(inlineLbl("Action:"));
        filterCard.add(actionFilterBox);
        filterCard.add(inlineLbl("Role:"));
        filterCard.add(roleFilterBox);
        filterCard.add(inlineLbl("From:"));
        filterCard.add(fromDateField);
        filterCard.add(inlineLbl("To:"));
        filterCard.add(toDateField);
        filterCard.add(applyBtn);
        filterCard.add(clearBtn);
        topStack.add(filterCard);
        topStack.add(Box.createVerticalStrut(6));

        alertBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(alertBanner);
        add(topStack, BorderLayout.NORTH);

        // Table
        String[] cols = {"Timestamp", "Action Performed", "User", "Role", "Related Record / Summary", "Full Details"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(30);
        table.setFont(ThemeManager.font(Font.PLAIN, 12));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11));
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < currentLogs.size()) {
                AuditLog log = currentLogs.get(row);
                detailInspector.setText(
                        "Audit ID: " + nonBlank(log.getId(), "-")
                                + "   |   Timestamp: " + formatTs(log.getCreatedAt())
                                + "   |   Action: " + nonBlank(log.getAction(), "-") + "\n"
                                + "Actor: @" + nonBlank(log.getActorUsername(), "system")
                                + " (ID: " + nonBlank(log.getActorId(), "-") + ", Role: " + nonBlank(log.getActorRole(), "-") + ")\n"
                                + "Details & State Changes:\n" + nonBlank(log.getDetails(), "-")
                );
            }
        });

        RoundedCard tblCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tblCard.setLayout(new BorderLayout());
        tblCard.setBorder(new EmptyBorder(12, 14, 12, 14));
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new MatteBorder(1, 1, 1, 1, ThemeManager.cardBorder()));
        tblCard.add(sp, BorderLayout.CENTER);
        add(tblCard, BorderLayout.CENTER);

        // Inspector
        RoundedCard inspCard = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        inspCard.setLayout(new BorderLayout(0, 6));
        inspCard.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel inspHdr = new JLabel("AUDIT RECORD INSPECTOR (Select any event row above)");
        inspHdr.setFont(ThemeManager.font(Font.BOLD, 10));
        inspHdr.setForeground(ThemeManager.CYAN_DARK);
        detailInspector.setEditable(false);
        detailInspector.setLineWrap(true);
        detailInspector.setWrapStyleWord(true);
        detailInspector.setFont(ThemeManager.monoFont(Font.PLAIN, 11));
        detailInspector.setBackground(ThemeManager.cardSubtleBg());
        detailInspector.setForeground(ThemeManager.primaryText());
        detailInspector.setBorder(new EmptyBorder(8, 10, 8, 10));
        JScrollPane inspSp = new JScrollPane(detailInspector);
        inspSp.setBorder(new MatteBorder(1, 1, 1, 1, ThemeManager.cardBorder()));
        inspSp.setPreferredSize(new Dimension(400, 92));
        inspCard.add(inspHdr, BorderLayout.NORTH);
        inspCard.add(inspSp, BorderLayout.CENTER);
        add(inspCard, BorderLayout.SOUTH);
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        if (actor == null) return;
        alertBanner.clear();

        String search = searchField.getText();
        String action = (String) actionFilterBox.getSelectedItem();
        String role = (String) roleFilterBox.getSelectedItem();
        String from = fromDateField.getText();
        String to = toDateField.getText();

        controller.loadAuditLogsAsync(actor, search, action, role, from, to, bundle -> {
            if (bundle.error() != null && !bundle.error().isBlank()) {
                alertBanner.showError(bundle.error());
            }
            scopeNoticeLabel.setText(bundle.fullInstitutionalView()
                    ? "Institutional Governance Scope: Showing full MySQL `audit_logs` across all users & roles (" + bundle.logs().size() + " entries)."
                    : "RBAC Restricted Scope: Sensitive system logs hidden. Showing your own recorded actions only (" + bundle.logs().size() + " entries).");

            if (!bundle.distinctActions().isEmpty() && actionFilterBox.getItemCount() <= 1) {
                String currentSel = (String) actionFilterBox.getSelectedItem();
                actionFilterBox.removeAllItems();
                actionFilterBox.addItem("ALL");
                for (String a : bundle.distinctActions()) {
                    actionFilterBox.addItem(a);
                }
                if (currentSel != null) actionFilterBox.setSelectedItem(currentSel);
            }
            roleFilterBox.setEnabled(bundle.fullInstitutionalView());

            currentLogs = bundle.logs();
            tableModel.setRowCount(0);
            for (AuditLog log : currentLogs) {
                tableModel.addRow(new Object[]{
                        formatTs(log.getCreatedAt()),
                        nonBlank(log.getAction(), "-"),
                        nonBlank(log.getActorUsername(), "system"),
                        nonBlank(log.getActorRole(), "-"),
                        extractRelatedTarget(log.getDetails()),
                        nonBlank(log.getDetails(), "-")
                });
            }
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        detailInspector.setBackground(ThemeManager.cardSubtleBg());
        detailInspector.setForeground(ThemeManager.primaryText());
    }

    private void exportCsv() {
        if (currentLogs.isEmpty()) {
            alertBanner.showError("No audit log entries available to export.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Export Audit Logs (CSV)");
        fc.setSelectedFile(new File("gcet_audit_logs.csv"));
        fc.setFileFilter(new FileNameExtensionFilter("CSV Spreadsheets (*.csv)", "csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File target = fc.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            target = new File(target.getAbsolutePath() + ".csv");
        }
        try {
            ReportExporter.exportAuditLogsCsv(target, currentLogs);
            JOptionPane.showMessageDialog(this, "Audit logs exported to:\n" + target.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            alertBanner.showError("Failed to export audit CSV: " + ex.getMessage());
        }
    }

    private String extractRelatedTarget(String details) {
        if (details == null || details.isBlank()) return "-";
        int q1 = details.indexOf('"');
        if (q1 >= 0) {
            int q2 = details.indexOf('"', q1 + 1);
            if (q2 > q1 + 1) {
                return details.substring(q1 + 1, q2);
            }
        }
        return details.length() > 42 ? details.substring(0, 42) + "…" : details;
    }

    private String formatTs(String iso) {
        if (iso == null || iso.isBlank()) return "-";
        try {
            return TS_FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private JLabel inlineLbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.font(Font.BOLD, 11));
        l.setForeground(ThemeManager.secondaryText());
        return l;
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }
}
