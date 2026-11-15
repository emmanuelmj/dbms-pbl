package edu.gcet.placementhub.ui.dashboard;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.services.DashboardService;
import edu.gcet.placementhub.services.DashboardService.*;
import edu.gcet.placementhub.ui.components.StatCardPanel;
import edu.gcet.placementhub.ui.components.StatCardPanel.Accent;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Complete Java Swing recreation of `src/pages/DashboardPage.tsx`.
 * Renders live MySQL data across KPI cards, Role-Based Executive/Coordinator Desks,
 * Official GCTC Circulars Archive, 5-Tab Placement Analytics, Upcoming Drives,
 * Recent Audit Logs, and Quick Access navigation cards.
 */
public class DashboardPanel extends JPanel {

    private enum AnalyticsTab {
        BRANCH, COMPANIES, PACKAGES, TRENDS, ELIGIBILITY
    }

    private static final DateTimeFormatter FULL_TS_FMT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm:ss a").withZone(ZoneId.systemDefault());

    private final Consumer<String> onNavigate;
    private final Consumer<String> onSelectDrive;
    private final Runnable onRefreshRequested;

    private final JPanel contentStack;
    private final DashboardHeroPanel heroPanel;
    private final StatCardPanel studentsCard;
    private final StatCardPanel companiesCard;
    private final StatCardPanel drivesCard;
    private final StatCardPanel placedCard;

    private final JPanel roleDeskSlot;
    private final JPanel circularsSlot;
    private final JPanel analyticsSlot;
    private final JPanel upcomingDrivesSlot;
    private final JPanel activityAndQuickSlot;

    private AnalyticsTab activeTab = AnalyticsTab.BRANCH;
    private DashboardData latestData;

    public DashboardPanel(
            Consumer<String> onNavigate,
            Consumer<String> onSelectDrive,
            Runnable onRefreshRequested
    ) {
        this.onNavigate = onNavigate;
        this.onSelectDrive = onSelectDrive;
        this.onRefreshRequested = onRefreshRequested;

        setLayout(new BorderLayout());
        setBackground(ThemeManager.surfaceBg());

        // Scrollable container tracking viewport width
        ScrollableStackPanel scrollableBody = new ScrollableStackPanel();
        scrollableBody.setBackground(ThemeManager.surfaceBg());
        scrollableBody.setLayout(new BorderLayout());

        contentStack = new JPanel();
        contentStack.setOpaque(false);
        contentStack.setLayout(new BoxLayout(contentStack, BoxLayout.Y_AXIS));
        contentStack.setBorder(new EmptyBorder(20, 22, 26, 22));

        // 1. Hero
        heroPanel = new DashboardHeroPanel(onNavigate, onRefreshRequested);
        heroPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentStack.add(heroPanel);
        contentStack.add(Box.createVerticalStrut(18));

        // 2. 4 KPI StatCards
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 14, 14));
        kpiGrid.setOpaque(false);
        kpiGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        kpiGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 145));

        studentsCard = new StatCardPanel(
                "Total Registered Students", "0",
                "Real records across GCET Engineering branches",
                "👥", Accent.BLUE
        );
        companiesCard = new StatCardPanel(
                "Approved Hiring Partners", "0",
                "Corporate partners across Tier 1, Super Dream & Core",
                "❖", Accent.CYAN
        );
        drivesCard = new StatCardPanel(
                "Active Placement Drives", "0",
                "0 upcoming recruitment drives scheduled",
                "⚑", Accent.EMERALD
        );
        placedCard = new StatCardPanel(
                "Students Placed", "0",
                "Average Package: 0.00 LPA · Highest: 0.0 LPA",
                "★", "0.0% Placement Rate", Accent.AMBER
        );

        kpiGrid.add(studentsCard);
        kpiGrid.add(companiesCard);
        kpiGrid.add(drivesCard);
        kpiGrid.add(placedCard);
        contentStack.add(kpiGrid);
        contentStack.add(Box.createVerticalStrut(18));

        // 3..7 Dynamic slots updated cleanly from live JDBC snapshot
        roleDeskSlot = createSectionSlot();
        circularsSlot = createSectionSlot();
        analyticsSlot = createSectionSlot();
        upcomingDrivesSlot = createSectionSlot();
        activityAndQuickSlot = createSectionSlot();

        contentStack.add(roleDeskSlot);
        contentStack.add(circularsSlot);
        contentStack.add(analyticsSlot);
        contentStack.add(upcomingDrivesSlot);
        contentStack.add(activityAndQuickSlot);

        scrollableBody.add(contentStack, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(scrollableBody);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ThemeManager.surfaceBg());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel createSectionSlot() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(new EmptyBorder(0, 0, 18, 0));
        return p;
    }

    public void setLoadingState(User currentUser, DbStatus dbStatus) {
        heroPanel.setRefreshing(true);
        if (latestData == null) {
            heroPanel.updateState(currentUser, dbStatus);
            heroPanel.setRefreshing(true);
        }
    }

    public void renderData(DashboardData data) {
        if (data == null) return;
        this.latestData = data;
        setBackground(ThemeManager.surfaceBg());

        // 1. Hero
        heroPanel.updateState(data.currentUser, data.dbStatus);

        // 2. KPI Cards
        studentsCard.updateValues(
                String.valueOf(data.totalStudents),
                "Real records across GCET Engineering branches",
                null
        );
        companiesCard.updateValues(
                String.valueOf(data.totalCompanies),
                "Corporate partners across Tier 1, Super Dream & Core",
                null
        );
        drivesCard.updateValues(
                String.valueOf(data.activeDrives),
                data.upcomingDrives + " upcoming recruitment drives scheduled",
                null
        );
        placedCard.updateValues(
                String.valueOf(data.placedCount),
                "Average Package: " + data.avgPackage + " LPA · Highest: " + data.highestPackage + " LPA",
                data.placementPercentage + "% Placement Rate"
        );

        // 3. Dedicated Role Desk
        rebuildRoleDesk(data);

        // 4. Official GCTC Archive Circulars
        rebuildCircularsSection(data);

        // 5. 5-Tab Analytics
        rebuildAnalyticsSection(data);

        // 6. Upcoming & Active Drives
        rebuildUpcomingDrivesSection(data);

        // 7. Audit Log + Quick Access Grid
        rebuildBottomSection(data);

        revalidate();
        repaint();
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        if (contentStack.getParent() instanceof JPanel p) {
            p.setBackground(ThemeManager.surfaceBg());
        }
        if (latestData != null) {
            renderData(latestData);
        } else {
            studentsCard.refreshTheme();
            companiesCard.refreshTheme();
            drivesCard.refreshTheme();
            placedCard.refreshTheme();
        }
    }

    // -------------------------------------------------------------------------
    // 3. DEDICATED ROLE-BASED DASHBOARD DESKS
    // -------------------------------------------------------------------------
    private void rebuildRoleDesk(DashboardData data) {
        roleDeskSlot.removeAll();
        User u = data.currentUser;
        if (u == null) {
            roleDeskSlot.setVisible(false);
            return;
        }
        roleDeskSlot.setVisible(true);

        if (DashboardService.isPrincipal(u)) {
            roleDeskSlot.add(buildExecutiveDeskCard(
                    ThemeManager.BLUE_ELECTRIC,
                    "⛨",
                    "PRINCIPAL & EXECUTIVE AUTHORITY DASHBOARD",
                    "UGC Autonomous",
                    nonBlank(u.getFullName(), "Dr. S. Udaya Kumar"),
                    "Principal & Chief Academic Executive",
                    "Approval Center (RBAC)", () -> onNavigate.accept("approvals"),
                    "Institutional Analytics", () -> onNavigate.accept("reports"),
                    new String[][]{
                            {"OVERALL PLACEMENT RATE", data.placementPercentage + "%", data.placedCount + " of " + data.totalStudents + " Eligible"},
                            {"TIER-1 & DREAM DRIVES", data.activeDrives + " Active", "Top: " + data.highestPackage + " LPA"},
                            {"DEPARTMENT CLEARANCES", "Dual-Tier Clearance", "Faculty + Admin Verified"},
                            {"AFFILIATION STANDARDS", "JNTUH & NAAC A+", "NBA Tier-1 Accredited"}
                    }
            ), BorderLayout.CENTER);
        } else if (DashboardService.isChairman(u)) {
            roleDeskSlot.add(buildExecutiveDeskCard(
                    ThemeManager.CYAN_ACCENT,
                    "❖",
                    "CHAIRMAN GOVERNING BODY DASHBOARD",
                    "Teja Educational Society",
                    nonBlank(u.getFullName(), "Sri G. R. Ravinder Reddy"),
                    "Chairman & Founder",
                    "Corporate Partners", () -> onNavigate.accept("companies"),
                    "Strategic Overview", () -> onNavigate.accept("reports"),
                    new String[][]{
                            {"INSTITUTIONAL TALENT POOL", data.totalStudents + " Scholars", "Across All Branches"},
                            {"CORPORATE HIRING ALLIANCES", data.totalCompanies + " Companies", "Tier-1 & Dream Recruiters"},
                            {"AVERAGE PACKAGE MILESTONE", data.avgPackage + " LPA", "Peak Offer: " + data.highestPackage + " LPA"},
                            {"AUTONOMOUS ACCREDITATION", "NAAC A+ & NBA", "Cheeryal Campus"}
                    }
            ), BorderLayout.CENTER);
        } else if (DashboardService.isViceChairman(u)) {
            roleDeskSlot.add(buildExecutiveDeskCard(
                    new Color(0x63, 0x66, 0xF1),
                    "⌖",
                    "VICE CHAIRMAN DASHBOARD",
                    "Institutional Governance",
                    nonBlank(u.getFullName(), "Sri G. V. V. S. N. V. Prasad"),
                    "Vice Chairman",
                    "Skill Matrix", () -> onNavigate.accept("skills"),
                    "Company Matching", () -> onNavigate.accept("matching"),
                    new String[][]{
                            {"SKILL READINESS MATRIX", "Level 1 - 5 Audited", "CSE, ECE & Emerging Branches"},
                            {"PLACED STUDENTS", data.placedCount + " Students", data.placementPercentage + "% Success Index"},
                            {"CORPORATE DRIVES SCHEDULED", (data.activeDrives + data.upcomingDrives) + " Drives", "Active & Upcoming Funnel"},
                            {"INDUSTRY SYNERGY", "CDC Programs", "Continuous Training"}
                    }
            ), BorderLayout.CENTER);
        } else if (DashboardService.isPlacementAdmin(u)) {
            String dbName = data.dbStatus != null && data.dbStatus.getDatabase() != null
                    ? data.dbStatus.getDatabase()
                    : "gcet_placement_hub";
            boolean dbOk = data.dbStatus != null && data.dbStatus.isConnected();
            roleDeskSlot.add(buildExecutiveDeskCard(
                    ThemeManager.EMERALD_PRIMARY,
                    "⚑",
                    "PLACEMENT ADMIN DASHBOARD",
                    "Central Placement Cell",
                    nonBlank(u.getFullName(), "Placement System Administrator"),
                    "System Admin & IT Infrastructure",
                    "Create Placement Drive", () -> onNavigate.accept("drives"),
                    "Eligibility Engine", () -> onNavigate.accept("eligibility"),
                    new String[][]{
                            {"ACTIVE RECRUITMENT DRIVES", data.activeDrives + " Open Drives", data.upcomingDrives + " Upcoming"},
                            {"ELIGIBILITY RULES ENGINE", "Multi-Criteria Active", "CGPA + Backlogs + Skills"},
                            {"RELATIONAL DATABASE", "MySQL 8 (" + dbName + ")", dbOk ? "Connected: Live MySQL responding" : "Disconnected: MySQL unavailable"},
                            {"RECRUITMENT PIPELINE", data.applications.size() + " Applications", "10-Stage Funnel Tracking"}
                    }
            ), BorderLayout.CENTER);
        } else if (u.getRole() == UserRole.FACULTY) {
            int cohortCount = DashboardService.countFacultyCohortStudents(u, data.students);
            String deptLabel = (u.getAuthorizedDepartments() != null && !u.getAuthorizedDepartments().isEmpty())
                    ? String.join(", ", u.getAuthorizedDepartments())
                    : nonBlank(u.getDepartment(), "CSE");
            roleDeskSlot.add(buildExecutiveDeskCard(
                    ThemeManager.AMBER_PRIMARY,
                    "★",
                    "FACULTY DASHBOARD",
                    nonBlank(u.getDepartment(), "Department Coordinator"),
                    nonBlank(u.getFullName(), "Faculty Coordinator"),
                    "Department Coordinator (" + deptLabel + ")",
                    "Approval Center", () -> onNavigate.accept("approvals"),
                    "Department Students", () -> onNavigate.accept("students"),
                    new String[][]{
                            {"DEPARTMENT COHORT", cohortCount + " Students", deptLabel + " Enrolled"},
                            {"APPROVAL RESPONSIBILITIES", "Tier-1 Clearances", "Academic & Attendance Checks"},
                            {"TECHNICAL SKILL MATRIX", "Endorsement Ready", "Faculty Validation Enabled"},
                            {"ACADEMIC BRANCH SYNC", "GCTC Cheeryal", "Autonomous Standards"}
                    }
            ), BorderLayout.CENTER);
        } else if (u.getRole() == UserRole.COMPANY) {
            String deptKey = nonBlank(u.getDepartment(), "").toLowerCase();
            long companyDrives = deptKey.isBlank() ? data.activeDrives
                    : data.drives.stream().filter(d -> d.getCompanyName() != null && d.getCompanyName().toLowerCase().contains(deptKey)).count();
            long companyApps = deptKey.isBlank() ? data.applications.size()
                    : data.applications.stream().filter(a -> a.getCompanyName() != null && a.getCompanyName().toLowerCase().contains(deptKey)).count();
            roleDeskSlot.add(buildExecutiveDeskCard(
                    ThemeManager.PURPLE_PRIMARY,
                    "⚑",
                    "RECRUITER DASHBOARD",
                    "Talent Acquisition Partner",
                    nonBlank(u.getFullName(), "Corporate Recruiter"),
                    "Corporate Partner (" + nonBlank(u.getDepartment(), "Tech Hiring") + ")",
                    "Placement Drives", () -> onNavigate.accept("drives"),
                    "Candidate Pipeline", () -> onNavigate.accept("applications"),
                    new String[][]{
                            {"ACTIVE CAMPUS DRIVES", (companyDrives > 0 ? companyDrives : data.activeDrives) + " Drives", "GCET Campus Recruitment"},
                            {"APPLICANTS IN FUNNEL", (companyApps > 0 ? companyApps : data.applications.size()) + " Candidates", "Across All Rounds"},
                            {"SKILL-VERIFIED CANDIDATES", data.totalStudents + " Assessed", "Automated Matrix Match"},
                            {"RECRUITER PARTNERSHIP", "Approved Partner", "GCET Placement Cell"}
                    }
            ), BorderLayout.CENTER);
        } else if (u.getRole() == UserRole.STUDENT && data.currentStudentRecord != null) {
            roleDeskSlot.add(buildStudentDeskCard(data.currentStudentRecord), BorderLayout.CENTER);
        } else {
            roleDeskSlot.setVisible(false);
        }
    }

    private JPanel buildExecutiveDeskCard(
            Color accentColor,
            String iconGlyph,
            String badgeTitle,
            String metaTag,
            String headingName,
            String subtitleRole,
            String primaryBtnText,
            Runnable onPrimary,
            String secondaryBtnText,
            Runnable onSecondary,
            String[][] tiles
    ) {
        Color borderCol = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 110);
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), borderCol, borderCol, 1.5f);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        // Top header strip
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(0, 0, 12, 0)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);
        left.add(UiComponents.createIconBadge(
                iconGlyph,
                new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 36),
                accentColor, 40, 17
        ));

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));

        JPanel badgeLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgeLine.setOpaque(false);
        badgeLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeLine.add(new BadgeLabel(
                badgeTitle,
                new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 30),
                accentColor,
                new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 80),
                6
        ));
        JLabel metaLbl = new JLabel(metaTag);
        metaLbl.setFont(ThemeManager.monoFont(Font.PLAIN, 10));
        metaLbl.setForeground(ThemeManager.mutedText());
        badgeLine.add(metaLbl);

        JLabel nameLbl = new JLabel(
                "<html><b>" + esc(headingName) + "</b>"
                        + " <span style='color:#64748B; font-size:11px; font-weight:normal;'>· " + esc(subtitleRole) + "</span></html>"
        );
        nameLbl.setFont(ThemeManager.font(Font.BOLD, 15));
        nameLbl.setForeground(ThemeManager.primaryText());
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleStack.add(badgeLine);
        titleStack.add(Box.createVerticalStrut(4));
        titleStack.add(nameLbl);
        left.add(titleStack);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightBtns.setOpaque(false);
        ActionButton primaryBtn = new ActionButton(
                primaryBtnText, accentColor, accentColor.darker(), Color.WHITE, null, 10
        );
        primaryBtn.setMargin(new Insets(7, 13, 7, 13));
        primaryBtn.addActionListener(e -> onPrimary.run());

        ActionButton secondaryBtn = new ActionButton(
                secondaryBtnText,
                ThemeManager.cardSubtleBg(),
                ThemeManager.cardBorder(),
                ThemeManager.primaryText(),
                ThemeManager.cardBorder(),
                10
        );
        secondaryBtn.setMargin(new Insets(7, 13, 7, 13));
        secondaryBtn.addActionListener(e -> onSecondary.run());

        rightBtns.add(primaryBtn);
        rightBtns.add(secondaryBtn);

        header.add(left, BorderLayout.CENTER);
        header.add(rightBtns, BorderLayout.EAST);

        // 4 metric tiles
        JPanel grid = new JPanel(new GridLayout(1, 4, 12, 12));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (String[] t : tiles) {
            RoundedCard tile = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
            tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
            tile.setBorder(new EmptyBorder(11, 13, 11, 13));

            JLabel tl = new JLabel(t[0]);
            tl.setFont(ThemeManager.font(Font.BOLD, 10));
            tl.setForeground(ThemeManager.mutedText());
            JLabel tv = new JLabel(t[1]);
            tv.setFont(ThemeManager.font(Font.BOLD, 13.5f));
            tv.setForeground(ThemeManager.primaryText());
            JLabel ts = new JLabel(t[2]);
            ts.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
            ts.setForeground(ThemeManager.mutedText());

            tile.add(tl);
            tile.add(Box.createVerticalStrut(4));
            tile.add(tv);
            tile.add(Box.createVerticalStrut(2));
            tile.add(ts);
            grid.add(tile);
        }

        card.add(header);
        card.add(Box.createVerticalStrut(12));
        card.add(grid);
        return card;
    }

    private JPanel buildStudentDeskCard(Student st) {
        Color borderCol = new Color(ThemeManager.CYAN_ACCENT.getRed(), ThemeManager.CYAN_ACCENT.getGreen(), ThemeManager.CYAN_ACCENT.getBlue(), 110);
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), borderCol, borderCol, 1.5f);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(0, 0, 10, 0)
        ));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel tag = new JLabel("STUDENT DASHBOARD · " + nonBlank(st.getRollNumber(), ""));
        tag.setFont(ThemeManager.font(Font.BOLD, 10));
        tag.setForeground(ThemeManager.CYAN_ACCENT);
        JLabel heading = new JLabel("My Academic Credentials & Placement Readiness (" + nonBlank(st.getFullName(), "") + ")");
        heading.setFont(ThemeManager.font(Font.BOLD, 14));
        heading.setForeground(ThemeManager.primaryText());
        left.add(tag);
        left.add(Box.createVerticalStrut(3));
        left.add(heading);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JLabel rollLbl = new JLabel("Roll No: " + nonBlank(st.getRollNumber(), "-"));
        rollLbl.setFont(ThemeManager.monoFont(Font.BOLD, 12));
        rollLbl.setForeground(ThemeManager.BLUE_ELECTRIC);
        String pStatus = nonBlank(st.getPlacementStatus(), "UNPLACED");
        Color statusColor = "PLACED".equalsIgnoreCase(pStatus) ? ThemeManager.EMERALD_PRIMARY
                : "SHORTLISTED".equalsIgnoreCase(pStatus) ? ThemeManager.BLUE_ELECTRIC : ThemeManager.SLATE_500;
        BadgeLabel statusBadge = new BadgeLabel(
                pStatus,
                new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), 30),
                statusColor,
                new Color(statusColor.getRed(), statusColor.getGreen(), statusColor.getBlue(), 80),
                6
        );
        right.add(rollLbl);
        right.add(statusBadge);

        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridLayout(1, 4, 12, 12));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        grid.add(studentMetricTile("DEPARTMENT", nonBlank(st.getBranch(), "CSE"), "Class of " + st.getGraduationYear(), ThemeManager.primaryText()));
        grid.add(studentMetricTile("CUMULATIVE CGPA", String.format("%.2f / 10.0", st.getCgpa()), st.getBacklogs() + " Backlogs", ThemeManager.BLUE_ELECTRIC));
        grid.add(studentMetricTile("TECHNICAL SKILLS", st.getSkills().size() + " Skills Logged", "Verified on Matrix", ThemeManager.primaryText()));

        // Tile 4: clickable Results & Circulars
        RoundedCard portalTile = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        portalTile.setLayout(new BoxLayout(portalTile, BoxLayout.Y_AXIS));
        portalTile.setBorder(new EmptyBorder(11, 13, 11, 13));
        JLabel pLbl = new JLabel("ACADEMIC PORTAL");
        pLbl.setFont(ThemeManager.font(Font.BOLD, 10));
        pLbl.setForeground(ThemeManager.mutedText());
        ActionButton resultsBtn = new ActionButton(
                "Results & Circulars  ↗",
                ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, null, 8
        );
        resultsBtn.setMargin(new Insets(2, 0, 2, 0));
        resultsBtn.setFont(ThemeManager.font(Font.BOLD, 12));
        resultsBtn.setHorizontalAlignment(SwingConstants.LEFT);
        resultsBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, "https://www.gctcportal.in/"));
        JLabel subP = new JLabel("GCTC Autonomous");
        subP.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
        subP.setForeground(ThemeManager.mutedText());
        portalTile.add(pLbl);
        portalTile.add(Box.createVerticalStrut(3));
        portalTile.add(resultsBtn);
        portalTile.add(Box.createVerticalStrut(2));
        portalTile.add(subP);
        grid.add(portalTile);

        card.add(header);
        card.add(Box.createVerticalStrut(12));
        card.add(grid);
        return card;
    }

    private RoundedCard studentMetricTile(String title, String val, String sub, Color valColor) {
        RoundedCard tile = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setBorder(new EmptyBorder(11, 13, 11, 13));
        JLabel tl = new JLabel(title);
        tl.setFont(ThemeManager.font(Font.BOLD, 10));
        tl.setForeground(ThemeManager.mutedText());
        JLabel tv = new JLabel(val);
        tv.setFont(ThemeManager.font(Font.BOLD, 13));
        tv.setForeground(valColor);
        JLabel ts = new JLabel(sub);
        ts.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
        ts.setForeground(ThemeManager.mutedText());
        tile.add(tl);
        tile.add(Box.createVerticalStrut(4));
        tile.add(tv);
        tile.add(Box.createVerticalStrut(2));
        tile.add(ts);
        return tile;
    }

    // -------------------------------------------------------------------------
    // 4. OFFICIAL GCTC ACADEMIC CIRCULARS ARCHIVE
    // -------------------------------------------------------------------------
    private void rebuildCircularsSection(DashboardData data) {
        circularsSlot.removeAll();
        Color cardBg = ThemeManager.isDarkMode() ? ThemeManager.NAVY_MIDNIGHT : new Color(0xF0, 0xF6, 0xFF);
        Color cardBorder = new Color(38, 132, 255, 85);

        RoundedCard wrap = new RoundedCard(14, cardBg, cardBorder);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
        wrap.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(0, 0, 10, 0)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        left.add(UiComponents.createIconBadge("★", new Color(38, 132, 255, 45), ThemeManager.CYAN_ACCENT, 34, 15));

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JPanel topT = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topT.setOpaque(false);
        topT.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel hLbl = new JLabel("Official GCTC Academic Circulars Archive");
        hLbl.setFont(ThemeManager.font(Font.BOLD, 14));
        hLbl.setForeground(ThemeManager.primaryText());
        BadgeLabel archiveBadge = new BadgeLabel(
                "Archived Official Notices",
                new Color(38, 132, 255, 40),
                ThemeManager.isDarkMode() ? ThemeManager.CYAN_ACCENT : ThemeManager.BLUE_ELECTRIC,
                new Color(38, 132, 255, 85),
                999
        );
        topT.add(hLbl);
        topT.add(archiveBadge);
        JLabel subLbl = new JLabel("Official institutional notifications indexed from the Cheeryal campus examination branch & academic cell");
        subLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        subLbl.setForeground(ThemeManager.mutedText());
        subLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleCol.add(topT);
        titleCol.add(subLbl);
        left.add(titleCol);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        right.setOpaque(false);
        ActionButton portalLink = new ActionButton("gctcportal.in  ↗", cardBg, ThemeManager.cardSubtleBg(), ThemeManager.BLUE_ELECTRIC, null, 8);
        portalLink.setMargin(new Insets(4, 8, 4, 8));
        portalLink.addActionListener(e -> ResourceLoader.openExternalUrl(this, "https://www.gctcportal.in/"));

        ActionButton allCircularsBtn = new ActionButton("Explore All Circulars  →", cardBg, ThemeManager.cardSubtleBg(), ThemeManager.isDarkMode() ? ThemeManager.CYAN_ACCENT : ThemeManager.BLUE_ELECTRIC, null, 8);
        allCircularsBtn.setMargin(new Insets(4, 8, 4, 8));
        allCircularsBtn.addActionListener(e -> onNavigate.accept("campus-connect"));

        right.add(portalLink);
        right.add(allCircularsBtn);

        header.add(left, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);

        JPanel cardsRow = new JPanel(new GridLayout(1, 3, 12, 12));
        cardsRow.setOpaque(false);
        cardsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (GctcAnnouncement ann : data.recentAnnouncements) {
            RoundedCard ac = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, 1.0f);
            ac.setLayout(new BorderLayout(0, 8));
            ac.setBorder(new EmptyBorder(13, 14, 12, 14));

            JPanel topPart = new JPanel();
            topPart.setOpaque(false);
            topPart.setLayout(new BoxLayout(topPart, BoxLayout.Y_AXIS));

            JPanel meta = new JPanel(new BorderLayout(6, 0));
            meta.setOpaque(false);
            meta.setAlignmentX(Component.LEFT_ALIGNMENT);
            BadgeLabel cat = new BadgeLabel(
                    ann.category(),
                    new Color(225, 29, 72, 26),
                    ThemeManager.ROSE_PRIMARY,
                    new Color(225, 29, 72, 65),
                    6
            );
            JLabel pub = new JLabel("Published: " + ann.date());
            pub.setFont(ThemeManager.monoFont(Font.PLAIN, 10));
            pub.setForeground(ThemeManager.mutedText());
            meta.add(cat, BorderLayout.WEST);
            meta.add(pub, BorderLayout.EAST);

            JLabel ttl = new JLabel("<html><body style='width:240px;'><b>" + esc(ann.title()) + "</b></body></html>");
            ttl.setFont(ThemeManager.font(Font.BOLD, 12));
            ttl.setForeground(ThemeManager.primaryText());
            ttl.setAlignmentX(Component.LEFT_ALIGNMENT);

            topPart.add(meta);
            topPart.add(Box.createVerticalStrut(8));
            topPart.add(ttl);

            JPanel footerPart = new JPanel(new BorderLayout(6, 0));
            footerPart.setOpaque(false);
            footerPart.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                    new EmptyBorder(8, 0, 0, 0)
            ));
            JLabel posted = new JLabel(ann.postedBy());
            posted.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
            posted.setForeground(ThemeManager.mutedText());
            ActionButton readBtn = new ActionButton("Read Notice  ↗", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.BLUE_ELECTRIC, null, 6);
            readBtn.setFont(ThemeManager.font(Font.BOLD, 11));
            readBtn.setMargin(new Insets(2, 6, 2, 6));
            readBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, ann.link()));
            footerPart.add(posted, BorderLayout.CENTER);
            footerPart.add(readBtn, BorderLayout.EAST);

            ac.add(topPart, BorderLayout.CENTER);
            ac.add(footerPart, BorderLayout.SOUTH);
            cardsRow.add(ac);
        }

        wrap.add(header);
        wrap.add(Box.createVerticalStrut(12));
        wrap.add(cardsRow);
        circularsSlot.add(wrap, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // 5. COMPREHENSIVE PLACEMENT ANALYTICS (5 INTERACTIVE TABS)
    // -------------------------------------------------------------------------
    private void rebuildAnalyticsSection(DashboardData data) {
        analyticsSlot.removeAll();
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 16, 20));

        // Header + 5 Tab Buttons
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(0, 0, 12, 0)
        ));

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        JLabel hLbl = new JLabel("▥  Comprehensive Placement Analytics");
        hLbl.setFont(ThemeManager.font(Font.BOLD, 14));
        hLbl.setForeground(ThemeManager.primaryText());
        JLabel subLbl = new JLabel("Live computational model derived strictly from verified MySQL 8 records");
        subLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        subLbl.setForeground(ThemeManager.mutedText());
        titlePanel.add(hLbl);
        titlePanel.add(Box.createVerticalStrut(2));
        titlePanel.add(subLbl);

        RoundedCard tabGroup = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        tabGroup.setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        tabGroup.add(createTabBtn("Branch-Wise", AnalyticsTab.BRANCH));
        tabGroup.add(createTabBtn("Company Recruitment", AnalyticsTab.COMPANIES));
        tabGroup.add(createTabBtn("Package Distribution", AnalyticsTab.PACKAGES));
        tabGroup.add(createTabBtn("Monthly Trends", AnalyticsTab.TRENDS));
        tabGroup.add(createTabBtn("Eligible vs Placed", AnalyticsTab.ELIGIBILITY));

        header.add(titlePanel, BorderLayout.WEST);
        header.add(tabGroup, BorderLayout.EAST);

        // Active Tab Content
        JPanel tabBody = new JPanel(new BorderLayout());
        tabBody.setOpaque(false);
        tabBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabBody.setBorder(new EmptyBorder(14, 0, 14, 0));
        switch (activeTab) {
            case BRANCH -> tabBody.add(buildBranchTab(data), BorderLayout.CENTER);
            case COMPANIES -> tabBody.add(buildCompaniesTab(data), BorderLayout.CENTER);
            case PACKAGES -> tabBody.add(buildPackagesTab(data), BorderLayout.CENTER);
            case TRENDS -> tabBody.add(buildTrendsTab(data), BorderLayout.CENTER);
            case ELIGIBILITY -> tabBody.add(buildEligibilityTab(data), BorderLayout.CENTER);
        }

        // Integrity Footer
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                new EmptyBorder(10, 0, 0, 0)
        ));
        JLabel integrityLbl = new JLabel("●  Strict institutional data integrity: No fabricated figures or mock averages");
        integrityLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        integrityLbl.setForeground(ThemeManager.mutedText());
        ActionButton fullReportBtn = new ActionButton(
                "Generate Full Statistical Placement Report  ›",
                ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.BLUE_ELECTRIC, null, 8
        );
        fullReportBtn.setMargin(new Insets(4, 8, 4, 8));
        fullReportBtn.addActionListener(e -> onNavigate.accept("reports"));
        footer.add(integrityLbl, BorderLayout.WEST);
        footer.add(fullReportBtn, BorderLayout.EAST);

        card.add(header);
        card.add(tabBody);
        card.add(footer);
        analyticsSlot.add(card, BorderLayout.CENTER);
    }

    private ActionButton createTabBtn(String label, AnalyticsTab tab) {
        boolean selected = activeTab == tab;
        Color bg = selected ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardSubtleBg();
        Color hover = selected ? ThemeManager.BLUE_HOVER : ThemeManager.cardBorder();
        Color fg = selected ? Color.WHITE : ThemeManager.secondaryText();
        ActionButton btn = new ActionButton(label, bg, hover, fg, null, 8);
        btn.setFont(ThemeManager.font(selected ? Font.BOLD : Font.PLAIN, 11.5f));
        btn.setMargin(new Insets(6, 10, 6, 10));
        btn.addActionListener(e -> {
            if (activeTab != tab) {
                activeTab = tab;
                if (latestData != null) rebuildAnalyticsSection(latestData);
                analyticsSlot.revalidate();
                analyticsSlot.repaint();
            }
        });
        return btn;
    }

    private JPanel buildBranchTab(DashboardData data) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setOpaque(false);
        hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l1 = new JLabel("Department / Engineering Branch");
        l1.setFont(ThemeManager.font(Font.BOLD, 11));
        l1.setForeground(ThemeManager.mutedText());
        JLabel l2 = new JLabel("Placed / Registered (% Placed · Avg CGPA)");
        l2.setFont(ThemeManager.font(Font.BOLD, 11));
        l2.setForeground(ThemeManager.mutedText());
        hdr.add(l1, BorderLayout.WEST);
        hdr.add(l2, BorderLayout.EAST);
        p.add(hdr);
        p.add(Box.createVerticalStrut(10));

        if (data.branchAnalytics.isEmpty()) {
            p.add(emptyStateBox("No student branch records found in the database.",
                    "Register students to view branch performance metrics."));
            return p;
        }

        for (Map.Entry<String, BranchStat> e : data.branchAnalytics.entrySet()) {
            BranchStat bs = e.getValue();
            int pct = bs.total() > 0 ? (int) Math.round((bs.placed() * 100.0) / bs.total()) : 0;

            RoundedCard row = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, 1.0f);
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setBorder(new EmptyBorder(11, 14, 11, 14));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.setOpaque(false);
            top.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            left.setOpaque(false);
            JLabel brLbl = new JLabel(bs.branch());
            brLbl.setFont(ThemeManager.font(Font.BOLD, 12.5f));
            brLbl.setForeground(ThemeManager.primaryText());
            BadgeLabel cgpaBadge = new BadgeLabel(
                    "Avg CGPA: " + String.format("%.2f", bs.avgCgpa()),
                    ThemeManager.cardBorder(), ThemeManager.secondaryText(), null, 6
            );
            left.add(brLbl);
            left.add(cgpaBadge);

            JLabel countLbl = new JLabel(bs.placed() + " placed of " + bs.total() + " students (" + pct + "%)");
            countLbl.setFont(ThemeManager.monoFont(Font.BOLD, 11));
            countLbl.setForeground(pct >= 60 ? ThemeManager.EMERALD_PRIMARY
                    : pct >= 30 ? ThemeManager.AMBER_PRIMARY : ThemeManager.secondaryText());

            top.add(left, BorderLayout.WEST);
            top.add(countLbl, BorderLayout.EAST);

            row.add(top);
            row.add(Box.createVerticalStrut(7));
            row.add(new GradientProgressBar(pct, ThemeManager.BLUE_ELECTRIC, ThemeManager.CYAN_ACCENT));
            p.add(row);
            p.add(Box.createVerticalStrut(8));
        }
        return p;
    }

    private JPanel buildCompaniesTab(DashboardData data) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        if (data.companyAnalytics.isEmpty()) {
            p.add(emptyStateBox("No approved hiring partners recorded yet.",
                    "Approved corporate partners from MySQL will appear here."), BorderLayout.CENTER);
            return p;
        }

        String[] cols = {"Company Partner", "Tier / Category", "Location", "Drives Held", "Offers Made", "Max Package", "Drive Status"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (CompanyStat cs : data.companyAnalytics) {
            Company c = cs.company();
            model.addRow(new Object[]{
                    nonBlank(c.getName(), "-"),
                    nonBlank(c.getTier(), "Standard IT"),
                    nonBlank(c.getLocation(), "Hyderabad / Pan-India"),
                    cs.drivesCount(),
                    cs.offersCount(),
                    cs.maxPackage(),
                    cs.hasActiveDrive() ? "Active Drive" : "Completed / Inactive"
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(36);
        table.setFont(ThemeManager.font(Font.PLAIN, 12));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);

        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean sel, boolean foc, int r, int c) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, sel, foc, r, c);
                lbl.setHorizontalAlignment(SwingConstants.RIGHT);
                String s = val != null ? val.toString() : "";
                lbl.setFont(ThemeManager.font(Font.BOLD, 11));
                lbl.setForeground("Active Drive".equals(s) ? ThemeManager.EMERALD_PRIMARY : ThemeManager.mutedText());
                return lbl;
            }
        };
        table.getColumnModel().getColumn(6).setCellRenderer(statusRenderer);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder()));
        int height = Math.min(290, Math.max(120, data.companyAnalytics.size() * 36 + 34));
        sp.setPreferredSize(new Dimension(600, height));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildPackagesTab(DashboardData data) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JPanel grid = new JPanel(new GridLayout(1, 4, 12, 12));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (PackageBracket b : data.packageDistribution) {
            int pct = data.placedCount > 0 ? (int) Math.round((b.count() * 100.0) / data.placedCount) : 0;
            RoundedCard card = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(new EmptyBorder(14, 14, 14, 14));

            JLabel lbl = new JLabel(b.label().toUpperCase());
            lbl.setFont(ThemeManager.font(Font.BOLD, 11));
            lbl.setForeground(ThemeManager.BLUE_ELECTRIC);
            JLabel cnt = new JLabel(b.count() + "  (" + pct + "% of placed)");
            cnt.setFont(ThemeManager.font(Font.BOLD, 18));
            cnt.setForeground(ThemeManager.primaryText());
            JLabel desc = new JLabel("<html><body style='width:170px;'>" + esc(b.description()) + "</body></html>");
            desc.setFont(ThemeManager.font(Font.PLAIN, 11));
            desc.setForeground(ThemeManager.mutedText());

            card.add(lbl);
            card.add(Box.createVerticalStrut(4));
            card.add(cnt);
            card.add(Box.createVerticalStrut(4));
            card.add(desc);
            card.add(Box.createVerticalStrut(10));
            card.add(new GradientProgressBar(pct, b.barStart(), b.barEnd()));
            grid.add(card);
        }

        p.add(grid);
        p.add(Box.createVerticalStrut(12));

        RoundedCard summary = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        summary.setLayout(new BorderLayout(10, 0));
        summary.setBorder(new EmptyBorder(11, 14, 11, 14));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel left = new JLabel(
                "<html>★  <b>Highest Institution Offer:</b> " + data.highestPackage + " LPA · <b>Average Offer:</b> " + data.avgPackage + " LPA</html>"
        );
        left.setFont(ThemeManager.font(Font.PLAIN, 12));
        left.setForeground(ThemeManager.primaryText());
        JLabel right = new JLabel("Data strictly compiled from registered GCET student placement records");
        right.setFont(ThemeManager.font(Font.PLAIN, 11));
        right.setForeground(ThemeManager.mutedText());
        summary.add(left, BorderLayout.WEST);
        summary.add(right, BorderLayout.EAST);
        p.add(summary);
        return p;
    }

    private JPanel buildTrendsTab(DashboardData data) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Campus Placement Drive & Selection Velocity (Academic Session 2025-2026)");
        title.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        title.setForeground(ThemeManager.mutedText());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createVerticalStrut(10));

        JPanel grid = new JPanel(new GridLayout(1, 8, 8, 8));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (MonthlyTrend t : data.monthlyTrends) {
            RoundedCard mCard = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
            mCard.setLayout(new BoxLayout(mCard, BoxLayout.Y_AXIS));
            mCard.setBorder(new EmptyBorder(10, 8, 10, 8));

            JLabel mLbl = new JLabel(t.month(), SwingConstants.CENTER);
            mLbl.setFont(ThemeManager.font(Font.BOLD, 12));
            mLbl.setForeground(ThemeManager.primaryText());
            mLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel dVal = new JLabel(String.valueOf(t.drives()), SwingConstants.CENTER);
            dVal.setFont(ThemeManager.font(Font.BOLD, 19));
            dVal.setForeground(ThemeManager.BLUE_ELECTRIC);
            dVal.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel dTxt = new JLabel("Drives", SwingConstants.CENTER);
            dTxt.setFont(ThemeManager.font(Font.PLAIN, 10));
            dTxt.setForeground(ThemeManager.mutedText());
            dTxt.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel oVal = new JLabel(t.offers() + " Offers", SwingConstants.CENTER);
            oVal.setFont(ThemeManager.font(Font.BOLD, 11));
            oVal.setForeground(ThemeManager.EMERALD_PRIMARY);
            oVal.setAlignmentX(Component.CENTER_ALIGNMENT);
            oVal.setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                    new EmptyBorder(6, 0, 0, 0)
            ));

            mCard.add(mLbl);
            mCard.add(Box.createVerticalStrut(6));
            mCard.add(dVal);
            mCard.add(dTxt);
            mCard.add(Box.createVerticalStrut(8));
            mCard.add(oVal);
            grid.add(mCard);
        }

        p.add(grid);
        p.add(Box.createVerticalStrut(10));

        JLabel note = new JLabel("⏱  Placement season peaks across September - December for Tier 1 hiring partners.");
        note.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        note.setForeground(ThemeManager.mutedText());
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(note);
        return p;
    }

    private JPanel buildEligibilityTab(DashboardData data) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        FunnelMetrics fm = data.funnelMetrics;
        JPanel grid = new JPanel(new GridLayout(1, 5, 10, 10));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        grid.add(funnelBox("1. TOTAL REGISTERED", String.valueOf(fm.total()), "Students on Portal", ThemeManager.primaryText()));
        grid.add(funnelBox("2. ACADEMICALLY ELIGIBLE", String.valueOf(fm.academicallyEligible()), "CGPA ≥ 6.5 & 0 Backlogs", ThemeManager.BLUE_ELECTRIC));
        grid.add(funnelBox("3. DRIVE APPLICANTS", String.valueOf(fm.studentIdsApplied()), "Applied to at least 1 drive", ThemeManager.CYAN_ACCENT));
        grid.add(funnelBox("4. SHORTLISTED", String.valueOf(fm.studentIdsShortlisted()), "Cleared Online Assessments", new Color(0x63, 0x66, 0xF1)));
        grid.add(funnelBox("5. OFFERS CONFIRMED", String.valueOf(fm.finalPlaced()), data.placementPercentage + "% Conversion", ThemeManager.EMERALD_PRIMARY));

        p.add(grid);
        p.add(Box.createVerticalStrut(12));

        RoundedCard strip = new RoundedCard(10, ThemeManager.cardSubtleBg(), new Color(38, 132, 255, 75));
        strip.setLayout(new BorderLayout(10, 0));
        strip.setBorder(new EmptyBorder(10, 14, 10, 14));
        strip.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel("⛨  Eligibility Engine evaluates real-time criteria: Branch suitability, CGPA thresholds, backlogs and required skill matrix levels.");
        desc.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        desc.setForeground(ThemeManager.secondaryText());

        ActionButton runBtn = new ActionButton(
                "Run Eligibility Engine", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, null, 8
        );
        runBtn.setMargin(new Insets(4, 10, 4, 10));
        runBtn.addActionListener(e -> onNavigate.accept("eligibility"));

        strip.add(desc, BorderLayout.CENTER);
        strip.add(runBtn, BorderLayout.EAST);
        p.add(strip);
        return p;
    }

    private RoundedCard funnelBox(String step, String value, String sub, Color accent) {
        RoundedCard box = new RoundedCard(12, ThemeManager.cardSubtleBg(), new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(13, 10, 13, 10));

        JLabel sLbl = new JLabel(step, SwingConstants.CENTER);
        sLbl.setFont(ThemeManager.font(Font.BOLD, 9.5f));
        sLbl.setForeground(accent);
        sLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel vLbl = new JLabel(value, SwingConstants.CENTER);
        vLbl.setFont(ThemeManager.font(Font.BOLD, 22));
        vLbl.setForeground(ThemeManager.primaryText());
        vLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel bLbl = new JLabel(sub, SwingConstants.CENTER);
        bLbl.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
        bLbl.setForeground(ThemeManager.mutedText());
        bLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(sLbl);
        box.add(Box.createVerticalStrut(5));
        box.add(vLbl);
        box.add(Box.createVerticalStrut(4));
        box.add(bLbl);
        return box;
    }

    // -------------------------------------------------------------------------
    // 6. UPCOMING & ACTIVE PLACEMENT DRIVES SECTION
    // -------------------------------------------------------------------------
    private void rebuildUpcomingDrivesSection(DashboardData data) {
        upcomingDrivesSlot.removeAll();
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(0, 0, 10, 0)
        ));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel hLbl = new JLabel("⚑  Upcoming & Active Placement Drives");
        hLbl.setFont(ThemeManager.font(Font.BOLD, 14));
        hLbl.setForeground(ThemeManager.primaryText());
        JLabel subLbl = new JLabel("Verified corporate hiring opportunities for Geethanjali College students");
        subLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        subLbl.setForeground(ThemeManager.mutedText());
        left.add(hLbl);
        left.add(Box.createVerticalStrut(2));
        left.add(subLbl);

        ActionButton viewAllBtn = new ActionButton(
                "View All Drives in Catalog  →",
                ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.BLUE_ELECTRIC, null, 8
        );
        viewAllBtn.setMargin(new Insets(5, 10, 5, 10));
        viewAllBtn.addActionListener(e -> onNavigate.accept("drives"));

        header.add(left, BorderLayout.WEST);
        header.add(viewAllBtn, BorderLayout.EAST);
        card.add(header);
        card.add(Box.createVerticalStrut(10));

        if (data.upcomingAndOpenDrives.isEmpty()) {
            JPanel empty = emptyStateBox("No recruitment drives currently scheduled.",
                    "Placement officers and administrators can add new drives via the Placement Drives module.");
            ActionButton schedBtn = new ActionButton("Schedule New Drive", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
            schedBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            schedBtn.addActionListener(e -> onNavigate.accept("drives"));
            empty.add(Box.createVerticalStrut(10));
            empty.add(schedBtn);
            card.add(empty);
            upcomingDrivesSlot.add(card, BorderLayout.CENTER);
            return;
        }

        boolean isStudent = data.currentUser != null && data.currentUser.getRole() == UserRole.STUDENT;
        for (int i = 0; i < data.upcomingAndOpenDrives.size(); i++) {
            PlacementDrive drv = data.upcomingAndOpenDrives.get(i);
            JPanel row = new JPanel(new BorderLayout(16, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setBorder(BorderFactory.createCompoundBorder(
                    i < data.upcomingAndOpenDrives.size() - 1
                            ? new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder())
                            : new EmptyBorder(0, 0, 0, 0),
                    new EmptyBorder(12, 6, 12, 6)
            ));

            JPanel infoCol = new JPanel();
            infoCol.setOpaque(false);
            infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));

            JPanel topLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            topLine.setOpaque(false);
            topLine.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel cName = new JLabel(nonBlank(drv.getCompanyName(), "Company"));
            cName.setFont(ThemeManager.font(Font.BOLD, 13.5f));
            cName.setForeground(ThemeManager.primaryText());

            boolean isOpen = "OPEN".equalsIgnoreCase(drv.getStatus());
            Color stCol = isOpen ? ThemeManager.EMERALD_PRIMARY : ThemeManager.AMBER_PRIMARY;
            BadgeLabel stBadge = new BadgeLabel(
                    nonBlank(drv.getStatus(), "OPEN"),
                    new Color(stCol.getRed(), stCol.getGreen(), stCol.getBlue(), 28),
                    stCol,
                    new Color(stCol.getRed(), stCol.getGreen(), stCol.getBlue(), 80),
                    6
            );

            topLine.add(cName);
            topLine.add(stBadge);

            if (isStudent && data.currentStudentRecord != null) {
                Application matchedApp = data.applications.stream()
                        .filter(a -> drv.getId().equals(a.getDriveId())
                                && (data.currentStudentRecord.getId().equals(a.getStudentId())
                                || (data.currentStudentRecord.getRollNumber() != null
                                && data.currentStudentRecord.getRollNumber().equalsIgnoreCase(a.getStudentRollNumber()))))
                        .findFirst()
                        .orElse(null);
                if (matchedApp != null) {
                    topLine.add(new BadgeLabel(
                            "Applied (" + matchedApp.getCurrentStage().name() + ")",
                            new Color(38, 132, 255, 28), ThemeManager.BLUE_ELECTRIC, new Color(38, 132, 255, 75), 6
                    ));
                } else {
                    topLine.add(new BadgeLabel(
                            "Eligible / Open",
                            new Color(16, 185, 129, 25), ThemeManager.EMERALD_PRIMARY, null, 6
                    ));
                }
            } else {
                long appCount = data.applications.stream().filter(a -> drv.getId().equals(a.getDriveId())).count();
                JLabel appLbl = new JLabel(appCount + " Applicants");
                appLbl.setFont(ThemeManager.monoFont(Font.PLAIN, 11));
                appLbl.setForeground(ThemeManager.mutedText());
                topLine.add(appLbl);
            }

            JLabel roleLbl = new JLabel(nonBlank(drv.getJobRole(), "Software Engineer"));
            roleLbl.setFont(ThemeManager.font(Font.BOLD, 12));
            roleLbl.setForeground(ThemeManager.secondaryText());
            roleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel metaLbl = new JLabel(
                    "Package: " + drv.getSalaryPackageLpa() + " LPA   ·   Drive Date: " + nonBlank(drv.getDriveDate(), "-")
                            + "   ·   Deadline: " + nonBlank(drv.getApplicationDeadline(), "-")
                            + "   ·   Min CGPA: " + String.format("%.2f", drv.getMinCgpa())
            );
            metaLbl.setFont(ThemeManager.font(Font.PLAIN, 11));
            metaLbl.setForeground(ThemeManager.mutedText());
            metaLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel branchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            branchRow.setOpaque(false);
            branchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel brTitle = new JLabel("ELIGIBLE BRANCHES:");
            brTitle.setFont(ThemeManager.font(Font.BOLD, 10));
            brTitle.setForeground(ThemeManager.mutedText());
            branchRow.add(brTitle);
            if (drv.getEligibleBranches().isEmpty()) {
                branchRow.add(new BadgeLabel("ALL BRANCHES", ThemeManager.cardSubtleBg(), ThemeManager.secondaryText(), ThemeManager.cardBorder(), 5));
            } else {
                for (String br : drv.getEligibleBranches()) {
                    BadgeLabel chip = new BadgeLabel(br, ThemeManager.cardSubtleBg(), ThemeManager.secondaryText(), ThemeManager.cardBorder(), 5);
                    chip.setFont(ThemeManager.monoFont(Font.PLAIN, 10));
                    chip.setBorder(new EmptyBorder(2, 6, 2, 6));
                    branchRow.add(chip);
                }
            }

            infoCol.add(topLine);
            infoCol.add(Box.createVerticalStrut(4));
            infoCol.add(roleLbl);
            infoCol.add(Box.createVerticalStrut(4));
            infoCol.add(metaLbl);
            infoCol.add(Box.createVerticalStrut(4));
            infoCol.add(branchRow);

            JPanel btnCol = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 14));
            btnCol.setOpaque(false);
            ActionButton eligBtn = new ActionButton(
                    "Check Eligibility",
                    ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.inputBorder(), 9
            );
            eligBtn.setMargin(new Insets(7, 12, 7, 12));
            eligBtn.addActionListener(e -> {
                if (onSelectDrive != null) onSelectDrive.accept(drv.getId());
                onNavigate.accept("eligibility");
            });

            ActionButton openBtn = new ActionButton(
                    "Open Drive  →",
                    ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 9
            );
            openBtn.setMargin(new Insets(7, 13, 7, 13));
            openBtn.addActionListener(e -> {
                if (onSelectDrive != null) onSelectDrive.accept(drv.getId());
                onNavigate.accept("drives");
            });

            btnCol.add(eligBtn);
            btnCol.add(openBtn);

            row.add(infoCol, BorderLayout.CENTER);
            row.add(btnCol, BorderLayout.EAST);
            card.add(row);
        }

        upcomingDrivesSlot.add(card, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------------------
    // 7. RECENT SYSTEM ACTIVITIES + INSTITUTIONAL QUICK ACCESS GRID
    // -------------------------------------------------------------------------
    private void rebuildBottomSection(DashboardData data) {
        activityAndQuickSlot.removeAll();
        JPanel split = new JPanel(new GridBagLayout());
        split.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 1.0;

        // Left 1/3: Audit logs card
        RoundedCard auditCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        auditCard.setLayout(new BorderLayout(0, 10));
        auditCard.setBorder(new EmptyBorder(16, 18, 14, 18));

        JPanel aHeader = new JPanel(new BorderLayout(8, 0));
        aHeader.setOpaque(false);
        aHeader.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(0, 0, 10, 0)
        ));
        JLabel aTitle = new JLabel("∿  Recent System Activities");
        aTitle.setFont(ThemeManager.font(Font.BOLD, 13.5f));
        aTitle.setForeground(ThemeManager.primaryText());
        BadgeLabel liveAuditBadge = new BadgeLabel(
                "Live Audit", ThemeManager.NAVY_DEEP, ThemeManager.CYAN_ACCENT, null, 6
        );
        aHeader.add(aTitle, BorderLayout.WEST);
        aHeader.add(liveAuditBadge, BorderLayout.EAST);

        JPanel logsList = new JPanel();
        logsList.setOpaque(false);
        logsList.setLayout(new BoxLayout(logsList, BoxLayout.Y_AXIS));
        if (data.recentActivities.isEmpty()) {
            JLabel empty = new JLabel("No recent activities recorded yet.", SwingConstants.CENTER);
            empty.setFont(ThemeManager.font(Font.PLAIN, 12));
            empty.setForeground(ThemeManager.mutedText());
            empty.setBorder(new EmptyBorder(24, 8, 24, 8));
            logsList.add(empty);
        } else {
            for (int i = 0; i < data.recentActivities.size(); i++) {
                AuditLog log = data.recentActivities.get(i);
                JPanel item = new JPanel();
                item.setOpaque(false);
                item.setLayout(new BoxLayout(item, BoxLayout.Y_AXIS));
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                item.setBorder(BorderFactory.createCompoundBorder(
                        i < data.recentActivities.size() - 1
                                ? new MatteBorder(0, 0, 1, 0, ThemeManager.cardBorder())
                                : new EmptyBorder(0, 0, 0, 0),
                        new EmptyBorder(7, 0, 7, 0)
                ));

                JPanel topL = new JPanel(new BorderLayout(6, 0));
                topL.setOpaque(false);
                topL.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel actor = new JLabel(nonBlank(log.getActorUsername(), "System"));
                actor.setFont(ThemeManager.font(Font.BOLD, 12));
                actor.setForeground(ThemeManager.primaryText());
                JLabel when = new JLabel(DashboardService.formatRelativeTime(log.getCreatedAt()));
                when.setFont(ThemeManager.monoFont(Font.PLAIN, 10));
                when.setForeground(ThemeManager.mutedText());
                topL.add(actor, BorderLayout.WEST);
                topL.add(when, BorderLayout.EAST);

                JLabel det = new JLabel("<html><body style='width:255px;'>" + esc(nonBlank(log.getDetails(), log.getAction())) + "</body></html>");
                det.setFont(ThemeManager.font(Font.PLAIN, 11));
                det.setForeground(ThemeManager.mutedText());
                det.setAlignmentX(Component.LEFT_ALIGNMENT);

                item.add(topL);
                item.add(Box.createVerticalStrut(2));
                item.add(det);
                logsList.add(item);
            }
        }

        JPanel aFooter = new JPanel();
        aFooter.setOpaque(false);
        aFooter.setLayout(new BoxLayout(aFooter, BoxLayout.Y_AXIS));
        aFooter.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                new EmptyBorder(10, 0, 0, 0)
        ));
        String dbName = data.dbStatus != null && data.dbStatus.getDatabase() != null
                ? data.dbStatus.getDatabase() : "gcet_placement_hub";
        JLabel tblNote = new JLabel("Audit log stored in MySQL table audit_logs (" + dbName + ")", SwingConstants.CENTER);
        tblNote.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
        tblNote.setForeground(ThemeManager.mutedText());
        tblNote.setAlignmentX(Component.CENTER_ALIGNMENT);

        String lastConn = "Not yet connected";
        if (data.dbStatus != null && data.dbStatus.getLastSuccessfulConnectionIso() != null) {
            try {
                lastConn = FULL_TS_FMT.format(Instant.parse(data.dbStatus.getLastSuccessfulConnectionIso()));
            } catch (Exception ignored) {
            }
        }
        JLabel tsNote = new JLabel("Last successful connection: " + lastConn, SwingConstants.CENTER);
        tsNote.setFont(ThemeManager.monoFont(Font.PLAIN, 10));
        tsNote.setForeground(ThemeManager.mutedText());
        tsNote.setAlignmentX(Component.CENTER_ALIGNMENT);
        aFooter.add(tblNote);
        aFooter.add(Box.createVerticalStrut(3));
        aFooter.add(tsNote);

        auditCard.add(aHeader, BorderLayout.NORTH);
        auditCard.add(logsList, BorderLayout.CENTER);
        auditCard.add(aFooter, BorderLayout.SOUTH);

        gbc.gridx = 0;
        gbc.weightx = 0.36;
        gbc.insets = new Insets(0, 0, 0, 16);
        split.add(auditCard, gbc);

        // Right 2/3: 2x2 Quick Access Navigation Cards
        JPanel quickGrid = new JPanel(new GridLayout(2, 2, 14, 14));
        quickGrid.setOpaque(false);

        quickGrid.add(quickAccessCard(
                "Matching Engine",
                "Automated student-company fit %",
                "Run Matching Matrix",
                "⌖",
                ThemeManager.BLUE_ELECTRIC,
                () -> onNavigate.accept("matching")
        ));
        quickGrid.add(quickAccessCard(
                "Skill Gap Analysis",
                "Curriculum bridge roadmaps",
                "Identify Deficiencies",
                "◫",
                ThemeManager.CYAN_ACCENT,
                () -> onNavigate.accept("skill-gap")
        ));
        quickGrid.add(quickAccessCard(
                "Skill Matrix Library",
                "Proficiency 1-5 faculty validations",
                "Manage Matrix",
                "✓",
                ThemeManager.EMERALD_PRIMARY,
                () -> onNavigate.accept("skills")
        ));
        quickGrid.add(quickAccessCard(
                "Placement Reports",
                "Institutional CSV and PDF export",
                "Export Reports",
                "▥",
                ThemeManager.AMBER_PRIMARY,
                () -> onNavigate.accept("reports")
        ));

        gbc.gridx = 1;
        gbc.weightx = 0.64;
        gbc.insets = new Insets(0, 0, 0, 0);
        split.add(quickGrid, gbc);

        activityAndQuickSlot.add(split, BorderLayout.CENTER);
    }

    private JPanel quickAccessCard(
            String title,
            String subtitle,
            String ctaLabel,
            String iconGlyph,
            Color accent,
            Runnable onClick
    ) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder(), accent, 1.1f);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(16, 18, 14, 18));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        top.setOpaque(false);
        top.add(UiComponents.createIconBadge(
                iconGlyph,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 28),
                accent,
                38,
                16
        ));
        JPanel txt = new JPanel();
        txt.setOpaque(false);
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        JLabel tLbl = new JLabel(title);
        tLbl.setFont(ThemeManager.font(Font.BOLD, 13));
        tLbl.setForeground(ThemeManager.primaryText());
        JLabel sLbl = new JLabel(subtitle);
        sLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sLbl.setForeground(ThemeManager.mutedText());
        txt.add(tLbl);
        txt.add(Box.createVerticalStrut(2));
        txt.add(sLbl);
        top.add(txt);

        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.cardBorder()),
                new EmptyBorder(9, 0, 0, 0)
        ));
        JLabel cLbl = new JLabel(ctaLabel);
        cLbl.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        cLbl.setForeground(accent);
        JLabel arrow = new JLabel("›");
        arrow.setFont(ThemeManager.font(Font.BOLD, 14));
        arrow.setForeground(accent);
        foot.add(cLbl, BorderLayout.WEST);
        foot.add(arrow, BorderLayout.EAST);

        card.add(top, BorderLayout.CENTER);
        card.add(foot, BorderLayout.SOUTH);

        java.awt.event.MouseAdapter clicker = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                onClick.run();
            }
        };
        card.addMouseListener(clicker);
        return card;
    }

    private JPanel emptyStateBox(String heading, String subtext) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(26, 16, 26, 16));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel h = new JLabel(heading, SwingConstants.CENTER);
        h.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        h.setForeground(ThemeManager.secondaryText());
        h.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel s = new JLabel(subtext, SwingConstants.CENTER);
        s.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        s.setForeground(ThemeManager.mutedText());
        s.setAlignmentX(Component.CENTER_ALIGNMENT);

        box.add(h);
        box.add(Box.createVerticalStrut(4));
        box.add(s);
        return box;
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Rounded linear-gradient progress bar for Branch-Wise & Package Distribution charts.
     */
    private static class GradientProgressBar extends JPanel {
        private final int percent;
        private final Color startColor;
        private final Color endColor;

        GradientProgressBar(int percent, Color startColor, Color endColor) {
            this.percent = Math.max(0, Math.min(100, percent));
            this.startColor = startColor;
            this.endColor = endColor;
            setOpaque(false);
            setPreferredSize(new Dimension(200, 10));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(ThemeManager.isDarkMode() ? new Color(0x0B, 0x11, 0x20) : new Color(0xE3, 0xEA, 0xF3));
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));

            if (percent > 0) {
                int fillW = Math.max(8, (int) Math.round((w * Math.max(4, percent)) / 100.0));
                GradientPaint gp = new GradientPaint(0, 0, startColor, fillW, 0, endColor);
                g2.setPaint(gp);
                g2.fill(new RoundRectangle2D.Float(0, 0, fillW, h, h, h));
            }
            g2.dispose();
        }
    }

    /**
     * Scrollable container implementing Swing Scrollable so viewport width stretches cleanly without horizontal scrolling.
     */
    private static class ScrollableStackPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 90;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
