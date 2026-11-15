package edu.gcet.placementhub.ui.diagnostics;

import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.GovernanceController;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.DatabaseException;
import edu.gcet.placementhub.database.MongoSchemaInitializer;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.GovernanceModels.CollectionDiagnostic;
import edu.gcet.placementhub.models.GovernanceModels.ExtendedDbStatus;
import edu.gcet.placementhub.models.PlacementDrive;
import edu.gcet.placementhub.services.DashboardService;
import edu.gcet.placementhub.services.PlacementEngine;
import edu.gcet.placementhub.utils.UiComponents.*;
import org.bson.Document;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Live Schema & Aggregation Inspector for MongoDB Atlas & Local Community Engine.
 * Built for academic evaluation and DBMS PBL presentations:
 *  - Live Atlas / Local cluster health, latency, dbStats, server version.
 *  - 10-collection availability & document counts.
 *  - Active index inspector (unique compound, text, single-key).
 *  - Live BSON document samples (highlighting Embedded vs. Referenced models).
 *  - Live Aggregation Pipeline runner ($group, $match, $sort) & .explain("executionStats").
 *  - Programmatic $jsonSchema validation rule inspector.
 */
public class DatabaseDiagnosticsPanel extends JPanel {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss a").withZone(ZoneId.systemDefault());

    private final GovernanceController controller = new GovernanceController();
    private final Consumer<DbStatus> onStatusRefreshed;

    private final BadgeLabel connBadge = new BadgeLabel("CHECKING...", ThemeManager.SLATE_200, ThemeManager.SLATE_800, null, 999);
    private final JLabel clusterTypeVal = new JLabel("-");
    private final JLabel dbNameVal = new JLabel("-");
    private final JLabel hostPortVal = new JLabel("-");
    private final JLabel versionVal = new JLabel("-");
    private final JLabel collectionsVal = new JLabel("0 / 10");
    private final JLabel latencyVal = new JLabel("-");
    private final JLabel storageSizeVal = new JLabel("-");
    private final JLabel lastConnVal = new JLabel("Never");

    private final JLabel diagnosticBanner = new JLabel("Connecting to MongoDB Atlas / Local engine...");
    private final DefaultTableModel collectionTableModel;
    private final JTextArea indexDetailsArea = new JTextArea();
    private final JTextArea bsonSampleArea = new JTextArea();
    private final JTextArea pipelineJsonArea = new JTextArea();
    private final JTextArea pipelineOutputArea = new JTextArea();
    private final JTextArea schemaRulesArea = new JTextArea();

    private final JComboBox<String> sampleColCombo = new JComboBox<>(new String[]{
            "students", "placement_drives", "applications", "users", "companies",
            "faculty", "skills", "approvals", "admin_requests", "announcements", "audit_logs"
    });

    private final JComboBox<String> pipelineCombo = new JComboBox<>(new String[]{
            "Branch Placement KPI Analytics ($group -> $sort)",
            "Placement Drive Eligibility Screening ($match -> $project -> $sort)",
            "Skill-Gap Analysis ($project $setIntersection / $setDifference -> $lookup -> $sort)",
            "CTC Distribution ($match -> $bucket)",
            "Application Funnel ($facet -> $unwind -> $group -> $sort)"
    });

    private final JComboBox<String> schemaColCombo = new JComboBox<>(new String[]{
            "students", "placement_drives", "applications", "users"
    });

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
        JLabel title = new JLabel("MongoDB Live Schema & Aggregation Inspector");
        title.setFont(ThemeManager.font(Font.BOLD, 17));
        title.setForeground(ThemeManager.primaryText());
        titleRow.add(title);
        titleRow.add(connBadge);

        JLabel sub = new JLabel("Real-time cluster monitoring, collection diagnostics, document model governance, and aggregation pipeline inspector.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sub.setForeground(ThemeManager.mutedText());
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        hdrLeft.add(titleRow);
        hdrLeft.add(Box.createVerticalStrut(4));
        hdrLeft.add(sub);

        ActionButton testBtn = new ActionButton("↻  Refresh Cluster Diagnostics", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        testBtn.addActionListener(e -> loadData());

        hdrCard.add(hdrLeft, BorderLayout.CENTER);
        hdrCard.add(testBtn, BorderLayout.EAST);

        // 2. Metadata Grid (Masked, zero credentials exposed)
        JPanel metaGrid = new JPanel(new GridLayout(2, 4, 10, 10));
        metaGrid.setOpaque(false);
        metaGrid.add(infoTile("CLUSTER TYPE", clusterTypeVal));
        metaGrid.add(infoTile("DATABASE NAME", dbNameVal));
        metaGrid.add(infoTile("ACTIVE HOST / SRV", hostPortVal));
        metaGrid.add(infoTile("SERVER VERSION", versionVal));
        metaGrid.add(infoTile("COLLECTIONS VERIFIED", collectionsVal));
        metaGrid.add(infoTile("PING LATENCY", latencyVal));
        metaGrid.add(infoTile("DATA & STORAGE SIZE", storageSizeVal));
        metaGrid.add(infoTile("LAST SUCCESSFUL PING", lastConnVal));

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        hdrCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(hdrCard);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(metaGrid);

        add(topStack, BorderLayout.NORTH);

        // 3. Tabbed Inspector Body
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(ThemeManager.font(Font.BOLD, 12));

        // Tab 1: Collections & Indexes
        String[] cols = {"#", "Collection Name", "Availability", "Live Docs", "Indexes", "Validator", "Document Architecture"};
        collectionTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable colTable = new JTable(collectionTableModel);
        colTable.setRowHeight(28);
        colTable.setFont(ThemeManager.monoFont(Font.PLAIN, 12));
        colTable.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11));
        colTable.setFillsViewportHeight(true);
        colTable.setShowVerticalLines(false);
        colTable.getColumnModel().getColumn(0).setMaxWidth(40);
        colTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
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

        indexDetailsArea.setFont(ThemeManager.monoFont(Font.PLAIN, 11.5f));
        indexDetailsArea.setEditable(false);
        indexDetailsArea.setBackground(new Color(0xF8, 0xFA, 0xFC));
        indexDetailsArea.setText("Select a collection row above to inspect its active MongoDB indexes and document modeling note.");

        colTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && colTable.getSelectedRow() >= 0) {
                int row = colTable.getSelectedRow();
                String colName = (String) collectionTableModel.getValueAt(row, 1);
                updateIndexDetails(colName);
            }
        });

        JSplitPane splitCol = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(colTable), new JScrollPane(indexDetailsArea));
        splitCol.setResizeWeight(0.65);
        splitCol.setDividerSize(6);

        tabbedPane.addTab("Collections & Indexes", splitCol);

        // Tab 2: Live BSON Document Samples
        JPanel bsonPanel = new JPanel(new BorderLayout(0, 8));
        bsonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        bsonPanel.setOpaque(false);

        JPanel bsonTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bsonTopBar.setOpaque(false);
        bsonTopBar.add(new JLabel("Target Collection:"));
        bsonTopBar.add(sampleColCombo);
        ActionButton fetchBsonBtn = new ActionButton("Query Live Sample", ThemeManager.CYAN_PRIMARY, ThemeManager.CYAN_DARK, Color.WHITE, null, 6);
        fetchBsonBtn.addActionListener(e -> fetchLiveBsonSample());
        sampleColCombo.addActionListener(e -> fetchLiveBsonSample());
        bsonTopBar.add(fetchBsonBtn);

        bsonSampleArea.setFont(ThemeManager.monoFont(Font.PLAIN, 12));
        bsonSampleArea.setEditable(false);
        bsonSampleArea.setBackground(new Color(0x0F, 0x17, 0x2A));
        bsonSampleArea.setForeground(new Color(0x38, 0xBD, 0xF8));
        bsonSampleArea.setText("// Click 'Query Live Sample' to retrieve live BSON documents from MongoDB...");

        bsonPanel.add(bsonTopBar, BorderLayout.NORTH);
        bsonPanel.add(new JScrollPane(bsonSampleArea), BorderLayout.CENTER);
        tabbedPane.addTab("Live BSON Samples", bsonPanel);

        // Tab 3: Aggregation Pipeline & EXPLAIN Inspector
        JPanel pipelinePanel = new JPanel(new BorderLayout(0, 8));
        pipelinePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        pipelinePanel.setOpaque(false);

        JPanel pipeTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pipeTopBar.setOpaque(false);
        pipeTopBar.add(new JLabel("Pipeline Template:"));
        pipeTopBar.add(pipelineCombo);
        ActionButton runPipeBtn = new ActionButton("▶ Run Pipeline", ThemeManager.EMERALD_PRIMARY, new Color(0x05, 0x96, 0x69), Color.WHITE, null, 6);
        ActionButton explainPipeBtn = new ActionButton("⚡ Explain Plan (.explain('executionStats'))", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 6);
        pipeTopBar.add(runPipeBtn);
        pipeTopBar.add(explainPipeBtn);

        pipelineCombo.addActionListener(e -> updatePipelineCode());
        runPipeBtn.addActionListener(e -> executeSelectedPipeline());
        explainPipeBtn.addActionListener(e -> explainSelectedPipeline());

        pipelineJsonArea.setFont(ThemeManager.monoFont(Font.PLAIN, 11.5f));
        pipelineJsonArea.setEditable(false);
        pipelineJsonArea.setBackground(new Color(0x1E, 0x29, 0x3B));
        pipelineJsonArea.setForeground(new Color(0x94, 0xA3, 0xB8));

        pipelineOutputArea.setFont(ThemeManager.monoFont(Font.PLAIN, 11.5f));
        pipelineOutputArea.setEditable(false);
        pipelineOutputArea.setBackground(new Color(0x0B, 0x11, 0x20));
        pipelineOutputArea.setForeground(new Color(0x34, 0xD3, 0x99));
        pipelineOutputArea.setText("// Output of execution or .explain(\"executionStats\") will render here...");

        JSplitPane splitPipe = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(pipelineJsonArea), new JScrollPane(pipelineOutputArea));
        splitPipe.setResizeWeight(0.45);
        splitPipe.setDividerSize(6);

        pipelinePanel.add(pipeTopBar, BorderLayout.NORTH);
        pipelinePanel.add(splitPipe, BorderLayout.CENTER);
        tabbedPane.addTab("Aggregation & EXPLAIN", pipelinePanel);

        // Tab 4: $jsonSchema Governance Rules
        JPanel schemaPanel = new JPanel(new BorderLayout(0, 8));
        schemaPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        schemaPanel.setOpaque(false);

        JPanel schemaTopBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        schemaTopBar.setOpaque(false);
        schemaTopBar.add(new JLabel("Governance Collection:"));
        schemaTopBar.add(schemaColCombo);
        schemaColCombo.addActionListener(e -> updateSchemaDisplay());

        schemaRulesArea.setFont(ThemeManager.monoFont(Font.PLAIN, 11.5f));
        schemaRulesArea.setEditable(false);
        schemaRulesArea.setBackground(new Color(0x1E, 0x29, 0x3B));
        schemaRulesArea.setForeground(new Color(0xFB, 0xBF, 0x24));

        schemaPanel.add(schemaTopBar, BorderLayout.NORTH);
        schemaPanel.add(new JScrollPane(schemaRulesArea), BorderLayout.CENTER);
        tabbedPane.addTab("$jsonSchema Rules", schemaPanel);

        updatePipelineCode();
        updateSchemaDisplay();

        add(tabbedPane, BorderLayout.CENTER);

        // 4. Bottom Diagnostic Banner
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
        diagnosticBanner.setText("Executing MongoDB ping command and scanning collection catalog...");
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

        clusterTypeVal.setText(nonBlank(ext.clusterType(), "MongoDB Atlas / Local"));
        dbNameVal.setText(nonBlank(st.getDatabase(), "gcet_placement_hub"));
        hostPortVal.setText(nonBlank(st.getHost(), "localhost") + (st.getPort() > 0 ? ":" + st.getPort() : " (SRV)"));
        versionVal.setText(nonBlank(ext.serverVersion(), "Unavailable"));
        collectionsVal.setText(ext.availableCollectionCount() + " / " + ext.totalRequiredCollections() + " Verified");
        latencyVal.setText(ext.pingLatencyMs() >= 0 ? ext.pingLatencyMs() + " ms" : "Unreachable");
        lastConnVal.setText(formatTs(st.getLastSuccessfulConnectionIso()));

        if (ext.dbStats() != null && !ext.dbStats().isEmpty()) {
            Object dataSize = ext.dbStats().get("dataSize");
            Object storageSize = ext.dbStats().get("storageSize");
            storageSizeVal.setText((dataSize != null ? dataSize : "-") + " KB data / " + (storageSize != null ? storageSize : "-") + " KB storage");
        } else {
            storageSizeVal.setText("N/A");
        }

        // Fill collections table
        collectionTableModel.setRowCount(0);
        int idx = 1;
        for (CollectionDiagnostic cd : ext.collections()) {
            collectionTableModel.addRow(new Object[]{
                    idx++,
                    cd.collectionName(),
                    cd.exists() ? "AVAILABLE" : "MISSING",
                    cd.documentCount(),
                    cd.indexCount() + " index(es)",
                    cd.hasJsonSchemaValidator() ? "Enforced ($jsonSchema)" : "None",
                    cd.modelNote()
            });
        }

        if (st.isConnected()) {
            connBadge.setText("CONNECTED");
            connBadge.setBadgeColors(new Color(16, 185, 129, 32), ThemeManager.EMERALD_PRIMARY, new Color(16, 185, 129, 95));
            diagnosticBanner.setForeground(ThemeManager.EMERALD_PRIMARY);
            diagnosticBanner.setText("<html><b>Cluster Health: Verified.</b> "
                    + esc(ext.clusterType()) + " · Server " + esc(ext.serverVersion())
                    + " · Ping Latency: " + ext.pingLatencyMs() + " ms"
                    + " · " + ext.availableCollectionCount() + "/" + ext.totalRequiredCollections() + " collections active.</html>");
        } else {
            connBadge.setText("DISCONNECTED");
            connBadge.setBadgeColors(new Color(225, 29, 72, 32), ThemeManager.ROSE_PRIMARY, new Color(225, 29, 72, 95));
            diagnosticBanner.setForeground(ThemeManager.ROSE_PRIMARY);
            diagnosticBanner.setText("<html><b>Connection Diagnostic Error:</b> "
                    + esc(nonBlank(st.getLastError(), st.getMessage()))
                    + "<br/><b>Remediation:</b> Verify MongoDB is running on " + esc(st.getHost())
                    + ", or check Atlas IP whitelist (0.0.0.0/0) in MongoDB Atlas Security Network Access.</html>");
        }
    }

    private void updateIndexDetails(String colName) {
        if (latest == null || latest.indexesByCollection() == null) return;
        List<String> idxList = latest.indexesByCollection().get(colName);
        StringBuilder sb = new StringBuilder();
        sb.append("=== Collection: ").append(colName).append(" ===\n");
        sb.append("Model Paradigm: ").append(DatabaseConnection.modelNote(colName)).append("\n\n");
        sb.append("Active Indexes in MongoDB:\n");
        if (idxList != null && !idxList.isEmpty()) {
            for (String idx : idxList) {
                sb.append(" • ").append(idx).append("\n");
            }
        } else {
            sb.append(" (No secondary indexes or collection not initialized yet)\n");
        }
        indexDetailsArea.setText(sb.toString());
        indexDetailsArea.setCaretPosition(0);
    }

    private void fetchLiveBsonSample() {
        String col = (String) sampleColCombo.getSelectedItem();
        if (col == null) return;
        try {
            List<Document> samples = DatabaseConnection.getInstance().sampleDocuments(col, 2);
            if (samples.isEmpty()) {
                bsonSampleArea.setText("// Collection '" + col + "' is currently empty.\n// Launch an operation or trigger seed data to populate demo documents.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("// Sample BSON Document from '").append(col).append("' collection (Limit: 2)\n");
            sb.append("// Note: Embedded sub-documents & arrays are highlighted below:\n\n");
            for (int i = 0; i < samples.size(); i++) {
                sb.append("/* Document #").append(i + 1).append(" */\n");
                sb.append(prettyPrintJson(samples.get(i).toJson())).append("\n\n");
            }
            bsonSampleArea.setText(sb.toString());
            bsonSampleArea.setCaretPosition(0);
        } catch (DatabaseException ex) {
            bsonSampleArea.setText("// Failed to fetch sample document: " + ex.getMessage());
        }
    }

    /** The live drive used by the drive-scoped pipelines (first OPEN drive, else a representative template). */
    private PlacementDrive resolveDemoDrive() {
        try {
            var db = DatabaseConnection.getInstance().getDatabase();
            List<PlacementDrive> drives = new edu.gcet.placementhub.dao.DriveDao().findAll(db);
            for (PlacementDrive d : drives) {
                if ("OPEN".equalsIgnoreCase(d.getStatus()) && !d.getRequiredSkills().isEmpty()) return d;
            }
            if (!drives.isEmpty()) return drives.get(0);
        } catch (Exception ignored) {
        }
        PlacementDrive template = new PlacementDrive();
        template.setCompanyName("Template Drive");
        template.setJobRole("Software Engineer");
        template.setMinCgpa(6.5);
        template.setMaxBacklogs(0);
        template.setTargetGradYear(2026);
        template.setEligibleBranches(List.of("CSE", "IT"));
        return template;
    }

    private String pipelineHeader(int idx, PlacementDrive drive) {
        return switch (idx) {
            case 0 -> "// MongoDB Aggregation Pipeline: Branch Placement KPI Analytics\n// Collection: students\n// Source: DashboardService.getBranchAggregationPipeline()\n\n";
            case 1 -> "// MongoDB Aggregation Pipeline: Drive Eligibility Screening\n// Collection: students · Drive: "
                    + drive.getCompanyName() + " - " + drive.getJobRole()
                    + "\n// Source: PlacementEngine.buildEligibilityPipeline(drive)\n\n";
            case 2 -> "// MongoDB Aggregation Pipeline: Skill-Gap Analysis (set algebra inside the engine)\n// Collection: students · Drive: "
                    + drive.getCompanyName() + " - " + drive.getJobRole()
                    + "\n// Source: PlacementEngine.buildSkillGapPipeline(drive)\n\n";
            case 3 -> "// MongoDB Aggregation Pipeline: CTC Distribution ($bucket by placedPackageLpa)\n// Collection: students\n// Source: DashboardService.getCtcDistributionPipeline()\n\n";
            default -> "// MongoDB Aggregation Pipeline: Application Funnel ($facet multi-pipeline + $unwind stageHistory[])\n// Collection: applications\n// Source: DashboardService.getApplicationFunnelPipeline()\n\n";
        };
    }

    private List<org.bson.conversions.Bson> selectedPipeline(int idx, PlacementDrive drive) {
        return switch (idx) {
            case 0 -> DashboardService.getBranchAggregationPipeline();
            case 1 -> PlacementEngine.buildEligibilityPipeline(drive);
            case 2 -> PlacementEngine.buildSkillGapPipeline(drive);
            case 3 -> DashboardService.getCtcDistributionPipeline();
            default -> DashboardService.getApplicationFunnelPipeline();
        };
    }

    private void updatePipelineCode() {
        int idx = pipelineCombo.getSelectedIndex();
        PlacementDrive drive = (idx == 1 || idx == 2) ? resolveDemoDrive() : new PlacementDrive();
        try {
            // Render the exact pipeline the Java code sends to MongoDB (no hand-written copy that can drift)
            pipelineJsonArea.setText(pipelineHeader(idx, drive)
                    + edu.gcet.placementhub.database.BsonUtil.prettyJson(selectedPipeline(idx, drive)));
        } catch (Exception ex) {
            pipelineJsonArea.setText("// Could not render pipeline: " + ex.getMessage());
        }
        pipelineJsonArea.setCaretPosition(0);
    }

    private void executeSelectedPipeline() {
        try {
            var db = DatabaseConnection.getInstance().getDatabase();
            int idx = pipelineCombo.getSelectedIndex();
            StringBuilder sb = new StringBuilder();
            if (idx == 0) {
                Map<String, DashboardService.BranchStat> stats = DashboardService.runBranchAggregationPipeline(db);
                sb.append("=== Executed Branch Aggregation Pipeline ===\n");
                sb.append("Total Branches Computed: ").append(stats.size()).append("\n\n");
                for (var entry : stats.entrySet()) {
                    DashboardService.BranchStat bs = entry.getValue();
                    sb.append(String.format("Branch: %-12s | Total: %2d | Placed: %2d | Unplaced: %2d | Avg CGPA: %.2f\n",
                            bs.branch(), bs.total(), bs.placed(), bs.unplaced(), bs.avgCgpa()));
                }
            } else if (idx == 1) {
                PlacementDrive drive = resolveDemoDrive();
                List<Document> matches = PlacementEngine.executeEligibilityPipeline(db, drive);
                sb.append("=== Executed Eligibility Pipeline for ").append(drive.getCompanyName()).append(" - ").append(drive.getJobRole()).append(" ===\n");
                sb.append("Eligible Students Found: ").append(matches.size()).append("\n\n");
                for (Document d : matches) {
                    sb.append(prettyPrintJson(d.toJson())).append("\n");
                }
            } else if (idx == 2) {
                PlacementDrive drive = resolveDemoDrive();
                List<Document> rows = PlacementEngine.executeSkillGapPipeline(db, drive);
                sb.append("=== Executed Skill-Gap Pipeline for ").append(drive.getCompanyName()).append(" - ").append(drive.getJobRole()).append(" ===\n");
                sb.append("Required skills: ").append(drive.getRequiredSkills().size())
                        .append(" · Students evaluated: ").append(rows.size()).append("\n\n");
                for (Document d : rows) {
                    sb.append(String.format("%-12s %-28s %-10s readiness %3s%%  matched %s  missing %s  mandatoryGap %s  -> %s\n",
                            d.get("rollNumber"), d.get("fullName"), d.get("branch"),
                            String.valueOf(d.get("readinessPct")), d.get("matchedCount"), d.get("missingCount"),
                            d.get("mandatoryGapCount"), d.get("missingSkillNames")));
                }
            } else if (idx == 3) {
                List<Document> buckets = DashboardService.runCtcDistributionPipeline(db);
                sb.append("=== Executed CTC Distribution ($bucket) ===\n\n");
                for (Document d : buckets) {
                    sb.append(prettyPrintJson(d.toJson())).append("\n");
                }
            } else {
                Document facet = DashboardService.runApplicationFunnelPipeline(db);
                sb.append("=== Executed Application Funnel ($facet) ===\n\n");
                sb.append(prettyPrintJson(facet.toJson()));
            }
            pipelineOutputArea.setText(sb.toString());
            pipelineOutputArea.setCaretPosition(0);
        } catch (Exception ex) {
            pipelineOutputArea.setText("// Execution error: " + ex.getMessage());
        }
    }

    private void explainSelectedPipeline() {
        try {
            var db = DatabaseConnection.getInstance().getDatabase();
            int idx = pipelineCombo.getSelectedIndex();
            String explainJson;
            switch (idx) {
                case 0 -> explainJson = DashboardService.explainBranchAggregation(db);
                case 1 -> explainJson = PlacementEngine.explainEligibilityPipeline(db, resolveDemoDrive());
                case 2 -> explainJson = PlacementEngine.explainSkillGapPipeline(db, resolveDemoDrive());
                case 3 -> explainJson = DashboardService.explainCtcDistribution(db);
                default -> explainJson = DashboardService.explainApplicationFunnel(db);
            }
            pipelineOutputArea.setText("// MongoDB executionStats Plan (index usage, docs examined, execution time):\n"
                    + summarizeExplain(explainJson) + "\n\n" + prettyPrintJson(explainJson));
            pipelineOutputArea.setCaretPosition(0);
        } catch (Exception ex) {
            pipelineOutputArea.setText("// Explain error: " + ex.getMessage());
        }
    }

    /** Pulls the headline executionStats numbers out of an explain document. */
    private static String summarizeExplain(String explainJson) {
        try {
            Document ex = Document.parse(explainJson);
            Document stats = findExecutionStats(ex);
            if (stats == null) return "// (executionStats not present in this explain output)";
            String indexName = findIndexName(ex);
            return "// executionTimeMillis=" + stats.get("executionTimeMillis")
                    + " · totalDocsExamined=" + stats.get("totalDocsExamined")
                    + " · totalKeysExamined=" + stats.get("totalKeysExamined")
                    + " · nReturned=" + stats.get("nReturned")
                    + " · index=" + (indexName != null ? indexName : "none (COLLSCAN)");
        } catch (Exception e) {
            return "";
        }
    }

    private static Document findExecutionStats(Document doc) {
        if (doc == null) return null;
        if (doc.get("executionStats") instanceof Document es) return es;
        for (Object v : doc.values()) {
            if (v instanceof Document d) {
                Document found = findExecutionStats(d);
                if (found != null) return found;
            } else if (v instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Document d) {
                        Document found = findExecutionStats(d);
                        if (found != null) return found;
                    }
                }
            }
        }
        return null;
    }

    private static String findIndexName(Object node) {
        if (node instanceof Document d) {
            if (d.get("indexName") instanceof String s) return s;
            for (Object v : d.values()) {
                String found = findIndexName(v);
                if (found != null) return found;
            }
        } else if (node instanceof List<?> list) {
            for (Object o : list) {
                String found = findIndexName(o);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void updateSchemaDisplay() {
        String col = (String) schemaColCombo.getSelectedItem();
        if (col == null) return;
        Map<String, String> defs = MongoSchemaInitializer.schemaDefinitions();
        String json = defs.getOrDefault(col, "// No $jsonSchema definition found for " + col);
        schemaRulesArea.setText("// Programmatic $jsonSchema for collection: " + col + "\n"
                + "// Enforced server-side with validationAction: 'error', validationLevel: 'moderate'\n\n"
                + prettyPrintJson(json));
        schemaRulesArea.setCaretPosition(0);
    }

    private static String prettyPrintJson(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;
        for (char c : raw.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
                sb.append(c);
            } else if (!inQuotes) {
                if (c == '{' || c == '[') {
                    sb.append(c).append("\n");
                    indent += 2;
                    sb.append(" ".repeat(indent));
                } else if (c == '}' || c == ']') {
                    sb.append("\n");
                    indent = Math.max(0, indent - 2);
                    sb.append(" ".repeat(indent));
                    sb.append(c);
                } else if (c == ',') {
                    sb.append(c).append("\n").append(" ".repeat(indent));
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private JPanel infoTile(String title, JLabel valLabel) {
        RoundedCard tile = new RoundedCard(10, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tile.setLayout(new BorderLayout(0, 4));
        tile.setBorder(new EmptyBorder(8, 12, 8, 12));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(ThemeManager.font(Font.BOLD, 10));
        titleLbl.setForeground(ThemeManager.mutedText());

        valLabel.setFont(ThemeManager.monoFont(Font.BOLD, 12));
        valLabel.setForeground(ThemeManager.primaryText());

        tile.add(titleLbl, BorderLayout.NORTH);
        tile.add(valLabel, BorderLayout.CENTER);
        return tile;
    }

    private static String formatTs(String iso) {
        if (iso == null || iso.isBlank()) return "Never";
        try {
            return TS_FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private static String nonBlank(String val, String fallback) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }

    private static String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
