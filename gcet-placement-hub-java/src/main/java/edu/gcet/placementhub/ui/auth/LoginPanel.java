package edu.gcet.placementhub.ui.auth;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.AuthController;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.services.AuthService;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents.*;
import static edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Exact Java Swing recreation of LoginPage.tsx:
 * - 4 Primary Role Tabs: ADMIN, FACULTY, STUDENTS, RECRUITERS
 * - Sign In / New Registration mode switcher (for Faculty, Students, Companies)
 * - Quick account cards populate ONLY username and NEVER password
 * - Password field ALWAYS starts empty and masked with Eye/Show-Hide toggle
 * - Direct MySQL JDBC verification via AuthController (never freezes Swing EDT)
 * - Complete Student, Faculty, and Company registration forms + pending-approval receipts
 */
public class LoginPanel extends JPanel {

    private record PresetOption(String username, String roleTitle, String subtitle) {}

    private static final List<PresetOption> ADMIN_OPTIONS = List.of(
            new PresetOption("principal.sagar", "Principal (Executive Authority)", "Executive Approvals & Leadership"),
            new PresetOption("vc.harishchandra", "Vice Chairman", "Institutional Governance"),
            new PresetOption("chairman.ravinder", "Chairman", "Governing Body Leadership"),
            new PresetOption("admin", "Placement System Admin", "System Infrastructure")
    );

    private static final List<PresetOption> FACULTY_OPTIONS = List.of(
            new PresetOption("faculty_lavanya", "B. Laxmi Lavanya · CSE Coordinator", "CSE & Emerging Tech (AIML/DS/CS)"),
            new PresetOption("faculty_aparna", "P. Aparna · ECE Coordinator", "Electronics & Communication"),
            new PresetOption("faculty_rajashakar", "S. Raja Shakar Reddy · EEE Coordinator", "Electrical & Electronics"),
            new PresetOption("faculty_cse", "CSE Faculty Mentor", "Computer Science Department"),
            new PresetOption("placement_officer", "Placement Officer (TPO)", "Training & Placement Cell")
    );

    private static final List<PresetOption> RECRUITER_OPTIONS = List.of(
            new PresetOption("recruiter_tcs", "Tier-1 Partner (Tata Consultancy Services)", "Campus Drives & Shortlists")
    );

    private static final List<String> AVAILABLE_SKILLS = List.of(
            "Java", "Python", "C++", "SQL", "React", "Data Structures",
            "Algorithms", "Spring Boot", "Machine Learning", "Cloud Computing", "Web Development", "DevOps"
    );

    private final AuthController authController = new AuthController();
    private final Consumer<AuthService.AuthResult> onLoginSuccess;
    private final Runnable onBackToWelcome;
    private final Runnable onOpenDbSettings;

    private ActionButton dbStatusBtn;
    private PortalCategory activeRole = PortalCategory.ADMIN;
    private boolean registerMode = false;

    // Tab buttons
    private ActionButton tabAdminBtn;
    private ActionButton tabFacultyBtn;
    private ActionButton tabStudentsBtn;
    private ActionButton tabCompaniesBtn;

    // Sub-header strip (hidden on ADMIN)
    private JPanel subHeaderBar;
    private JLabel subHeaderLabel;
    private ActionButton modeLoginBtn;
    private ActionButton modeRegBtn;

    // Center content switcher
    private final CardLayout bodyCardLayout = new CardLayout();
    private final JPanel bodyCardContainer = new JPanel(bodyCardLayout);

    // Shared login state per portal view
    private final Map<PortalCategory, JTextField> loginUserFields = new EnumMap<>(PortalCategory.class);
    private final Map<PortalCategory, PasswordInputField> loginPassFields = new EnumMap<>(PortalCategory.class);
    private final Map<PortalCategory, AlertBanner> loginErrorBanners = new EnumMap<>(PortalCategory.class);
    private final Map<PortalCategory, ActionButton> loginSubmitButtons = new EnumMap<>(PortalCategory.class);
    private final Map<PortalCategory, String> loginDefaultBtnTexts = new EnumMap<>(PortalCategory.class);
    private final Map<PortalCategory, List<PresetCardButton>> presetCardGroups = new EnumMap<>(PortalCategory.class);

    // Debounced auto-login (~1.2s after typing password stops) + duplicate-request guard
    private final AtomicBoolean authInFlight = new AtomicBoolean(false);
    private final javax.swing.Timer autoLoginTimer;
    private volatile boolean suppressAutoLogin = false;
    private String lastAttemptedCredKey = null;

    // Student Registration fields
    private JTextField stuNameField, stuRollField, stuEmailField, stuPhoneField, stuCgpaField;
    private JComboBox<String> stuBranchCombo, stuYearCombo;
    private PasswordInputField stuPassField, stuConfirmPassField;
    private AlertBanner stuRegErrorBanner;
    private ActionButton stuRegSubmitBtn;
    private final Set<String> selectedStuSkills = new LinkedHashSet<>(List.of("Java", "SQL", "Data Structures"));
    private final List<JToggleButton> skillToggleButtons = new ArrayList<>();
    private JPanel stuRegFormPanel;
    private JPanel stuRegReceiptPanel;
    private JLabel stuReceiptBodyLabel;
    private String lastRegisteredRoll = "";

    // Faculty Registration fields
    private JTextField facNameField, facEmpIdField, facEmailField, facPhoneField;
    private JComboBox<String> facDeptCombo, facDesigCombo;
    private PasswordInputField facPassField, facConfirmPassField;
    private AlertBanner facRegErrorBanner;
    private ActionButton facRegSubmitBtn;
    private JPanel facRegFormPanel;
    private JPanel facRegReceiptPanel;
    private JLabel facReceiptBodyLabel;
    private String lastRegisteredFacId = "";

    // Company Registration fields
    private JTextField compNameField, compWebsiteField, compLocationField, compHrNameField, compHrEmailField, compHrPhoneField;
    private JComboBox<String> compIndustryCombo, compTierCombo;
    private PasswordInputField compPassField, compConfirmPassField;
    private AlertBanner compRegErrorBanner;
    private ActionButton compRegSubmitBtn;
    private JPanel compRegFormPanel;
    private JPanel compRegReceiptPanel;
    private JLabel compReceiptBodyLabel;
    private String lastRegisteredCompanyEmail = "";

    public LoginPanel(Consumer<AuthService.AuthResult> onLoginSuccess, Runnable onBackToWelcome) {
        this(onLoginSuccess, onBackToWelcome, null);
    }

    public LoginPanel(Consumer<AuthService.AuthResult> onLoginSuccess, Runnable onBackToWelcome, Runnable onOpenDbSettings) {
        this.onLoginSuccess = onLoginSuccess;
        this.onBackToWelcome = onBackToWelcome;
        this.onOpenDbSettings = onOpenDbSettings;
        this.autoLoginTimer = new javax.swing.Timer(1200, e -> triggerDebouncedAutoLogin());
        this.autoLoginTimer.setRepeats(false);

        setLayout(new GridBagLayout());
        setBackground(new Color(0x0B, 0x13, 0x29));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        RoundedCard dialogCard = new RoundedCard(20, Color.WHITE, ThemeManager.SLATE_200);
        dialogCard.setLayout(new BorderLayout());
        dialogCard.setPreferredSize(new Dimension(920, 720));
        dialogCard.setMaximumSize(new Dimension(960, 820));

        dialogCard.add(buildTopHeader(), BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(buildRoleTabsStrip(), BorderLayout.NORTH);

        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.add(buildSubHeaderBar(), BorderLayout.NORTH);

        buildAllBodyCards();
        JScrollPane bodyScroll = new JScrollPane(bodyCardContainer);
        bodyScroll.setBorder(null);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(20);
        bodyScroll.setBackground(Color.WHITE);
        bodyScroll.getViewport().setBackground(Color.WHITE);
        bodyWrap.add(bodyScroll, BorderLayout.CENTER);

        middle.add(bodyWrap, BorderLayout.CENTER);
        dialogCard.add(middle, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.insets = new Insets(8, 48, 8, 48);
        add(dialogCard, gbc);

        openPortal(PortalCategory.ADMIN, false);
    }

    public void openPortal(PortalCategory role, boolean startRegisterMode) {
        autoLoginTimer.stop();
        suppressAutoLogin = true;
        try {
            this.activeRole = role;
            this.registerMode = (role != PortalCategory.ADMIN) && startRegisterMode;
            this.lastAttemptedCredKey = null;

            // Clear ALL password fields and error banners on every switch
            for (PasswordInputField pf : loginPassFields.values()) {
                pf.clearPassword();
            }
            for (AlertBanner ab : loginErrorBanners.values()) {
                ab.clear();
            }

            if (role == PortalCategory.ADMIN) {
                setSelectedPreset(PortalCategory.ADMIN, "principal.sagar");
            } else if (role == PortalCategory.FACULTY) {
                setSelectedPreset(PortalCategory.FACULTY, "faculty_lavanya");
            } else if (role == PortalCategory.COMPANIES) {
                setSelectedPreset(PortalCategory.COMPANIES, "recruiter_tcs");
            } else {
                JTextField stuUser = loginUserFields.get(PortalCategory.STUDENTS);
                if (stuUser != null) stuUser.setText("");
            }

            updateRoleTabStyles();
            updateSubHeaderAndCard();
        } finally {
            suppressAutoLogin = false;
        }
    }

    private JPanel buildTopHeader() {
        JPanel hdr = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, ThemeManager.NAVY_MIDNIGHT, getWidth(), 0, new Color(0x0C, 0x16, 0x2E)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        hdr.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.SLATE_800),
                new EmptyBorder(14, 22, 14, 22)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(new JLabel(ResourceLoader.createCircularLogoIcon(44, new Color(50, 197, 244, 120))));

        JPanel txt = new JPanel();
        txt.setOpaque(false);
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        r1.setOpaque(false);
        JLabel brand = new JLabel("<html>GCET <span style='color:#32C5F4;'>PLACEMENT HUB</span></html>");
        brand.setFont(ThemeManager.font(Font.BOLD, 17));
        brand.setForeground(Color.WHITE);
        BadgeLabel tag = new BadgeLabel("INSTITUTIONAL AUTH", new Color(6, 182, 212, 45), new Color(0x67, 0xE8, 0xF9), new Color(6, 182, 212, 85), 999);
        tag.setFont(ThemeManager.font(Font.BOLD, 9.5f));
        r1.add(brand);
        r1.add(tag);

        JLabel sub = new JLabel("Geethanjali College of Engineering and Technology · Hyderabad (501301)");
        sub.setFont(ThemeManager.font(Font.PLAIN, 11.2f));
        sub.setForeground(ThemeManager.SLATE_300);
        sub.setBorder(new EmptyBorder(0, 8, 0, 0));
        txt.add(r1);
        txt.add(sub);
        left.add(txt);

        dbStatusBtn = new ActionButton(
                "●  MySQL 8: Checking...",
                ThemeManager.SLATE_800,
                ThemeManager.SLATE_700,
                ThemeManager.SLATE_200,
                ThemeManager.SLATE_700,
                10
        );
        dbStatusBtn.setFont(ThemeManager.monoFont(Font.BOLD, 11f));
        dbStatusBtn.setToolTipText("Inspect & configure local MySQL 8 JDBC connection (gcet_placement_hub)");
        dbStatusBtn.addActionListener(e -> {
            if (onOpenDbSettings != null) {
                onOpenDbSettings.run();
            }
        });

        ActionButton backBtn = new ActionButton(
                "←  Back to Public Portal",
                ThemeManager.SLATE_800,
                ThemeManager.SLATE_700,
                ThemeManager.SLATE_200,
                ThemeManager.SLATE_700,
                10
        );
        backBtn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        backBtn.addActionListener(e -> {
            autoLoginTimer.stop();
            onBackToWelcome.run();
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        right.setOpaque(false);
        right.add(dbStatusBtn);
        right.add(backBtn);

        hdr.add(left, BorderLayout.WEST);
        hdr.add(right, BorderLayout.EAST);
        return hdr;
    }

    public void updateDbStatus(DbStatus status) {
        if (dbStatusBtn == null) return;
        if (status != null && status.isConnected()) {
            dbStatusBtn.setText("●  MySQL: Connected (" + status.getDatabase() + ")");
            dbStatusBtn.updateColors(ThemeManager.SLATE_800, ThemeManager.SLATE_700, new Color(0x34, 0xD3, 0x99), ThemeManager.SLATE_700);
        } else {
            dbStatusBtn.setText("●  MySQL: Configure Connection");
            dbStatusBtn.updateColors(new Color(0x4C, 0x1D, 0x24), new Color(0x6B, 0x21, 0x2E), new Color(0xFB, 0x71, 0x85), new Color(0x9F, 0x12, 0x39));
        }
    }

    private JPanel buildRoleTabsStrip() {
        JPanel strip = new JPanel(new GridLayout(1, 4, 8, 0));
        strip.setBackground(ThemeManager.SLATE_100);
        strip.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.SLATE_200),
                new EmptyBorder(8, 10, 8, 10)
        ));

        tabAdminBtn = createRoleTab("⛨  ADMIN", PortalCategory.ADMIN);
        tabFacultyBtn = createRoleTab("✓  FACULTY", PortalCategory.FACULTY);
        tabStudentsBtn = createRoleTab("★  STUDENTS", PortalCategory.STUDENTS);
        tabCompaniesBtn = createRoleTab("❖  RECRUITERS", PortalCategory.COMPANIES);

        strip.add(tabAdminBtn);
        strip.add(tabFacultyBtn);
        strip.add(tabStudentsBtn);
        strip.add(tabCompaniesBtn);
        return strip;
    }

    private ActionButton createRoleTab(String text, PortalCategory role) {
        ActionButton btn = new ActionButton(text, ThemeManager.SLATE_100, Color.WHITE, ThemeManager.SLATE_600, null, 12);
        btn.setFont(ThemeManager.font(Font.BOLD, 12));
        btn.setMargin(new Insets(10, 12, 10, 12));
        btn.addActionListener(e -> openPortal(role, false));
        return btn;
    }

    private void updateRoleTabStyles() {
        styleTab(tabAdminBtn, activeRole == PortalCategory.ADMIN, ThemeManager.BLUE_ELECTRIC);
        styleTab(tabFacultyBtn, activeRole == PortalCategory.FACULTY, ThemeManager.EMERALD_PRIMARY);
        styleTab(tabStudentsBtn, activeRole == PortalCategory.STUDENTS, ThemeManager.CYAN_DARK);
        styleTab(tabCompaniesBtn, activeRole == PortalCategory.COMPANIES, ThemeManager.PURPLE_PRIMARY);
    }

    private void styleTab(ActionButton btn, boolean active, Color accentColor) {
        if (active) {
            btn.updateColors(Color.WHITE, Color.WHITE, accentColor, accentColor);
        } else {
            btn.updateColors(ThemeManager.SLATE_100, new Color(255, 255, 255, 200), ThemeManager.SLATE_600, null);
        }
    }

    private JPanel buildSubHeaderBar() {
        subHeaderBar = new JPanel(new BorderLayout());
        subHeaderBar.setBackground(ThemeManager.SLATE_50);
        subHeaderBar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.SLATE_200),
                new EmptyBorder(8, 22, 8, 22)
        ));

        subHeaderLabel = new JLabel("Student Access Gateway");
        subHeaderLabel.setFont(ThemeManager.font(Font.BOLD, 12));
        subHeaderLabel.setForeground(ThemeManager.SLATE_600);

        JPanel toggleGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toggleGroup.setOpaque(false);

        modeLoginBtn = new ActionButton("Sign In", Color.WHITE, Color.WHITE, ThemeManager.SLATE_900, ThemeManager.SLATE_300, 8);
        modeLoginBtn.setMargin(new Insets(5, 12, 5, 12));
        modeLoginBtn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        modeLoginBtn.addActionListener(e -> {
            registerMode = false;
            clearPasswordsAndErrors();
            updateSubHeaderAndCard();
        });

        modeRegBtn = new ActionButton("New Registration", ThemeManager.SLATE_200, ThemeManager.SLATE_300, ThemeManager.SLATE_600, null, 8);
        modeRegBtn.setMargin(new Insets(5, 12, 5, 12));
        modeRegBtn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        modeRegBtn.addActionListener(e -> {
            registerMode = true;
            clearPasswordsAndErrors();
            updateSubHeaderAndCard();
        });

        toggleGroup.add(modeLoginBtn);
        toggleGroup.add(modeRegBtn);

        subHeaderBar.add(subHeaderLabel, BorderLayout.WEST);
        subHeaderBar.add(toggleGroup, BorderLayout.EAST);
        return subHeaderBar;
    }

    private void clearPasswordsAndErrors() {
        autoLoginTimer.stop();
        suppressAutoLogin = true;
        try {
            lastAttemptedCredKey = null;
            for (PasswordInputField pf : loginPassFields.values()) pf.clearPassword();
            for (AlertBanner ab : loginErrorBanners.values()) ab.clear();
        } finally {
            suppressAutoLogin = false;
        }
    }

    private void updateSubHeaderAndCard() {
        boolean showSub = activeRole != PortalCategory.ADMIN;
        subHeaderBar.setVisible(showSub);
        if (showSub) {
            String lbl = switch (activeRole) {
                case STUDENTS -> "Student Access Gateway";
                case FACULTY -> "Department Coordinator Gateway";
                case COMPANIES -> "Corporate Partner Gateway";
                default -> "";
            };
            subHeaderLabel.setText(lbl);
            modeLoginBtn.updateColors(
                    !registerMode ? Color.WHITE : ThemeManager.SLATE_200,
                    !registerMode ? Color.WHITE : ThemeManager.SLATE_300,
                    !registerMode ? ThemeManager.SLATE_900 : ThemeManager.SLATE_600,
                    !registerMode ? ThemeManager.SLATE_300 : null
            );
            modeRegBtn.updateColors(
                    registerMode ? Color.WHITE : ThemeManager.SLATE_200,
                    registerMode ? Color.WHITE : ThemeManager.SLATE_300,
                    registerMode ? ThemeManager.SLATE_900 : ThemeManager.SLATE_600,
                    registerMode ? ThemeManager.SLATE_300 : null
            );
        }
        String key = activeRole.name() + "_" + (registerMode ? "REGISTER" : "LOGIN");
        bodyCardLayout.show(bodyCardContainer, key);
    }

    private void buildAllBodyCards() {
        bodyCardContainer.setBackground(Color.WHITE);
        bodyCardContainer.add(buildAdminLoginCard(), "ADMIN_LOGIN");
        bodyCardContainer.add(buildFacultyLoginCard(), "FACULTY_LOGIN");
        bodyCardContainer.add(buildFacultyRegisterCard(), "FACULTY_REGISTER");
        bodyCardContainer.add(buildStudentLoginCard(), "STUDENTS_LOGIN");
        bodyCardContainer.add(buildStudentRegisterCard(), "STUDENTS_REGISTER");
        bodyCardContainer.add(buildCompanyLoginCard(), "COMPANIES_LOGIN");
        bodyCardContainer.add(buildCompanyRegisterCard(), "COMPANIES_REGISTER");
    }

    private JPanel buildAdminLoginCard() {
        return buildStandardLoginView(
                PortalCategory.ADMIN,
                "INSTITUTIONAL LEADERSHIP GATEWAY",
                "Administrator & Executive Sign In",
                "Reserved for Principal Dr. K. Sagar, Chairman Mr. G.R. Ravinder Reddy, Vice Chairman Mr. R. Harishchandra Reddy, and Placement System Admin.",
                ADMIN_OPTIONS, 2,
                "Administrator Username", "Enter username",
                "Authenticate & Access Admin Portal",
                ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER,
                new Color(0xEF, 0xF6, 0xFF), new Color(0xBF, 0xDB, 0xFE),
                null
        );
    }

    private JPanel buildFacultyLoginCard() {
        return buildStandardLoginView(
                PortalCategory.FACULTY,
                "FACULTY & COORDINATOR GATEWAY",
                "Faculty Coordinator Sign In",
                "Department Coordinators verify student registrations, endorse skill matrix proficiencies, and review cohort readiness.",
                FACULTY_OPTIONS, 3,
                "Faculty ID or Registered Username", "Enter Faculty ID or username",
                "Authenticate & Open Faculty Portal",
                ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57),
                ThemeManager.EMERALD_SOFT, new Color(0xA7, 0xF3, 0xD0),
                "New faculty coordinator? Register your account here →"
        );
    }

    private JPanel buildStudentLoginCard() {
        return buildStandardLoginView(
                PortalCategory.STUDENTS,
                "ENGINEERING CANDIDATE PORTAL",
                "Registered Student Sign In",
                "Sign in using your registered college roll number and password. Only accounts approved by your Department Faculty Coordinator can access the portal.",
                List.of(), 1,
                "Student Roll Number", "Enter your registered roll number (e.g. 22R11A0501)",
                "Authenticate & Sign In",
                ThemeManager.CYAN_DARK, ThemeManager.BLUE_ELECTRIC,
                new Color(0xEC, 0xFE, 0xFF), new Color(0xA5, 0xF3, 0xFC),
                "New to GCET Placement Hub? Register as a candidate →"
        );
    }

    private JPanel buildCompanyLoginCard() {
        return buildStandardLoginView(
                PortalCategory.COMPANIES,
                "CORPORATE RECRUITMENT GATEWAY",
                "Recruiter & Company Sign In",
                "Partner companies post on-campus drives, filter candidates by technical skill scores, and manage hiring stages.",
                RECRUITER_OPTIONS, 1,
                "Corporate HR Email or Username", "Enter corporate email or username",
                "Authenticate & Open Recruiter Portal",
                ThemeManager.PURPLE_PRIMARY, new Color(0x7E, 0x22, 0xCE),
                ThemeManager.PURPLE_SOFT, new Color(0xE9, 0xD5, 0xFF),
                "New recruiting partner? Register your company here →"
        );
    }

    private JPanel buildStandardLoginView(
            PortalCategory category,
            String eyebrowText,
            String headingText,
            String descText,
            List<PresetOption> presets,
            int presetCols,
            String userFieldLabel,
            String userPlaceholder,
            String submitBtnText,
            Color accent,
            Color accentHover,
            Color badgeBg,
            Color badgeBorder,
            String switchToRegText
    ) {
        JPanel root = new JPanel();
        root.setBackground(Color.WHITE);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(24, 30, 28, 30));

        BadgeLabel eyebrow = new BadgeLabel(eyebrowText, badgeBg, accent, badgeBorder, 8);
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(headingText);
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.NAVY_MIDNIGHT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel("<html><body style='width:720px;'>" + descText + "</body></html>");
        desc.setFont(ThemeManager.font(Font.PLAIN, 12));
        desc.setForeground(ThemeManager.SLATE_500);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);

        root.add(eyebrow);
        root.add(Box.createVerticalStrut(8));
        root.add(title);
        root.add(Box.createVerticalStrut(4));
        root.add(desc);
        root.add(Box.createVerticalStrut(18));

        // Quick username presets (populate ONLY username, NEVER password)
        if (!presets.isEmpty()) {
            JPanel presetGrid = new JPanel(new GridLayout(0, presetCols, 10, 10));
            presetGrid.setOpaque(false);
            int rows = (int) Math.ceil((double) presets.size() / Math.max(1, presetCols));
            presetGrid.setMaximumSize(new Dimension(820, rows > 1 ? 180 : 95));
            presetGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

            List<PresetCardButton> group = new ArrayList<>();
            for (PresetOption opt : presets) {
                PresetCardButton card = new PresetCardButton(opt, accent, badgeBg);
                card.addActionListener(e -> {
                    autoLoginTimer.stop();
                    suppressAutoLogin = true;
                    try {
                        lastAttemptedCredKey = null;
                        setSelectedPreset(category, opt.username());
                        // Crucial rule: clear password and keep masked when choosing account
                        PasswordInputField pf = loginPassFields.get(category);
                        if (pf != null) {
                            pf.clearPassword();
                            pf.getPasswordField().requestFocusInWindow();
                        }
                        AlertBanner ab = loginErrorBanners.get(category);
                        if (ab != null) ab.clear();
                    } finally {
                        suppressAutoLogin = false;
                    }
                });
                group.add(card);
                presetGrid.add(card);
            }
            presetCardGroups.put(category, group);
            root.add(presetGrid);
            root.add(Box.createVerticalStrut(18));
        }

        // Centered form container
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setMaximumSize(new Dimension(520, 380));
        form.setAlignmentX(Component.CENTER_ALIGNMENT);

        AlertBanner errorBanner = new AlertBanner();
        errorBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginErrorBanners.put(category, errorBanner);
        form.add(errorBanner);
        form.add(Box.createVerticalStrut(8));

        JLabel userLbl = new JLabel(userFieldLabel);
        userLbl.setFont(ThemeManager.font(Font.BOLD, 12));
        userLbl.setForeground(ThemeManager.SLATE_700);
        userLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField userField = createStyledTextField(userPlaceholder);
        if (category == PortalCategory.STUDENTS) {
            userField.setFont(ThemeManager.monoFont(Font.PLAIN, 13));
        }
        userField.setMaximumSize(new Dimension(520, 38));
        userField.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginUserFields.put(category, userField);

        JLabel passLbl = new JLabel("Password");
        passLbl.setFont(ThemeManager.font(Font.BOLD, 12));
        passLbl.setForeground(ThemeManager.SLATE_700);
        passLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        PasswordInputField passField = new PasswordInputField("Enter password");
        passField.setMaximumSize(new Dimension(520, 38));
        passField.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginPassFields.put(category, passField);

        ActionButton submitBtn = new ActionButton(submitBtnText, accent, accentHover, Color.WHITE, null, 12);
        submitBtn.setMaximumSize(new Dimension(520, 40));
        submitBtn.setPreferredSize(new Dimension(520, 40));
        submitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginSubmitButtons.put(category, submitBtn);
        loginDefaultBtnTexts.put(category, submitBtnText);

        DocumentListener autoLoginListener = new DocumentListener() {
            private void onEdit() {
                if (suppressAutoLogin || registerMode || activeRole != category || authInFlight.get()) {
                    return;
                }
                AlertBanner b = loginErrorBanners.get(category);
                if (b != null && b.isVisible()) b.clear();
                scheduleAutoLoginIfReady(category);
            }
            @Override public void insertUpdate(DocumentEvent e) { onEdit(); }
            @Override public void removeUpdate(DocumentEvent e) { onEdit(); }
            @Override public void changedUpdate(DocumentEvent e) { onEdit(); }
        };
        passField.getPasswordField().getDocument().addDocumentListener(autoLoginListener);
        userField.getDocument().addDocumentListener(new DocumentListener() {
            private void onUserEdit() {
                if (suppressAutoLogin || registerMode || activeRole != category || authInFlight.get()) {
                    return;
                }
                if (!passField.getPasswordText().isEmpty()) {
                    scheduleAutoLoginIfReady(category);
                } else {
                    autoLoginTimer.stop();
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { onUserEdit(); }
            @Override public void removeUpdate(DocumentEvent e) { onUserEdit(); }
            @Override public void changedUpdate(DocumentEvent e) { onUserEdit(); }
        });

        Runnable doManualLogin = () -> {
            autoLoginTimer.stop();
            lastAttemptedCredKey = null;
            submitLogin(category, submitBtnText);
        };
        submitBtn.addActionListener(e -> doManualLogin.run());
        passField.getPasswordField().addActionListener(e -> doManualLogin.run());
        userField.addActionListener(e -> passField.getPasswordField().requestFocusInWindow());

        form.add(userLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(userField);
        form.add(Box.createVerticalStrut(12));
        form.add(passLbl);
        form.add(Box.createVerticalStrut(4));
        form.add(passField);
        form.add(Box.createVerticalStrut(16));
        form.add(submitBtn);

        JPanel linksRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        linksRow.setOpaque(false);
        linksRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton forgotBtn = new JButton("Forgot / Set Password for an Account I Own");
        forgotBtn.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        forgotBtn.setForeground(ThemeManager.SLATE_600);
        forgotBtn.setBorderPainted(false);
        forgotBtn.setContentAreaFilled(false);
        forgotBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        forgotBtn.setMargin(new Insets(0, 0, 0, 14));
        forgotBtn.addActionListener(e -> showPasswordRecoveryDialog(category));
        linksRow.add(forgotBtn);

        if (switchToRegText != null) {
            JButton switchBtn = new JButton(switchToRegText);
            switchBtn.setFont(ThemeManager.font(Font.BOLD, 12));
            switchBtn.setForeground(accent);
            switchBtn.setBorderPainted(false);
            switchBtn.setContentAreaFilled(false);
            switchBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            switchBtn.addActionListener(e -> {
                registerMode = true;
                clearPasswordsAndErrors();
                updateSubHeaderAndCard();
            });
            linksRow.add(switchBtn);
        }

        form.add(Box.createVerticalStrut(10));
        form.add(linksRow);

        JPanel centerHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        centerHolder.setOpaque(false);
        centerHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.setPreferredSize(new Dimension(520, 355));
        centerHolder.add(form);

        root.add(centerHolder);
        return root;
    }

    private void showPasswordRecoveryDialog(PortalCategory category) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dlg = new JDialog(owner, "Recover / Set Account Password — MySQL Verified", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(520, 500);
        dlg.setLocationRelativeTo(owner);
        dlg.setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setBackground(Color.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));

        JLabel title = new JLabel("MySQL-Verified Account Password Update");
        title.setFont(ThemeManager.font(Font.BOLD, 16));
        title.setForeground(ThemeManager.NAVY_MIDNIGHT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("<html><body style='width:450px;'>Safely set a new BCrypt password for an existing account you own in <code>gcet_placement_hub</code>. Requires BOTH your registered email match and your local MySQL Server password. Approval flags are never altered.</body></html>");
        subtitle.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        subtitle.setForeground(ThemeManager.SLATE_600);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        AlertBanner dlgAlert = new AlertBanner();
        dlgAlert.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField currentUser = loginUserFields.get(category);
        JTextField idField = createStyledTextField("Username, Roll Number, or Faculty ID");
        if (currentUser != null && !currentUser.getText().isBlank()) {
            idField.setText(currentUser.getText().trim());
        }
        JTextField emailField = createStyledTextField("Registered email stored in users.email");
        PasswordInputField dbPassField = new PasswordInputField("Local MySQL password (for root / DB user)");
        PasswordInputField newPassField = new PasswordInputField("New account password (min. 6 chars)");
        PasswordInputField confirmPassField = new PasswordInputField("Confirm new account password");

        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(10));
        content.add(dlgAlert);
        content.add(Box.createVerticalStrut(8));
        content.add(labeledField("Account Username / Roll Number *", idField));
        content.add(Box.createVerticalStrut(8));
        content.add(labeledField("Registered Account Email *", emailField));
        content.add(Box.createVerticalStrut(8));
        content.add(labeledField("Local MySQL Database Password (Owner Verification) *", dbPassField));
        content.add(Box.createVerticalStrut(8));
        content.add(labeledField("New Account Password *", newPassField));
        content.add(Box.createVerticalStrut(8));
        content.add(labeledField("Confirm New Password *", confirmPassField));

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        footer.setBackground(ThemeManager.SLATE_50);
        footer.setBorder(new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_200));

        ActionButton cancelBtn = new ActionButton("Cancel", Color.WHITE, ThemeManager.SLATE_100, ThemeManager.SLATE_700, ThemeManager.SLATE_300, 10);
        cancelBtn.addActionListener(e -> dlg.dispose());

        ActionButton saveBtn = new ActionButton("Verify & Update Password", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        saveBtn.addActionListener(e -> {
            dlgAlert.clear();
            saveBtn.setEnabled(false);
            saveBtn.setText("Verifying & Hashing...");
            authController.recoverPasswordAsync(
                    idField.getText(),
                    emailField.getText(),
                    dbPassField.getPasswordText(),
                    newPassField.getPasswordText(),
                    confirmPassField.getPasswordText(),
                    successMsg -> {
                        saveBtn.setEnabled(true);
                        saveBtn.setText("Verify & Update Password");
                        dlg.dispose();
                        JOptionPane.showMessageDialog(owner, successMsg, "Password Updated", JOptionPane.INFORMATION_MESSAGE);
                        PasswordInputField pf = loginPassFields.get(category);
                        if (pf != null) {
                            pf.clearPassword();
                            pf.getPasswordField().requestFocusInWindow();
                        }
                    },
                    err -> {
                        saveBtn.setEnabled(true);
                        saveBtn.setText("Verify & Update Password");
                        dlgAlert.showError(err);
                    }
            );
        });

        footer.add(cancelBtn);
        footer.add(saveBtn);

        dlg.add(new JScrollPane(content), BorderLayout.CENTER);
        dlg.add(footer, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void setSelectedPreset(PortalCategory category, String username) {
        JTextField uf = loginUserFields.get(category);
        if (uf != null) uf.setText(username);
        List<PresetCardButton> cards = presetCardGroups.get(category);
        if (cards != null) {
            for (PresetCardButton card : cards) {
                card.setSelected(card.option.username().equals(username));
            }
        }
    }

    private void scheduleAutoLoginIfReady(PortalCategory category) {
        if (suppressAutoLogin || registerMode || activeRole != category || authInFlight.get()) {
            autoLoginTimer.stop();
            return;
        }
        JTextField uf = loginUserFields.get(category);
        PasswordInputField pf = loginPassFields.get(category);
        String username = uf != null ? uf.getText().trim() : "";
        String password = pf != null ? pf.getPasswordText() : "";
        if (username.isEmpty() || password.isEmpty()) {
            autoLoginTimer.stop();
            return;
        }
        String normalizedUser = category == PortalCategory.STUDENTS ? username.toUpperCase(Locale.ROOT) : username;
        String credKey = category.name() + "|" + normalizedUser + "|" + password;
        if (credKey.equals(lastAttemptedCredKey)) {
            autoLoginTimer.stop();
            return;
        }
        autoLoginTimer.restart();
    }

    private void triggerDebouncedAutoLogin() {
        if (suppressAutoLogin || registerMode || authInFlight.get()) return;
        String defaultBtnText = loginDefaultBtnTexts.getOrDefault(activeRole, "Sign In");
        JTextField uf = loginUserFields.get(activeRole);
        PasswordInputField pf = loginPassFields.get(activeRole);
        String username = uf != null ? uf.getText().trim() : "";
        String password = pf != null ? pf.getPasswordText() : "";
        if (username.isEmpty() || password.isEmpty()) return;
        String normalizedUser = activeRole == PortalCategory.STUDENTS ? username.toUpperCase(Locale.ROOT) : username;
        String credKey = activeRole.name() + "|" + normalizedUser + "|" + password;
        if (credKey.equals(lastAttemptedCredKey)) return;
        submitLogin(activeRole, defaultBtnText);
    }

    private void submitLogin(PortalCategory category, String defaultBtnText) {
        autoLoginTimer.stop();
        ActionButton btn = loginSubmitButtons.get(category);
        if (btn == null || !btn.isEnabled()) return;
        if (!authInFlight.compareAndSet(false, true)) return;

        AlertBanner banner = loginErrorBanners.get(category);
        JTextField uf = loginUserFields.get(category);
        PasswordInputField pf = loginPassFields.get(category);
        if (banner != null) banner.clear();

        String rawUser = uf != null ? uf.getText() : "";
        String username = rawUser.trim();
        if (category == PortalCategory.STUDENTS) {
            username = username.toUpperCase(Locale.ROOT);
            if (uf != null && !username.equals(rawUser)) {
                suppressAutoLogin = true;
                try {
                    uf.setText(username);
                } finally {
                    suppressAutoLogin = false;
                }
            }
        }
        String password = pf != null ? pf.getPasswordText() : "";
        final String credKey = category.name() + "|" + username + "|" + password;
        lastAttemptedCredKey = credKey;

        btn.setEnabled(false);
        btn.setText("Verifying Credentials...");

        authController.loginAsync(
                username,
                password,
                category,
                authResult -> {
                    authInFlight.set(false);
                    lastAttemptedCredKey = null;
                    btn.setEnabled(true);
                    btn.setText(defaultBtnText);
                    if (pf != null) {
                        suppressAutoLogin = true;
                        try {
                            pf.clearPassword();
                        } finally {
                            suppressAutoLogin = false;
                        }
                    }
                    onLoginSuccess.accept(authResult);
                },
                err -> {
                    authInFlight.set(false);
                    lastAttemptedCredKey = credKey;
                    btn.setEnabled(true);
                    btn.setText(defaultBtnText);
                    if (banner != null) banner.showError(err);
                }
        );
    }

    // -------------------------------------------------------------------------
    // STUDENT REGISTRATION CARD
    // -------------------------------------------------------------------------
    private JPanel buildStudentRegisterCard() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new EmptyBorder(24, 30, 28, 30));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        BadgeLabel eye = new BadgeLabel("CANDIDATE ONBOARDING", new Color(0xEC, 0xFE, 0xFF), ThemeManager.CYAN_DARK, new Color(0xA5, 0xF3, 0xFC), 8);
        JLabel h = new JLabel("New Student Registration");
        h.setFont(ThemeManager.font(Font.BOLD, 20));
        h.setForeground(ThemeManager.NAVY_MIDNIGHT);
        JLabel p = new JLabel("<html><body style='width:720px;'>Register once with your official college credentials. In accordance with GCET policy, your registration routes to your Department Faculty Coordinator for review.</body></html>");
        p.setFont(ThemeManager.font(Font.PLAIN, 12));
        p.setForeground(ThemeManager.SLATE_500);
        top.add(eye);
        top.add(Box.createVerticalStrut(6));
        top.add(h);
        top.add(Box.createVerticalStrut(4));
        top.add(p);
        top.add(Box.createVerticalStrut(14));
        wrapper.add(top, BorderLayout.NORTH);

        CardLayout regStateLayout = new CardLayout();
        JPanel regStatePanel = new JPanel(regStateLayout);
        regStatePanel.setOpaque(false);

        stuRegFormPanel = new JPanel();
        stuRegFormPanel.setOpaque(false);
        stuRegFormPanel.setLayout(new BoxLayout(stuRegFormPanel, BoxLayout.Y_AXIS));

        stuRegErrorBanner = new AlertBanner();
        stuRegFormPanel.add(stuRegErrorBanner);
        stuRegFormPanel.add(Box.createVerticalStrut(8));

        stuNameField = createStyledTextField("e.g. Rahul Sharma");
        stuRollField = createStyledTextField("e.g. 22R11A0501");
        stuRollField.setFont(ThemeManager.monoFont(Font.PLAIN, 13));
        stuEmailField = createStyledTextField("e.g. 22r11a0501@gcet.edu.in");
        stuPhoneField = createStyledTextField("+91 98765 43210");
        stuBranchCombo = new JComboBox<>(DeptNormalizer.GCET_BRANCHES.toArray(new String[0]));
        stuYearCombo = new JComboBox<>(new String[]{"4th Year (B.Tech)", "3rd Year (B.Tech)", "2nd Year (B.Tech)", "1st Year (B.Tech)"});
        stuCgpaField = createStyledTextField("8.40");
        stuCgpaField.setText("8.40");
        stuPassField = new PasswordInputField("Create password (min. 6 chars)");
        stuConfirmPassField = new PasswordInputField("Re-enter password");

        stuRegFormPanel.add(twoColRow("Full Student Name *", stuNameField, "Student Roll Number *", stuRollField));
        stuRegFormPanel.add(Box.createVerticalStrut(10));
        stuRegFormPanel.add(twoColRow("College / Contact Email *", stuEmailField, "Contact Phone Number", stuPhoneField));
        stuRegFormPanel.add(Box.createVerticalStrut(10));
        stuRegFormPanel.add(threeColRow("Branch / Department *", stuBranchCombo, "Academic Year *", stuYearCombo, "Current CGPA *", stuCgpaField));
        stuRegFormPanel.add(Box.createVerticalStrut(12));

        // Interactive Skill Matrix chips
        JLabel skillLbl = new JLabel("Technical Skills & Competencies (Skill Matrix)");
        skillLbl.setFont(ThemeManager.font(Font.BOLD, 12));
        skillLbl.setForeground(ThemeManager.SLATE_700);
        skillLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel skillsWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        skillsWrap.setOpaque(false);
        skillsWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String skill : AVAILABLE_SKILLS) {
            boolean initiallySelected = selectedStuSkills.contains(skill);
            JToggleButton tb = new JToggleButton((initiallySelected ? "✓ " : "+ ") + skill, initiallySelected);
            tb.setFont(ThemeManager.font(Font.BOLD, 11.5f));
            tb.setFocusPainted(false);
            tb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            styleSkillChip(tb);
            tb.addActionListener(e -> {
                if (tb.isSelected()) {
                    selectedStuSkills.add(skill);
                    tb.setText("✓ " + skill);
                } else {
                    selectedStuSkills.remove(skill);
                    tb.setText("+ " + skill);
                }
                styleSkillChip(tb);
            });
            skillToggleButtons.add(tb);
            skillsWrap.add(tb);
        }
        stuRegFormPanel.add(skillLbl);
        stuRegFormPanel.add(skillsWrap);
        stuRegFormPanel.add(Box.createVerticalStrut(10));

        stuRegFormPanel.add(twoColRow("Account Password (min. 6 chars) *", stuPassField, "Confirm Password *", stuConfirmPassField));
        stuRegFormPanel.add(Box.createVerticalStrut(16));

        stuRegSubmitBtn = new ActionButton(
                "Submit Student Registration for Faculty Approval",
                ThemeManager.CYAN_DARK,
                ThemeManager.BLUE_ELECTRIC,
                Color.WHITE,
                null,
                12
        );
        stuRegSubmitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        stuRegSubmitBtn.setPreferredSize(new Dimension(600, 40));
        stuRegSubmitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        stuRegSubmitBtn.addActionListener(e -> submitStudentRegistration(regStateLayout, regStatePanel));
        stuRegFormPanel.add(stuRegSubmitBtn);

        // Receipt Panel
        stuReceiptBodyLabel = new JLabel();
        stuRegReceiptPanel = createReceiptPanel(
                "Registration Successfully Submitted!",
                stuReceiptBodyLabel,
                "Your application is now visible in the Faculty Approval Center. Only an authorized Department Faculty Coordinator can verify your details and approve your login. Administrators cannot approve student accounts.",
                "Go to Student Sign In",
                "Register Another Student",
                ThemeManager.CYAN_DARK,
                () -> {
                    regStateLayout.show(regStatePanel, "FORM");
                    registerMode = false;
                    updateSubHeaderAndCard();
                    JTextField uf = loginUserFields.get(PortalCategory.STUDENTS);
                    if (uf != null) uf.setText(lastRegisteredRoll);
                    PasswordInputField pf = loginPassFields.get(PortalCategory.STUDENTS);
                    if (pf != null) pf.clearPassword();
                },
                () -> {
                    stuNameField.setText("");
                    stuRollField.setText("");
                    stuEmailField.setText("");
                    stuPhoneField.setText("");
                    stuPassField.clearPassword();
                    stuConfirmPassField.clearPassword();
                    stuRegErrorBanner.clear();
                    regStateLayout.show(regStatePanel, "FORM");
                }
        );

        regStatePanel.add(stuRegFormPanel, "FORM");
        regStatePanel.add(stuRegReceiptPanel, "RECEIPT");
        wrapper.add(regStatePanel, BorderLayout.CENTER);
        return wrapper;
    }

    private void styleSkillChip(JToggleButton tb) {
        if (tb.isSelected()) {
            tb.setBackground(ThemeManager.CYAN_DARK);
            tb.setForeground(Color.WHITE);
        } else {
            tb.setBackground(ThemeManager.SLATE_100);
            tb.setForeground(ThemeManager.SLATE_600);
        }
    }

    private void submitStudentRegistration(CardLayout layout, JPanel container) {
        stuRegErrorBanner.clear();
        double cgpa;
        try {
            cgpa = Double.parseDouble(stuCgpaField.getText().trim());
        } catch (NumberFormatException ex) {
            stuRegErrorBanner.showError("CGPA must be a valid number between 0.00 and 10.00.");
            return;
        }
        int academicYear = switch (stuYearCombo.getSelectedIndex()) {
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 1;
            default -> 4;
        };
        stuRegSubmitBtn.setEnabled(false);
        stuRegSubmitBtn.setText("Submitting Registration...");

        authController.registerStudentAsync(
                stuNameField.getText(),
                stuRollField.getText(),
                stuEmailField.getText(),
                stuPhoneField.getText(),
                String.valueOf(stuBranchCombo.getSelectedItem()),
                academicYear,
                2026,
                cgpa,
                stuPassField.getPasswordText(),
                stuConfirmPassField.getPasswordText(),
                new ArrayList<>(selectedStuSkills),
                receipt -> {
                    stuRegSubmitBtn.setEnabled(true);
                    stuRegSubmitBtn.setText("Submit Student Registration for Faculty Approval");
                    lastRegisteredRoll = receipt.getLoginHint();
                    stuReceiptBodyLabel.setText("<html><center>Account for <b>" + receipt.getDisplayName()
                            + "</b> (" + receipt.getLoginHint() + ") has been stored in MySQL with status <b>PENDING_APPROVAL</b>.</center></html>");
                    layout.show(container, "RECEIPT");
                },
                err -> {
                    stuRegSubmitBtn.setEnabled(true);
                    stuRegSubmitBtn.setText("Submit Student Registration for Faculty Approval");
                    stuRegErrorBanner.showError(err);
                }
        );
    }

    // -------------------------------------------------------------------------
    // FACULTY REGISTRATION CARD
    // -------------------------------------------------------------------------
    private JPanel buildFacultyRegisterCard() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new EmptyBorder(24, 30, 28, 30));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        BadgeLabel eye = new BadgeLabel("FACULTY REGISTRATION", ThemeManager.EMERALD_SOFT, ThemeManager.EMERALD_PRIMARY, new Color(0xA7, 0xF3, 0xD0), 8);
        JLabel h = new JLabel("Register as New Faculty Coordinator");
        h.setFont(ThemeManager.font(Font.BOLD, 20));
        h.setForeground(ThemeManager.NAVY_MIDNIGHT);
        JLabel p = new JLabel("Faculty accounts require official clearance from College Administration before activation.");
        p.setFont(ThemeManager.font(Font.PLAIN, 12));
        p.setForeground(ThemeManager.SLATE_500);
        top.add(eye);
        top.add(Box.createVerticalStrut(6));
        top.add(h);
        top.add(Box.createVerticalStrut(4));
        top.add(p);
        top.add(Box.createVerticalStrut(14));
        wrapper.add(top, BorderLayout.NORTH);

        CardLayout regLayout = new CardLayout();
        JPanel regContainer = new JPanel(regLayout);
        regContainer.setOpaque(false);

        facRegFormPanel = new JPanel();
        facRegFormPanel.setOpaque(false);
        facRegFormPanel.setLayout(new BoxLayout(facRegFormPanel, BoxLayout.Y_AXIS));

        facRegErrorBanner = new AlertBanner();
        facRegFormPanel.add(facRegErrorBanner);
        facRegFormPanel.add(Box.createVerticalStrut(8));

        facNameField = createStyledTextField("e.g. Dr. K. Sreenivasa Rao");
        facEmpIdField = createStyledTextField("e.g. EMP-CSE-204");
        facEmailField = createStyledTextField("e.g. sreenivasa.cse@gcet.edu.in");
        facPhoneField = createStyledTextField("+91 98480 12345");
        List<String> depts = new ArrayList<>(DeptNormalizer.GCET_BRANCHES);
        depts.add("Science & Humanities");
        facDeptCombo = new JComboBox<>(depts.toArray(new String[0]));
        facDesigCombo = new JComboBox<>(new String[]{
                "Assistant Professor", "Associate Professor", "Professor", "Department Coordinator", "HOD"
        });
        facPassField = new PasswordInputField("Create password (min. 6 chars)");
        facConfirmPassField = new PasswordInputField("Re-enter password");

        facRegFormPanel.add(twoColRow("Full Official Name *", facNameField, "Faculty Employee ID *", facEmpIdField));
        facRegFormPanel.add(Box.createVerticalStrut(10));
        facRegFormPanel.add(twoColRow("Official College Email *", facEmailField, "Contact Phone", facPhoneField));
        facRegFormPanel.add(Box.createVerticalStrut(10));
        facRegFormPanel.add(twoColRow("Academic Department *", facDeptCombo, "Designation *", facDesigCombo));
        facRegFormPanel.add(Box.createVerticalStrut(10));
        facRegFormPanel.add(twoColRow("Account Password (min. 6 chars) *", facPassField, "Confirm Password *", facConfirmPassField));
        facRegFormPanel.add(Box.createVerticalStrut(16));

        facRegSubmitBtn = new ActionButton(
                "Submit Faculty Registration for Admin Approval",
                ThemeManager.EMERALD_PRIMARY,
                new Color(0x04, 0x78, 0x57),
                Color.WHITE,
                null,
                12
        );
        facRegSubmitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        facRegSubmitBtn.setPreferredSize(new Dimension(600, 40));
        facRegSubmitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        facRegSubmitBtn.addActionListener(e -> {
            facRegErrorBanner.clear();
            facRegSubmitBtn.setEnabled(false);
            facRegSubmitBtn.setText("Submitting Faculty Registration...");
            authController.registerFacultyAsync(
                    facNameField.getText(),
                    facEmpIdField.getText(),
                    facEmailField.getText(),
                    String.valueOf(facDeptCombo.getSelectedItem()),
                    String.valueOf(facDesigCombo.getSelectedItem()),
                    facPhoneField.getText(),
                    facPassField.getPasswordText(),
                    facConfirmPassField.getPasswordText(),
                    receipt -> {
                        facRegSubmitBtn.setEnabled(true);
                        facRegSubmitBtn.setText("Submit Faculty Registration for Admin Approval");
                        lastRegisteredFacId = receipt.getLoginHint();
                        facReceiptBodyLabel.setText("<html><center>Account for <b>" + receipt.getDisplayName()
                                + "</b> (" + receipt.getLoginHint() + ") has been registered with status <b>PENDING_APPROVAL</b>.</center></html>");
                        regLayout.show(regContainer, "RECEIPT");
                    },
                    err -> {
                        facRegSubmitBtn.setEnabled(true);
                        facRegSubmitBtn.setText("Submit Faculty Registration for Admin Approval");
                        facRegErrorBanner.showError(err);
                    }
            );
        });
        facRegFormPanel.add(facRegSubmitBtn);

        facReceiptBodyLabel = new JLabel();
        facRegReceiptPanel = createReceiptPanel(
                "Faculty Application Submitted",
                facReceiptBodyLabel,
                "Institutional Rule: New faculty registrations route exclusively to the Admin Approval Center. Only authorized administrators (Principal / College Administration) may approve your account before you can log in.",
                "Go to Faculty Sign In",
                "Register Another Faculty",
                ThemeManager.EMERALD_PRIMARY,
                () -> {
                    regLayout.show(regContainer, "FORM");
                    registerMode = false;
                    updateSubHeaderAndCard();
                    JTextField uf = loginUserFields.get(PortalCategory.FACULTY);
                    if (uf != null) uf.setText(lastRegisteredFacId);
                    PasswordInputField pf = loginPassFields.get(PortalCategory.FACULTY);
                    if (pf != null) pf.clearPassword();
                },
                () -> {
                    facNameField.setText("");
                    facEmpIdField.setText("");
                    facEmailField.setText("");
                    facPhoneField.setText("");
                    facPassField.clearPassword();
                    facConfirmPassField.clearPassword();
                    facRegErrorBanner.clear();
                    regLayout.show(regContainer, "FORM");
                }
        );

        regContainer.add(facRegFormPanel, "FORM");
        regContainer.add(facRegReceiptPanel, "RECEIPT");
        wrapper.add(regContainer, BorderLayout.CENTER);
        return wrapper;
    }

    // -------------------------------------------------------------------------
    // COMPANY REGISTRATION CARD
    // -------------------------------------------------------------------------
    private JPanel buildCompanyRegisterCard() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.setBorder(new EmptyBorder(24, 30, 28, 30));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        BadgeLabel eye = new BadgeLabel("CORPORATE ONBOARDING", ThemeManager.PURPLE_SOFT, ThemeManager.PURPLE_PRIMARY, new Color(0xE9, 0xD5, 0xFF), 8);
        JLabel h = new JLabel("Register as Corporate Recruiting Partner");
        h.setFont(ThemeManager.font(Font.BOLD, 20));
        h.setForeground(ThemeManager.NAVY_MIDNIGHT);
        JLabel p = new JLabel("All recruiting organizations are reviewed and approved exclusively by College Administration before campus drives can be announced.");
        p.setFont(ThemeManager.font(Font.PLAIN, 12));
        p.setForeground(ThemeManager.SLATE_500);
        top.add(eye);
        top.add(Box.createVerticalStrut(6));
        top.add(h);
        top.add(Box.createVerticalStrut(4));
        top.add(p);
        top.add(Box.createVerticalStrut(14));
        wrapper.add(top, BorderLayout.NORTH);

        CardLayout regLayout = new CardLayout();
        JPanel regContainer = new JPanel(regLayout);
        regContainer.setOpaque(false);

        compRegFormPanel = new JPanel();
        compRegFormPanel.setOpaque(false);
        compRegFormPanel.setLayout(new BoxLayout(compRegFormPanel, BoxLayout.Y_AXIS));

        compRegErrorBanner = new AlertBanner();
        compRegFormPanel.add(compRegErrorBanner);
        compRegFormPanel.add(Box.createVerticalStrut(8));

        compNameField = createStyledTextField("e.g. Infosys Ltd.");
        compIndustryCombo = new JComboBox<>(new String[]{
                "IT / Software Services", "Core Engineering", "Cloud & AI Solutions",
                "Product Engineering", "Financial Services / FinTech", "Automotive & Mobility"
        });
        compWebsiteField = createStyledTextField("https://company.com");
        compLocationField = createStyledTextField("e.g. HITEC City, Hyderabad");
        compLocationField.setText("Hyderabad, Telangana");
        compHrNameField = createStyledTextField("e.g. Priya Sharma");
        compHrEmailField = createStyledTextField("campus@company.com");
        compHrPhoneField = createStyledTextField("+91 98480 00000");
        compTierCombo = new JComboBox<>(new String[]{
                "Standard IT", "Tier 1 (Dream)", "Tier 2 (Super Dream)", "Core"
        });
        compPassField = new PasswordInputField("Create password (min. 6 chars)");
        compConfirmPassField = new PasswordInputField("Re-enter password");

        compRegFormPanel.add(twoColRow("Company Name *", compNameField, "Industry Domain *", compIndustryCombo));
        compRegFormPanel.add(Box.createVerticalStrut(10));
        compRegFormPanel.add(twoColRow("Company Website", compWebsiteField, "Location / HQ", compLocationField));
        compRegFormPanel.add(Box.createVerticalStrut(10));
        compRegFormPanel.add(threeColRow("HR / Recruiter Name *", compHrNameField, "Corporate Email *", compHrEmailField, "Recruitment Tier", compTierCombo));
        compRegFormPanel.add(Box.createVerticalStrut(10));
        compRegFormPanel.add(twoColRow("HR Contact Phone", compHrPhoneField, "Account Password (min. 6 chars) *", compPassField));
        compRegFormPanel.add(Box.createVerticalStrut(10));
        compRegFormPanel.add(twoColRow("Confirm Password *", compConfirmPassField, "Verification Gate", new JLabel("Admin Approval Required")));
        compRegFormPanel.add(Box.createVerticalStrut(16));

        compRegSubmitBtn = new ActionButton(
                "Submit Company Registration for Admin Approval",
                ThemeManager.PURPLE_PRIMARY,
                new Color(0x7E, 0x22, 0xCE),
                Color.WHITE,
                null,
                12
        );
        compRegSubmitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        compRegSubmitBtn.setPreferredSize(new Dimension(600, 40));
        compRegSubmitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        compRegSubmitBtn.addActionListener(e -> {
            compRegErrorBanner.clear();
            compRegSubmitBtn.setEnabled(false);
            compRegSubmitBtn.setText("Submitting Company Registration...");
            authController.registerCompanyAsync(
                    compNameField.getText(),
                    String.valueOf(compIndustryCombo.getSelectedItem()),
                    compWebsiteField.getText(),
                    compLocationField.getText(),
                    compHrNameField.getText(),
                    compHrEmailField.getText(),
                    compHrPhoneField.getText(),
                    String.valueOf(compTierCombo.getSelectedItem()),
                    compPassField.getPasswordText(),
                    compConfirmPassField.getPasswordText(),
                    receipt -> {
                        compRegSubmitBtn.setEnabled(true);
                        compRegSubmitBtn.setText("Submit Company Registration for Admin Approval");
                        lastRegisteredCompanyEmail = receipt.getLoginHint();
                        compReceiptBodyLabel.setText("<html><center>Profile for <b>" + receipt.getDisplayName()
                                + "</b> (" + receipt.getLoginHint() + ") is stored in MySQL with status <b>PENDING_APPROVAL</b>.</center></html>");
                        regLayout.show(regContainer, "RECEIPT");
                    },
                    err -> {
                        compRegSubmitBtn.setEnabled(true);
                        compRegSubmitBtn.setText("Submit Company Registration for Admin Approval");
                        compRegErrorBanner.showError(err);
                    }
            );
        });
        compRegFormPanel.add(compRegSubmitBtn);

        compReceiptBodyLabel = new JLabel();
        compRegReceiptPanel = createReceiptPanel(
                "Company Registration Submitted",
                compReceiptBodyLabel,
                "Institutional Rule: New corporate partners route exclusively to the Admin Approval Center. Only authorized administrators (Principal / College Administration) may approve company accounts.",
                "Go to Recruiter Sign In",
                "Register Another Company",
                ThemeManager.PURPLE_PRIMARY,
                () -> {
                    regLayout.show(regContainer, "FORM");
                    registerMode = false;
                    updateSubHeaderAndCard();
                    JTextField uf = loginUserFields.get(PortalCategory.COMPANIES);
                    if (uf != null) uf.setText(lastRegisteredCompanyEmail);
                    PasswordInputField pf = loginPassFields.get(PortalCategory.COMPANIES);
                    if (pf != null) pf.clearPassword();
                },
                () -> {
                    compNameField.setText("");
                    compWebsiteField.setText("");
                    compHrNameField.setText("");
                    compHrEmailField.setText("");
                    compHrPhoneField.setText("");
                    compPassField.clearPassword();
                    compConfirmPassField.clearPassword();
                    compRegErrorBanner.clear();
                    regLayout.show(regContainer, "FORM");
                }
        );

        regContainer.add(compRegFormPanel, "FORM");
        regContainer.add(compRegReceiptPanel, "RECEIPT");
        wrapper.add(regContainer, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createReceiptPanel(
            String titleText,
            JLabel bodyLbl,
            String noteText,
            String signInBtnText,
            String anotherBtnText,
            Color accent,
            Runnable onGoLogin,
            Runnable onRegisterAnother
    ) {
        RoundedCard card = new RoundedCard(18, new Color(0xF8, 0xFA, 0xFC), ThemeManager.SLATE_300);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(26, 28, 26, 28));

        JPanel badge = createIconBadge("✓", accent, Color.WHITE, 48, 22);
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel(titleText);
        title.setFont(ThemeManager.font(Font.BOLD, 17));
        title.setForeground(ThemeManager.NAVY_MIDNIGHT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        bodyLbl.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        bodyLbl.setForeground(ThemeManager.SLATE_700);
        bodyLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        RoundedCard noteBox = new RoundedCard(12, Color.WHITE, ThemeManager.SLATE_200);
        noteBox.setLayout(new BorderLayout());
        noteBox.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel noteLbl = new JLabel("<html><body style='width:460px;'>" + noteText + "</body></html>");
        noteLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        noteLbl.setForeground(ThemeManager.SLATE_600);
        noteBox.add(noteLbl, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnRow.setOpaque(false);
        ActionButton b1 = new ActionButton(signInBtnText, accent, accent.darker(), Color.WHITE, null, 12);
        b1.addActionListener(e -> onGoLogin.run());
        ActionButton b2 = new ActionButton(anotherBtnText, Color.WHITE, ThemeManager.SLATE_100, ThemeManager.SLATE_700, ThemeManager.SLATE_300, 12);
        b2.addActionListener(e -> onRegisterAnother.run());
        btnRow.add(b1);
        btnRow.add(b2);

        card.add(badge);
        card.add(Box.createVerticalStrut(10));
        card.add(title);
        card.add(Box.createVerticalStrut(8));
        card.add(bodyLbl);
        card.add(Box.createVerticalStrut(14));
        card.add(noteBox);
        card.add(Box.createVerticalStrut(18));
        card.add(btnRow);

        JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER));
        holder.setOpaque(false);
        holder.add(card);
        return holder;
    }

    private JPanel twoColRow(String l1, Component c1, String l2, Component c2) {
        JPanel row = new JPanel(new GridLayout(1, 2, 14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        row.add(labeledField(l1, c1));
        row.add(labeledField(l2, c2));
        return row;
    }

    private JPanel threeColRow(String l1, Component c1, String l2, Component c2, String l3, Component c3) {
        JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        row.add(labeledField(l1, c1));
        row.add(labeledField(l2, c2));
        row.add(labeledField(l3, c3));
        return row;
    }

    private JPanel labeledField(String label, Component comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        lbl.setForeground(ThemeManager.SLATE_700);
        if (comp instanceof JComboBox<?> combo) {
            combo.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
            combo.setPreferredSize(new Dimension(180, 38));
        }
        p.add(lbl, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private static class PresetCardButton extends JButton {
        private final PresetOption option;
        private final Color accent;
        private final Color selectedBg;
        private boolean selected;

        PresetCardButton(PresetOption option, Color accent, Color selectedBg) {
            this.option = option;
            this.accent = accent;
            this.selectedBg = selectedBg;

            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(10, 12, 10, 12));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel title = new JLabel(option.roleTitle());
            title.setFont(ThemeManager.font(Font.BOLD, 12));
            title.setForeground(ThemeManager.SLATE_900);

            JLabel dept = new JLabel(option.subtitle());
            dept.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
            dept.setForeground(ThemeManager.SLATE_500);

            JLabel user = new JLabel("@" + option.username());
            user.setFont(ThemeManager.monoFont(Font.PLAIN, 10.5f));
            user.setForeground(accent);

            add(title);
            add(Box.createVerticalStrut(2));
            add(dept);
            add(Box.createVerticalStrut(3));
            add(user);
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Shape r = new java.awt.geom.RoundRectangle2D.Float(1, 1, w - 2f, h - 2f, 12, 12);
            g2.setColor(selected ? selectedBg : Color.WHITE);
            g2.fill(r);
            g2.setColor(selected ? accent : ThemeManager.SLATE_200);
            g2.setStroke(new BasicStroke(selected ? 1.8f : 1.0f));
            g2.draw(r);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
