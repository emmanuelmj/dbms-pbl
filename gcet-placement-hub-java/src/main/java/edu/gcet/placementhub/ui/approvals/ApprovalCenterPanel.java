package edu.gcet.placementhub.ui.approvals;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.GovernanceController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.GovernanceModels.*;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.UiComponents.*;
import static edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * Phase 5 Approval Center (Java Swing + FlatLaf + JDBC).
 * Mirrors ApprovalsPage.tsx with strict RBAC enforcement:
 *  - Student Registrations: Faculty / TPO only (Department-scoped), supports batch & single Approve/Reject
 *  - Faculty & Company Registrations: College Admins only
 *  - Admin Privilege Requests (`admin_requests`): Principal only
 *  - Mandatory rejection reason enforced on every rejection
 *  - Persistent Approval History (`approval_records`)
 */
public class ApprovalCenterPanel extends JPanel {

    private enum Tab {
        STUDENTS, FACULTY, COMPANIES, ADMIN_REQUESTS, HISTORY
    }

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final GovernanceController controller = new GovernanceController();

    private Tab activeTab = Tab.STUDENTS;
    private ApprovalBundle bundle;

    private final JLabel kpiStudentsVal = new JLabel("0");
    private final JLabel kpiFacultyVal = new JLabel("0");
    private final JLabel kpiCompaniesVal = new JLabel("0");
    private final JLabel kpiAdminReqVal = new JLabel("0");
    private final JLabel kpiHistoryVal = new JLabel("0");

    private final JTextField searchField;
    private final JComboBox<String> typeFilterBox;
    private final JComboBox<String> deptFilterBox;
    private final JComboBox<String> statusFilterBox;
    private final AlertBanner alertBanner = new AlertBanner();
    private final JLabel feedbackToast = new JLabel("");

    private final JPanel tabBarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final JPanel contentSlot = new JPanel(new BorderLayout());
    private final JTextArea inspectorArea = new JTextArea(4, 40);

    private List<Student> filteredStudents = List.of();
    private List<Faculty> filteredFaculty = List.of();
    private List<Company> filteredCompanies = List.of();
    private List<AdminRequest> filteredAdminRequests = List.of();
    private List<ApprovalRecord> filteredHistory = List.of();

    public ApprovalCenterPanel() {
        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(18, 22, 18, 22));

        // Top Header + KPIs + Filters
        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));

        // Header Card
        RoundedCard headerCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        headerCard.setLayout(new BorderLayout(12, 0));
        headerCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        headerCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel hdrLeft = new JPanel();
        hdrLeft.setOpaque(false);
        hdrLeft.setLayout(new BoxLayout(hdrLeft, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("⛨  Institutional Approval Center (RBAC Governance)");
        title.setFont(ThemeManager.font(Font.BOLD, 18));
        title.setForeground(ThemeManager.primaryText());
        JLabel sub = new JLabel("Dual-tier clearance: Faculty verify student registrations; Administrators clear Faculty & Companies; Principal decides Admin privileges.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 12));
        sub.setForeground(ThemeManager.mutedText());
        hdrLeft.add(title);
        hdrLeft.add(Box.createVerticalStrut(3));
        hdrLeft.add(sub);

        JPanel hdrRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        hdrRight.setOpaque(false);
        feedbackToast.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        feedbackToast.setForeground(ThemeManager.EMERALD_PRIMARY);
        ActionButton refreshBtn = new ActionButton("↻  Refresh MySQL", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        refreshBtn.addActionListener(e -> loadData());
        hdrRight.add(feedbackToast);
        hdrRight.add(refreshBtn);

        headerCard.add(hdrLeft, BorderLayout.CENTER);
        headerCard.add(hdrRight, BorderLayout.EAST);
        topStack.add(headerCard);
        topStack.add(Box.createVerticalStrut(12));

        // 5 KPI Strip
        JPanel kpiGrid = new JPanel(new GridLayout(1, 5, 10, 10));
        kpiGrid.setOpaque(false);
        kpiGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        kpiGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        kpiGrid.add(kpiCard("PENDING STUDENTS", kpiStudentsVal, ThemeManager.AMBER_PRIMARY));
        kpiGrid.add(kpiCard("PENDING FACULTY", kpiFacultyVal, ThemeManager.EMERALD_PRIMARY));
        kpiGrid.add(kpiCard("PENDING COMPANIES", kpiCompaniesVal, ThemeManager.PURPLE_PRIMARY));
        kpiGrid.add(kpiCard("ADMIN PETITIONS", kpiAdminReqVal, ThemeManager.BLUE_ELECTRIC));
        kpiGrid.add(kpiCard("AUDIT HISTORY", kpiHistoryVal, ThemeManager.CYAN_DARK));
        topStack.add(kpiGrid);
        topStack.add(Box.createVerticalStrut(12));

        // Search & Filter Toolbar
        RoundedCard filterCard = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField = createStyledTextField("Search name, roll no, email, company...");
        searchField.setPreferredSize(new Dimension(240, 34));
        searchField.addActionListener(e -> applyFiltersAndRender());

        typeFilterBox = new JComboBox<>(new String[]{"ALL TYPES", "STUDENT", "FACULTY", "COMPANY", "ADMIN_PRIVILEGE"});
        typeFilterBox.setPreferredSize(new Dimension(150, 34));
        typeFilterBox.addActionListener(e -> {
            String sel = (String) typeFilterBox.getSelectedItem();
            if ("STUDENT".equals(sel)) activeTab = Tab.STUDENTS;
            else if ("FACULTY".equals(sel)) activeTab = Tab.FACULTY;
            else if ("COMPANY".equals(sel)) activeTab = Tab.COMPANIES;
            else if ("ADMIN_PRIVILEGE".equals(sel)) activeTab = Tab.ADMIN_REQUESTS;
            applyFiltersAndRender();
        });

        List<String> depts = new ArrayList<>();
        depts.add("ALL DEPTS");
        depts.addAll(DeptNormalizer.GCET_BRANCHES);
        deptFilterBox = new JComboBox<>(depts.toArray(new String[0]));
        deptFilterBox.setPreferredSize(new Dimension(165, 34));
        deptFilterBox.addActionListener(e -> applyFiltersAndRender());

        statusFilterBox = new JComboBox<>(new String[]{"PENDING", "APPROVED", "REJECTED", "ALL"});
        statusFilterBox.setPreferredSize(new Dimension(130, 34));
        statusFilterBox.addActionListener(e -> applyFiltersAndRender());

        ActionButton applyBtn = new ActionButton("Filter", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.inputBorder(), 9);
        applyBtn.addActionListener(e -> applyFiltersAndRender());

        ActionButton resetBtn = new ActionButton("Reset", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.secondaryText(), ThemeManager.cardBorder(), 9);
        resetBtn.addActionListener(e -> {
            searchField.setText("");
            typeFilterBox.setSelectedIndex(0);
            deptFilterBox.setSelectedIndex(0);
            statusFilterBox.setSelectedItem("PENDING");
            applyFiltersAndRender();
        });

        filterCard.add(labelInline("Search:"));
        filterCard.add(searchField);
        filterCard.add(labelInline("Type:"));
        filterCard.add(typeFilterBox);
        filterCard.add(labelInline("Dept:"));
        filterCard.add(deptFilterBox);
        filterCard.add(labelInline("Status:"));
        filterCard.add(statusFilterBox);
        filterCard.add(applyBtn);
        filterCard.add(resetBtn);

        topStack.add(filterCard);
        topStack.add(Box.createVerticalStrut(8));

        alertBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(alertBanner);

        // Tabs
        tabBarPanel.setOpaque(false);
        tabBarPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(tabBarPanel);

        add(topStack, BorderLayout.NORTH);

        // Main Table + Bottom Inspector Split
        contentSlot.setOpaque(false);

        RoundedCard inspectorCard = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        inspectorCard.setLayout(new BorderLayout(0, 6));
        inspectorCard.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel inspHdr = new JLabel("APPLICANT & DECISION INSPECTOR (Select any row above)");
        inspHdr.setFont(ThemeManager.font(Font.BOLD, 10.5f));
        inspHdr.setForeground(ThemeManager.CYAN_DARK);
        inspectorArea.setEditable(false);
        inspectorArea.setLineWrap(true);
        inspectorArea.setWrapStyleWord(true);
        inspectorArea.setFont(ThemeManager.font(Font.PLAIN, 12));
        inspectorArea.setBackground(ThemeManager.cardSubtleBg());
        inspectorArea.setForeground(ThemeManager.primaryText());
        inspectorArea.setBorder(new EmptyBorder(8, 10, 8, 10));
        inspectorArea.setText("Select a registration or petition row to inspect full applicant credentials, academic metrics, HR contact info, or rejection notes.");
        JScrollPane inspScroll = new JScrollPane(inspectorArea);
        inspScroll.setBorder(new MatteBorder(1, 1, 1, 1, ThemeManager.cardBorder()));
        inspScroll.setPreferredSize(new Dimension(400, 96));
        inspectorCard.add(inspHdr, BorderLayout.NORTH);
        inspectorCard.add(inspScroll, BorderLayout.CENTER);

        add(contentSlot, BorderLayout.CENTER);
        add(inspectorCard, BorderLayout.SOUTH);
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        if (actor == null) return;
        alertBanner.clear();
        feedbackToast.setText("Querying MySQL...");
        feedbackToast.setForeground(ThemeManager.mutedText());

        // Pick sensible default tab for user role
        if (bundle == null) {
            if (actor.getRole() == UserRole.FACULTY || actor.getRole() == UserRole.PLACEMENT_OFFICER) {
                activeTab = Tab.STUDENTS;
            } else if (actor.getRole().isAdminRole()) {
                activeTab = Tab.FACULTY;
            }
        }

        controller.loadApprovalBundleAsync(actor, b -> {
            this.bundle = b;
            if (b.error() != null && !b.error().isBlank()) {
                alertBanner.showError(b.error());
                feedbackToast.setText("");
            } else {
                feedbackToast.setText("Live MySQL Synced");
                feedbackToast.setForeground(ThemeManager.EMERALD_PRIMARY);
            }
            updateKpis();
            applyFiltersAndRender();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        inspectorArea.setBackground(ThemeManager.cardSubtleBg());
        inspectorArea.setForeground(ThemeManager.primaryText());
        applyFiltersAndRender();
    }

    private void updateKpis() {
        if (bundle == null) return;
        long pendingStu = bundle.students().stream().filter(s -> isPendingStatus(s.getApprovalStatus())).count();
        long pendingFac = bundle.faculty().stream().filter(f -> isPendingStatus(f.getStatus())).count();
        long pendingComp = bundle.companies().stream().filter(c -> isPendingStatus(nonBlank(c.getApprovalStatus(), c.getStatus()))).count();
        long pendingAdm = bundle.adminRequests().stream().filter(r -> isPendingStatus(r.getStatus())).count();
        kpiStudentsVal.setText(String.valueOf(pendingStu));
        kpiFacultyVal.setText(String.valueOf(pendingFac));
        kpiCompaniesVal.setText(String.valueOf(pendingComp));
        kpiAdminReqVal.setText(String.valueOf(pendingAdm));
        kpiHistoryVal.setText(String.valueOf(bundle.approvalHistory().size()));
    }

    private void applyFiltersAndRender() {
        User actor = AppSession.getInstance().getCurrentUser();
        rebuildTabBar(actor);
        contentSlot.removeAll();

        if (bundle == null) {
            contentSlot.add(emptyPanel("Loading approval queues from MySQL...", "Please wait."), BorderLayout.CENTER);
            contentSlot.revalidate();
            contentSlot.repaint();
            return;
        }

        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String deptFilter = (String) deptFilterBox.getSelectedItem();
        String statusFilter = (String) statusFilterBox.getSelectedItem();

        filteredStudents = bundle.students().stream()
                .filter(s -> matchesStatus(s.getApprovalStatus(), statusFilter))
                .filter(s -> matchesDept(s.getBranch(), deptFilter))
                .filter(s -> q.isEmpty() || containsAny(q, s.getFullName(), s.getRollNumber(), s.getEmail(), s.getBranch()))
                .toList();

        filteredFaculty = bundle.faculty().stream()
                .filter(f -> matchesStatus(f.getStatus(), statusFilter))
                .filter(f -> matchesDept(f.getDepartment(), deptFilter))
                .filter(f -> q.isEmpty() || containsAny(q, f.getFullName(), f.getEmployeeId(), f.getEmail(), f.getDepartment(), f.getDesignation()))
                .toList();

        filteredCompanies = bundle.companies().stream()
                .filter(c -> matchesStatus(nonBlank(c.getApprovalStatus(), c.getStatus()), statusFilter))
                .filter(c -> q.isEmpty() || containsAny(q, c.getName(), c.getIndustry(), c.getHrName(), c.getHrEmail(), c.getLocation()))
                .toList();

        filteredAdminRequests = bundle.adminRequests().stream()
                .filter(r -> matchesStatus(r.getStatus(), statusFilter))
                .filter(r -> matchesDept(r.getDepartment(), deptFilter))
                .filter(r -> q.isEmpty() || containsAny(q, r.getApplicantName(), r.getApplicantEmail(), r.getRequestedRole(), r.getReason()))
                .toList();

        filteredHistory = bundle.approvalHistory().stream()
                .filter(h -> matchesStatus(h.getDecision(), statusFilter))
                .filter(h -> matchesDept(h.getDepartment(), deptFilter))
                .filter(h -> q.isEmpty() || containsAny(q, h.getTargetName(), h.getStudentRollNumber(), h.getApproverName(), h.getRequestType(), h.getComments()))
                .toList();

        JPanel body = switch (activeTab) {
            case STUDENTS -> buildStudentsTab(actor);
            case FACULTY -> buildFacultyTab(actor);
            case COMPANIES -> buildCompaniesTab(actor);
            case ADMIN_REQUESTS -> buildAdminRequestsTab(actor);
            case HISTORY -> buildHistoryTab();
        };

        contentSlot.add(body, BorderLayout.CENTER);
        contentSlot.revalidate();
        contentSlot.repaint();
    }

    private void rebuildTabBar(User actor) {
        tabBarPanel.removeAll();
        boolean isAdmin = actor != null && actor.getRole().isAdminRole();

        int stuPending = (int) (bundle == null ? 0 : bundle.students().stream().filter(s -> isPendingStatus(s.getApprovalStatus())).count());
        int facPending = (int) (bundle == null ? 0 : bundle.faculty().stream().filter(f -> isPendingStatus(f.getStatus())).count());
        int compPending = (int) (bundle == null ? 0 : bundle.companies().stream().filter(c -> isPendingStatus(nonBlank(c.getApprovalStatus(), c.getStatus()))).count());
        int admPending = (int) (bundle == null ? 0 : bundle.adminRequests().stream().filter(r -> isPendingStatus(r.getStatus())).count());
        int histCount = bundle == null ? 0 : bundle.approvalHistory().size();

        tabBarPanel.add(tabButton("Student Registrations (" + stuPending + " Pending)", Tab.STUDENTS));
        if (isAdmin) {
            tabBarPanel.add(tabButton("Faculty Registrations (" + facPending + " Pending)", Tab.FACULTY));
            tabBarPanel.add(tabButton("Company Registrations (" + compPending + " Pending)", Tab.COMPANIES));
            tabBarPanel.add(tabButton("Admin Privilege Petitions (" + admPending + " Pending)", Tab.ADMIN_REQUESTS));
        } else if (activeTab == Tab.FACULTY || activeTab == Tab.COMPANIES || activeTab == Tab.ADMIN_REQUESTS) {
            activeTab = Tab.STUDENTS;
        }
        tabBarPanel.add(tabButton("Approval History (" + histCount + ")", Tab.HISTORY));
        tabBarPanel.revalidate();
        tabBarPanel.repaint();
    }

    private ActionButton tabButton(String text, Tab tab) {
        boolean sel = activeTab == tab;
        ActionButton btn = new ActionButton(
                text,
                sel ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardBg(),
                sel ? ThemeManager.BLUE_HOVER : ThemeManager.cardSubtleBg(),
                sel ? Color.WHITE : ThemeManager.primaryText(),
                sel ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardBorder(),
                10
        );
        btn.setMargin(new Insets(7, 12, 7, 12));
        btn.addActionListener(e -> {
            activeTab = tab;
            applyFiltersAndRender();
        });
        return btn;
    }

    // -------------------------------------------------------------------------
    // TAB 1: STUDENT REGISTRATIONS (FACULTY / TPO ONLY)
    // -------------------------------------------------------------------------

    private JPanel buildStudentsTab(User actor) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        boolean canClearStudents = actor != null
                && (actor.getRole() == UserRole.FACULTY || actor.getRole() == UserRole.PLACEMENT_OFFICER);

        JPanel bannerBar = new JPanel(new BorderLayout(10, 0));
        bannerBar.setOpaque(false);

        String ruleText = canClearStudents
                ? (actor.getRole() == UserRole.FACULTY
                ? "Faculty Department Clearance: Authorized for branches ["
                + String.join(", ", actor.getAuthorizedDepartments().isEmpty()
                ? DeptNormalizer.getFacultyAuthorizedDepts(actor.getUsername())
                : actor.getAuthorizedDepartments()) + "]. Select rows to batch-approve or reject with a mandatory reason."
                : "Placement Officer Clearance: You can approve or reject pending student registrations across departments.")
                : "RBAC Governance Notice: College Administrators & Executives view student registrations in READ-ONLY mode. Only Department Faculty / TPO may clear students.";

        JLabel ruleLbl = new JLabel("<html><b>Governance Policy:</b> " + esc(ruleText) + "</html>");
        ruleLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        ruleLbl.setForeground(canClearStudents ? ThemeManager.EMERALD_PRIMARY : ThemeManager.AMBER_PRIMARY);
        bannerBar.add(ruleLbl, BorderLayout.CENTER);

        String[] cols = {"Select", "Roll No", "Student Name", "Branch", "Grad Yr", "CGPA", "Backlogs", "Status", "Approved By"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                if (column != 0 || !canClearStudents) return false;
                Student s = filteredStudents.get(row);
                return isPendingStatus(s.getApprovalStatus());
            }
        };

        for (Student s : filteredStudents) {
            model.addRow(new Object[]{
                    Boolean.FALSE,
                    nonBlank(s.getRollNumber(), "-"),
                    nonBlank(s.getFullName(), "-"),
                    nonBlank(s.getBranch(), "-"),
                    s.getGraduationYear(),
                    String.format(Locale.US, "%.2f", s.getCgpa()),
                    s.getBacklogs(),
                    normalizeStatusDisplay(s.getApprovalStatus()),
                    nonBlank(s.getApprovedByFacultyName(), "-")
            });
        }

        JTable table = styledTable(model);
        table.getColumnModel().getColumn(0).setMaxWidth(56);
        table.getColumnModel().getColumn(7).setCellRenderer(statusCellRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < filteredStudents.size()) {
                Student s = filteredStudents.get(row);
                inspectorArea.setText(
                        "STUDENT REGISTRATION DETAILS\n"
                                + "Name: " + nonBlank(s.getFullName(), "-") + "   |   Roll No: " + nonBlank(s.getRollNumber(), "-")
                                + "   |   Email: " + nonBlank(s.getEmail(), "-") + "   |   Phone: " + nonBlank(s.getPhone(), "-") + "\n"
                                + "Branch: " + nonBlank(s.getBranch(), "-") + "   |   Year: " + s.getAcademicYear()
                                + "   |   Grad Year: " + s.getGraduationYear() + "   |   CGPA: " + String.format(Locale.US, "%.2f", s.getCgpa())
                                + "   |   10th: " + String.format(Locale.US, "%.1f%%", s.getTenthPercentage())
                                + "   |   Inter: " + String.format(Locale.US, "%.1f%%", s.getInterPercentage())
                                + "   |   Backlogs: " + s.getBacklogs() + "\n"
                                + "Approval Status: " + normalizeStatusDisplay(s.getApprovalStatus())
                                + (s.getRejectionReason() != null && !s.getRejectionReason().isBlank()
                                ? "   |   Rejection Reason: " + s.getRejectionReason() : "")
                );
            }
        });

        if (canClearStudents) {
            JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actionBtns.setOpaque(false);

            ActionButton approveSelBtn = new ActionButton("✓  Approve Selected", ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57), Color.WHITE, null, 9);
            approveSelBtn.addActionListener(e -> {
                List<String> targetIds = collectCheckedOrSelectedStudentIds(model, table);
                if (targetIds.isEmpty()) {
                    alertBanner.showError("Check at least one PENDING student checkbox or select a pending student row.");
                    return;
                }
                int ok = JOptionPane.showConfirmDialog(
                        this,
                        "Approve " + targetIds.size() + " student registration(s) and activate their MySQL portal logins?",
                        "Confirm Student Approval",
                        JOptionPane.YES_NO_OPTION
                );
                if (ok == JOptionPane.YES_OPTION) {
                    controller.decideStudentsAsync(actor, targetIds, true, "", res -> handleOperationResult(res.success, res.message));
                }
            });

            ActionButton rejectSelBtn = new ActionButton("✕  Reject Selected", ThemeManager.ROSE_PRIMARY, new Color(0xBE, 0x12, 0x3C), Color.WHITE, null, 9);
            rejectSelBtn.addActionListener(e -> {
                List<String> targetIds = collectCheckedOrSelectedStudentIds(model, table);
                if (targetIds.isEmpty()) {
                    alertBanner.showError("Check at least one PENDING student checkbox or select a pending student row.");
                    return;
                }
                String reason = promptMandatoryRejectionReason("Reject Student Registration (" + targetIds.size() + ")");
                if (reason != null) {
                    controller.decideStudentsAsync(actor, targetIds, false, reason, res -> handleOperationResult(res.success, res.message));
                }
            });

            actionBtns.add(approveSelBtn);
            actionBtns.add(rejectSelBtn);
            bannerBar.add(actionBtns, BorderLayout.EAST);
        }

        card.add(bannerBar, BorderLayout.NORTH);
        if (filteredStudents.isEmpty()) {
            card.add(emptyPanel("No student registrations match the active filters.", "Adjust status or department filters above."), BorderLayout.CENTER);
        } else {
            card.add(wrapScroll(table), BorderLayout.CENTER);
        }
        return card;
    }

    private List<String> collectCheckedOrSelectedStudentIds(DefaultTableModel model, JTable table) {
        List<String> ids = new ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            if (Boolean.TRUE.equals(model.getValueAt(r, 0))) {
                ids.add(filteredStudents.get(r).getId());
            }
        }
        if (ids.isEmpty() && table.getSelectedRow() >= 0) {
            Student s = filteredStudents.get(table.getSelectedRow());
            if (isPendingStatus(s.getApprovalStatus())) {
                ids.add(s.getId());
            }
        }
        return ids;
    }

    // -------------------------------------------------------------------------
    // TAB 2: FACULTY REGISTRATIONS (ADMIN ONLY)
    // -------------------------------------------------------------------------

    private JPanel buildFacultyTab(User actor) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        JLabel hdr = new JLabel("Admin Authority Required: Authorize or reject newly registered Faculty Coordinators.");
        hdr.setFont(ThemeManager.font(Font.BOLD, 12));
        hdr.setForeground(ThemeManager.EMERALD_PRIMARY);
        top.add(hdr, BorderLayout.WEST);

        String[] cols = {"Employee ID", "Faculty Name", "Official Email", "Department", "Designation", "Status", "Approved By"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Faculty f : filteredFaculty) {
            model.addRow(new Object[]{
                    nonBlank(f.getEmployeeId(), "-"),
                    nonBlank(f.getFullName(), "-"),
                    nonBlank(f.getEmail(), "-"),
                    nonBlank(f.getDepartment(), "-"),
                    nonBlank(f.getDesignation(), "-"),
                    normalizeStatusDisplay(f.getStatus()),
                    nonBlank(f.getApprovedByName(), "-")
            });
        }
        JTable table = styledTable(model);
        table.getColumnModel().getColumn(5).setCellRenderer(statusCellRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < filteredFaculty.size()) {
                Faculty f = filteredFaculty.get(row);
                inspectorArea.setText(
                        "FACULTY COORDINATOR REGISTRATION\n"
                                + "Name: " + nonBlank(f.getFullName(), "-") + "   |   Employee ID: " + nonBlank(f.getEmployeeId(), "-")
                                + "   |   Username: " + nonBlank(f.getUsername(), "-") + "\n"
                                + "Email: " + nonBlank(f.getEmail(), "-") + "   |   Phone: " + nonBlank(f.getPhone(), "-")
                                + "   |   Dept: " + nonBlank(f.getDepartment(), "-") + "   |   Designation: " + nonBlank(f.getDesignation(), "-") + "\n"
                                + "Authorized Depts: " + f.getAuthorizedDepartments() + "   |   Status: " + normalizeStatusDisplay(f.getStatus())
                                + (f.getRejectionReason() != null && !f.getRejectionReason().isBlank() ? "   |   Reason: " + f.getRejectionReason() : "")
                );
            }
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        ActionButton approveBtn = new ActionButton("✓  Approve Faculty", ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57), Color.WHITE, null, 9);
        approveBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                alertBanner.showError("Select a Faculty registration row first.");
                return;
            }
            Faculty target = filteredFaculty.get(row);
            int ok = JOptionPane.showConfirmDialog(this, "Approve Faculty coordinator \"" + target.getFullName() + "\"?", "Confirm Faculty Approval", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                controller.decideFacultyAsync(actor, target, true, "", res -> handleOperationResult(res.success, res.message));
            }
        });

        ActionButton rejectBtn = new ActionButton("✕  Reject Faculty", ThemeManager.ROSE_PRIMARY, new Color(0xBE, 0x12, 0x3C), Color.WHITE, null, 9);
        rejectBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                alertBanner.showError("Select a Faculty registration row first.");
                return;
            }
            Faculty target = filteredFaculty.get(row);
            String reason = promptMandatoryRejectionReason("Reject Faculty: " + target.getFullName());
            if (reason != null) {
                controller.decideFacultyAsync(actor, target, false, reason, res -> handleOperationResult(res.success, res.message));
            }
        });
        btns.add(approveBtn);
        btns.add(rejectBtn);
        top.add(btns, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        if (filteredFaculty.isEmpty()) {
            card.add(emptyPanel("No faculty registrations match the current filters.", "Switch status filter to ALL to view active faculty coordinators."), BorderLayout.CENTER);
        } else {
            card.add(wrapScroll(table), BorderLayout.CENTER);
        }
        return card;
    }

    // -------------------------------------------------------------------------
    // TAB 3: COMPANY REGISTRATIONS (ADMIN ONLY)
    // -------------------------------------------------------------------------

    private JPanel buildCompaniesTab(User actor) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        JLabel hdr = new JLabel("Corporate Recruiter Clearance: Verify legitimacy before granting campus drive privileges.");
        hdr.setFont(ThemeManager.font(Font.BOLD, 12));
        hdr.setForeground(ThemeManager.PURPLE_PRIMARY);
        top.add(hdr, BorderLayout.WEST);

        String[] cols = {"Company Name", "Industry", "Location", "Tier", "HR Contact", "HR Email", "Approval Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Company c : filteredCompanies) {
            model.addRow(new Object[]{
                    nonBlank(c.getName(), "-"),
                    nonBlank(c.getIndustry(), "-"),
                    nonBlank(c.getLocation(), "-"),
                    nonBlank(c.getTier(), "Standard IT"),
                    nonBlank(c.getHrName(), "-"),
                    nonBlank(c.getHrEmail(), "-"),
                    normalizeStatusDisplay(nonBlank(c.getApprovalStatus(), c.getStatus()))
            });
        }
        JTable table = styledTable(model);
        table.getColumnModel().getColumn(6).setCellRenderer(statusCellRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < filteredCompanies.size()) {
                Company c = filteredCompanies.get(row);
                inspectorArea.setText(
                        "CORPORATE PARTNER APPLICATION\n"
                                + "Company: " + nonBlank(c.getName(), "-") + "   |   Industry: " + nonBlank(c.getIndustry(), "-")
                                + "   |   Tier: " + nonBlank(c.getTier(), "-") + "   |   Website: " + nonBlank(c.getWebsite(), "-") + "\n"
                                + "HR Contact: " + nonBlank(c.getHrName(), "-") + "   |   Email: " + nonBlank(c.getHrEmail(), "-")
                                + "   |   Phone: " + nonBlank(c.getHrPhone(), "-") + "   |   Location: " + nonBlank(c.getLocation(), "-") + "\n"
                                + "Status: " + normalizeStatusDisplay(nonBlank(c.getApprovalStatus(), c.getStatus()))
                                + (c.getRejectionReason() != null && !c.getRejectionReason().isBlank() ? "   |   Reason: " + c.getRejectionReason() : "")
                );
            }
        });

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        ActionButton approveBtn = new ActionButton("✓  Approve Partner", ThemeManager.PURPLE_PRIMARY, new Color(0x7E, 0x22, 0xCE), Color.WHITE, null, 9);
        approveBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                alertBanner.showError("Select a Corporate Partner row first.");
                return;
            }
            Company target = filteredCompanies.get(row);
            int ok = JOptionPane.showConfirmDialog(this, "Approve corporate partner \"" + target.getName() + "\" and activate recruiter access?", "Confirm Company Approval", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                controller.decideCompanyAsync(actor, target, true, "", res -> handleOperationResult(res.success, res.message));
            }
        });

        ActionButton rejectBtn = new ActionButton("✕  Reject Partner", ThemeManager.ROSE_PRIMARY, new Color(0xBE, 0x12, 0x3C), Color.WHITE, null, 9);
        rejectBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                alertBanner.showError("Select a Corporate Partner row first.");
                return;
            }
            Company target = filteredCompanies.get(row);
            String reason = promptMandatoryRejectionReason("Reject Company: " + target.getName());
            if (reason != null) {
                controller.decideCompanyAsync(actor, target, false, reason, res -> handleOperationResult(res.success, res.message));
            }
        });
        btns.add(approveBtn);
        btns.add(rejectBtn);
        top.add(btns, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        if (filteredCompanies.isEmpty()) {
            card.add(emptyPanel("No company registrations match the current filters.", "Switch status filter to ALL to view approved corporate partners."), BorderLayout.CENTER);
        } else {
            card.add(wrapScroll(table), BorderLayout.CENTER);
        }
        return card;
    }

    // -------------------------------------------------------------------------
    // TAB 4: ADMIN PRIVILEGE REQUESTS (PRINCIPAL ONLY)
    // -------------------------------------------------------------------------

    private JPanel buildAdminRequestsTab(User actor) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        boolean isPrincipal = actor != null && actor.getRole() == UserRole.PRINCIPAL;

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        JLabel hdr = new JLabel(isPrincipal
                ? "Principal Exclusive Authority: You hold sole authorization to grant or decline Administrator privilege petitions."
                : "Audit View Only: Only the College Principal may grant or decline Administrator privilege petitions.");
        hdr.setFont(ThemeManager.font(Font.BOLD, 12));
        hdr.setForeground(isPrincipal ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY);
        top.add(hdr, BorderLayout.WEST);

        String[] cols = {"Applicant", "Email", "Current Role", "Requested Role", "Department", "Justification", "Status", "Decided By"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (AdminRequest r : filteredAdminRequests) {
            model.addRow(new Object[]{
                    nonBlank(r.getApplicantName(), "-"),
                    nonBlank(r.getApplicantEmail(), "-"),
                    nonBlank(r.getCurrentRole(), "-"),
                    nonBlank(r.getRequestedRole(), "-"),
                    nonBlank(r.getDepartment(), "-"),
                    nonBlank(r.getReason(), "-"),
                    normalizeStatusDisplay(r.getStatus()),
                    nonBlank(r.getDecidedByName(), "-")
            });
        }
        JTable table = styledTable(model);
        table.getColumnModel().getColumn(6).setCellRenderer(statusCellRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < filteredAdminRequests.size()) {
                AdminRequest r = filteredAdminRequests.get(row);
                inspectorArea.setText(
                        "ADMIN PRIVILEGE PETITION\n"
                                + "Applicant: " + nonBlank(r.getApplicantName(), "-") + " (" + nonBlank(r.getApplicantEmail(), "-") + ")"
                                + "   |   Current Role: " + nonBlank(r.getCurrentRole(), "-")
                                + "   ->   Requested: " + nonBlank(r.getRequestedRole(), "-") + "\n"
                                + "Department: " + nonBlank(r.getDepartment(), "-") + "   |   Status: " + normalizeStatusDisplay(r.getStatus()) + "\n"
                                + "Justification: " + nonBlank(r.getReason(), "-")
                                + (r.getDecisionNotes() != null && !r.getDecisionNotes().isBlank() ? "\nDecision Notes: " + r.getDecisionNotes() : "")
                );
            }
        });

        if (isPrincipal) {
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            btns.setOpaque(false);
            ActionButton grantBtn = new ActionButton("✓  Grant Privilege", ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57), Color.WHITE, null, 9);
            grantBtn.addActionListener(e -> {
                int row = table.getSelectedRow();
                if (row < 0) {
                    alertBanner.showError("Select an Admin privilege petition row first.");
                    return;
                }
                AdminRequest req = filteredAdminRequests.get(row);
                if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                    alertBanner.showError("This petition has already been processed (" + req.getStatus() + ").");
                    return;
                }
                String notes = JOptionPane.showInputDialog(this, "Enter Principal authorization note (optional):", "Grant " + req.getRequestedRole() + " Privilege", JOptionPane.PLAIN_MESSAGE);
                if (notes != null) {
                    controller.decideAdminRequestAsync(actor, req.getId(), true, notes, res -> handleOperationResult(res.success, res.message));
                }
            });

            ActionButton declineBtn = new ActionButton("✕  Decline Petition", ThemeManager.ROSE_PRIMARY, new Color(0xBE, 0x12, 0x3C), Color.WHITE, null, 9);
            declineBtn.addActionListener(e -> {
                int row = table.getSelectedRow();
                if (row < 0) {
                    alertBanner.showError("Select an Admin privilege petition row first.");
                    return;
                }
                AdminRequest req = filteredAdminRequests.get(row);
                if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                    alertBanner.showError("This petition has already been processed (" + req.getStatus() + ").");
                    return;
                }
                String reason = promptMandatoryRejectionReason("Decline Privilege Petition: " + req.getApplicantName());
                if (reason != null) {
                    controller.decideAdminRequestAsync(actor, req.getId(), false, reason, res -> handleOperationResult(res.success, res.message));
                }
            });
            btns.add(grantBtn);
            btns.add(declineBtn);
            top.add(btns, BorderLayout.EAST);
        }

        card.add(top, BorderLayout.NORTH);
        if (filteredAdminRequests.isEmpty()) {
            card.add(emptyPanel("No admin privilege requests recorded in MySQL `admin_requests`.", "Requests submitted by staff for elevated privileges appear here."), BorderLayout.CENTER);
        } else {
            card.add(wrapScroll(table), BorderLayout.CENTER);
        }
        return card;
    }

    // -------------------------------------------------------------------------
    // TAB 5: APPROVAL HISTORY (`approval_records`)
    // -------------------------------------------------------------------------

    private JPanel buildHistoryTab() {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel hdr = new JLabel("Immutable Approval History (`approval_records` in MySQL)");
        hdr.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        hdr.setForeground(ThemeManager.primaryText());
        card.add(hdr, BorderLayout.NORTH);

        String[] cols = {"Timestamp", "Request Type", "Target / Candidate", "Roll / Ref", "Department", "Approver", "Role", "Decision", "Comments / Reason"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (ApprovalRecord r : filteredHistory) {
            model.addRow(new Object[]{
                    formatTimestamp(r.getDecidedAt()),
                    nonBlank(r.getRequestType(), "-"),
                    nonBlank(r.getTargetName(), "-"),
                    nonBlank(r.getStudentRollNumber(), "-"),
                    nonBlank(r.getDepartment(), "-"),
                    nonBlank(r.getApproverName(), "-"),
                    nonBlank(r.getApproverRole(), "-"),
                    nonBlank(r.getDecision(), "-"),
                    nonBlank(r.getComments(), "-")
            });
        }
        JTable table = styledTable(model);
        table.getColumnModel().getColumn(7).setCellRenderer(statusCellRenderer());
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < filteredHistory.size()) {
                ApprovalRecord r = filteredHistory.get(row);
                inspectorArea.setText(
                        "APPROVAL AUDIT RECORD\n"
                                + "Type: " + nonBlank(r.getRequestType(), "-") + "   |   Target: " + nonBlank(r.getTargetName(), "-")
                                + "   |   Decision: " + nonBlank(r.getDecision(), "-") + "   |   When: " + formatTimestamp(r.getDecidedAt()) + "\n"
                                + "Approver: " + nonBlank(r.getApproverName(), "-") + " (" + nonBlank(r.getApproverRole(), "-") + ")"
                                + "   |   Dept: " + nonBlank(r.getDepartment(), "-") + "\n"
                                + "Comments / Rejection Reason: " + nonBlank(r.getComments(), "-")
                );
            }
        });

        if (filteredHistory.isEmpty()) {
            card.add(emptyPanel("No approval history records found.", "Decisions recorded across student, faculty, company, and admin approvals appear here."), BorderLayout.CENTER);
        } else {
            card.add(wrapScroll(table), BorderLayout.CENTER);
        }
        return card;
    }

    // -------------------------------------------------------------------------
    // MANDATORY REJECTION REASON DIALOG & HELPERS
    // -------------------------------------------------------------------------

    private String promptMandatoryRejectionReason(String dialogTitle) {
        JTextArea area = new JTextArea(4, 34);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        JScrollPane sp = new JScrollPane(area);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JLabel prompt = new JLabel("<html><b>Rejection Reason (Mandatory):</b><br/>Enter the institutional reason to record in MySQL <code>approval_records</code> and notify the applicant.</html>");
        prompt.setFont(ThemeManager.font(Font.PLAIN, 12));
        panel.add(prompt, BorderLayout.NORTH);
        panel.add(sp, BorderLayout.CENTER);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    panel,
                    dialogTitle,
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (choice != JOptionPane.OK_OPTION) {
                return null;
            }
            String txt = area.getText() == null ? "" : area.getText().trim();
            if (!txt.isEmpty()) {
                return txt;
            }
            JOptionPane.showMessageDialog(
                    this,
                    "A rejection reason is mandatory. Please provide a clear explanation before rejecting.",
                    "Rejection Reason Required",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleOperationResult(boolean success, String message) {
        if (success) {
            alertBanner.clear();
            feedbackToast.setText(message);
            feedbackToast.setForeground(ThemeManager.EMERALD_PRIMARY);
            loadData();
        } else {
            alertBanner.showError(message);
        }
    }

    private RoundedCard kpiCard(String title, JLabel valLabel, Color accent) {
        RoundedCard card = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.font(Font.BOLD, 10));
        t.setForeground(ThemeManager.mutedText());
        valLabel.setFont(ThemeManager.font(Font.BOLD, 22));
        valLabel.setForeground(accent);
        card.add(t);
        card.add(Box.createVerticalStrut(4));
        card.add(valLabel);
        return card;
    }

    private JTable styledTable(DefaultTableModel model) {
        JTable t = new JTable(model);
        t.setRowHeight(32);
        t.setFont(ThemeManager.font(Font.PLAIN, 12));
        t.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11));
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setFillsViewportHeight(true);
        t.setShowVerticalLines(false);
        return t;
    }

    private JScrollPane wrapScroll(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new MatteBorder(1, 1, 1, 1, ThemeManager.cardBorder()));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private DefaultTableCellRenderer statusCellRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(ThemeManager.font(Font.BOLD, 11));
                String v = value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
                if (!isSelected) {
                    if (v.contains("APPROV") || v.contains("ACTIVE")) {
                        lbl.setForeground(ThemeManager.EMERALD_PRIMARY);
                    } else if (v.contains("REJECT")) {
                        lbl.setForeground(ThemeManager.ROSE_PRIMARY);
                    } else {
                        lbl.setForeground(ThemeManager.AMBER_PRIMARY);
                    }
                }
                return lbl;
            }
        };
    }

    private JPanel emptyPanel(String title, String sub) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(42, 16, 42, 16));
        JLabel t = new JLabel(title, SwingConstants.CENTER);
        t.setFont(ThemeManager.font(Font.BOLD, 13.5f));
        t.setForeground(ThemeManager.secondaryText());
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel s = new JLabel(sub, SwingConstants.CENTER);
        s.setFont(ThemeManager.font(Font.PLAIN, 12));
        s.setForeground(ThemeManager.mutedText());
        s.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(t);
        p.add(Box.createVerticalStrut(4));
        p.add(s);
        return p;
    }

    private JLabel labelInline(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.font(Font.BOLD, 11));
        l.setForeground(ThemeManager.secondaryText());
        return l;
    }

    private boolean isPendingStatus(String status) {
        if (status == null || status.isBlank()) return false;
        String s = status.trim().toUpperCase(Locale.ROOT);
        return s.equals("PENDING") || s.equals("PENDING_APPROVAL");
    }

    private String normalizeStatusDisplay(String status) {
        if (status == null || status.isBlank()) return "PENDING";
        String s = status.trim().toUpperCase(Locale.ROOT);
        if ("PENDING_APPROVAL".equals(s)) return "PENDING";
        return s;
    }

    private boolean matchesStatus(String rawStatus, String filter) {
        if (filter == null || "ALL".equalsIgnoreCase(filter)) return true;
        String norm = normalizeStatusDisplay(rawStatus);
        if ("APPROVED".equalsIgnoreCase(filter)) {
            return "APPROVED".equals(norm) || "ACTIVE".equals(norm);
        }
        return norm.equalsIgnoreCase(filter);
    }

    private boolean matchesDept(String rawDept, String filterDept) {
        if (filterDept == null || "ALL DEPTS".equalsIgnoreCase(filterDept)) return true;
        return DeptNormalizer.normalizeDeptCode(rawDept)
                .equalsIgnoreCase(DeptNormalizer.normalizeDeptCode(filterDept));
    }

    private boolean containsAny(String q, String... fields) {
        for (String f : fields) {
            if (f != null && f.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    private String formatTimestamp(String iso) {
        if (iso == null || iso.isBlank()) return "-";
        try {
            return DATE_FMT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso.length() > 16 ? iso.substring(0, 16).replace('T', ' ') : iso;
        }
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
