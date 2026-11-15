package edu.gcet.placementhub.ui;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.AuthController;
import edu.gcet.placementhub.controllers.DashboardController;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.services.AuthService;
import edu.gcet.placementhub.services.GovernanceService;
import edu.gcet.placementhub.ui.applications.ApplicationsPanel;
import edu.gcet.placementhub.ui.approvals.ApprovalCenterPanel;
import edu.gcet.placementhub.ui.audit.AuditLogsPanel;
import edu.gcet.placementhub.ui.auth.LoginPanel;
import edu.gcet.placementhub.ui.auth.WelcomePanel;
import edu.gcet.placementhub.ui.campus.CampusConnectPanel;
import edu.gcet.placementhub.ui.companies.CompaniesPanel;
import edu.gcet.placementhub.ui.dashboard.DashboardPanel;
import edu.gcet.placementhub.ui.diagnostics.DatabaseDiagnosticsPanel;
import edu.gcet.placementhub.ui.drives.DrivesPanel;
import edu.gcet.placementhub.ui.eligibility.EligibilityPanel;
import edu.gcet.placementhub.ui.eligibility.MatchingPanel;
import edu.gcet.placementhub.ui.eligibility.SkillGapPanel;
import edu.gcet.placementhub.ui.faculty.FacultyDirectoryPanel;
import edu.gcet.placementhub.ui.reports.ReportsCenterPanel;
import edu.gcet.placementhub.ui.settings.SettingsPanel;
import edu.gcet.placementhub.ui.shell.DbDiagnosticsDialog;
import edu.gcet.placementhub.ui.shell.HeaderBarPanel;
import edu.gcet.placementhub.ui.shell.SidebarPanel;
import edu.gcet.placementhub.ui.skills.SkillMatrixPanel;
import edu.gcet.placementhub.ui.students.StudentProfilePanel;
import edu.gcet.placementhub.ui.students.StudentsPanel;
import edu.gcet.placementhub.utils.OperationResult;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.PasswordInputField;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Top-level desktop JFrame managing Welcome -> Login/Registration -> Authenticated Workspace.
 */
public class MainFrame extends JFrame {

    private static final String CARD_WELCOME = "WELCOME";
    private static final String CARD_LOGIN = "LOGIN";
    private static final String CARD_WORKSPACE = "WORKSPACE";

    private final AuthController authController = new AuthController();
    private final DashboardController dashboardController = new DashboardController();
    private final CardLayout rootCards = new CardLayout();
    private final JPanel rootPanel = new JPanel(rootCards);

    private final WelcomePanel welcomePanel;
    private final LoginPanel loginPanel;

    private HeaderBarPanel headerBar;
    private SidebarPanel sidebar;
    private JPanel workspaceBody;
    private DashboardPanel dashboardPanel;
    private CampusConnectPanel campusConnectPanel;
    private StudentsPanel studentsPanel;
    private StudentProfilePanel studentProfilePanel;
    private SkillMatrixPanel skillMatrixPanel;
    private FacultyDirectoryPanel facultyDirectoryPanel;
    private CompaniesPanel companiesPanel;
    private DrivesPanel drivesPanel;
    private EligibilityPanel eligibilityPanel;
    private SkillGapPanel skillGapPanel;
    private MatchingPanel matchingPanel;
    private ApplicationsPanel applicationsPanel;
    private ApprovalCenterPanel approvalCenterPanel;
    private ReportsCenterPanel reportsCenterPanel;
    private AuditLogsPanel auditLogsPanel;
    private DatabaseDiagnosticsPanel databaseDiagnosticsPanel;
    private SettingsPanel settingsPanel;
    private DbStatus latestDbStatus;
    private String currentPageId = "dashboard";
    private String selectedDriveId;
    private boolean connectionDialogShown = false;

    public MainFrame() {
        super("GCET PLACEMENT HUB — Smart Placement Cell & Skill Matrix Portal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1340, 860);
        setMinimumSize(new Dimension(1120, 700));
        setLocationRelativeTo(null);

        BufferedImage logo = ResourceLoader.loadLogoImage();
        if (logo != null) {
            setIconImage(logo);
        }

        welcomePanel = new WelcomePanel(this::openPortalLogin);
        loginPanel = new LoginPanel(this::handleLoginSuccess, this::showWelcome, this::openDbDiagnosticsDialog);
        JPanel workspaceShell = buildWorkspaceShell();

        rootPanel.add(welcomePanel, CARD_WELCOME);
        rootPanel.add(loginPanel, CARD_LOGIN);
        rootPanel.add(workspaceShell, CARD_WORKSPACE);

        setContentPane(rootPanel);
        showWelcome();
        refreshDbHealth();
    }

    private void openDbDiagnosticsDialog() {
        DbDiagnosticsDialog dlg = new DbDiagnosticsDialog(this, latestDbStatus, status -> {
            latestDbStatus = status;
            if (headerBar != null) headerBar.updateDbStatus(status);
            if (loginPanel != null) loginPanel.updateDbStatus(status);
            if ("dashboard".equals(currentPageId) && AppSession.getInstance().getCurrentUser() != null) {
                refreshDashboardData();
            }
        });
        dlg.setVisible(true);
    }

    private void openPortalLogin(PortalCategory category, boolean registerMode) {
        loginPanel.openPortal(category, registerMode);
        rootCards.show(rootPanel, CARD_LOGIN);
    }

    private void showWelcome() {
        rootCards.show(rootPanel, CARD_WELCOME);
    }

    private void handleLoginSuccess(AuthService.AuthResult result) {
        User user = result.getUser();
        Student stu = result.getStudentRecord();

        if (user != null && user.isMustChangePassword()) {
            boolean rotated = showForcePasswordChangeDialog(user);
            if (!rotated) {
                authController.logout();
                rootCards.show(rootPanel, CARD_LOGIN);
                return;
            }
            user.setMustChangePassword(false);
            AppSession.getInstance().startSession(user, stu);
        }

        this.currentPageId = "dashboard";
        headerBar.setCurrentUser(user);
        headerBar.setActiveModuleTitle("Dashboard");
        sidebar.rebuildNavForRole(user.getRole());
        sidebar.setActivePage("dashboard");
        renderAuthenticatedSessionView(user, stu, "dashboard", "Dashboard");

        rootCards.show(rootPanel, CARD_WORKSPACE);
    }

    private boolean showForcePasswordChangeDialog(User user) {
        AtomicBoolean completed = new AtomicBoolean(false);
        JDialog dlg = new JDialog(this, "First-Login Security — Change Temporary Password", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(490, 440);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());
        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));

        BadgeLabel badge = new BadgeLabel("FIRST-LOGIN SECURITY POLICY", new Color(0xFE, 0xF3, 0xC7), new Color(0x92, 0x40, 0x0E), new Color(0xFD, 0xE6, 0x8A), 8);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Change Your Temporary Password");
        title.setFont(ThemeManager.font(Font.BOLD, 17));
        title.setForeground(ThemeManager.NAVY_MIDNIGHT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("<html><body style='width:420px;'>Signed in as <b>" + user.getFullName()
                + "</b> (<code>@" + user.getUsername()
                + "</code>). Your account uses a temporary password and must be updated before entering the dashboard.</body></html>");
        subtitle.setFont(ThemeManager.font(Font.PLAIN, 12));
        subtitle.setForeground(ThemeManager.SLATE_600);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        AlertBanner alert = new AlertBanner();
        alert.setAlignmentX(Component.LEFT_ALIGNMENT);

        PasswordInputField curField = new PasswordInputField("Enter temporary / current password");
        PasswordInputField newField = new PasswordInputField("New password (min. 6 chars)");
        PasswordInputField confirmField = new PasswordInputField("Confirm new password");

        content.add(badge);
        content.add(Box.createVerticalStrut(8));
        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(10));
        content.add(alert);
        content.add(Box.createVerticalStrut(8));
        content.add(forceLabeledField("Temporary / Current Password *", curField));
        content.add(Box.createVerticalStrut(10));
        content.add(forceLabeledField("New Personal Password (min. 6 chars) *", newField));
        content.add(Box.createVerticalStrut(10));
        content.add(forceLabeledField("Confirm New Password *", confirmField));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        footer.setBackground(ThemeManager.SLATE_50);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_200));

        ActionButton signOutBtn = new ActionButton("Sign Out", Color.WHITE, ThemeManager.SLATE_100, ThemeManager.SLATE_700, ThemeManager.SLATE_300, 10);
        signOutBtn.addActionListener(e -> dlg.dispose());

        ActionButton saveBtn = new ActionButton("Save Password & Enter Portal", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        saveBtn.addActionListener(e -> {
            alert.clear();
            String cur = curField.getPasswordText();
            String next = newField.getPasswordText();
            String conf = confirmField.getPasswordText();

            if (cur.isEmpty()) {
                alert.showError("Please enter your temporary / current password.");
                return;
            }
            if (next.length() < 6) {
                alert.showError("New password must be at least 6 characters long.");
                return;
            }
            if (!next.equals(conf)) {
                alert.showError("New password and confirmation do not match.");
                return;
            }
            if (cur.equals(next)) {
                alert.showError("New password must differ from your temporary password.");
                return;
            }

            saveBtn.setEnabled(false);
            saveBtn.setText("Updating Password...");
            signOutBtn.setEnabled(false);

            new SwingWorker<OperationResult<Void>, Void>() {
                @Override
                protected OperationResult<Void> doInBackground() {
                    return new GovernanceService().changeMyPassword(user, cur, next);
                }

                @Override
                protected void done() {
                    saveBtn.setEnabled(true);
                    saveBtn.setText("Save Password & Enter Portal");
                    signOutBtn.setEnabled(true);
                    try {
                        OperationResult<Void> res = get();
                        if (res.isSuccess()) {
                            completed.set(true);
                            dlg.dispose();
                        } else {
                            alert.showError(res.getMessage());
                        }
                    } catch (Exception ex) {
                        alert.showError("Password update failed: " + ex.getMessage());
                    }
                }
            }.execute();
        });

        footer.add(signOutBtn);
        footer.add(saveBtn);

        dlg.add(new JScrollPane(content), BorderLayout.CENTER);
        dlg.add(footer, BorderLayout.SOUTH);
        dlg.setVisible(true);
        return completed.get();
    }

    private JPanel forceLabeledField(String label, Component comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        JLabel lbl = new JLabel(label);
        lbl.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        lbl.setForeground(ThemeManager.SLATE_700);
        p.add(lbl, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildWorkspaceShell() {
        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(ThemeManager.surfaceBg());

        headerBar = new HeaderBarPanel(
                () -> {
                    authController.logout();
                    showWelcome();
                },
                this::toggleWorkspaceTheme,
                () -> {
                    DbDiagnosticsDialog dlg = new DbDiagnosticsDialog(this, latestDbStatus, status -> {
                        latestDbStatus = status;
                        headerBar.updateDbStatus(status);
                        if ("dashboard".equals(currentPageId)) {
                            refreshDashboardData();
                        }
                    });
                    dlg.setVisible(true);
                }
        );

        sidebar = new SidebarPanel((pageId, label) -> {
            this.currentPageId = pageId;
            headerBar.setActiveModuleTitle(label);
            User u = AppSession.getInstance().getCurrentUser();
            renderAuthenticatedSessionView(u, AppSession.getInstance().getCurrentStudent(), pageId, label);
        });

        dashboardPanel = new DashboardPanel(
                this::navigateToPage,
                driveId -> {
                    this.selectedDriveId = driveId;
                    if (eligibilityPanel != null) eligibilityPanel.setInitialDriveId(driveId);
                    if (applicationsPanel != null) applicationsPanel.setInitialDriveId(driveId);
                },
                this::refreshDashboardData
        );
        campusConnectPanel = new CampusConnectPanel(this::navigateToPage);
        studentsPanel = new StudentsPanel(this::openStudentProfile, this::navigateToPage);
        studentProfilePanel = new StudentProfilePanel(this::navigateToPage);
        skillMatrixPanel = new SkillMatrixPanel(this::openStudentProfile);
        facultyDirectoryPanel = new FacultyDirectoryPanel();
        companiesPanel = new CompaniesPanel(companyId -> {
            drivesPanel.setCompanyFilterId(companyId);
            navigateToPage("drives");
        });
        drivesPanel = new DrivesPanel(
                driveId -> {
                    eligibilityPanel.setInitialDriveId(driveId);
                    navigateToPage("eligibility");
                },
                driveId -> {
                    skillGapPanel.setInitialDriveId(driveId);
                    navigateToPage("skill-gap");
                },
                driveId -> {
                    applicationsPanel.setInitialDriveId(driveId);
                    navigateToPage("applications");
                }
        );
        eligibilityPanel = new EligibilityPanel((route, driveId) -> {
            if ("skill-gap".equals(route)) {
                skillGapPanel.setInitialDriveId(driveId);
            }
            navigateToPage(route);
        });
        skillGapPanel = new SkillGapPanel(this::navigateToPage);
        matchingPanel = new MatchingPanel(driveId -> {
            eligibilityPanel.setInitialDriveId(driveId);
            navigateToPage("eligibility");
        });
        applicationsPanel = new ApplicationsPanel();
        approvalCenterPanel = new ApprovalCenterPanel();
        reportsCenterPanel = new ReportsCenterPanel();
        auditLogsPanel = new AuditLogsPanel();
        databaseDiagnosticsPanel = new DatabaseDiagnosticsPanel(status -> {
            this.latestDbStatus = status;
            if (headerBar != null) headerBar.updateDbStatus(status);
            if (loginPanel != null) loginPanel.updateDbStatus(status);
        });
        settingsPanel = new SettingsPanel(
                this::toggleWorkspaceTheme,
                updatedUser -> {
                    if (headerBar != null) headerBar.setCurrentUser(updatedUser);
                },
                status -> {
                    this.latestDbStatus = status;
                    if (headerBar != null) headerBar.updateDbStatus(status);
                    if (loginPanel != null) loginPanel.updateDbStatus(status);
                },
                this::openDbDiagnosticsDialog
        );

        workspaceBody = new JPanel(new BorderLayout());
        workspaceBody.setBackground(ThemeManager.surfaceBg());
        workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));

        shell.add(headerBar, BorderLayout.NORTH);
        shell.add(sidebar, BorderLayout.WEST);
        shell.add(workspaceBody, BorderLayout.CENTER);
        return shell;
    }

    private void toggleWorkspaceTheme() {
        ThemeManager.toggleTheme();
        workspaceBody.setBackground(ThemeManager.surfaceBg());
        if (headerBar != null) headerBar.syncThemeButton();
        if (dashboardPanel != null) dashboardPanel.refreshTheme();
        if (campusConnectPanel != null) campusConnectPanel.refreshTheme();
        if (studentsPanel != null) studentsPanel.refreshTheme();
        if (studentProfilePanel != null) studentProfilePanel.refreshTheme();
        if (skillMatrixPanel != null) skillMatrixPanel.refreshTheme();
        if (facultyDirectoryPanel != null) facultyDirectoryPanel.refreshTheme();
        if (companiesPanel != null) companiesPanel.refreshTheme();
        if (drivesPanel != null) drivesPanel.refreshTheme();
        if (eligibilityPanel != null) eligibilityPanel.refreshTheme();
        if (skillGapPanel != null) skillGapPanel.refreshTheme();
        if (matchingPanel != null) matchingPanel.refreshTheme();
        if (applicationsPanel != null) applicationsPanel.refreshTheme();
        if (approvalCenterPanel != null) approvalCenterPanel.refreshTheme();
        if (reportsCenterPanel != null) reportsCenterPanel.refreshTheme();
        if (auditLogsPanel != null) auditLogsPanel.refreshTheme();
        if (databaseDiagnosticsPanel != null) databaseDiagnosticsPanel.refreshTheme();
        if (settingsPanel != null) settingsPanel.refreshTheme();
    }

    private void navigateToPage(String pageId) {
        if (pageId == null || pageId.isBlank()) return;
        this.currentPageId = pageId;
        String label = sidebar.getLabelFor(pageId);
        sidebar.setActivePage(pageId);
        headerBar.setActiveModuleTitle(label);
        renderAuthenticatedSessionView(
                AppSession.getInstance().getCurrentUser(),
                AppSession.getInstance().getCurrentStudent(),
                pageId,
                label
        );
    }

    private void openStudentProfile(String studentId) {
        if (studentProfilePanel != null) {
            studentProfilePanel.setSelectedStudentId(studentId);
        }
        navigateToPage("profile");
    }

    /**
     * Renders active workspace module into `workspaceBody`.
     * Phase 2 mounts `DashboardPanel`; Phase 3 mounts Campus Connect, Student Directory,
     * Individual Student Profiles, Skill Matrix, and Faculty Directory.
     */
    private void renderAuthenticatedSessionView(User user, Student student, String pageId, String pageLabel) {
        workspaceBody.removeAll();
        workspaceBody.setBackground(ThemeManager.surfaceBg());

        if ("dashboard".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(dashboardPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            refreshDashboardData();
            return;
        }
        if ("campus-connect".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(campusConnectPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            return;
        }
        if ("students".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(studentsPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            studentsPanel.loadData();
            return;
        }
        if ("profile".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(studentProfilePanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            studentProfilePanel.loadData();
            return;
        }
        if ("skills".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(skillMatrixPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            skillMatrixPanel.loadData();
            return;
        }
        if ("faculty".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(facultyDirectoryPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            facultyDirectoryPanel.loadData();
            return;
        }
        if ("companies".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(companiesPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            companiesPanel.loadData();
            return;
        }
        if ("drives".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(drivesPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            drivesPanel.loadData();
            return;
        }
        if ("eligibility".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(eligibilityPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            eligibilityPanel.loadData();
            return;
        }
        if ("skill-gap".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(skillGapPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            skillGapPanel.loadData();
            return;
        }
        if ("matching".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(matchingPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            matchingPanel.loadData();
            return;
        }
        if ("applications".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(applicationsPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            applicationsPanel.loadData();
            return;
        }
        if ("approvals".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(approvalCenterPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            approvalCenterPanel.loadData();
            return;
        }
        if ("reports".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(reportsCenterPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            reportsCenterPanel.loadData();
            return;
        }
        if ("audit-logs".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(auditLogsPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            auditLogsPanel.loadData();
            return;
        }
        if ("diagnostics".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(databaseDiagnosticsPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            databaseDiagnosticsPanel.loadData();
            return;
        }
        if ("settings".equals(pageId)) {
            workspaceBody.setBorder(new EmptyBorder(0, 0, 0, 0));
            workspaceBody.add(settingsPanel, BorderLayout.CENTER);
            workspaceBody.revalidate();
            workspaceBody.repaint();
            settingsPanel.loadData();
            return;
        }

        workspaceBody.setBorder(new EmptyBorder(24, 24, 24, 24));
        RoundedCard card = new RoundedCard(18, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(26, 28, 26, 28));

        BadgeLabel badge = new BadgeLabel(
                "AUTHENTICATED SESSION · MYSQL JDBC VERIFIED",
                new Color(16, 185, 129, 30),
                ThemeManager.EMERALD_PRIMARY,
                new Color(16, 185, 129, 90),
                999
        );
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Module: " + pageLabel);
        title.setFont(ThemeManager.font(Font.BOLD, 22));
        title.setForeground(ThemeManager.primaryText());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        String roleLine = user != null
                ? "Role: " + user.getRole().name() + "  ·  Username: @" + user.getUsername()
                + (user.getDepartment() != null && !user.getDepartment().isBlank() ? "  ·  Department: " + user.getDepartment() : "")
                : "";
        JLabel sub = new JLabel(roleLine);
        sub.setFont(ThemeManager.font(Font.PLAIN, 13));
        sub.setForeground(ThemeManager.secondaryText());
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(badge);
        card.add(Box.createVerticalStrut(12));
        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(sub);

        if (student != null) {
            card.add(Box.createVerticalStrut(16));
            RoundedCard stuCard = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
            stuCard.setLayout(new GridLayout(1, 4, 14, 0));
            stuCard.setBorder(new EmptyBorder(14, 16, 14, 16));
            stuCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            stuCard.setMaximumSize(new Dimension(960, 72));
            stuCard.add(metricBlock("Roll Number", student.getRollNumber()));
            stuCard.add(metricBlock("Branch", student.getBranch()));
            stuCard.add(metricBlock("CGPA", String.format("%.2f", student.getCgpa())));
            stuCard.add(metricBlock("Placement Status", student.getPlacementStatus()));
            card.add(stuCard);
        }

        card.add(Box.createVerticalStrut(18));
        JLabel moduleNote = new JLabel(
                "<html><body style='width:680px; line-height:1.5;'>"
                        + "<b>Selected Route:</b> <code>" + pageId + "</code>"
                        + (selectedDriveId != null && ("eligibility".equals(pageId) || "drives".equals(pageId))
                        ? " (Selected Drive ID: <code>" + selectedDriveId + "</code>)" : "")
                        + "<br/>This route is not registered in the sidebar navigation. Return to the <b>Dashboard</b> or select a module from the left navigation rail."
                        + "</body></html>"
        );
        moduleNote.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        moduleNote.setForeground(ThemeManager.secondaryText());
        moduleNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(moduleNote);

        card.add(Box.createVerticalStrut(16));
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        ActionButton backDashBtn = new ActionButton("←  Return to Dashboard", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 12);
        backDashBtn.addActionListener(e -> navigateToPage("dashboard"));

        ActionButton diagBtn = new ActionButton("Inspect Live MySQL 8 JDBC Diagnostics", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 12);
        diagBtn.addActionListener(e -> {
            DbDiagnosticsDialog dlg = new DbDiagnosticsDialog(this, latestDbStatus, status -> {
                latestDbStatus = status;
                headerBar.updateDbStatus(status);
            });
            dlg.setVisible(true);
        });

        btnRow.add(backDashBtn);
        btnRow.add(diagBtn);
        card.add(btnRow);

        workspaceBody.add(card, BorderLayout.NORTH);
        workspaceBody.revalidate();
        workspaceBody.repaint();
    }

    private void refreshDashboardData() {
        User u = AppSession.getInstance().getCurrentUser();
        Student s = AppSession.getInstance().getCurrentStudent();
        if (u == null || dashboardPanel == null) return;
        dashboardPanel.setLoadingState(u, latestDbStatus);
        dashboardController.loadDashboardAsync(u, s, data -> {
            if (data != null && data.dbStatus != null) {
                this.latestDbStatus = data.dbStatus;
                if (headerBar != null) {
                    headerBar.updateDbStatus(data.dbStatus);
                }
            }
            if (data != null && data.currentStudentRecord != null && AppSession.getInstance().getCurrentStudent() == null) {
                AppSession.getInstance().startSession(u, data.currentStudentRecord);
            }
            dashboardPanel.renderData(data);
        });
    }

    private JPanel metricBlock(String label, String value) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.PLAIN, 11));
        l.setForeground(ThemeManager.mutedText());
        JLabel v = new JLabel(value != null ? value : "-");
        v.setFont(ThemeManager.font(Font.BOLD, 15));
        v.setForeground(ThemeManager.primaryText());
        p.add(l);
        p.add(Box.createVerticalStrut(2));
        p.add(v);
        return p;
    }

    private void refreshDbHealth() {
        authController.checkDatabaseHealthAsync(status -> {
            this.latestDbStatus = status;
            if (headerBar != null) {
                headerBar.updateDbStatus(status);
            }
            if (loginPanel != null) {
                loginPanel.updateDbStatus(status);
            }
            if (status != null && !status.isConnected() && !connectionDialogShown) {
                // Never crash on an unreachable Atlas cluster / offline Wi-Fi: explain and offer a retry
                connectionDialogShown = true;
                edu.gcet.placementhub.ui.shell.ConnectionErrorDialog.show(
                        this,
                        status,
                        () -> {
                            connectionDialogShown = false;
                            edu.gcet.placementhub.database.DatabaseConnection.getInstance().reconnect();
                            refreshDbHealth();
                        },
                        this::openDbDiagnosticsDialog
                );
            } else if (status != null && status.isConnected()) {
                connectionDialogShown = false;
            }
        });
    }
}
