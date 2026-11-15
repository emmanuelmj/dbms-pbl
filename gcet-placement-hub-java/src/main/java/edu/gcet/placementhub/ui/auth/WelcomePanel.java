package edu.gcet.placementhub.ui.auth;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Year;
import java.util.function.BiConsumer;

/**
 * Complete Java Swing recreation of WelcomePage.tsx:
 * 1. Top institutional accreditation strip
 * 2. Sticky white public header with circular GCET logo + action buttons
 * 3. Unified CampusHeroPanel with GCET campus photograph & multi-stop gradient
 * 4. 4-column Institutional Portal Cards (ADMIN, FACULTY, COMPANIES, STUDENTS)
 * 5. 8-card Comprehensive Placement Features Grid
 * 6. 3-tab Institution Overview / About Placement Cell / Accreditations & Honors section
 * 7. Institutional Footer (#0F1D3D) with Campus Contact & External Links
 */
public class WelcomePanel extends JPanel {

    private final BiConsumer<PortalCategory, Boolean> onOpenPortal;
    private final JScrollPane scrollPane;
    private JPanel portalCardsAnchor;
    private final CardLayout aboutTabCards = new CardLayout();
    private final JPanel aboutTabContent = new JPanel(aboutTabCards);
    private JButton tabOverviewBtn;
    private JButton tabPlacementBtn;
    private JButton tabAccredBtn;

    public WelcomePanel(BiConsumer<PortalCategory, Boolean> onOpenPortal) {
        this.onOpenPortal = onOpenPortal;
        setLayout(new BorderLayout());
        setBackground(Color.WHITE);

        // 1 + 2: Top strip + Public Header fixed at top (just like sticky top header)
        JPanel topFixed = new JPanel();
        topFixed.setLayout(new BoxLayout(topFixed, BoxLayout.Y_AXIS));
        topFixed.add(buildAccreditationStrip());
        topFixed.add(buildPublicHeader());
        add(topFixed, BorderLayout.NORTH);

        // Scrollable Body
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(Color.WHITE);

        body.add(buildHeroSection());
        body.add(buildPortalSelectionSection());
        body.add(buildFeaturesSection());
        body.add(buildInstitutionTabsSection());
        body.add(buildFooter());

        scrollPane = new JScrollPane(body);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(22);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel buildAccreditationStrip() {
        JPanel strip = new JPanel(new BorderLayout());
        strip.setBackground(ThemeManager.SLATE_100);
        strip.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.SLATE_200),
                new EmptyBorder(7, 28, 7, 28)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        BadgeLabel ugcBadge = new BadgeLabel(
                "★  UGC AUTONOMOUS",
                new Color(0xFE, 0xF3, 0xC7),
                new Color(0x92, 0x40, 0x0E),
                new Color(0xFC, 0xD3, 0x4D),
                999
        );
        JLabel stripText = new JLabel(
                "<html><b>NAAC 'A+' Grade</b> &nbsp;·&nbsp; NBA Accredited Branches (CSE, ECE, EEE, MECH) &nbsp;·&nbsp; Affiliated to JNTU Hyderabad</html>"
        );
        stripText.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        stripText.setForeground(ThemeManager.SLATE_700);
        left.add(ugcBadge);
        left.add(stripText);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setOpaque(false);
        JLabel locLbl = new JLabel("Cheeryal, Keesara, Telangana - 501301");
        locLbl.setFont(ThemeManager.font(Font.PLAIN, 11));
        locLbl.setForeground(ThemeManager.SLATE_600);

        JLabel siteLink = createHyperlink("gcet.edu.in ↗", "https://gcet.edu.in", ThemeManager.SLATE_700, ThemeManager.BLUE_ELECTRIC, 11);
        right.add(locLbl);
        right.add(siteLink);

        strip.add(left, BorderLayout.WEST);
        strip.add(right, BorderLayout.EAST);
        return strip;
    }

    private JPanel buildPublicHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.SLATE_200),
                new EmptyBorder(12, 28, 12, 28)
        ));

        // Left: GCET Logo + Title + Subtitle
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        JLabel logoLabel = new JLabel(ResourceLoader.createCircularLogoIcon(48, ThemeManager.SLATE_200));
        left.add(logoLabel);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));

        JPanel topBrandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topBrandRow.setOpaque(false);
        JLabel brandTitle = new JLabel("<html><span style='color:#0F1D3D;'>GCET</span> <span style='color:#2684FF;'>PLACEMENT HUB</span></html>");
        brandTitle.setFont(ThemeManager.font(Font.BOLD, 17));
        BadgeLabel portalPill = new BadgeLabel(
                "INSTITUTIONAL PORTAL",
                new Color(0xEF, 0xF6, 0xFF),
                ThemeManager.BLUE_ELECTRIC,
                new Color(0xBF, 0xDB, 0xFE),
                999
        );
        portalPill.setFont(ThemeManager.font(Font.BOLD, 9.5f));
        topBrandRow.add(brandTitle);
        topBrandRow.add(portalPill);

        JLabel brandSub = new JLabel("Geethanjali College of Engineering and Technology · Hyderabad");
        brandSub.setFont(ThemeManager.font(Font.PLAIN, 12));
        brandSub.setForeground(ThemeManager.SLATE_500);
        brandSub.setBorder(new EmptyBorder(0, 8, 0, 0));

        titleStack.add(topBrandRow);
        titleStack.add(brandSub);
        left.add(titleStack);

        // Right: Student Sign In & Portal Access Buttons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        right.setOpaque(false);

        ActionButton stuBtn = new ActionButton(
                "Student Sign In",
                ThemeManager.SLATE_100,
                ThemeManager.SLATE_200,
                ThemeManager.SLATE_700,
                ThemeManager.SLATE_200,
                12
        );
        stuBtn.addActionListener(e -> onOpenPortal.accept(PortalCategory.STUDENTS, false));

        ActionButton portalBtn = new ActionButton(
                "Portal Access",
                ThemeManager.BLUE_ELECTRIC,
                ThemeManager.BLUE_HOVER,
                Color.WHITE,
                null,
                12
        );
        portalBtn.addActionListener(e -> onOpenPortal.accept(PortalCategory.ADMIN, false));

        right.add(stuBtn);
        right.add(portalBtn);

        header.add(left, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel buildHeroSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(new Color(0xF5, 0xFA, 0xFF));
        section.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.SLATE_200),
                new EmptyBorder(26, 36, 30, 36)
        ));

        CampusHeroPanel hero = new CampusHeroPanel(
                onOpenPortal,
                () -> {
                    if (portalCardsAnchor != null) {
                        Rectangle rect = SwingUtilities.convertRectangle(
                                portalCardsAnchor.getParent(),
                                portalCardsAnchor.getBounds(),
                                (Component) scrollPane.getViewport().getView()
                        );
                        scrollPane.getViewport().setViewPosition(new Point(0, Math.max(0, rect.y - 16)));
                    }
                }
        );

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setOpaque(false);
        centerWrap.setMaximumSize(new Dimension(1220, 440));
        centerWrap.add(hero, BorderLayout.CENTER);
        section.add(centerWrap, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildPortalSelectionSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(Color.WHITE);
        section.setBorder(new EmptyBorder(42, 36, 46, 36));
        portalCardsAnchor = section;

        // Centered heading
        JPanel headerBox = new JPanel();
        headerBox.setOpaque(false);
        headerBox.setLayout(new BoxLayout(headerBox, BoxLayout.Y_AXIS));
        headerBox.setAlignmentX(Component.CENTER_ALIGNMENT);

        BadgeLabel eyebrow = new BadgeLabel(
                "ACCOUNT SELECTION AREA",
                new Color(0xEF, 0xF6, 0xFF),
                ThemeManager.BLUE_ELECTRIC,
                new Color(0xBF, 0xDB, 0xFE),
                999
        );
        eyebrow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Choose Your Institutional Portal");
        title.setFont(ThemeManager.font(Font.BOLD, 26));
        title.setForeground(ThemeManager.NAVY_HEADER_DARK);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel desc = new JLabel(
                "Secure, separate access gateways designed for College Leadership, Faculty Coordinators, Corporate Recruiters, and Engineering Students."
        );
        desc.setFont(ThemeManager.font(Font.PLAIN, 13));
        desc.setForeground(ThemeManager.SLATE_600);
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        headerBox.add(eyebrow);
        headerBox.add(Box.createVerticalStrut(10));
        headerBox.add(title);
        headerBox.add(Box.createVerticalStrut(6));
        headerBox.add(desc);

        // 4-Column Portal Cards Grid
        JPanel grid = new JPanel(new GridLayout(1, 4, 18, 18));
        grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(1240, 390));
        grid.setAlignmentX(Component.CENTER_ALIGNMENT);

        grid.add(createPortalCard(
                "ADMIN", "Leadership",
                ThemeManager.NAVY_HEADER_DARK, new Color(0xEF, 0xF6, 0xFF), ThemeManager.BLUE_ELECTRIC,
                new Color(0x60, 0xA5, 0xFA),
                "Dedicated portal for the Principal, Chairman, Vice Chairman, and Placement Officers to oversee institutional policy, approvals, and recruitment drives.",
                new String[]{
                        "Approves Faculty Registrations",
                        "Approves Corporate Recruiters",
                        "Oversees Campus Placements"
                },
                "Admin Sign In", null,
                ThemeManager.NAVY_HEADER_DARK, new Color(0x1E, 0x29, 0x3B),
                null, null,
                false,
                () -> onOpenPortal.accept(PortalCategory.ADMIN, false),
                null
        ));

        grid.add(createPortalCard(
                "FACULTY", "Coordinators",
                ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_SOFT, new Color(0x04, 0x78, 0x57),
                new Color(0x34, 0xD3, 0x99),
                "Departmental coordinators verify student registrations, validate technical skill assessments, and manage departmental student cohorts.",
                new String[]{
                        "Department Student Approval Gate",
                        "Verified Skill Matrix Endorsement",
                        "Department-Level Reports"
                },
                "Faculty Sign In", "Faculty Registration ›",
                ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57),
                ThemeManager.EMERALD_SOFT, new Color(0xA7, 0xF3, 0xD0),
                false,
                () -> onOpenPortal.accept(PortalCategory.FACULTY, false),
                () -> onOpenPortal.accept(PortalCategory.FACULTY, true)
        ));

        grid.add(createPortalCard(
                "COMPANIES", "Recruiters",
                ThemeManager.PURPLE_PRIMARY, ThemeManager.PURPLE_SOFT, new Color(0x7E, 0x22, 0xCE),
                new Color(0xC0, 0x84, 0xFC),
                "Corporate recruiters post hiring drives, filter candidates by technical skill scores, and manage shortlisted engineering talent.",
                new String[]{
                        "Post Placement Drives",
                        "Filter by Verified Skill Scores",
                        "Multi-Stage Candidate Tracking"
                },
                "Recruiter Sign In", "Company Registration ›",
                ThemeManager.PURPLE_PRIMARY, new Color(0x7E, 0x22, 0xCE),
                ThemeManager.PURPLE_SOFT, new Color(0xE9, 0xD5, 0xFF),
                false,
                () -> onOpenPortal.accept(PortalCategory.COMPANIES, false),
                () -> onOpenPortal.accept(PortalCategory.COMPANIES, true)
        ));

        grid.add(createPortalCard(
                "STUDENTS", "Candidates",
                ThemeManager.BLUE_ELECTRIC, new Color(0xEF, 0xF6, 0xFF), ThemeManager.BLUE_ELECTRIC,
                ThemeManager.BLUE_ELECTRIC,
                "GCET engineering students register dynamically, build verified skill matrices, track eligibility, and apply to top tier recruitment drives.",
                new String[]{
                        "2-Field Direct Roll Number Login",
                        "Real-time Eligibility Matching",
                        "Department Faculty Approval Gate"
                },
                "Student Sign In", "Student Registration ›",
                ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER,
                new Color(0xEF, 0xF6, 0xFF), new Color(0xBF, 0xDB, 0xFE),
                true,
                () -> onOpenPortal.accept(PortalCategory.STUDENTS, false),
                () -> onOpenPortal.accept(PortalCategory.STUDENTS, true)
        ));

        section.add(headerBox);
        section.add(Box.createVerticalStrut(28));
        section.add(grid);
        return section;
    }

    private JPanel createPortalCard(
            String roleTitle,
            String pillText,
            Color iconBg,
            Color pillBg,
            Color pillTextCol,
            Color hoverBorder,
            String description,
            String[] checks,
            String primaryBtnText,
            String secondaryBtnText,
            Color primaryBtnBg,
            Color primaryBtnHover,
            Color secondaryBg,
            Color secondaryBorder,
            boolean highlightStudentHub,
            Runnable onPrimary,
            Runnable onSecondary
    ) {
        RoundedCard card = new RoundedCard(
                18,
                Color.WHITE,
                highlightStudentHub ? ThemeManager.BLUE_ELECTRIC : ThemeManager.SLATE_200,
                hoverBorder,
                highlightStudentHub ? 2.0f : 1.0f
        );
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel iconRow = new JPanel(new BorderLayout());
        iconRow.setOpaque(false);
        iconRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        String iconGlyph = switch (roleTitle) {
            case "ADMIN" -> "⛨";
            case "FACULTY" -> "✓";
            case "COMPANIES" -> "❖";
            default -> "★";
        };
        iconRow.add(UiComponents.createIconBadge(iconGlyph, iconBg, Color.WHITE, 46, 20), BorderLayout.WEST);

        if (highlightStudentHub) {
            BadgeLabel ribbon = new BadgeLabel("STUDENT HUB", ThemeManager.BLUE_ELECTRIC, Color.WHITE, null, 8);
            ribbon.setFont(ThemeManager.font(Font.BOLD, 9.5f));
            JPanel ribbonWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            ribbonWrap.setOpaque(false);
            ribbonWrap.add(ribbon);
            iconRow.add(ribbonWrap, BorderLayout.EAST);
        }

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel titleLbl = new JLabel(roleTitle);
        titleLbl.setFont(ThemeManager.font(Font.BOLD, 17));
        titleLbl.setForeground(ThemeManager.NAVY_HEADER_DARK);
        BadgeLabel tag = new BadgeLabel(pillText.toUpperCase(), pillBg, pillTextCol, null, 8);
        tag.setFont(ThemeManager.font(Font.BOLD, 9.5f));
        titleRow.add(titleLbl);
        titleRow.add(tag);

        JLabel descLbl = new JLabel("<html><body style='width:215px; line-height:1.4;'>" + description + "</body></html>");
        descLbl.setFont(ThemeManager.font(Font.PLAIN, 11.8f));
        descLbl.setForeground(ThemeManager.SLATE_500);
        descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel checkList = new JPanel();
        checkList.setOpaque(false);
        checkList.setLayout(new BoxLayout(checkList, BoxLayout.Y_AXIS));
        checkList.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_100),
                new EmptyBorder(10, 0, 0, 0)
        ));
        checkList.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (String item : checks) {
            JLabel chk = new JLabel("✓  " + item);
            chk.setFont(ThemeManager.font(Font.PLAIN, 11.2f));
            chk.setForeground(ThemeManager.SLATE_600);
            chk.setBorder(new EmptyBorder(2, 0, 2, 0));
            checkList.add(chk);
        }

        top.add(iconRow);
        top.add(Box.createVerticalStrut(12));
        top.add(titleRow);
        top.add(Box.createVerticalStrut(6));
        top.add(descLbl);
        top.add(Box.createVerticalStrut(10));
        top.add(checkList);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_100),
                new EmptyBorder(12, 0, 0, 0)
        ));

        ActionButton primaryBtn = new ActionButton(primaryBtnText, primaryBtnBg, primaryBtnHover, Color.WHITE, null, 12);
        primaryBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        primaryBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        primaryBtn.addActionListener(e -> onPrimary.run());
        bottom.add(primaryBtn);

        if (secondaryBtnText != null && onSecondary != null) {
            bottom.add(Box.createVerticalStrut(8));
            ActionButton secBtn = new ActionButton(secondaryBtnText, secondaryBg, secondaryBorder, pillTextCol, secondaryBorder, 12);
            secBtn.setFont(ThemeManager.font(Font.BOLD, 11));
            secBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            secBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
            secBtn.addActionListener(e -> onSecondary.run());
            bottom.add(secBtn);
        }

        card.add(top, BorderLayout.NORTH);
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildFeaturesSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ThemeManager.SLATE_50);
        section.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_200),
                new EmptyBorder(42, 36, 46, 36)
        ));

        BadgeLabel eyebrow = new BadgeLabel(
                "PLATFORM MODULES",
                new Color(0xEF, 0xF6, 0xFF),
                ThemeManager.BLUE_ELECTRIC,
                new Color(0xBF, 0xDB, 0xFE),
                999
        );
        eyebrow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Comprehensive Placement Features");
        title.setFont(ThemeManager.font(Font.BOLD, 25));
        title.setForeground(ThemeManager.NAVY_HEADER_DARK);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Explore the core capabilities built into the GCET Placement Hub to streamline campus recruitment and skill readiness.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 13));
        sub.setForeground(ThemeManager.SLATE_600);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel grid = new JPanel(new GridLayout(2, 4, 16, 16));
        grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(1240, 340));
        grid.setAlignmentX(Component.CENTER_ALIGNMENT);

        grid.add(createFeatureCard("Student Profile Management",
                "Centralized academic profiles with verified CGPA, 10th & Intermediate percentages, branch specialization, and resume links.",
                "👥", new Color(0xEF, 0xF6, 0xFF), ThemeManager.BLUE_ELECTRIC));
        grid.add(createFeatureCard("Technical Skill Matrix",
                "12+ core engineering skills evaluated across Java, Python, SQL, and Data Structures with official faculty endorsement ratings.",
                "◎", new Color(0xEC, 0xFE, 0xFF), ThemeManager.CYAN_DARK));
        grid.add(createFeatureCard("Placement Eligibility Engine",
                "Automated multi-criteria evaluation matching student qualifications against drive minimum CGPA, eligible branches, and backlog policies.",
                "✓", ThemeManager.EMERALD_SOFT, ThemeManager.EMERALD_PRIMARY));
        grid.add(createFeatureCard("Company Management",
                "Structured hiring partner directory categorized by Tier 1 (Dream), Tier 2 (Super Dream), and Core engineering domains.",
                "❖", ThemeManager.PURPLE_SOFT, ThemeManager.PURPLE_PRIMARY));
        grid.add(createFeatureCard("Placement Drive Management",
                "Publish and manage campus drives, CTC salary packages, registration deadlines, online assessments, and interview round logistics.",
                "★", ThemeManager.AMBER_SOFT, ThemeManager.AMBER_PRIMARY));
        grid.add(createFeatureCard("Student Approval System",
                "Role-gated departmental verification gate where authorized faculty coordinators review and approve student registrations before login.",
                "⛨", new Color(0xF0, 0xFD, 0xFA), new Color(0x0D, 0x94, 0x88)));
        grid.add(createFeatureCard("Placement Tracking",
                "Real-time tracking of candidate progression across application, test shortlisted, technical rounds, HR interview, and final offer letters.",
                "↗", ThemeManager.ROSE_SOFT, ThemeManager.ROSE_PRIMARY));
        grid.add(createFeatureCard("Reports & Analytics",
                "Database-driven institutional reports, department-wise placement percentages, salary distribution charts, and exportable data.",
                "▤", new Color(0xEE, 0xF2, 0xFF), new Color(0x4F, 0x46, 0xE5)));

        section.add(eyebrow);
        section.add(Box.createVerticalStrut(10));
        section.add(title);
        section.add(Box.createVerticalStrut(6));
        section.add(sub);
        section.add(Box.createVerticalStrut(26));
        section.add(grid);
        return section;
    }

    private JPanel createFeatureCard(String title, String body, String icon, Color iconBg, Color iconFg) {
        RoundedCard card = new RoundedCard(16, Color.WHITE, ThemeManager.SLATE_200, ThemeManager.SLATE_300, 1.0f);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel badge = UiComponents.createIconBadge(icon, iconBg, iconFg, 38, 16);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(ThemeManager.font(Font.BOLD, 13.5f));
        titleLbl.setForeground(ThemeManager.NAVY_HEADER_DARK);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel bodyLbl = new JLabel("<html><body style='width:225px; line-height:1.4;'>" + body + "</body></html>");
        bodyLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        bodyLbl.setForeground(ThemeManager.SLATE_500);
        bodyLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(badge);
        card.add(Box.createVerticalStrut(12));
        card.add(titleLbl);
        card.add(Box.createVerticalStrut(6));
        card.add(bodyLbl);
        return card;
    }

    private JPanel buildInstitutionTabsSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(Color.WHITE);
        section.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_200),
                new EmptyBorder(40, 36, 46, 36)
        ));

        JPanel tabsRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        tabsRow.setOpaque(false);

        tabOverviewBtn = createTabButton("Institution Overview", true);
        tabPlacementBtn = createTabButton("About Placement Cell", false);
        tabAccredBtn = createTabButton("Accreditations & Honors", false);

        tabOverviewBtn.addActionListener(e -> switchAboutTab("OVERVIEW", tabOverviewBtn));
        tabPlacementBtn.addActionListener(e -> switchAboutTab("PLACEMENT_CELL", tabPlacementBtn));
        tabAccredBtn.addActionListener(e -> switchAboutTab("ACCREDITATIONS", tabAccredBtn));

        tabsRow.add(tabOverviewBtn);
        tabsRow.add(tabPlacementBtn);
        tabsRow.add(tabAccredBtn);

        RoundedCard tabWrapper = new RoundedCard(18, ThemeManager.SLATE_50, ThemeManager.SLATE_200);
        tabWrapper.setLayout(new BorderLayout());
        tabWrapper.setBorder(new EmptyBorder(24, 24, 24, 24));
        tabWrapper.setMaximumSize(new Dimension(1240, 290));

        aboutTabContent.setOpaque(false);
        aboutTabContent.add(buildOverviewTab(), "OVERVIEW");
        aboutTabContent.add(buildPlacementCellTab(), "PLACEMENT_CELL");
        aboutTabContent.add(buildAccreditationsTab(), "ACCREDITATIONS");
        tabWrapper.add(aboutTabContent, BorderLayout.CENTER);

        section.add(tabsRow);
        section.add(Box.createVerticalStrut(22));
        section.add(tabWrapper);
        return section;
    }

    private JButton createTabButton(String text, boolean active) {
        ActionButton btn = new ActionButton(
                text,
                active ? ThemeManager.NAVY_HEADER_DARK : ThemeManager.SLATE_100,
                active ? ThemeManager.NAVY_HEADER_DARK : ThemeManager.SLATE_200,
                active ? Color.WHITE : ThemeManager.SLATE_600,
                null,
                12
        );
        btn.setFont(ThemeManager.font(Font.BOLD, 12));
        return btn;
    }

    private void switchAboutTab(String cardName, JButton activeBtn) {
        aboutTabCards.show(aboutTabContent, cardName);
        for (JButton btn : new JButton[]{tabOverviewBtn, tabPlacementBtn, tabAccredBtn}) {
            boolean selected = btn == activeBtn;
            ((ActionButton) btn).updateColors(
                    selected ? ThemeManager.NAVY_HEADER_DARK : ThemeManager.SLATE_100,
                    selected ? ThemeManager.NAVY_HEADER_DARK : ThemeManager.SLATE_200,
                    selected ? Color.WHITE : ThemeManager.SLATE_600,
                    null
            );
        }
    }

    private JPanel buildOverviewTab() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 28, 0));
        panel.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Geethanjali College of Engineering and Technology");
        title.setFont(ThemeManager.font(Font.BOLD, 19));
        title.setForeground(ThemeManager.NAVY_HEADER_DARK);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel addr = new JLabel("Cheeryal (V), Keesara (M), Medchal-Malkajgiri District, Telangana - 501301");
        addr.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        addr.setForeground(ThemeManager.BLUE_ELECTRIC);
        addr.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel body = new JLabel(
                "<html><body style='width:500px; line-height:1.5;'>"
                        + "Established in 2005 under the visionary leadership of the Teja Educational Society, Geethanjali College "
                        + "of Engineering and Technology stands as an autonomous pillar of academic and technical excellence. Spanning a "
                        + "verdant, state-of-the-art campus in Cheeryal, the institution imparts industry-aligned education in Computer "
                        + "Science, Artificial Intelligence, Data Science, Cyber Security, Electronics, Electrical, Mechanical, and Civil Engineering."
                        + "</body></html>"
        );
        body.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        body.setForeground(ThemeManager.SLATE_600);
        body.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.add(new BadgeLabel("UGC Autonomous Since 2016", Color.WHITE, ThemeManager.SLATE_700, ThemeManager.SLATE_200, 10));
        chips.add(new BadgeLabel("Approved by AICTE New Delhi", Color.WHITE, ThemeManager.SLATE_700, ThemeManager.SLATE_200, 10));
        chips.add(new BadgeLabel("Affiliated to JNTU Hyderabad", Color.WHITE, ThemeManager.SLATE_700, ThemeManager.SLATE_200, 10));

        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(addr);
        left.add(Box.createVerticalStrut(12));
        left.add(body);
        left.add(Box.createVerticalStrut(14));
        left.add(chips);

        RoundedCard right = new RoundedCard(16, Color.WHITE, ThemeManager.SLATE_200);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel leadHdr = new JLabel("INSTITUTIONAL LEADERSHIP");
        leadHdr.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        leadHdr.setForeground(ThemeManager.BLUE_ELECTRIC);
        leadHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        right.add(leadHdr);
        right.add(Box.createVerticalStrut(12));

        right.add(createLeadershipRow("Mr. G. R. Ravinder Reddy", "Teja Educational Society", "Chairman", false, true));
        right.add(createLeadershipRow("Mr. R. Harishchandra Reddy", "Institutional Governance", "Vice Chairman", false, true));
        right.add(createLeadershipRow("Dr. K. Sagar", "Executive Head of Institution", "Principal", true, false));

        panel.add(left);
        panel.add(right);
        return panel;
    }

    private JPanel createLeadershipRow(String name, String org, String role, boolean bluePill, boolean divider) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, divider ? 1 : 0, 0, ThemeManager.SLATE_100),
                new EmptyBorder(7, 0, 7, 0)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel n = new JLabel(name);
        n.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        n.setForeground(ThemeManager.NAVY_HEADER_DARK);
        JLabel o = new JLabel(org);
        o.setFont(ThemeManager.font(Font.PLAIN, 11));
        o.setForeground(ThemeManager.SLATE_400);
        left.add(n);
        left.add(o);

        BadgeLabel badge = new BadgeLabel(
                role,
                bluePill ? new Color(0xEF, 0xF6, 0xFF) : ThemeManager.SLATE_100,
                bluePill ? ThemeManager.BLUE_ELECTRIC : ThemeManager.SLATE_700,
                bluePill ? new Color(0xBF, 0xDB, 0xFE) : null,
                8
        );
        badge.setFont(ThemeManager.font(Font.BOLD, 11));

        JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        rightWrap.setOpaque(false);
        rightWrap.add(badge);

        row.add(left, BorderLayout.WEST);
        row.add(rightWrap, BorderLayout.EAST);
        return row;
    }

    private JPanel buildPlacementCellTab() {
        JPanel grid = new JPanel(new GridLayout(1, 3, 18, 0));
        grid.setOpaque(false);
        grid.add(createPillarCard("01", "Industry Alignment",
                "Bridging academia and the modern tech ecosystem through corporate training modules, guest masterclasses, and hands-on hackathons.",
                new Color(0xDB, 0xEA, 0xFE), ThemeManager.BLUE_ELECTRIC));
        grid.add(createPillarCard("02", "Skill Matrix Benchmarking",
                "Evaluating students across Programming, Data Structures, Cloud, and AI with faculty-verified endorsements rather than unverified ratings.",
                new Color(0xCF, 0xFA, 0xFE), ThemeManager.CYAN_DARK));
        grid.add(createPillarCard("03", "Rigorous Approval Gates",
                "Strict role segregation ensures only verified students and authorized corporate recruiters participate in on-campus recruitment cycles.",
                new Color(0xD1, 0xFA, 0xE5), ThemeManager.EMERALD_PRIMARY));
        return grid;
    }

    private JPanel createPillarCard(String num, String title, String desc, Color numBg, Color numFg) {
        RoundedCard card = new RoundedCard(14, Color.WHITE, ThemeManager.SLATE_200);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel badge = UiComponents.createIconBadge(num, numBg, numFg, 32, 12);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.font(Font.BOLD, 14));
        t.setForeground(ThemeManager.NAVY_HEADER_DARK);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel d = new JLabel("<html><body style='width:295px; line-height:1.45;'>" + desc + "</body></html>");
        d.setFont(ThemeManager.font(Font.PLAIN, 12));
        d.setForeground(ThemeManager.SLATE_600);
        d.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(badge);
        card.add(Box.createVerticalStrut(10));
        card.add(t);
        card.add(Box.createVerticalStrut(6));
        card.add(d);
        return card;
    }

    private JPanel buildAccreditationsTab() {
        JPanel grid = new JPanel(new GridLayout(1, 4, 16, 0));
        grid.setOpaque(false);
        grid.add(createAccredCard("NAAC 'A+' Grade", "National Assessment & Accreditation Council", ThemeManager.AMBER_PRIMARY, new Color(0xFD, 0xE6, 0x8A)));
        grid.add(createAccredCard("NBA Accredited", "National Board of Accreditation for Core Branches", ThemeManager.BLUE_ELECTRIC, new Color(0xBF, 0xDB, 0xFE)));
        grid.add(createAccredCard("UGC Autonomous", "Autonomous Status Conferred by UGC New Delhi", ThemeManager.EMERALD_PRIMARY, new Color(0xA7, 0xF3, 0xD0)));
        grid.add(createAccredCard("JNTUH Affiliation", "Permanent Affiliation to JNTU Hyderabad", ThemeManager.PURPLE_PRIMARY, new Color(0xE9, 0xD5, 0xFF)));
        return grid;
    }

    private JPanel createAccredCard(String title, String sub, Color accent, Color border) {
        RoundedCard card = new RoundedCard(14, Color.WHITE, border);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 16, 22, 16));

        JLabel star = new JLabel("★");
        star.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        star.setForeground(accent);
        star.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.font(Font.BOLD, 14));
        t.setForeground(ThemeManager.NAVY_HEADER_DARK);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel s = new JLabel("<html><center>" + sub + "</center></html>");
        s.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        s.setForeground(ThemeManager.SLATE_500);
        s.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(Box.createVerticalGlue());
        card.add(star);
        card.add(Box.createVerticalStrut(8));
        card.add(t);
        card.add(Box.createVerticalStrut(4));
        card.add(s);
        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 20));
        footer.setBackground(ThemeManager.NAVY_HEADER_DARK);
        footer.setBorder(new EmptyBorder(34, 36, 24, 36));

        JPanel cols = new JPanel(new GridLayout(1, 3, 28, 0));
        cols.setOpaque(false);

        // Col 1: Brand
        JPanel col1 = new JPanel();
        col1.setOpaque(false);
        col1.setLayout(new BoxLayout(col1, BoxLayout.Y_AXIS));
        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandRow.add(new JLabel(ResourceLoader.createCircularLogoIcon(40, new Color(50, 197, 244, 140))));
        JLabel fn = new JLabel("GCET PLACEMENT HUB");
        fn.setFont(ThemeManager.font(Font.BOLD, 16));
        fn.setForeground(Color.WHITE);
        brandRow.add(fn);

        JLabel fdesc = new JLabel("<html><body style='width:350px; line-height:1.45;'>Geethanjali College of Engineering and Technology. An Autonomous Institution committed to shaping industry-ready engineering professionals through rigorous academic curricula, skill centers, and seamless placement workflows.</body></html>");
        fdesc.setFont(ThemeManager.font(Font.PLAIN, 11.8f));
        fdesc.setForeground(ThemeManager.SLATE_400);
        fdesc.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel ftag = new JLabel("Tagline: \"Your Skills. Your Opportunities. Your Future.\"");
        ftag.setFont(ThemeManager.font(Font.BOLD, 11.2f));
        ftag.setForeground(new Color(0x67, 0xE8, 0xF9));
        ftag.setAlignmentX(Component.LEFT_ALIGNMENT);

        col1.add(brandRow);
        col1.add(Box.createVerticalStrut(10));
        col1.add(fdesc);
        col1.add(Box.createVerticalStrut(10));
        col1.add(ftag);

        // Col 2: Links
        JPanel col2 = new JPanel();
        col2.setOpaque(false);
        col2.setLayout(new BoxLayout(col2, BoxLayout.Y_AXIS));
        JLabel c2Hdr = new JLabel("INSTITUTIONAL PORTALS");
        c2Hdr.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        c2Hdr.setForeground(Color.WHITE);
        col2.add(c2Hdr);
        col2.add(Box.createVerticalStrut(10));
        col2.add(createHyperlink("GCTC Student & Faculty Portal ↗", "https://www.gctcportal.in/", ThemeManager.SLATE_300, ThemeManager.CYAN_ACCENT, 12));
        col2.add(Box.createVerticalStrut(6));
        col2.add(createHyperlink("Official GCET Website ↗", "https://gcet.edu.in/", ThemeManager.SLATE_300, ThemeManager.CYAN_ACCENT, 12));
        col2.add(Box.createVerticalStrut(6));
        col2.add(createHyperlink("Training & Placement Cell ↗", "https://gcet.edu.in/placements.html", ThemeManager.SLATE_300, ThemeManager.CYAN_ACCENT, 12));

        // Col 3: Contact
        JPanel col3 = new JPanel();
        col3.setOpaque(false);
        col3.setLayout(new BoxLayout(col3, BoxLayout.Y_AXIS));
        JLabel c3Hdr = new JLabel("CAMPUS CONTACT");
        c3Hdr.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        c3Hdr.setForeground(Color.WHITE);
        col3.add(c3Hdr);
        col3.add(Box.createVerticalStrut(10));
        JLabel inst = new JLabel("Geethanjali College of Engg. & Tech.");
        inst.setFont(ThemeManager.font(Font.BOLD, 12));
        inst.setForeground(Color.WHITE);
        col3.add(inst);
        col3.add(createFooterText("Sy. No. 33 & 34, Cheeryal (V), Keesara (M)"));
        col3.add(createFooterText("Medchal-Malkajgiri Dist., Telangana - 501301"));
        col3.add(Box.createVerticalStrut(8));
        col3.add(createFooterAccent("☎  +91 9182058187"));
        col3.add(createFooterAccent("✉  placements@gcet.edu.in"));

        cols.add(col1);
        cols.add(col2);
        cols.add(col3);

        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setOpaque(false);
        bottomBar.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.SLATE_800),
                new EmptyBorder(14, 0, 0, 0)
        ));
        JLabel copy = new JLabel("© " + Year.now().getValue() + " Geethanjali College of Engineering and Technology. All rights reserved.");
        copy.setFont(ThemeManager.font(Font.PLAIN, 11));
        copy.setForeground(ThemeManager.SLATE_400);
        JLabel rightTag = new JLabel("GCET Placement Hub · Java 17 Desktop & MySQL Client");
        rightTag.setFont(ThemeManager.font(Font.PLAIN, 11));
        rightTag.setForeground(ThemeManager.SLATE_400);
        bottomBar.add(copy, BorderLayout.WEST);
        bottomBar.add(rightTag, BorderLayout.EAST);

        footer.add(cols, BorderLayout.CENTER);
        footer.add(bottomBar, BorderLayout.SOUTH);
        return footer;
    }

    private JLabel createFooterText(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        lbl.setForeground(ThemeManager.SLATE_400);
        return lbl;
    }

    private JLabel createFooterAccent(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        lbl.setForeground(ThemeManager.SLATE_300);
        return lbl;
    }

    private JLabel createHyperlink(String label, String url, Color base, Color hover, float size) {
        JLabel link = new JLabel(label);
        link.setFont(ThemeManager.font(Font.BOLD, size));
        link.setForeground(base);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { link.setForeground(hover); }
            @Override
            public void mouseExited(MouseEvent e) { link.setForeground(base); }
            @Override
            public void mouseClicked(MouseEvent e) { ResourceLoader.openExternalUrl(WelcomePanel.this, url); }
        });
        return link;
    }
}
