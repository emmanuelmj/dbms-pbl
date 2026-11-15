package edu.gcet.placementhub.ui.pages;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.ApprovalsDao;
import edu.gcet.placementhub.dao.CompanyDao;
import edu.gcet.placementhub.dao.FacultyDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.model.*;
import edu.gcet.placementhub.service.PermissionService;
import edu.gcet.placementhub.service.RegistrationApprovalService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

/**
 * GCET PLACEMENT HUB - Centralized Approval Management Dashboard
 * 
 * Provides centralized institutional governance across 4 critical workflows:
 * 1. Administrator Privilege Requests (Principal Dr. K. Sagar exclusive)
 * 2. Faculty Member Dual-Approval (Peer validation + Administrative authorization)
 * 3. Student Registration Review (Department Faculty validation)
 * 4. Corporate Recruiter Verification (College Administration clearance)
 */
public class ApprovalsPage extends JPanel {
    private final User currentUser;
    private final RegistrationApprovalService approvalService = new RegistrationApprovalService();
    private final ApprovalsDao approvalsDao = new ApprovalsDao();
    private final FacultyDao facultyDao = new FacultyDao();
    private final StudentDao studentDao = new StudentDao();
    private final CompanyDao companyDao = new CompanyDao();

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // KPI Badges
    private JLabel lblTotalPending;
    private JLabel lblFacultyPending;
    private JLabel lblStudentPending;
    private JLabel lblCompanyPending;
    private JLabel lblAdminPending;

    // Tabs
    private JTabbedPane tabbedPane;

    // Tab 1: Admin Requests
    private JTable tblAdminReqs;
    private DefaultTableModel modelAdminReqs;
    private JComboBox<String> cmbAdminFilter;

    // Tab 2: Faculty Dual Approvals
    private JTable tblFaculty;
    private DefaultTableModel modelFaculty;
    private JComboBox<String> cmbFacultyFilter;

    // Tab 3: Student Approvals
    private JTable tblStudents;
    private DefaultTableModel modelStudents;
    private JComboBox<String> cmbStudentFilter;

    // Tab 4: Company Approvals
    private JTable tblCompanies;
    private DefaultTableModel modelCompanies;
    private JComboBox<String> cmbCompanyFilter;

    public ApprovalsPage(User currentUser) {
        this.currentUser = currentUser;
        initUI();
        loadAllDataAsync();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Theme.LIGHT_BG);
        setBorder(new EmptyBorder(16, 20, 16, 20));

        // 1. Header Banner & KPI Summary Cards
        JPanel topContainer = new JPanel(new BorderLayout(0, 10));
        topContainer.setOpaque(false);

        // Header Title Bar
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JPanel titleText = new JPanel(new GridLayout(2, 1, 2, 2));
        titleText.setOpaque(false);
        JLabel pageTitle = new JLabel("📋 Institutional Approval Center");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        pageTitle.setForeground(Theme.MIDNIGHT_NAVY);

        JLabel pageSub = new JLabel("Centralized Multi-User Authorization & RBAC Workflow Management · " + AppConfig.COLLEGE_NAME);
        pageSub.setFont(Theme.FONT_REGULAR);
        pageSub.setForeground(Theme.MUTED_TEXT);

        titleText.add(pageTitle);
        titleText.add(pageSub);
        titlePanel.add(titleText, BorderLayout.WEST);

        JButton btnRefreshAll = Theme.createPrimaryButton("↻ Refresh All Queues");
        btnRefreshAll.addActionListener(e -> loadAllDataAsync());
        titlePanel.add(btnRefreshAll, BorderLayout.EAST);

        topContainer.add(titlePanel, BorderLayout.NORTH);

        // KPI Badges Row
        JPanel kpiRow = new JPanel(new GridLayout(1, 5, 12, 0));
        kpiRow.setOpaque(false);
        kpiRow.setPreferredSize(new Dimension(1000, 72));

        lblTotalPending = new JLabel("0", JLabel.CENTER);
        lblFacultyPending = new JLabel("0", JLabel.CENTER);
        lblStudentPending = new JLabel("0", JLabel.CENTER);
        lblCompanyPending = new JLabel("0", JLabel.CENTER);
        lblAdminPending = new JLabel("0", JLabel.CENTER);

        kpiRow.add(createKpiCard("Total Pending", lblTotalPending, Theme.ELECTRIC_BLUE));
        kpiRow.add(createKpiCard("Faculty Dual Queue", lblFacultyPending, Theme.DEEP_BLUE));
        kpiRow.add(createKpiCard("Pending Students", lblStudentPending, Theme.CYAN_ACCENT));
        kpiRow.add(createKpiCard("Pending Companies", lblCompanyPending, Theme.AMBER_WARN));
        kpiRow.add(createKpiCard("Principal Petitions", lblAdminPending, Theme.DANGER_ROSE));

        topContainer.add(kpiRow, BorderLayout.SOUTH);
        add(topContainer, BorderLayout.NORTH);

        // 2. Tabbed Queues
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("👑 Administrator Requests (Principal Only)", createAdminRequestsPanel());
        tabbedPane.addTab("🎓 Faculty Dual-Approval Queue", createFacultyPanel());
        tabbedPane.addTab("👨‍🎓 Student Registration Queue", createStudentsPanel());
        tabbedPane.addTab("🏢 Corporate Recruiter Clearance", createCompaniesPanel());

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(4, 2));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitle.setForeground(Theme.MUTED_TEXT);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        valueLabel.setForeground(accentColor);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // ========================================================
    // TAB 1: ADMINISTRATOR REQUESTS (PRINCIPAL EXCLUSIVE)
    // ========================================================
    private JPanel createAdminRequestsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Authority Notice Banner
        boolean isPrincipal = PermissionService.canApproveAdminRequests(currentUser);
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(isPrincipal ? new Color(0x10, 0xB9, 0x81, 0x18) : new Color(0xEF, 0x44, 0x44, 0x14));
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(isPrincipal ? Theme.SUCCESS_GREEN : Theme.DANGER_ROSE, 1),
            new EmptyBorder(8, 14, 8, 14)
        ));

        JLabel lblBanner = new JLabel(isPrincipal ?
            "<html><b>Principal Executive Authority:</b> You are logged in as Principal Dr. K. Sagar. You hold constitutional authority to approve or reject administrator petitions.</html>" :
            "<html><b>Principal Exclusive Restricted Authority:</b> Only Principal <b>Dr. K. Sagar</b> has constitutional authority to grant or reject administrator privilege petitions. Other accounts cannot approve.</html>");
        lblBanner.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblBanner.setForeground(Theme.MIDNIGHT_NAVY);
        banner.add(lblBanner, BorderLayout.CENTER);
        panel.add(banner, BorderLayout.NORTH);

        // Filter & Action Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Status Filter:"));

        cmbAdminFilter = new JComboBox<>(new String[]{"PENDING", "APPROVED", "REJECTED", "ALL"});
        cmbAdminFilter.addActionListener(e -> loadAdminRequestsAsync());
        toolbar.add(cmbAdminFilter);

        JButton btnApproveReq = Theme.createPrimaryButton("✓ Approve as Principal");
        btnApproveReq.setEnabled(isPrincipal);
        btnApproveReq.addActionListener(e -> decideAdminRequestSelected(true));
        toolbar.add(btnApproveReq);

        JButton btnRejectReq = Theme.createDangerButton("✕ Reject Petition");
        btnRejectReq.setEnabled(isPrincipal);
        btnRejectReq.addActionListener(e -> decideAdminRequestSelected(false));
        toolbar.add(btnRejectReq);

        JButton btnViewDetails = Theme.createSecondaryButton("View Petition Details");
        btnViewDetails.addActionListener(e -> viewAdminRequestDetails());
        toolbar.add(btnViewDetails);

        // Table
        String[] cols = {"Request ID", "Applicant Name", "Email", "Current Role", "Dept", "Requested Role", "Justification", "Submitted", "Status", "Decided By"};
        modelAdminReqs = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblAdminReqs = new JTable(modelAdminReqs);
        tblAdminReqs.setRowHeight(28);
        tblAdminReqs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblAdminReqs.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        JScrollPane scroll = new JScrollPane(tblAdminReqs);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(toolbar, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // TAB 2: FACULTY DUAL APPROVAL QUEUE
    // ========================================================
    private JPanel createFacultyPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Rule Banner
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(0x26, 0x84, 0xFF, 0x14));
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.ELECTRIC_BLUE, 1),
            new EmptyBorder(8, 14, 8, 14)
        ));
        JLabel lblNotice = new JLabel("<html><b>Mandatory Dual Approval Policy:</b> New faculty accounts require BOTH Faculty Peer Validation (≥1 Faculty SPOC) AND College Administrative Authorization (≥1 Admin). Only upon dual clearance does the user account switch to ACTIVE.</html>");
        lblNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblNotice.setForeground(Theme.MIDNIGHT_NAVY);
        banner.add(lblNotice, BorderLayout.CENTER);
        panel.add(banner, BorderLayout.NORTH);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Status Filter:"));

        cmbFacultyFilter = new JComboBox<>(new String[]{"PENDING", "ACTIVE", "REJECTED", "ALL"});
        cmbFacultyFilter.addActionListener(e -> loadFacultyAsync());
        toolbar.add(cmbFacultyFilter);

        boolean canFac = PermissionService.canApproveFacultyFacultySide(currentUser);
        boolean canAdm = PermissionService.canApproveFacultyAdminSide(currentUser);

        JButton btnFacApprove = Theme.createPrimaryButton("✓ Faculty Peer Approval");
        btnFacApprove.setEnabled(canFac);
        btnFacApprove.setToolTipText("Conduct departmental faculty peer validation");
        btnFacApprove.addActionListener(e -> approveFacultyPeerSelected());
        toolbar.add(btnFacApprove);

        JButton btnAdmApprove = Theme.createPrimaryButton("✓ Administrator Clearance");
        btnAdmApprove.setEnabled(canAdm);
        btnAdmApprove.setToolTipText("Grant institutional administrative authorization");
        btnAdmApprove.addActionListener(e -> approveFacultyAdminSelected());
        toolbar.add(btnAdmApprove);

        JButton btnReject = Theme.createDangerButton("✕ Reject Application");
        btnReject.setEnabled(canFac || canAdm);
        btnReject.addActionListener(e -> rejectFacultySelected());
        toolbar.add(btnReject);

        // Table
        String[] cols = {"DB ID", "Emp ID", "Full Name", "Dept", "Designation", "Email", "Phone", "Faculty Approved?", "Admin Approved?", "Status"};
        modelFaculty = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblFaculty = new JTable(modelFaculty);
        tblFaculty.setRowHeight(28);
        tblFaculty.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblFaculty.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        JScrollPane scroll = new JScrollPane(tblFaculty);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(toolbar, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // TAB 3: STUDENT REGISTRATION QUEUE
    // ========================================================
    private JPanel createStudentsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(0x10, 0xB9, 0x81, 0x14));
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.SUCCESS_GREEN, 1),
            new EmptyBorder(8, 14, 8, 14)
        ));
        JLabel lblNotice = new JLabel("<html><b>Academic Verification Queue:</b> Department Faculty Coordinators review student identity, roll numbers, and academic branch. Approved students immediately gain portal access to skills & drives.</html>");
        lblNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblNotice.setForeground(Theme.MIDNIGHT_NAVY);
        banner.add(lblNotice, BorderLayout.CENTER);
        panel.add(banner, BorderLayout.NORTH);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Status Filter:"));

        cmbStudentFilter = new JComboBox<>(new String[]{"PENDING", "APPROVED", "REJECTED", "ALL"});
        cmbStudentFilter.addActionListener(e -> loadStudentsAsync());
        toolbar.add(cmbStudentFilter);

        boolean canStu = PermissionService.canApproveStudentRegistration(currentUser);

        JButton btnApproveStu = Theme.createPrimaryButton("✓ Approve Student");
        btnApproveStu.setEnabled(canStu);
        btnApproveStu.addActionListener(e -> approveStudentSelected());
        toolbar.add(btnApproveStu);

        JButton btnRejectStu = Theme.createDangerButton("✕ Reject Student");
        btnRejectStu.setEnabled(canStu);
        btnRejectStu.addActionListener(e -> rejectStudentSelected());
        toolbar.add(btnRejectStu);

        // Table
        String[] cols = {"DB ID", "Roll No", "ID Card", "Full Name", "Branch", "Year", "Sec", "Email", "Phone", "Status", "Approved By"};
        modelStudents = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblStudents = new JTable(modelStudents);
        tblStudents.setRowHeight(28);
        tblStudents.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblStudents.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        JScrollPane scroll = new JScrollPane(tblStudents);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(toolbar, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // TAB 4: CORPORATE RECRUITER CLEARANCE
    // ========================================================
    private JPanel createCompaniesPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(0xD9, 0x77, 0x06, 0x14));
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.AMBER_WARN, 1),
            new EmptyBorder(8, 14, 8, 14)
        ));
        JLabel lblNotice = new JLabel("<html><b>Corporate Recruiter Verification:</b> Institutional Administrators verify hiring organizations before recruitment drives can be scheduled. Approved companies can view eligible talent and schedule interview rounds.</html>");
        lblNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblNotice.setForeground(Theme.MIDNIGHT_NAVY);
        banner.add(lblNotice, BorderLayout.CENTER);
        panel.add(banner, BorderLayout.NORTH);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Status Filter:"));

        cmbCompanyFilter = new JComboBox<>(new String[]{"PENDING", "APPROVED", "REJECTED", "ALL"});
        cmbCompanyFilter.addActionListener(e -> loadCompaniesAsync());
        toolbar.add(cmbCompanyFilter);

        boolean canComp = PermissionService.canApproveCompanyRegistration(currentUser);

        JButton btnApproveComp = Theme.createPrimaryButton("✓ Grant Recruiter Clearance");
        btnApproveComp.setEnabled(canComp);
        btnApproveComp.addActionListener(e -> approveCompanySelected());
        toolbar.add(btnApproveComp);

        JButton btnRejectComp = Theme.createDangerButton("✕ Reject Clearance");
        btnRejectComp.setEnabled(canComp);
        btnRejectComp.addActionListener(e -> rejectCompanySelected());
        toolbar.add(btnRejectComp);

        // Table
        String[] cols = {"ID", "Company Name", "Industry", "Location", "HR Name", "HR Email", "HR Phone", "Website", "Status", "Approved By"};
        modelCompanies = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        tblCompanies = new JTable(modelCompanies);
        tblCompanies.setRowHeight(28);
        tblCompanies.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblCompanies.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        JScrollPane scroll = new JScrollPane(tblCompanies);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(toolbar, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);

        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // ASYNC DATA LOADING
    // ========================================================

    public void loadAllDataAsync() {
        loadKpisAsync();
        loadAdminRequestsAsync();
        loadFacultyAsync();
        loadStudentsAsync();
        loadCompaniesAsync();
    }

    private void loadKpisAsync() {
        SwingWorker<Map<String, Integer>, Void> worker = new SwingWorker<>() {
            @Override
            protected Map<String, Integer> doInBackground() {
                return approvalsDao.getPendingCounts();
            }

            @Override
            protected void done() {
                try {
                    Map<String, Integer> counts = get();
                    int total = counts.getOrDefault("TOTAL", 0);
                    int fac = counts.getOrDefault("FACULTY", 0);
                    int stu = counts.getOrDefault("STUDENT", 0);
                    int comp = counts.getOrDefault("COMPANY", 0);
                    int adm = counts.getOrDefault("ADMIN_REQUEST", 0);

                    lblTotalPending.setText(String.valueOf(total));
                    lblFacultyPending.setText(String.valueOf(fac));
                    lblStudentPending.setText(String.valueOf(stu));
                    lblCompanyPending.setText(String.valueOf(comp));
                    lblAdminPending.setText(String.valueOf(adm));
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void loadAdminRequestsAsync() {
        String filter = (String) cmbAdminFilter.getSelectedItem();
        SwingWorker<List<AdminRequest>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<AdminRequest> doInBackground() {
                return approvalsDao.findAdminRequests(filter);
            }

            @Override
            protected void done() {
                try {
                    List<AdminRequest> list = get();
                    modelAdminReqs.setRowCount(0);
                    for (AdminRequest r : list) {
                        modelAdminReqs.addRow(new Object[]{
                            r.getId(),
                            r.getApplicantName(),
                            r.getApplicantEmail(),
                            r.getCurrentRole(),
                            r.getDepartment(),
                            r.getRequestedRole(),
                            r.getReason(),
                            r.getCreatedAt() != null ? dateFormat.format(r.getCreatedAt()) : "-",
                            r.getStatus(),
                            r.getDecidedByName() != null ? r.getDecidedByName() : "-"
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void loadFacultyAsync() {
        String filter = (String) cmbFacultyFilter.getSelectedItem();
        SwingWorker<List<Faculty>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Faculty> doInBackground() {
                return facultyDao.findAll(null, null, filter);
            }

            @Override
            protected void done() {
                try {
                    List<Faculty> list = get();
                    modelFaculty.setRowCount(0);
                    for (Faculty f : list) {
                        String facAppStr = f.isFacultyApproved() ? "✓ YES (" + f.getFacultyApprovedByName() + ")" : "⏳ PENDING";
                        String admAppStr = f.isAdminApproved() ? "✓ YES (" + f.getAdminApprovedByName() + ")" : "⏳ PENDING";

                        modelFaculty.addRow(new Object[]{
                            f.getId(),
                            f.getEmployeeId(),
                            f.getFullName(),
                            f.getDepartment(),
                            f.getDesignation(),
                            f.getEmail(),
                            f.getPhone(),
                            facAppStr,
                            admAppStr,
                            f.getStatus()
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void loadStudentsAsync() {
        String filter = (String) cmbStudentFilter.getSelectedItem();
        SwingWorker<List<Student>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Student> doInBackground() {
                return studentDao.findAll(null, null, null, filter);
            }

            @Override
            protected void done() {
                try {
                    List<Student> list = get();
                    modelStudents.setRowCount(0);
                    for (Student s : list) {
                        modelStudents.addRow(new Object[]{
                            s.getId(),
                            s.getRollNumber(),
                            s.getStudentIdCard() != null ? s.getStudentIdCard() : "-",
                            s.getFullName(),
                            s.getBranch(),
                            s.getYearOfStudy() > 0 ? "Year " + s.getYearOfStudy() : "-",
                            s.getSection() != null ? s.getSection() : "A",
                            s.getEmail(),
                            s.getPhone() != null ? s.getPhone() : "-",
                            s.getApprovalStatus(),
                            s.getApprovedByName() != null ? s.getApprovedByName() : "-"
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void loadCompaniesAsync() {
        String filter = (String) cmbCompanyFilter.getSelectedItem();
        SwingWorker<List<Company>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Company> doInBackground() {
                return companyDao.findAll(null, filter);
            }

            @Override
            protected void done() {
                try {
                    List<Company> list = get();
                    modelCompanies.setRowCount(0);
                    for (Company c : list) {
                        modelCompanies.addRow(new Object[]{
                            c.getId(),
                            c.getName(),
                            c.getIndustry(),
                            c.getLocation(),
                            c.getHrName(),
                            c.getHrEmail(),
                            c.getHrPhone(),
                            c.getWebsite(),
                            c.getApprovalStatus(),
                            c.getApprovedByName() != null ? c.getApprovedByName() : "-"
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    // ========================================================
    // ACTION HANDLERS
    // ========================================================

    private void decideAdminRequestSelected(boolean approve) {
        int row = tblAdminReqs.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an administrator petition row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String reqId = (String) modelAdminReqs.getValueAt(row, 0);
        String applicant = (String) modelAdminReqs.getValueAt(row, 1);
        String role = (String) modelAdminReqs.getValueAt(row, 5);

        String notes = JOptionPane.showInputDialog(this,
            (approve ? "Confirm Principal Approval for " : "Confirm Rejection of petition for ") + applicant + " (" + role + "):\nOptional institutional decision notes:",
            approve ? "Executive Approval Granted" : "Executive Decision",
            JOptionPane.PLAIN_MESSAGE);

        if (notes == null) return; // User cancelled

        try {
            approvalService.decideAdminRequest(currentUser, reqId, approve, notes);
            JOptionPane.showMessageDialog(this,
                "Petition " + (approve ? "APPROVED" : "REJECTED") + " successfully.\nAccount privilege updated in central database.",
                "Principal Decision Recorded", JOptionPane.INFORMATION_MESSAGE);
            loadAllDataAsync();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to record decision: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void viewAdminRequestDetails() {
        int row = tblAdminReqs.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a petition row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String applicant = (String) modelAdminReqs.getValueAt(row, 1);
        String email = (String) modelAdminReqs.getValueAt(row, 2);
        String currentRole = (String) modelAdminReqs.getValueAt(row, 3);
        String dept = (String) modelAdminReqs.getValueAt(row, 4);
        String requestedRole = (String) modelAdminReqs.getValueAt(row, 5);
        String reason = (String) modelAdminReqs.getValueAt(row, 6);
        String status = (String) modelAdminReqs.getValueAt(row, 8);

        String msg = "ADMINISTRATOR PRIVILEGE PETITION DETAILS\n\n" +
                     "Applicant Name: " + applicant + "\n" +
                     "Email: " + email + "\n" +
                     "Department: " + dept + "\n" +
                     "Current Role: " + currentRole + "\n" +
                     "Requested Role: " + requestedRole + "\n" +
                     "Current Status: " + status + "\n\n" +
                     "Applicant Justification:\n\"" + reason + "\"\n\n" +
                     "Institutional Note:\nOnly Principal Dr. K. Sagar possesses executive authority to grant this promotion.";

        JOptionPane.showMessageDialog(this, msg, "Petition Inspector · Principal Executive Office", JOptionPane.INFORMATION_MESSAGE);
    }

    private void approveFacultyPeerSelected() {
        int row = tblFaculty.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a faculty application row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = (String) modelFaculty.getValueAt(row, 0);
        String name = (String) modelFaculty.getValueAt(row, 2);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Confirm Faculty Peer Validation for: " + name + "?\n\n" +
            "This confirms departmental peer review.\n" +
            "Note: Account also requires Administrator Clearance before activation.",
            "Confirm Faculty Peer Approval", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                approvalService.approveFacultyByFaculty(currentUser, id);
                JOptionPane.showMessageDialog(this, "Faculty peer approval recorded successfully!", "Approval Recorded", JOptionPane.INFORMATION_MESSAGE);
                loadAllDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Approval Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void approveFacultyAdminSelected() {
        int row = tblFaculty.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a faculty application row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = (String) modelFaculty.getValueAt(row, 0);
        String name = (String) modelFaculty.getValueAt(row, 2);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Confirm College Administrator Clearance for: " + name + "?\n\n" +
            "This authorizes institutional access.\n" +
            "If Faculty Peer approval is already recorded, account becomes ACTIVE immediately.",
            "Confirm Administrator Authorization", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                approvalService.approveFacultyByAdmin(currentUser, id);
                JOptionPane.showMessageDialog(this, "Administrator clearance recorded successfully!", "Clearance Recorded", JOptionPane.INFORMATION_MESSAGE);
                loadAllDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Clearance Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void rejectFacultySelected() {
        int row = tblFaculty.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a faculty application row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = (String) modelFaculty.getValueAt(row, 0);
        String name = (String) modelFaculty.getValueAt(row, 2);

        String reason = JOptionPane.showInputDialog(this,
            "Enter reason for rejecting faculty registration for: " + name,
            "Rejection Reason", JOptionPane.PLAIN_MESSAGE);

        if (reason != null && !reason.trim().isEmpty()) {
            try {
                approvalService.rejectFaculty(currentUser, id, reason.trim());
                JOptionPane.showMessageDialog(this, "Faculty application rejected.", "Action Recorded", JOptionPane.INFORMATION_MESSAGE);
                loadAllDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Rejection Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void approveStudentSelected() {
        int row = tblStudents.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a student row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = (String) modelStudents.getValueAt(row, 0);
        String roll = (String) modelStudents.getValueAt(row, 1);
        String name = (String) modelStudents.getValueAt(row, 3);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Approve Student Registration for:\n" + name + " (" + roll + ")?\n\n" +
            "Student will immediately gain access to the Placement Hub, Skill Matrix, and Eligibility checks.",
            "Confirm Student Approval", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                approvalService.approveStudent(currentUser, id);
                JOptionPane.showMessageDialog(this, "Student account approved and activated!", "Student Approved", JOptionPane.INFORMATION_MESSAGE);
                loadAllDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Approval Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void rejectStudentSelected() {
        int row = tblStudents.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a student row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = (String) modelStudents.getValueAt(row, 0);
        String name = (String) modelStudents.getValueAt(row, 3);

        String reason = JOptionPane.showInputDialog(this,
            "Enter reason for rejecting registration for: " + name,
            "Rejection Reason", JOptionPane.PLAIN_MESSAGE);

        if (reason != null && !reason.trim().isEmpty()) {
            try {
                approvalService.rejectStudent(currentUser, id, reason.trim());
                JOptionPane.showMessageDialog(this, "Student registration rejected.", "Action Recorded", JOptionPane.INFORMATION_MESSAGE);
                loadAllDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void approveCompanySelected() {
        int row = tblCompanies.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a company row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = (String) modelCompanies.getValueAt(row, 0);
        String name = (String) modelCompanies.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Grant Placement Clearance for Company:\n" + name + "?\n\n" +
            "Recruiter will be authorized to post campus recruitment drives and evaluate candidate profiles.",
            "Confirm Recruiter Clearance", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                approvalService.approveCompany(currentUser, id);
                JOptionPane.showMessageDialog(this, "Company partner clearance approved!", "Clearance Approved", JOptionPane.INFORMATION_MESSAGE);
                loadAllDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void rejectCompanySelected() {
        int row = tblCompanies.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a company row.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String id = (String) modelCompanies.getValueAt(row, 0);
        String name = (String) modelCompanies.getValueAt(row, 1);

        String reason = JOptionPane.showInputDialog(this,
            "Enter reason for rejecting recruiter registration for: " + name,
            "Rejection Reason", JOptionPane.PLAIN_MESSAGE);

        if (reason != null && !reason.trim().isEmpty()) {
            try {
                approvalService.rejectCompany(currentUser, id, reason.trim());
                JOptionPane.showMessageDialog(this, "Company registration rejected.", "Action Recorded", JOptionPane.INFORMATION_MESSAGE);
                loadAllDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
