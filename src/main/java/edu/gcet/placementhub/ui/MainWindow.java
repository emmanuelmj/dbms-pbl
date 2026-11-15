package edu.gcet.placementhub.ui;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.ui.pages.*;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * GCET PLACEMENT HUB - Main Desktop Application Window
 * Geethanjali College of Engineering and Technology (Cheeryal, Keesara, Telangana)
 */
public class MainWindow extends JFrame {
    private final User currentUser;
    private final CardLayout cardLayout;
    private final JPanel mainCardPanel;

    private StudentsPage studentsPage;
    private CampusConnectPanel campusConnectPanel;
    private UsersPage usersPage;
    private FacultyPage facultyPage;
    private SkillMatrixPage skillMatrixPage;
    private ApprovalsPage approvalsPage;
    private ProfilePage profilePage;
    private JLabel lblUser;

    public MainWindow(User currentUser) {
        super(AppConfig.APP_NAME + " - " + AppConfig.APP_TITLE);
        this.currentUser = currentUser;

        this.cardLayout = new CardLayout();
        this.mainCardPanel = new JPanel(cardLayout);

        initWindow();
    }

    private void initWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setMinimumSize(new Dimension(1024, 700));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. Top Header Bar
        add(createHeaderBar(), BorderLayout.NORTH);

        // 2. Left Sidebar Navigation
        add(createSidebar(), BorderLayout.WEST);

        // 3. Central Working Area with CardLayout
        initPages();
        add(mainCardPanel, BorderLayout.CENTER);

        // Default to Students page
        cardLayout.show(mainCardPanel, "STUDENTS");
    }

    private JPanel createHeaderBar() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setPreferredSize(new Dimension(1280, 64));
        header.setBorder(new EmptyBorder(10, 20, 10, 20));

        // Left Branding
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
        left.setOpaque(false);

        // GCET Crest Symbol
        JLabel lblLogo = new JLabel(" G ");
        lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setOpaque(true);
        lblLogo.setBackground(Theme.ELECTRIC_BLUE);
        lblLogo.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        left.add(lblLogo);

        JPanel titleBlock = new JPanel(new GridLayout(2, 1));
        titleBlock.setOpaque(false);

        JLabel lblName = new JLabel(AppConfig.APP_NAME + " · Cheeryal Campus");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblName.setForeground(Color.WHITE);

        JLabel lblTag = new JLabel(AppConfig.COLLEGE_NAME + " · " + AppConfig.COLLEGE_TAGLINE);
        lblTag.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblTag.setForeground(Theme.CYAN_ACCENT);

        titleBlock.add(lblName);
        titleBlock.add(lblTag);
        left.add(titleBlock);
        header.add(left, BorderLayout.WEST);

        // Right Info: Date, MySQL indicator, User profile chip
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 8));
        right.setOpaque(false);

        String today = new SimpleDateFormat("EEE, d MMM yyyy").format(new Date());
        JLabel lblDate = new JLabel("📅 " + today);
        lblDate.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDate.setForeground(Theme.SOFT_WHITE);
        right.add(lblDate);

        // MySQL Connection Pulse Indicator
        boolean dbOk = DatabaseConnection.testConnection();
        JLabel lblDb = new JLabel(dbOk ? "● MySQL 8 Connected" : "○ Offline Demo Mode");
        lblDb.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblDb.setForeground(dbOk ? Theme.SUCCESS_GREEN : Theme.WARNING_AMBER);
        right.add(lblDb);

        // User badge
        String roleStr = currentUser != null ? currentUser.getRole().name() : "GUEST";
        String userName = currentUser != null ? currentUser.getFullName() : "User";
        lblUser = new JLabel("👤 " + userName + " (" + roleStr + ")");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUser.setForeground(Theme.CYAN_ACCENT);
        right.add(lblUser);

        JButton btnLogout = Theme.createSecondaryButton("Sign Out");
        btnLogout.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLogout.addActionListener(e -> {
            dispose();
            System.exit(0);
        });
        right.add(btnLogout);

        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.MIDNIGHT_NAVY);
        sidebar.setPreferredSize(new Dimension(230, 750));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.DEEP_BLUE),
                new EmptyBorder(14, 10, 14, 10)
        ));

        JLabel navTitle = new JLabel("CAMPUS NAVIGATION");
        navTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        navTitle.setForeground(Theme.CYAN_ACCENT);
        navTitle.setBorder(new EmptyBorder(0, 8, 8, 0));
        sidebar.add(navTitle);

        sidebar.add(createNavButton("👤 My Profile", "MY_PROFILE"));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("👥 Student Directory", "STUDENTS"));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("🎯 Technical Skill Matrix", "SKILLS"));
        sidebar.add(Box.createVerticalStrut(4));

        // Approval Center (Admins, Placement Officer, Faculty)
        if (currentUser == null || currentUser.getRole() != User.Role.STUDENT) {
            sidebar.add(createNavButton("📋 Approval Center", "APPROVALS"));
            sidebar.add(Box.createVerticalStrut(4));
        }

        // Faculty Directory (Admin, Placement Officer, Faculty)
        if (currentUser == null || currentUser.getRole() != User.Role.STUDENT) {
            sidebar.add(createNavButton("👨‍🏫 Faculty Mentors", "FACULTY"));
            sidebar.add(Box.createVerticalStrut(4));
        }

        // User Accounts & RBAC (Admin only)
        if (currentUser == null || currentUser.canManageUsers()) {
            sidebar.add(createNavButton("🔐 User Accounts & RBAC", "USERS"));
            sidebar.add(Box.createVerticalStrut(4));
        }

        sidebar.add(createNavButton("🏛️ Campus Connect (GCTC)", "CAMPUS_CONNECT"));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("🏢 Corporate Recruiters", "COMPANIES"));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("💼 Placement Drives", "DRIVES"));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("✓ Eligibility Engine", "ELIGIBILITY"));
        sidebar.add(Box.createVerticalStrut(4));
        sidebar.add(createNavButton("📊 Reports & Analytics", "REPORTS"));

        sidebar.add(Box.createVerticalGlue());

        JLabel lblFooter = new JLabel("<html><center>GCET Placement Hub v1.0<br>Java 17 Swing · FlatLaf</center></html>", JLabel.CENTER);
        lblFooter.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblFooter.setForeground(Color.GRAY);
        lblFooter.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(lblFooter);

        return sidebar;
    }

    private JButton createNavButton(String title, String cardName) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Theme.SOFT_WHITE);
        btn.setBackground(Theme.DEEP_BLUE);
        btn.setFocusPainted(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(210, 38));
        btn.setPreferredSize(new Dimension(210, 38));
        btn.setBorder(new EmptyBorder(6, 12, 6, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> {
            cardLayout.show(mainCardPanel, cardName);
            if ("STUDENTS".equals(cardName) && studentsPage != null) {
                studentsPage.loadStudentDataAsync();
            } else if ("USERS".equals(cardName) && usersPage != null) {
                usersPage.loadUserDataAsync();
            } else if ("FACULTY".equals(cardName) && facultyPage != null) {
                facultyPage.loadFacultyDataAsync();
            } else if ("SKILLS".equals(cardName) && skillMatrixPage != null) {
                skillMatrixPage.loadAllDataAsync();
            } else if ("APPROVALS".equals(cardName) && approvalsPage != null) {
                approvalsPage.loadAllDataAsync();
            } else if ("MY_PROFILE".equals(cardName) && profilePage != null) {
                profilePage.loadProfileAsync();
            }
        });

        return btn;
    }

    private void initPages() {
        studentsPage = new StudentsPage(currentUser);
        campusConnectPanel = new CampusConnectPanel();
        usersPage = new UsersPage(currentUser);
        facultyPage = new FacultyPage(currentUser);
        skillMatrixPage = new SkillMatrixPage(currentUser);
        approvalsPage = new ApprovalsPage(currentUser);
        profilePage = new ProfilePage(currentUser, () -> {
            if (lblUser != null && currentUser != null) {
                lblUser.setText("👤 " + currentUser.getFullName() + " (" + currentUser.getRole().name() + ")");
            }
        });

        mainCardPanel.add(profilePage, "MY_PROFILE");
        mainCardPanel.add(studentsPage, "STUDENTS");
        mainCardPanel.add(campusConnectPanel, "CAMPUS_CONNECT");
        mainCardPanel.add(usersPage, "USERS");
        mainCardPanel.add(facultyPage, "FACULTY");
        mainCardPanel.add(skillMatrixPage, "SKILLS");
        mainCardPanel.add(approvalsPage, "APPROVALS");

        // Placeholders for remaining modules until their dedicated panels are opened
        mainCardPanel.add(createPlaceholderPanel("Corporate Recruitment Partners (Tier 1 & IT Services)"), "COMPANIES");
        mainCardPanel.add(createPlaceholderPanel("Campus Placement Drives & Registration Deadlines"), "DRIVES");
        mainCardPanel.add(createPlaceholderPanel("Automated Multi-Criteria Eligibility Engine"), "ELIGIBILITY");
        mainCardPanel.add(createPlaceholderPanel("Institutional Placement Reports & JFreeChart Analytics"), "REPORTS");
    }

    private JPanel createPlaceholderPanel(String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Theme.LIGHT_BG);
        JLabel l = new JLabel("<html><center><h2>" + title + "</h2><p>Integrated in GCET Placement Hub MySQL Architecture.</p></center></html>", JLabel.CENTER);
        l.setFont(Theme.FONT_BODY);
        p.add(l, BorderLayout.CENTER);
        return p;
    }
}
