package edu.gcet.placementhub.ui.applications;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.PlacementController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.EligibilityResult;
import edu.gcet.placementhub.services.PlacementEngine;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * Placement Applications & 10-Stage Recruitment Pipeline Module (Phase 4):
 * - Role-aware ledger (Student own applications, Company own-drive applicants, Admin/Officer full oversight)
 * - Eligibility-gated Apply / Re-apply dialog
 * - 10-Stage visual progress tracker & chronological `application_stage_history` viewer
 * - Authorized Advance Stage (with Offer LPA), Reject (mandatory reason), Withdraw, and CSV Export
 */
public class ApplicationsPanel extends JPanel {

    private static final List<ApplicationStage> PIPELINE_ORDER = List.of(
            ApplicationStage.APPLICATION_SUBMITTED,
            ApplicationStage.APPLICATION_REVIEWED,
            ApplicationStage.SHORTLISTED,
            ApplicationStage.ONLINE_ASSESSMENT,
            ApplicationStage.TECHNICAL_INTERVIEW,
            ApplicationStage.HR_INTERVIEW,
            ApplicationStage.SELECTED,
            ApplicationStage.OFFERED,
            ApplicationStage.JOINED
    );

    private final PlacementController controller = new PlacementController();

    private List<Application> allApplications = List.of();
    private List<Application> filteredApps = List.of();
    private List<PlacementDrive> allDrives = List.of();
    private List<Company> allCompanies = List.of();
    private List<Student> allStudents = List.of();
    private List<Skill> allSkills = List.of();

    private String pendingDriveFilterId = "ALL";
    private boolean suppressFilters = false;

    private final Map<String, String> driveComboIdMap = new LinkedHashMap<>();
    private final Map<String, String> companyComboIdMap = new LinkedHashMap<>();

    private JLabel subtitleLabel;
    private AlertBanner statusBanner;
    private JTextField searchField;
    private JComboBox<String> companyCombo;
    private JComboBox<String> driveCombo;
    private JComboBox<String> stageCombo;

    private ActionButton applyEligibleBtn;
    private ActionButton advanceBtn;
    private ActionButton rejectBtn;
    private ActionButton withdrawBtn;
    private ActionButton reapplyBtn;

    private JPanel kpiStrip;
    private JTable table;
    private DefaultTableModel tableModel;
    private JPanel inspectorContainer;

    public ApplicationsPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));
        buildUi();
    }

    public void setInitialDriveId(String driveId) {
        this.pendingDriveFilterId = (driveId != null && !driveId.isBlank()) ? driveId : "ALL";
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        subtitleLabel.setText("Loading applications and 10-stage recruitment history from MySQL...");
        controller.loadPlacementBundleAsync(actor, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.allApplications = bundle.applications;
            this.allDrives = bundle.drives;
            this.allCompanies = bundle.companies;
            this.allStudents = bundle.students;
            this.allSkills = bundle.skills;
            populateFilterCombos();
            applyFiltersAndRender();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        populateFilterCombos();
        applyFiltersAndRender();
        revalidate();
        repaint();
    }

    private void buildUi() {
        // Header
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Placement Applications & Recruitment Pipeline");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("10-stage recruitment tracking from application submission to offer letters and joining.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(subtitleLabel);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionRow.setOpaque(false);
        applyEligibleBtn = new ActionButton("+  Apply to Eligible Drive", ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY.darker(), Color.WHITE, null, 10);
        applyEligibleBtn.addActionListener(e -> openEligibleApplyDialog());
        ActionButton exportBtn = new ActionButton("Export CSV", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        exportBtn.addActionListener(e -> exportCsv());
        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());

        actionRow.add(applyEligibleBtn);
        actionRow.add(exportBtn);
        actionRow.add(refreshBtn);

        header.add(titleCol, BorderLayout.WEST);
        header.add(actionRow, BorderLayout.EAST);

        // KPI Strip
        kpiStrip = new JPanel(new GridLayout(1, 5, 10, 0));
        kpiStrip.setOpaque(false);
        kpiStrip.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Filter Card
        RoundedCard filterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));
        filterCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        statusBanner = new AlertBanner();
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filterRow = new JPanel(new GridBagLayout());
        filterRow.setOpaque(false);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 10);

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search student name, roll number, company, or role...");
        searchField.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        searchField.setPreferredSize(new Dimension(220, 34));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void removeUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void changedUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
        });

        companyCombo = new JComboBox<>(new String[]{"All Companies"});
        companyCombo.setPreferredSize(new Dimension(170, 34));
        companyCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        driveCombo = new JComboBox<>(new String[]{"All Drives"});
        driveCombo.setPreferredSize(new Dimension(200, 34));
        driveCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        stageCombo = new JComboBox<>();
        stageCombo.setPreferredSize(new Dimension(175, 34));
        stageCombo.addItem("All Stages");
        for (ApplicationStage s : PIPELINE_ORDER) {
            stageCombo.addItem(s.name() + " (" + s.getLabel() + ")");
        }
        stageCombo.addItem("REJECTED (Rejected)");
        stageCombo.addItem("WITHDRAWN (Withdrawn)");
        stageCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        gbc.gridx = 0; gbc.weightx = 1.0;
        filterRow.add(searchField, gbc);
        gbc.gridx = 1; gbc.weightx = 0.0;
        filterRow.add(companyCombo, gbc);
        gbc.gridx = 2;
        filterRow.add(driveCombo, gbc);
        gbc.gridx = 3; gbc.insets = new Insets(0, 0, 0, 0);
        filterRow.add(stageCombo, gbc);

        filterCard.add(statusBanner);
        filterCard.add(Box.createVerticalStrut(2));
        filterCard.add(filterRow);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(header);
        topStack.add(Box.createVerticalStrut(10));
        topStack.add(kpiStrip);
        topStack.add(Box.createVerticalStrut(10));
        topStack.add(filterCard);

        // Main split: Applications Table + 10-Stage Journey Inspector
        RoundedCard tableCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.setBorder(new EmptyBorder(12, 14, 12, 14));

        String[] cols = {"Candidate", "Roll No", "Branch / CGPA", "Company & Role", "Current Stage", "Applied On", "History"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(36);
        table.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                renderSelectedInspector();
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder()));

        JPanel rowBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rowBtns.setOpaque(false);

        advanceBtn = new ActionButton("Advance Stage  →", ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 8);
        advanceBtn.addActionListener(e -> openAdvanceDialog());

        rejectBtn = new ActionButton("Reject Candidate", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, new Color(0xFC, 0xA5, 0xA5), 8);
        rejectBtn.addActionListener(e -> openRejectDialog());

        withdrawBtn = new ActionButton("Withdraw Application", ThemeManager.AMBER_SOFT, new Color(0xFD, 0xE6, 0x8A), ThemeManager.AMBER_PRIMARY, ThemeManager.AMBER_PRIMARY, 8);
        withdrawBtn.addActionListener(e -> confirmWithdraw());

        reapplyBtn = new ActionButton("Re-Submit Application", ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY.darker(), Color.WHITE, null, 8);
        reapplyBtn.addActionListener(e -> confirmReapply());

        rowBtns.add(reapplyBtn);
        rowBtns.add(withdrawBtn);
        rowBtns.add(advanceBtn);
        rowBtns.add(rejectBtn);

        tableCard.add(tableScroll, BorderLayout.CENTER);
        tableCard.add(rowBtns, BorderLayout.SOUTH);

        inspectorContainer = new JPanel(new BorderLayout());
        inspectorContainer.setOpaque(false);
        inspectorContainer.setPreferredSize(new Dimension(410, 480));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableCard, inspectorContainer);
        split.setResizeWeight(0.62);
        split.setDividerSize(10);
        split.setBorder(null);
        split.setOpaque(false);

        add(topStack, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private void populateFilterCombos() {
        suppressFilters = true;
        try {
            companyCombo.removeAllItems();
            companyComboIdMap.clear();
            companyCombo.addItem("All Companies");
            companyComboIdMap.put("All Companies", "ALL");
            for (Company c : allCompanies) {
                companyCombo.addItem(c.getName());
                companyComboIdMap.put(c.getName(), c.getId());
            }

            driveCombo.removeAllItems();
            driveComboIdMap.clear();
            driveCombo.addItem("All Drives");
            driveComboIdMap.put("All Drives", "ALL");
            String matchedLabel = "All Drives";
            for (PlacementDrive d : allDrives) {
                String lbl = d.getCompanyName() + " — " + d.getJobRole();
                if (driveComboIdMap.containsKey(lbl)) {
                    lbl = lbl + " [" + d.getId().substring(0, Math.min(6, d.getId().length())) + "]";
                }
                driveCombo.addItem(lbl);
                driveComboIdMap.put(lbl, d.getId());
                if (d.getId().equals(pendingDriveFilterId)) {
                    matchedLabel = lbl;
                }
            }
            driveCombo.setSelectedItem(matchedLabel);
        } finally {
            suppressFilters = false;
        }
    }

    private void applyFiltersAndRender() {
        User actor = AppSession.getInstance().getCurrentUser();
        boolean canApplyFeature = actor != null && (actor.getRole() == UserRole.STUDENT || actor.getRole().isPrivilegedRole());
        if (applyEligibleBtn != null) applyEligibleBtn.setVisible(canApplyFeature);

        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String compLabel = companyCombo != null && companyCombo.getSelectedItem() != null
                ? companyCombo.getSelectedItem().toString() : "All Companies";
        String compId = companyComboIdMap.getOrDefault(compLabel, "ALL");

        String drvLabel = driveCombo != null && driveCombo.getSelectedItem() != null
                ? driveCombo.getSelectedItem().toString() : "All Drives";
        String drvId = driveComboIdMap.getOrDefault(drvLabel, "ALL");

        String stLabel = stageCombo != null && stageCombo.getSelectedItem() != null
                ? stageCombo.getSelectedItem().toString() : "All Stages";
        String stageFilter = "All Stages".equals(stLabel) ? "ALL" : stLabel.split(" ")[0];

        List<Application> list = new ArrayList<>();
        for (Application a : allApplications) {
            if (actor != null && actor.getRole() == UserRole.STUDENT && actor.getStudentRollNumber() != null) {
                if (!actor.getStudentRollNumber().equalsIgnoreCase(a.getStudentRollNumber())) {
                    continue;
                }
            }
            if (actor != null && actor.getRole() == UserRole.COMPANY && actor.getCompanyId() != null) {
                if (!actor.getCompanyId().equals(a.getCompanyId())) {
                    continue;
                }
            }

            boolean matchQ = q.isEmpty()
                    || safeLower(a.getStudentName()).contains(q)
                    || safeLower(a.getStudentRollNumber()).contains(q)
                    || safeLower(a.getCompanyName()).contains(q)
                    || safeLower(a.getJobRole()).contains(q);
            boolean matchComp = "ALL".equals(compId) || compId.equals(a.getCompanyId());
            boolean matchDrv = "ALL".equals(drvId) || drvId.equals(a.getDriveId());
            boolean matchStage = "ALL".equals(stageFilter)
                    || (a.getCurrentStage() != null && a.getCurrentStage().name().equalsIgnoreCase(stageFilter));

            if (matchQ && matchComp && matchDrv && matchStage) {
                list.add(a);
            }
        }

        this.filteredApps = list;
        subtitleLabel.setText("Showing " + filteredApps.size() + " application(s) in MySQL · 10-Stage Pipeline Tracker");
        refreshKpiStrip(filteredApps);

        tableModel.setRowCount(0);
        for (Application a : filteredApps) {
            String stageTxt = a.getCurrentStage() != null ? a.getCurrentStage().getLabel() : "Application Submitted";
            if (a.getFinalOfferLpa() != null && a.getFinalOfferLpa() > 0) {
                stageTxt += String.format(Locale.US, " (%.1f LPA)", a.getFinalOfferLpa());
            }
            String appliedDate = a.getAppliedAt() != null && a.getAppliedAt().length() >= 10
                    ? a.getAppliedAt().substring(0, 10) : "—";
            tableModel.addRow(new Object[]{
                    a.getStudentName(),
                    a.getStudentRollNumber(),
                    String.format(Locale.US, "%s · %.2f", nonNull(a.getStudentBranch()), a.getStudentCgpa()),
                    a.getCompanyName() + " — " + a.getJobRole(),
                    stageTxt,
                    appliedDate,
                    a.getStageHistory().size() + " steps"
            });
        }

        if (!filteredApps.isEmpty() && table.getSelectedRow() < 0) {
            table.setRowSelectionInterval(0, 0);
        } else {
            renderSelectedInspector();
        }
    }

    private void refreshKpiStrip(List<Application> apps) {
        kpiStrip.removeAll();
        int total = apps.size();
        int submitted = 0, shortlisted = 0, interviews = 0, selected = 0;
        for (Application a : apps) {
            ApplicationStage s = a.getCurrentStage();
            if (s == ApplicationStage.APPLICATION_SUBMITTED || s == ApplicationStage.APPLICATION_REVIEWED) {
                submitted++;
            } else if (s == ApplicationStage.SHORTLISTED || s == ApplicationStage.ONLINE_ASSESSMENT) {
                shortlisted++;
            } else if (s == ApplicationStage.TECHNICAL_INTERVIEW || s == ApplicationStage.HR_INTERVIEW) {
                interviews++;
            } else if (s != null && s.isOfferOrSelected()) {
                selected++;
            }
        }
        kpiStrip.add(kpiCard("Total Applications", String.valueOf(total), ThemeManager.BLUE_ELECTRIC));
        kpiStrip.add(kpiCard("Submitted / Reviewed", String.valueOf(submitted), ThemeManager.CYAN_DARK));
        kpiStrip.add(kpiCard("Shortlisted / OA", String.valueOf(shortlisted), ThemeManager.PURPLE_PRIMARY));
        kpiStrip.add(kpiCard("Tech & HR Interviews", String.valueOf(interviews), ThemeManager.AMBER_PRIMARY));
        kpiStrip.add(kpiCard("Selected / Offered / Joined", String.valueOf(selected), ThemeManager.EMERALD_PRIMARY));
        kpiStrip.revalidate();
        kpiStrip.repaint();
    }

    private JPanel kpiCard(String label, String val, Color accent) {
        RoundedCard c = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel v = new JLabel(val);
        v.setFont(ThemeManager.font(Font.BOLD, 20));
        v.setForeground(accent);
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.PLAIN, 11));
        l.setForeground(ThemeManager.mutedText());
        c.add(v);
        c.add(Box.createVerticalStrut(2));
        c.add(l);
        return c;
    }

    private Application getSelectedApplication() {
        int row = table != null ? table.getSelectedRow() : -1;
        if (row < 0 || row >= filteredApps.size()) return null;
        return filteredApps.get(row);
    }

    private void renderSelectedInspector() {
        inspectorContainer.removeAll();
        Application app = getSelectedApplication();
        User actor = AppSession.getInstance().getCurrentUser();

        boolean canManageStage = false;
        boolean canWithdraw = false;
        boolean canReapply = false;

        if (app != null && actor != null) {
            boolean ownsCompanyApp = actor.getRole() == UserRole.COMPANY
                    && actor.getCompanyId() != null
                    && actor.getCompanyId().equals(app.getCompanyId());
            boolean staffManager = actor.getRole().isPrivilegedRole() || ownsCompanyApp;
            ApplicationStage st = app.getCurrentStage();
            canManageStage = staffManager
                    && st != ApplicationStage.REJECTED
                    && st != ApplicationStage.WITHDRAWN
                    && st != ApplicationStage.JOINED;

            boolean isStudentSelf = actor.getRole() == UserRole.STUDENT
                    && actor.getStudentRollNumber() != null
                    && actor.getStudentRollNumber().equalsIgnoreCase(app.getStudentRollNumber());
            canWithdraw = isStudentSelf
                    && st != ApplicationStage.WITHDRAWN
                    && st != ApplicationStage.REJECTED
                    && !st.isOfferOrSelected();
            canReapply = isStudentSelf && st == ApplicationStage.WITHDRAWN;
        }

        if (advanceBtn != null) advanceBtn.setEnabled(canManageStage);
        if (rejectBtn != null) rejectBtn.setEnabled(canManageStage);
        if (withdrawBtn != null) withdrawBtn.setVisible(actor != null && actor.getRole() == UserRole.STUDENT);
        if (withdrawBtn != null) withdrawBtn.setEnabled(canWithdraw);
        if (reapplyBtn != null) reapplyBtn.setVisible(canReapply);

        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        if (app == null) {
            JLabel empty = new JLabel("Select an application row to inspect the 10-stage recruitment timeline.");
            empty.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
            empty.setForeground(ThemeManager.mutedText());
            card.add(empty);
            inspectorContainer.add(card, BorderLayout.CENTER);
            inspectorContainer.revalidate();
            inspectorContainer.repaint();
            return;
        }

        JLabel title = new JLabel(app.getStudentName() + " (" + app.getStudentRollNumber() + ")");
        title.setFont(ThemeManager.font(Font.BOLD, 15.5f));
        title.setForeground(ThemeManager.primaryText());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sub = new JLabel(app.getCompanyName() + "  ·  " + app.getJobRole());
        sub.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        sub.setForeground(ThemeManager.CYAN_DARK);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        ApplicationStage cur = app.getCurrentStage() != null ? app.getCurrentStage() : ApplicationStage.APPLICATION_SUBMITTED;
        Color badgeFg = cur.isOfferOrSelected() ? ThemeManager.EMERALD_PRIMARY
                : (cur == ApplicationStage.REJECTED ? ThemeManager.ROSE_PRIMARY : ThemeManager.BLUE_ELECTRIC);
        Color badgeBg = cur.isOfferOrSelected() ? ThemeManager.EMERALD_SOFT
                : (cur == ApplicationStage.REJECTED ? ThemeManager.ROSE_SOFT : new Color(219, 234, 254));
        BadgeLabel stageBadge = new BadgeLabel(
                cur.getLabel() + (app.getFinalOfferLpa() != null ? String.format(Locale.US, " · %.1f LPA", app.getFinalOfferLpa()) : ""),
                badgeBg, badgeFg, badgeFg, 8
        );
        stageBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(title);
        card.add(Box.createVerticalStrut(3));
        card.add(sub);
        card.add(Box.createVerticalStrut(6));
        card.add(stageBadge);

        if (app.getRejectionReason() != null && !app.getRejectionReason().isBlank()) {
            card.add(Box.createVerticalStrut(8));
            RoundedCard rejBox = new RoundedCard(8, ThemeManager.ROSE_SOFT, ThemeManager.ROSE_PRIMARY);
            rejBox.setBorder(new EmptyBorder(8, 10, 8, 10));
            rejBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            rejBox.setLayout(new BorderLayout());
            JLabel rLbl = new JLabel("<html><body style='width:320px;'><b>Feedback / Rejection Reason:</b> "
                    + esc(app.getRejectionReason()) + "</body></html>");
            rLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            rLbl.setForeground(new Color(0x9F, 0x12, 0x39));
            rejBox.add(rLbl, BorderLayout.CENTER);
            card.add(rejBox);
        }

        // 10-Stage Visual Stepper
        card.add(Box.createVerticalStrut(12));
        JLabel pipeTitle = new JLabel("RECRUITMENT PIPELINE PROGRESS");
        pipeTitle.setFont(ThemeManager.font(Font.BOLD, 11));
        pipeTitle.setForeground(ThemeManager.mutedText());
        pipeTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(pipeTitle);
        card.add(Box.createVerticalStrut(6));

        int activeIdx = PIPELINE_ORDER.indexOf(cur);
        JPanel stepper = new JPanel();
        stepper.setOpaque(false);
        stepper.setLayout(new BoxLayout(stepper, BoxLayout.Y_AXIS));
        stepper.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (int i = 0; i < PIPELINE_ORDER.size(); i++) {
            ApplicationStage st = PIPELINE_ORDER.get(i);
            boolean reached = activeIdx >= i;
            boolean isCurrent = st == cur;
            JLabel step = new JLabel((reached ? "●  " : "○  ") + (i + 1) + ". " + st.getLabel() + (isCurrent ? "   [CURRENT]" : ""));
            step.setFont(ThemeManager.font(isCurrent ? Font.BOLD : Font.PLAIN, 11.5f));
            step.setForeground(isCurrent ? ThemeManager.BLUE_ELECTRIC : (reached ? ThemeManager.EMERALD_PRIMARY : ThemeManager.mutedText()));
            step.setAlignmentX(Component.LEFT_ALIGNMENT);
            stepper.add(step);
            stepper.add(Box.createVerticalStrut(2));
        }
        if (cur == ApplicationStage.REJECTED || cur == ApplicationStage.WITHDRAWN) {
            JLabel term = new JLabel("✖  Terminal State: " + cur.getLabel());
            term.setFont(ThemeManager.font(Font.BOLD, 11.5f));
            term.setForeground(ThemeManager.ROSE_PRIMARY);
            term.setAlignmentX(Component.LEFT_ALIGNMENT);
            stepper.add(term);
        }
        card.add(stepper);

        // Chronological Stage Audit History
        card.add(Box.createVerticalStrut(12));
        JLabel histTitle = new JLabel("STAGE HISTORY AUDIT LOG (" + app.getStageHistory().size() + ")");
        histTitle.setFont(ThemeManager.font(Font.BOLD, 11));
        histTitle.setForeground(ThemeManager.mutedText());
        histTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(histTitle);
        card.add(Box.createVerticalStrut(6));

        if (app.getStageHistory().isEmpty()) {
            JLabel noHist = new JLabel("No stage history entries recorded.");
            noHist.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            noHist.setForeground(ThemeManager.mutedText());
            noHist.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(noHist);
        } else {
            for (StageHistoryEntry entry : app.getStageHistory()) {
                RoundedCard hCard = new RoundedCard(8, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
                hCard.setLayout(new BoxLayout(hCard, BoxLayout.Y_AXIS));
                hCard.setBorder(new EmptyBorder(8, 10, 8, 10));
                hCard.setAlignmentX(Component.LEFT_ALIGNMENT);
                String date = entry.getTimestamp() != null && entry.getTimestamp().length() >= 10
                        ? entry.getTimestamp().substring(0, 10) : "";
                JLabel hTop = new JLabel((entry.getStage() != null ? entry.getStage().getLabel() : "Update") + "  ·  " + date);
                hTop.setFont(ThemeManager.font(Font.BOLD, 11.5f));
                hTop.setForeground(ThemeManager.primaryText());
                JLabel hBy = new JLabel("By: " + nonNull(entry.getUpdatedBy()));
                hBy.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
                hBy.setForeground(ThemeManager.mutedText());
                hCard.add(hTop);
                hCard.add(hBy);
                if (entry.getNotes() != null && !entry.getNotes().isBlank()) {
                    JLabel hNote = new JLabel("<html><body style='width:310px;'>" + esc(entry.getNotes()) + "</body></html>");
                    hNote.setFont(ThemeManager.font(Font.PLAIN, 11));
                    hNote.setForeground(ThemeManager.secondaryText());
                    hCard.add(Box.createVerticalStrut(2));
                    hCard.add(hNote);
                }
                card.add(hCard);
                card.add(Box.createVerticalStrut(5));
            }
        }

        JScrollPane sp = new JScrollPane(card);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        inspectorContainer.add(sp, BorderLayout.CENTER);
        inspectorContainer.revalidate();
        inspectorContainer.repaint();
    }

    private void openEligibleApplyDialog() {
        User actor = AppSession.getInstance().getCurrentUser();
        if (allStudents.isEmpty() || allDrives.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Ensure students and active placement drives exist in MySQL.",
                    "No Data", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Student targetStudent = allStudents.get(0);
        if (actor != null && actor.getRole() != UserRole.STUDENT && allStudents.size() > 1) {
            Object[] stuOptions = allStudents.stream()
                    .map(s -> s.getRollNumber() + " - " + s.getFullName() + " (" + s.getBranch() + ")")
                    .toArray();
            Object picked = JOptionPane.showInputDialog(this, "Select Student to Apply:",
                    "Select Candidate", JOptionPane.QUESTION_MESSAGE, null, stuOptions, stuOptions[0]);
            if (picked == null) return;
            String pickedRoll = picked.toString().split(" - ")[0].trim();
            for (Student s : allStudents) {
                if (s.getRollNumber().equalsIgnoreCase(pickedRoll)) {
                    targetStudent = s;
                    break;
                }
            }
        }

        // Collect strictly eligible, open drives not yet actively applied to
        List<PlacementDrive> eligibleDrives = new ArrayList<>();
        for (PlacementDrive d : allDrives) {
            if (!PlacementEngine.isDriveOpenForApplications(d)) continue;
            EligibilityResult elig = PlacementEngine.checkEligibility(targetStudent, d, allSkills);
            if (!elig.eligible) continue;

            boolean alreadyActive = false;
            for (Application a : allApplications) {
                if (targetStudent.getId().equals(a.getStudentId())
                        && d.getId().equals(a.getDriveId())
                        && a.getCurrentStage() != ApplicationStage.WITHDRAWN) {
                    alreadyActive = true;
                    break;
                }
            }
            if (!alreadyActive) {
                eligibleDrives.add(d);
            }
        }

        if (eligibleDrives.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No open eligible placement drives remaining for " + targetStudent.getFullName()
                            + ".\nUse Eligibility Checker or Skill Gap Analysis to inspect unmet criteria.",
                    "No Eligible Drives Available", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] labels = eligibleDrives.stream()
                .map(d -> String.format(Locale.US, "%s — %s (%.1f LPA · Min CGPA %.2f)",
                        d.getCompanyName(), d.getJobRole(), d.getSalaryPackageLpa(), d.getMinCgpa()))
                .toArray(String[]::new);

        Object selected = JOptionPane.showInputDialog(
                this,
                "Select an Eligible Placement Drive for " + targetStudent.getFullName() + ":",
                "Apply to Eligible Drive",
                JOptionPane.QUESTION_MESSAGE,
                null,
                labels,
                labels[0]
        );
        if (selected == null) return;
        int idx = Arrays.asList(labels).indexOf(selected.toString());
        if (idx < 0) return;

        PlacementDrive chosenDrive = eligibleDrives.get(idx);
        controller.submitApplicationAsync(actor, chosenDrive.getId(), targetStudent.getId(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Application Submitted", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Application Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openAdvanceDialog() {
        Application app = getSelectedApplication();
        if (app == null) return;

        JPanel panel = new JPanel(new GridLayout(0, 1, 6, 6));
        JComboBox<ApplicationStage> nextCombo = new JComboBox<>(PIPELINE_ORDER.toArray(new ApplicationStage[0]));
        int curIdx = PIPELINE_ORDER.indexOf(app.getCurrentStage());
        if (curIdx >= 0 && curIdx < PIPELINE_ORDER.size() - 1) {
            nextCombo.setSelectedItem(PIPELINE_ORDER.get(curIdx + 1));
        } else {
            nextCombo.setSelectedItem(ApplicationStage.SELECTED);
        }

        double defaultLpa = app.getFinalOfferLpa() != null ? app.getFinalOfferLpa() : 7.5;
        for (PlacementDrive d : allDrives) {
            if (d.getId().equals(app.getDriveId()) && d.getSalaryPackageLpa() > 0 && app.getFinalOfferLpa() == null) {
                defaultLpa = d.getSalaryPackageLpa();
                break;
            }
        }
        JTextField offerField = new JTextField(String.format(Locale.US, "%.2f", defaultLpa));
        JTextArea notesArea = new JTextArea(3, 28);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        panel.add(new JLabel("Candidate: " + app.getStudentName() + " (" + app.getCompanyName() + ")"));
        panel.add(new JLabel("Target Stage *"));
        panel.add(nextCombo);
        panel.add(new JLabel("Offer Package (LPA — applied if Selected / Offered / Joined)"));
        panel.add(offerField);
        panel.add(new JLabel("Evaluation & Panel Notes"));
        panel.add(new JScrollPane(notesArea));

        int opt = JOptionPane.showConfirmDialog(this, panel, "Advance Recruitment Stage",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opt != JOptionPane.OK_OPTION) return;

        ApplicationStage target = (ApplicationStage) nextCombo.getSelectedItem();
        Double lpa = null;
        if (target != null && target.isOfferOrSelected()) {
            try {
                lpa = Double.parseDouble(offerField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Enter a valid numeric Offer Package (LPA).",
                        "Invalid Package", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        String notes = notesArea.getText().trim();

        controller.advanceStageAsync(
                AppSession.getInstance().getCurrentUser(),
                app.getId(),
                target,
                lpa,
                null,
                notes,
                res -> {
                    if (res.success) {
                        JOptionPane.showMessageDialog(this, res.message, "Stage Updated", JOptionPane.INFORMATION_MESSAGE);
                        loadData();
                    } else {
                        JOptionPane.showMessageDialog(this, res.message, "Update Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
        );
    }

    private void openRejectDialog() {
        Application app = getSelectedApplication();
        if (app == null) return;
        String reason = JOptionPane.showInputDialog(
                this,
                "Record rejection feedback for " + app.getStudentName() + " (" + app.getCompanyName() + "):",
                "Reject Application",
                JOptionPane.WARNING_MESSAGE
        );
        if (reason == null) return;
        if (reason.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Rejection reason is mandatory.",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        controller.advanceStageAsync(
                AppSession.getInstance().getCurrentUser(),
                app.getId(),
                ApplicationStage.REJECTED,
                null,
                reason.trim(),
                "Rejection reason: " + reason.trim(),
                res -> {
                    if (res.success) {
                        JOptionPane.showMessageDialog(this, res.message, "Application Rejected", JOptionPane.INFORMATION_MESSAGE);
                        loadData();
                    } else {
                        JOptionPane.showMessageDialog(this, res.message, "Rejection Failed", JOptionPane.ERROR_MESSAGE);
                    }
                }
        );
    }

    private void confirmWithdraw() {
        Application app = getSelectedApplication();
        if (app == null) return;
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Withdraw your application for " + app.getCompanyName() + " (" + app.getJobRole() + ")?\n"
                        + "(You can re-submit later if the drive remains open and you remain eligible.)",
                "Withdraw Application",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        controller.withdrawApplicationAsync(AppSession.getInstance().getCurrentUser(), app.getId(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Application Withdrawn", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Withdrawal Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void confirmReapply() {
        Application app = getSelectedApplication();
        if (app == null) return;
        controller.submitApplicationAsync(
                AppSession.getInstance().getCurrentUser(),
                app.getDriveId(),
                app.getStudentId(),
                res -> {
                    if (res.success) {
                        JOptionPane.showMessageDialog(this, res.message, "Application Re-Submitted", JOptionPane.INFORMATION_MESSAGE);
                        loadData();
                    } else {
                        JOptionPane.showMessageDialog(this, res.message, "Cannot Re-Apply", JOptionPane.ERROR_MESSAGE);
                    }
                }
        );
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("gcet_placement_applications.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            pw.println("Application ID,Student Roll No,Student Name,Branch,CGPA,Company Name,Job Role,Current Stage,Applied On,Offer LPA");
            for (Application a : filteredApps) {
                pw.printf("%s,%s,%s,%s,%.2f,%s,%s,%s,%s,%s%n",
                        csv(a.getId()),
                        csv(a.getStudentRollNumber()),
                        csv(a.getStudentName()),
                        csv(a.getStudentBranch()),
                        a.getStudentCgpa(),
                        csv(a.getCompanyName()),
                        csv(a.getJobRole()),
                        csv(a.getCurrentStage() != null ? a.getCurrentStage().getLabel() : ""),
                        csv(a.getAppliedAt()),
                        a.getFinalOfferLpa() != null ? a.getFinalOfferLpa().toString() : ""
                );
            }
            JOptionPane.showMessageDialog(this, "Exported " + filteredApps.size() + " application(s) to:\n" + file.getAbsolutePath(),
                    "CSV Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "CSV Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csv(String v) {
        return "\"" + (v == null ? "" : v).replace("\"", "\"\"") + "\"";
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
