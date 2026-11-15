package edu.gcet.placementhub.ui.students;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.StudentController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exact Java Swing recreation of StudentProfilePage.tsx:
 * Comprehensive individual student profile backed by live MySQL records,
 * featuring Academic Stats, Profile Readiness Gauge, Technical Skills & Faculty Verification,
 * Technical Projects, Certifications & Credentials, and Industrial Internships.
 */
public class StudentProfilePanel extends JPanel {

    private static final String[] PROFICIENCY_LABELS = {
            "", "1 - Beginner", "2 - Foundational", "3 - Intermediate", "4 - Advanced", "5 - Expert"
    };

    private final StudentController controller = new StudentController();
    private final Consumer<String> onNavigatePage;

    private List<Student> students = List.of();
    private List<Skill> skills = List.of();
    private Map<String, Skill> skillMap = new HashMap<>();
    private String selectedStudentId;
    private boolean suppressSelector = false;

    private JComboBox<StudentItem> studentSelectorCombo;
    private JPanel selectorWrap;
    private AlertBanner statusBanner;
    private JPanel contentArea;

    public StudentProfilePanel(Consumer<String> onNavigatePage) {
        this.onNavigatePage = onNavigatePage;
        setLayout(new BorderLayout());
        setBackground(ThemeManager.surfaceBg());
        buildShell();
    }

    public void setSelectedStudentId(String studentId) {
        this.selectedStudentId = studentId;
    }

    public void loadData() {
        User currentUser = AppSession.getInstance().getCurrentUser();
        controller.loadDirectoryBundleAsync(currentUser, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.students = bundle.students != null ? bundle.students : List.of();
            this.skills = bundle.skills != null ? bundle.skills : List.of();
            this.skillMap = new HashMap<>();
            for (Skill sk : this.skills) {
                skillMap.put(sk.getId(), sk);
            }
            resolveInitialSelection(currentUser);
            populateSelector(currentUser);
            renderSelectedStudent();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildShell();
        populateSelector(AppSession.getInstance().getCurrentUser());
        renderSelectedStudent();
        revalidate();
        repaint();
    }

    private void buildShell() {
        JPanel outer = new JPanel();
        outer.setOpaque(false);
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setBorder(new EmptyBorder(20, 22, 26, 22));

        // Top Selector & Controls Bar
        RoundedCard topBar = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        topBar.setLayout(new BorderLayout(12, 8));
        topBar.setBorder(new EmptyBorder(12, 16, 12, 16));
        topBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        selectorWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        selectorWrap.setOpaque(false);
        JLabel selLbl = new JLabel("Inspect Student Record:");
        selLbl.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        selLbl.setForeground(ThemeManager.primaryText());
        studentSelectorCombo = new JComboBox<>();
        studentSelectorCombo.setPreferredSize(new Dimension(380, 34));
        studentSelectorCombo.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        studentSelectorCombo.addActionListener(e -> {
            if (suppressSelector) return;
            Object item = studentSelectorCombo.getSelectedItem();
            if (item instanceof StudentItem si) {
                this.selectedStudentId = si.student.getId();
                renderSelectedStudent();
            }
        });
        selectorWrap.add(selLbl);
        selectorWrap.add(studentSelectorCombo);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);
        ActionButton refreshBtn = new ActionButton("↻  Refresh MySQL", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        ActionButton gapBtn = new ActionButton("Skill Gap Analysis  →", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, ThemeManager.cardBorder(), 10);
        gapBtn.addActionListener(e -> onNavigatePage.accept("skill-gap"));
        rightBtns.add(refreshBtn);
        rightBtns.add(gapBtn);

        topBar.add(selectorWrap, BorderLayout.WEST);
        topBar.add(rightBtns, BorderLayout.EAST);

        statusBanner = new AlertBanner();
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        contentArea = new JPanel();
        contentArea.setOpaque(false);
        contentArea.setLayout(new BoxLayout(contentArea, BoxLayout.Y_AXIS));
        contentArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        outer.add(topBar);
        outer.add(Box.createVerticalStrut(10));
        outer.add(statusBanner);
        outer.add(Box.createVerticalStrut(6));
        outer.add(contentArea);

        JScrollPane scroll = new JScrollPane(outer);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getViewport().setBackground(ThemeManager.surfaceBg());
        add(scroll, BorderLayout.CENTER);
    }

    private void resolveInitialSelection(User u) {
        if (students.isEmpty()) {
            selectedStudentId = null;
            return;
        }
        if (u != null && u.getRole() == UserRole.STUDENT && u.getStudentRollNumber() != null) {
            for (Student s : students) {
                if (u.getStudentRollNumber().equalsIgnoreCase(s.getRollNumber())) {
                    selectedStudentId = s.getId();
                    return;
                }
            }
        }
        if (selectedStudentId != null) {
            for (Student s : students) {
                if (selectedStudentId.equals(s.getId())) return;
            }
        }
        selectedStudentId = students.get(0).getId();
    }

    private void populateSelector(User u) {
        boolean isStudentRole = u != null && u.getRole() == UserRole.STUDENT;
        selectorWrap.setVisible(!isStudentRole);

        suppressSelector = true;
        try {
            studentSelectorCombo.removeAllItems();
            StudentItem activeItem = null;
            for (Student s : students) {
                StudentItem item = new StudentItem(s);
                studentSelectorCombo.addItem(item);
                if (s.getId().equals(selectedStudentId)) {
                    activeItem = item;
                }
            }
            if (activeItem != null) {
                studentSelectorCombo.setSelectedItem(activeItem);
            }
        } finally {
            suppressSelector = false;
        }
    }

    private Student findActiveStudent() {
        if (students.isEmpty()) return null;
        if (selectedStudentId != null) {
            for (Student s : students) {
                if (selectedStudentId.equals(s.getId())) return s;
            }
        }
        return students.get(0);
    }

    private void renderSelectedStudent() {
        contentArea.removeAll();
        Student s = findActiveStudent();
        User currentUser = AppSession.getInstance().getCurrentUser();

        if (s == null) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setLayout(new GridBagLayout());
            empty.setPreferredSize(new Dimension(700, 180));
            JLabel lbl = new JLabel("No student records found in MySQL `students` table.");
            lbl.setForeground(ThemeManager.mutedText());
            empty.add(lbl);
            contentArea.add(empty);
            contentArea.revalidate();
            contentArea.repaint();
            return;
        }

        boolean isRecruiter = currentUser != null && currentUser.getRole() == UserRole.COMPANY;
        boolean canEditStudent = currentUser != null && !isRecruiter && (
                currentUser.getRole() != UserRole.STUDENT
                        || (currentUser.getStudentRollNumber() != null && currentUser.getStudentRollNumber().equalsIgnoreCase(s.getRollNumber()))
        );
        boolean canVerifySkills = currentUser != null
                && currentUser.getRole() != UserRole.STUDENT
                && currentUser.getRole() != UserRole.COMPANY;

        // 1. Hero Profile Header Card
        contentArea.add(buildHeroCard(s, canEditStudent, currentUser));
        contentArea.add(Box.createVerticalStrut(14));

        // 2. Academic KPI Row + Completeness Meter
        contentArea.add(buildAcademicAndCompletenessRow(s));
        contentArea.add(Box.createVerticalStrut(16));

        // 3. Technical Skills & Proficiency Matrix
        contentArea.add(buildSkillsSection(s, canEditStudent, canVerifySkills, currentUser));
        contentArea.add(Box.createVerticalStrut(16));

        // 4. Projects & Certifications Split Row
        JPanel portGrid = new JPanel(new GridLayout(1, 2, 16, 0));
        portGrid.setOpaque(false);
        portGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        portGrid.add(buildProjectsSection(s, canEditStudent));
        portGrid.add(buildCertificationsSection(s, canEditStudent));
        contentArea.add(portGrid);
        contentArea.add(Box.createVerticalStrut(16));

        // 5. Industrial Internships Section
        contentArea.add(buildInternshipsSection(s, canEditStudent));

        contentArea.revalidate();
        contentArea.repaint();
    }

    private JPanel buildHeroCard(Student s, boolean canEditStudent, User currentUser) {
        RoundedCard card = new RoundedCard(16, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(18, 10));
        card.setBorder(new EmptyBorder(20, 22, 20, 22));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 175));

        // Avatar circle
        String initial = (s.getFullName() != null && !s.getFullName().isBlank())
                ? s.getFullName().substring(0, 1).toUpperCase(Locale.ROOT)
                : "S";
        RoundedCard avatar = new RoundedCard(999, ThemeManager.NAVY_DEEP, ThemeManager.CYAN_ACCENT, null, 2.0f);
        avatar.setPreferredSize(new Dimension(72, 72));
        avatar.setMinimumSize(new Dimension(72, 72));
        avatar.setMaximumSize(new Dimension(72, 72));
        avatar.setLayout(new GridBagLayout());
        JLabel initLbl = new JLabel(initial);
        initLbl.setFont(ThemeManager.font(Font.BOLD, 28));
        initLbl.setForeground(Color.WHITE);
        avatar.add(initLbl);

        JPanel avatarWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        avatarWrap.setOpaque(false);
        avatarWrap.add(avatar);

        // Center details
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel nameLbl = new JLabel(nonNull(s.getFullName()));
        nameLbl.setFont(ThemeManager.font(Font.BOLD, 20));
        nameLbl.setForeground(ThemeManager.primaryText());

        boolean placed = "PLACED".equalsIgnoreCase(s.getPlacementStatus());
        BadgeLabel placeBadge = new BadgeLabel(
                placed && s.getPlacedPackageLpa() != null
                        ? String.format(Locale.US, "PLACED · %.1f LPA", s.getPlacedPackageLpa())
                        : (placed ? "PLACED" : "UNPLACED / ACTIVE"),
                placed ? new Color(16, 185, 129, 25) : new Color(38, 132, 255, 25),
                placed ? ThemeManager.EMERALD_PRIMARY : ThemeManager.BLUE_ELECTRIC,
                null, 999
        );
        boolean approved = "APPROVED".equalsIgnoreCase(s.getApprovalStatus());
        BadgeLabel appBadge = new BadgeLabel(
                approved ? "FACULTY APPROVED" : nonNull(s.getApprovalStatus()),
                approved ? new Color(16, 185, 129, 20) : new Color(217, 119, 6, 25),
                approved ? ThemeManager.EMERALD_PRIMARY : ThemeManager.AMBER_PRIMARY,
                null, 999
        );

        nameRow.add(nameLbl);
        nameRow.add(placeBadge);
        nameRow.add(appBadge);

        JLabel subRow = new JLabel("Roll No: " + nonNull(s.getRollNumber())
                + "   ·   Branch: " + nonNull(s.getBranch())
                + "   ·   Batch: " + (s.getGraduationYear() > 0 ? s.getGraduationYear() : 2026)
                + (s.getStudentIdCard() != null && !s.getStudentIdCard().isBlank() ? "   ·   ID: " + s.getStudentIdCard() : ""));
        subRow.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        subRow.setForeground(ThemeManager.CYAN_DARK);
        subRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel contactRow = new JLabel("Email: " + nonNull(s.getEmail())
                + "   ·   Phone: " + (s.getPhone() != null && !s.getPhone().isBlank() ? s.getPhone() : "Not Provided")
                + "   ·   Mentor: " + DeptNormalizer.assignedCoordinatorLabel(s.getBranch()));
        contactRow.setFont(ThemeManager.font(Font.PLAIN, 12));
        contactRow.setForeground(ThemeManager.secondaryText());
        contactRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        center.add(nameRow);
        center.add(Box.createVerticalStrut(5));
        center.add(subRow);
        center.add(Box.createVerticalStrut(5));
        center.add(contactRow);

        // Right actions
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));

        if (canEditStudent) {
            ActionButton editBtn = new ActionButton("Edit Profile Details", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
            editBtn.setAlignmentX(Component.RIGHT_ALIGNMENT);
            editBtn.addActionListener(e -> openEditProfileDialog(s, currentUser));
            actions.add(editBtn);
            actions.add(Box.createVerticalStrut(8));
        }

        if (s.getResumeUrl() != null && !s.getResumeUrl().isBlank()) {
            ActionButton resBtn = new ActionButton("Open Resume Link  ↗", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
            resBtn.setAlignmentX(Component.RIGHT_ALIGNMENT);
            resBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, s.getResumeUrl()));
            actions.add(resBtn);
        }

        card.add(avatarWrap, BorderLayout.WEST);
        card.add(center, BorderLayout.CENTER);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private JPanel buildAcademicAndCompletenessRow(Student s) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        JPanel statsGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        statsGrid.setOpaque(false);
        statsGrid.add(metricTile("Current CGPA", String.format(Locale.US, "%.2f / 10", s.getCgpa()), ThemeManager.BLUE_ELECTRIC));
        statsGrid.add(metricTile("10th / SSC %", String.format(Locale.US, "%.1f%%", s.getTenthPercentage()), ThemeManager.CYAN_DARK));
        statsGrid.add(metricTile("Inter / Diploma %", String.format(Locale.US, "%.1f%%", s.getInterPercentage()), ThemeManager.PURPLE_PRIMARY));
        statsGrid.add(metricTile("Active Backlogs",
                s.getBacklogs() == 0 ? "0 (Clear)" : String.valueOf(s.getBacklogs()),
                s.getBacklogs() == 0 ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY));

        int pct = computeCompleteness(s);
        RoundedCard meterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        meterCard.setLayout(new BoxLayout(meterCard, BoxLayout.Y_AXIS));
        meterCard.setBorder(new EmptyBorder(14, 18, 14, 18));
        meterCard.setPreferredSize(new Dimension(280, 96));

        JPanel mHead = new JPanel(new BorderLayout());
        mHead.setOpaque(false);
        mHead.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel mLbl = new JLabel("Profile Readiness");
        mLbl.setFont(ThemeManager.font(Font.BOLD, 12));
        mLbl.setForeground(ThemeManager.primaryText());
        JLabel mPct = new JLabel(pct + "%");
        mPct.setFont(ThemeManager.font(Font.BOLD, 14));
        mPct.setForeground(pct >= 80 ? ThemeManager.EMERALD_PRIMARY : ThemeManager.BLUE_ELECTRIC);
        mHead.add(mLbl, BorderLayout.WEST);
        mHead.add(mPct, BorderLayout.EAST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setValue(pct);
        bar.setPreferredSize(new Dimension(240, 10));
        bar.setForeground(pct >= 80 ? ThemeManager.EMERALD_PRIMARY : ThemeManager.BLUE_ELECTRIC);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel mHint = new JLabel("Personal · Academics · Skills · Projects · Certs");
        mHint.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
        mHint.setForeground(ThemeManager.mutedText());
        mHint.setAlignmentX(Component.LEFT_ALIGNMENT);

        meterCard.add(mHead);
        meterCard.add(Box.createVerticalStrut(8));
        meterCard.add(bar);
        meterCard.add(Box.createVerticalStrut(6));
        meterCard.add(mHint);

        row.add(statsGrid, BorderLayout.CENTER);
        row.add(meterCard, BorderLayout.EAST);
        return row;
    }

    private JPanel metricTile(String label, String value, Color accent) {
        RoundedCard tile = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));
        tile.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        l.setForeground(ThemeManager.mutedText());
        JLabel v = new JLabel(value);
        v.setFont(ThemeManager.font(Font.BOLD, 20));
        v.setForeground(accent);
        tile.add(l);
        tile.add(Box.createVerticalStrut(5));
        tile.add(v);
        return tile;
    }

    private JPanel buildSkillsSection(Student s, boolean canEditStudent, boolean canVerifySkills, User currentUser) {
        RoundedCard card = new RoundedCard(16, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel tCol = new JPanel();
        tCol.setOpaque(false);
        tCol.setLayout(new BoxLayout(tCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Technical Skills & Proficiency Matrix (" + s.getSkills().size() + ")");
        title.setFont(ThemeManager.font(Font.BOLD, 15.5f));
        title.setForeground(ThemeManager.primaryText());
        JLabel sub = new JLabel("Assessed on a 1 (Beginner) to 5 (Expert) scale with GCET faculty verification.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sub.setForeground(ThemeManager.mutedText());
        tCol.add(title);
        tCol.add(Box.createVerticalStrut(2));
        tCol.add(sub);

        head.add(tCol, BorderLayout.WEST);
        if (canEditStudent) {
            ActionButton addBtn = new ActionButton("+  Add / Update Skill", ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 10);
            addBtn.addActionListener(e -> openAddSkillDialog(s, canVerifySkills, currentUser));
            head.add(addBtn, BorderLayout.EAST);
        }

        JPanel body = new JPanel(new GridLayout(0, 2, 12, 12));
        body.setOpaque(false);
        if (s.getSkills().isEmpty()) {
            JLabel empty = new JLabel("No technical skills recorded yet. Use '+ Add / Update Skill' to record competencies in MySQL.");
            empty.setFont(ThemeManager.font(Font.PLAIN, 12));
            empty.setForeground(ThemeManager.mutedText());
            body.add(empty);
        } else {
            for (StudentSkill sk : s.getSkills()) {
                Skill catalog = skillMap.get(sk.getSkillId());
                String skillName = catalog != null ? catalog.getName() : sk.getSkillId();
                String skillCat = catalog != null ? catalog.getCategory() : "Technical Skill";

                RoundedCard item = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
                item.setLayout(new BorderLayout(8, 8));
                item.setBorder(new EmptyBorder(12, 14, 12, 14));

                JPanel top = new JPanel(new BorderLayout());
                top.setOpaque(false);
                JPanel leftT = new JPanel();
                leftT.setOpaque(false);
                leftT.setLayout(new BoxLayout(leftT, BoxLayout.Y_AXIS));
                JLabel sName = new JLabel(skillName);
                sName.setFont(ThemeManager.font(Font.BOLD, 13));
                sName.setForeground(ThemeManager.primaryText());
                JLabel sCat = new JLabel(skillCat);
                sCat.setFont(ThemeManager.font(Font.PLAIN, 11));
                sCat.setForeground(ThemeManager.CYAN_DARK);
                leftT.add(sName);
                leftT.add(sCat);

                BadgeLabel vBadge = new BadgeLabel(
                        sk.isVerifiedByFaculty() ? "✓ Faculty Verified" : "Self-Assessed",
                        sk.isVerifiedByFaculty() ? new Color(16, 185, 129, 25) : new Color(100, 116, 139, 25),
                        sk.isVerifiedByFaculty() ? ThemeManager.EMERALD_PRIMARY : ThemeManager.mutedText(),
                        null, 999
                );
                top.add(leftT, BorderLayout.CENTER);
                top.add(vBadge, BorderLayout.EAST);

                JPanel mid = new JPanel(new BorderLayout(8, 4));
                mid.setOpaque(false);
                int prof = Math.min(5, Math.max(1, sk.getProficiency()));
                JProgressBar pBar = new JProgressBar(0, 5);
                pBar.setValue(prof);
                pBar.setPreferredSize(new Dimension(180, 8));
                pBar.setForeground(sk.isVerifiedByFaculty() ? ThemeManager.EMERALD_PRIMARY : ThemeManager.BLUE_ELECTRIC);

                JLabel pLbl = new JLabel(PROFICIENCY_LABELS[prof]);
                pLbl.setFont(ThemeManager.font(Font.BOLD, 11.5f));
                pLbl.setForeground(ThemeManager.secondaryText());
                mid.add(pLbl, BorderLayout.NORTH);
                mid.add(pBar, BorderLayout.CENTER);

                item.add(top, BorderLayout.NORTH);
                item.add(mid, BorderLayout.CENTER);

                if (canVerifySkills && !sk.isVerifiedByFaculty()) {
                    ActionButton verBtn = new ActionButton("Verify This Assessment", ThemeManager.EMERALD_SOFT, ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY, 8);
                    verBtn.setFont(ThemeManager.font(Font.BOLD, 11));
                    verBtn.setMargin(new Insets(5, 10, 5, 10));
                    verBtn.addActionListener(e -> verifySkill(s, sk.getSkillId(), currentUser));
                    item.add(verBtn, BorderLayout.SOUTH);
                }

                body.add(item);
            }
        }

        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildProjectsSection(Student s, boolean canEditStudent) {
        RoundedCard card = new RoundedCard(16, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel("Technical Projects (" + s.getProjects().size() + ")");
        title.setFont(ThemeManager.font(Font.BOLD, 15));
        title.setForeground(ThemeManager.primaryText());
        head.add(title, BorderLayout.WEST);
        if (canEditStudent) {
            ActionButton addBtn = new ActionButton("+ Add Project", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 8);
            addBtn.setFont(ThemeManager.font(Font.BOLD, 11));
            addBtn.setMargin(new Insets(5, 10, 5, 10));
            addBtn.addActionListener(e -> openAddProjectDialog(s));
            head.add(addBtn, BorderLayout.EAST);
        }

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        if (s.getProjects().isEmpty()) {
            JLabel empty = new JLabel("No technical projects recorded in MySQL.");
            empty.setFont(ThemeManager.font(Font.PLAIN, 12));
            empty.setForeground(ThemeManager.mutedText());
            list.add(empty);
        } else {
            for (StudentProject p : s.getProjects()) {
                RoundedCard pc = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
                pc.setLayout(new BorderLayout(8, 6));
                pc.setBorder(new EmptyBorder(12, 14, 12, 14));
                pc.setAlignmentX(Component.LEFT_ALIGNMENT);

                JPanel pTop = new JPanel(new BorderLayout());
                pTop.setOpaque(false);
                JLabel pt = new JLabel(nonNull(p.getTitle()));
                pt.setFont(ThemeManager.font(Font.BOLD, 13));
                pt.setForeground(ThemeManager.primaryText());
                JLabel pdur = new JLabel(p.getDuration() != null && !p.getDuration().isBlank() ? p.getDuration() : "");
                pdur.setFont(ThemeManager.font(Font.PLAIN, 11));
                pdur.setForeground(ThemeManager.mutedText());
                pTop.add(pt, BorderLayout.WEST);
                pTop.add(pdur, BorderLayout.EAST);

                JPanel pMid = new JPanel();
                pMid.setOpaque(false);
                pMid.setLayout(new BoxLayout(pMid, BoxLayout.Y_AXIS));
                JLabel desc = new JLabel("<html><body style='width:380px; line-height:1.4;'>"
                        + escapeHtml(nonNull(p.getDescription())) + "</body></html>");
                desc.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
                desc.setForeground(ThemeManager.secondaryText());
                desc.setAlignmentX(Component.LEFT_ALIGNMENT);
                pMid.add(desc);

                if (p.getTechnologies() != null && !p.getTechnologies().isEmpty()) {
                    JPanel techRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
                    techRow.setOpaque(false);
                    techRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                    for (String t : p.getTechnologies()) {
                        techRow.add(new BadgeLabel(t, new Color(38, 132, 255, 22), ThemeManager.BLUE_ELECTRIC, null, 6));
                    }
                    pMid.add(techRow);
                }

                JPanel pFoot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
                pFoot.setOpaque(false);
                if (p.getLink() != null && !p.getLink().isBlank()) {
                    ActionButton linkBtn = new ActionButton("Repo / Demo  ↗", ThemeManager.cardBg(), ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_ELECTRIC, ThemeManager.cardBorder(), 8);
                    linkBtn.setFont(ThemeManager.font(Font.BOLD, 10.5f));
                    linkBtn.setMargin(new Insets(3, 8, 3, 8));
                    linkBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, p.getLink()));
                    pFoot.add(linkBtn);
                }
                if (canEditStudent) {
                    ActionButton delBtn = new ActionButton("Remove", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, null, 8);
                    delBtn.setFont(ThemeManager.font(Font.BOLD, 10.5f));
                    delBtn.setMargin(new Insets(3, 8, 3, 8));
                    delBtn.addActionListener(e -> deleteProject(s, p));
                    pFoot.add(delBtn);
                }

                pc.add(pTop, BorderLayout.NORTH);
                pc.add(pMid, BorderLayout.CENTER);
                pc.add(pFoot, BorderLayout.SOUTH);
                list.add(pc);
                list.add(Box.createVerticalStrut(8));
            }
        }

        card.add(head, BorderLayout.NORTH);
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildCertificationsSection(Student s, boolean canEditStudent) {
        RoundedCard card = new RoundedCard(16, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel("Certifications & Credentials (" + s.getCertifications().size() + ")");
        title.setFont(ThemeManager.font(Font.BOLD, 15));
        title.setForeground(ThemeManager.primaryText());
        head.add(title, BorderLayout.WEST);
        if (canEditStudent) {
            ActionButton addBtn = new ActionButton("+ Add Cert", ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY.darker(), Color.WHITE, null, 8);
            addBtn.setFont(ThemeManager.font(Font.BOLD, 11));
            addBtn.setMargin(new Insets(5, 10, 5, 10));
            addBtn.addActionListener(e -> openAddCertificationDialog(s));
            head.add(addBtn, BorderLayout.EAST);
        }

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        if (s.getCertifications().isEmpty()) {
            JLabel empty = new JLabel("No certifications recorded in MySQL.");
            empty.setFont(ThemeManager.font(Font.PLAIN, 12));
            empty.setForeground(ThemeManager.mutedText());
            list.add(empty);
        } else {
            for (StudentCertification c : s.getCertifications()) {
                RoundedCard cc = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
                cc.setLayout(new BorderLayout(8, 4));
                cc.setBorder(new EmptyBorder(12, 14, 12, 14));
                cc.setAlignmentX(Component.LEFT_ALIGNMENT);

                JPanel info = new JPanel();
                info.setOpaque(false);
                info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
                JLabel cn = new JLabel(nonNull(c.getName()));
                cn.setFont(ThemeManager.font(Font.BOLD, 13));
                cn.setForeground(ThemeManager.primaryText());
                JLabel org = new JLabel(nonNull(c.getIssuingOrg()) + "  ·  Issued: " + nonNull(c.getIssueDate()));
                org.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
                org.setForeground(ThemeManager.secondaryText());
                info.add(cn);
                info.add(Box.createVerticalStrut(2));
                info.add(org);
                if (c.getCredentialId() != null && !c.getCredentialId().isBlank()) {
                    JLabel cid = new JLabel("Credential ID: " + c.getCredentialId());
                    cid.setFont(ThemeManager.monoFont(Font.PLAIN, 11));
                    cid.setForeground(ThemeManager.mutedText());
                    info.add(cid);
                }

                cc.add(info, BorderLayout.CENTER);
                if (canEditStudent) {
                    ActionButton delBtn = new ActionButton("Remove", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, null, 8);
                    delBtn.setFont(ThemeManager.font(Font.BOLD, 10.5f));
                    delBtn.setMargin(new Insets(3, 8, 3, 8));
                    delBtn.addActionListener(e -> deleteCertification(s, c));
                    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                    right.setOpaque(false);
                    right.add(delBtn);
                    cc.add(right, BorderLayout.EAST);
                }
                list.add(cc);
                list.add(Box.createVerticalStrut(8));
            }
        }

        card.add(head, BorderLayout.NORTH);
        card.add(list, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildInternshipsSection(Student s, boolean canEditStudent) {
        RoundedCard card = new RoundedCard(16, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel("Industrial Internships & Training (" + s.getInternships().size() + ")");
        title.setFont(ThemeManager.font(Font.BOLD, 15));
        title.setForeground(ThemeManager.primaryText());
        head.add(title, BorderLayout.WEST);
        if (canEditStudent) {
            ActionButton addBtn = new ActionButton("+ Add Internship", ThemeManager.PURPLE_PRIMARY, ThemeManager.PURPLE_PRIMARY.darker(), Color.WHITE, null, 8);
            addBtn.setFont(ThemeManager.font(Font.BOLD, 11));
            addBtn.setMargin(new Insets(5, 10, 5, 10));
            addBtn.addActionListener(e -> openAddInternshipDialog(s));
            head.add(addBtn, BorderLayout.EAST);
        }

        JPanel grid = new JPanel(new GridLayout(0, 2, 12, 12));
        grid.setOpaque(false);
        if (s.getInternships().isEmpty()) {
            JLabel empty = new JLabel("No industrial internships recorded in MySQL.");
            empty.setFont(ThemeManager.font(Font.PLAIN, 12));
            empty.setForeground(ThemeManager.mutedText());
            grid.add(empty);
        } else {
            for (StudentInternship in : s.getInternships()) {
                RoundedCard ic = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
                ic.setLayout(new BorderLayout(8, 6));
                ic.setBorder(new EmptyBorder(12, 14, 12, 14));

                JPanel top = new JPanel(new BorderLayout());
                top.setOpaque(false);
                JLabel role = new JLabel(nonNull(in.getRole()) + " — " + nonNull(in.getCompanyName()));
                role.setFont(ThemeManager.font(Font.BOLD, 13));
                role.setForeground(ThemeManager.primaryText());
                BadgeLabel dur = new BadgeLabel(nonNull(in.getDuration()), new Color(147, 51, 234, 24), ThemeManager.PURPLE_PRIMARY, null, 999);
                top.add(role, BorderLayout.CENTER);
                top.add(dur, BorderLayout.EAST);

                JLabel desc = new JLabel("<html><body style='width:390px; line-height:1.4;'>"
                        + escapeHtml(nonNull(in.getDescription())) + "</body></html>");
                desc.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
                desc.setForeground(ThemeManager.secondaryText());

                ic.add(top, BorderLayout.NORTH);
                ic.add(desc, BorderLayout.CENTER);
                if (canEditStudent) {
                    ActionButton delBtn = new ActionButton("Remove", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, null, 8);
                    delBtn.setFont(ThemeManager.font(Font.BOLD, 10.5f));
                    delBtn.setMargin(new Insets(3, 8, 3, 8));
                    delBtn.addActionListener(e -> deleteInternship(s, in));
                    JPanel f = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                    f.setOpaque(false);
                    f.add(delBtn);
                    ic.add(f, BorderLayout.SOUTH);
                }
                grid.add(ic);
            }
        }

        card.add(head, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    // --- Interactive Modal Dialogs & JDBC Actions ---
    private void openEditProfileDialog(Student s, User actor) {
        boolean isSelf = actor != null && actor.getRole() == UserRole.STUDENT;
        StudentFormDialog dlg = new StudentFormDialog(SwingUtilities.getWindowAncestor(this), s, isSelf);
        Student updated = dlg.showDialog();
        if (updated == null) return;
        controller.updateStudentAsync(actor, updated, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Profile Updated", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Update Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openAddSkillDialog(Student s, boolean canVerifySkills, User actor) {
        if (skills.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No skills available in `skills` catalog.", "Empty Catalog", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JComboBox<SkillItem> skCombo = new JComboBox<>();
        for (Skill sk : skills) skCombo.addItem(new SkillItem(sk));

        JComboBox<String> profCombo = new JComboBox<>(new String[]{
                "1 - Beginner", "2 - Foundational", "3 - Intermediate", "4 - Advanced", "5 - Expert"
        });
        profCombo.setSelectedIndex(2);

        JCheckBox verifyBox = new JCheckBox("Mark as Faculty Verified (Authorized Dept Only)");
        verifyBox.setVisible(canVerifySkills);

        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 8));
        panel.add(new JLabel("Select Technical Skill:"));
        panel.add(skCombo);
        panel.add(new JLabel("Proficiency Level (1-5):"));
        panel.add(profCombo);
        if (canVerifySkills) panel.add(verifyBox);

        int ok = JOptionPane.showConfirmDialog(this, panel, "Add / Update Student Skill", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        SkillItem chosen = (SkillItem) skCombo.getSelectedItem();
        if (chosen == null) return;
        int prof = profCombo.getSelectedIndex() + 1;

        controller.upsertStudentSkillAsync(actor, s.getId(), chosen.skill.getId(), prof, verifyBox.isSelected(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Skill Saved", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Skill Save Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void verifySkill(Student s, String skillId, User actor) {
        controller.verifyStudentSkillAsync(actor, s.getId(), skillId, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Skill Verified", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Authorization Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openAddProjectDialog(Student s) {
        JTextField titleField = new JTextField();
        JTextField durationField = new JTextField("3 Months");
        JTextField techField = new JTextField("Java, Swing, MySQL");
        JTextField linkField = new JTextField();
        JTextArea descArea = new JTextArea(3, 28);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
        p.add(new JLabel("Project Title *:"));
        p.add(titleField);
        p.add(new JLabel("Duration (e.g. Jan - Apr 2025):"));
        p.add(durationField);
        p.add(new JLabel("Technologies (comma-separated):"));
        p.add(techField);
        p.add(new JLabel("GitHub / Demo URL (optional):"));
        p.add(linkField);
        p.add(new JLabel("Description:"));
        p.add(new JScrollPane(descArea));

        if (JOptionPane.showConfirmDialog(this, p, "Add Technical Project", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        if (titleField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Project title is required.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StudentProject proj = new StudentProject();
        proj.setTitle(titleField.getText().trim());
        proj.setDuration(durationField.getText().trim());
        proj.setLink(linkField.getText().trim());
        proj.setDescription(descArea.getText().trim());
        List<String> techs = new ArrayList<>();
        for (String t : techField.getText().split(",")) {
            if (!t.trim().isEmpty()) techs.add(t.trim());
        }
        proj.setTechnologies(techs);

        controller.addProjectAsync(AppSession.getInstance().getCurrentUser(), s.getId(), proj, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Project Added", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteProject(Student s, StudentProject p) {
        if (JOptionPane.showConfirmDialog(this, "Remove project \"" + p.getTitle() + "\"?", "Confirm Remove", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        controller.deleteProjectAsync(AppSession.getInstance().getCurrentUser(), s.getId(), p.getId(), res -> {
            if (res.success) loadData();
            else JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void openAddCertificationDialog(Student s) {
        JTextField nameField = new JTextField();
        JTextField orgField = new JTextField();
        JTextField dateField = new JTextField(LocalDate.now().toString());
        JTextField credField = new JTextField();

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
        p.add(new JLabel("Certification Name *:"));
        p.add(nameField);
        p.add(new JLabel("Issuing Organization * (e.g. AWS, Oracle, NPTEL):"));
        p.add(orgField);
        p.add(new JLabel("Issue Date (YYYY-MM-DD):"));
        p.add(dateField);
        p.add(new JLabel("Credential ID (optional):"));
        p.add(credField);

        if (JOptionPane.showConfirmDialog(this, p, "Add Certification Credential", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }

        StudentCertification cert = new StudentCertification();
        cert.setName(nameField.getText().trim());
        cert.setIssuingOrg(orgField.getText().trim());
        cert.setIssueDate(dateField.getText().trim());
        cert.setCredentialId(credField.getText().trim());

        controller.addCertificationAsync(AppSession.getInstance().getCurrentUser(), s.getId(), cert, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Certification Added", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteCertification(Student s, StudentCertification c) {
        if (JOptionPane.showConfirmDialog(this, "Remove certification \"" + c.getName() + "\"?", "Confirm Remove", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        controller.deleteCertificationAsync(AppSession.getInstance().getCurrentUser(), s.getId(), c.getId(), res -> {
            if (res.success) loadData();
            else JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void openAddInternshipDialog(Student s) {
        JTextField compField = new JTextField();
        JTextField roleField = new JTextField();
        JTextField durField = new JTextField("2 Months (Summer 2025)");
        JTextArea descArea = new JTextArea(3, 28);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);

        JPanel p = new JPanel(new GridLayout(0, 1, 0, 6));
        p.add(new JLabel("Company / Organization *:"));
        p.add(compField);
        p.add(new JLabel("Internship Role *:"));
        p.add(roleField);
        p.add(new JLabel("Duration *:"));
        p.add(durField);
        p.add(new JLabel("Work Summary / Deliverables:"));
        p.add(new JScrollPane(descArea));

        if (JOptionPane.showConfirmDialog(this, p, "Add Industrial Internship", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }

        StudentInternship intern = new StudentInternship();
        intern.setCompanyName(compField.getText().trim());
        intern.setRole(roleField.getText().trim());
        intern.setDuration(durField.getText().trim());
        intern.setDescription(descArea.getText().trim());

        controller.addInternshipAsync(AppSession.getInstance().getCurrentUser(), s.getId(), intern, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Internship Added", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteInternship(Student s, StudentInternship in) {
        if (JOptionPane.showConfirmDialog(this, "Remove internship at \"" + in.getCompanyName() + "\"?", "Confirm Remove", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        controller.deleteInternshipAsync(AppSession.getInstance().getCurrentUser(), s.getId(), in.getId(), res -> {
            if (res.success) loadData();
            else JOptionPane.showMessageDialog(this, res.message, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private int computeCompleteness(Student s) {
        int score = 0;
        if (s.getFullName() != null && !s.getFullName().isBlank() && s.getEmail() != null && !s.getEmail().isBlank()) score += 20;
        if (s.getCgpa() > 0 && s.getTenthPercentage() > 0) score += 20;
        if (!s.getSkills().isEmpty()) score += Math.min(25, s.getSkills().size() * 5);
        if (!s.getProjects().isEmpty()) score += 15;
        if (!s.getCertifications().isEmpty()) score += 10;
        if (s.getResumeUrl() != null && !s.getResumeUrl().isBlank()) score += 10;
        return Math.min(100, score);
    }

    private static String nonNull(String s) { return s == null || s.isBlank() ? "-" : s; }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static class StudentItem {
        final Student student;
        StudentItem(Student s) { this.student = s; }
        @Override public String toString() {
            return student.getRollNumber() + " — " + student.getFullName() + " (" + student.getBranch() + ")";
        }
    }

    private static class SkillItem {
        final Skill skill;
        SkillItem(Skill sk) { this.skill = sk; }
        @Override public String toString() {
            return skill.getName() + "  [" + skill.getCategory() + "]";
        }
    }
}
