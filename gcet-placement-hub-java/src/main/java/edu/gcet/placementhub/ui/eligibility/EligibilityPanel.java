package edu.gcet.placementhub.ui.eligibility;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.PlacementController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.EligibilityResult;
import edu.gcet.placementhub.models.PlacementModels.SkillCheckDetail;
import edu.gcet.placementhub.services.PlacementEngine;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Automated Placement Eligibility Engine (Phase 4):
 * - Single-student 5-criteria evaluation (CGPA, Branch, Graduation Year, Backlogs, Mandatory Skills)
 * - Direct MySQL eligibility-gated application submission
 * - Cohort Eligibility Matrix tab for staff/faculty/companies
 */
public class EligibilityPanel extends JPanel {

    private record StudentOption(Student student) {
        @Override
        public String toString() {
            return String.format(Locale.US, "%s - %s (%s, CGPA: %.2f)",
                    student.getRollNumber(), student.getFullName(), student.getBranch(), student.getCgpa());
        }
    }

    private record DriveOption(PlacementDrive drive) {
        @Override
        public String toString() {
            return String.format(Locale.US, "%s - %s (%.1f LPA · Min CGPA %.2f · %s)",
                    drive.getCompanyName(), drive.getJobRole(), drive.getSalaryPackageLpa(),
                    drive.getMinCgpa(), drive.getStatus());
        }
    }

    private final PlacementController controller = new PlacementController();
    private final BiConsumer<String, String> onNavigateWithDrive;

    private List<Student> allStudents = List.of();
    private List<PlacementDrive> allDrives = List.of();
    private List<Skill> allSkills = List.of();
    private List<Application> allApplications = List.of();

    private String pendingStudentId;
    private String pendingDriveId;
    private boolean suppressCombos = false;

    private JLabel subtitleLabel;
    private AlertBanner statusBanner;
    private JComboBox<StudentOption> studentCombo;
    private JComboBox<DriveOption> driveCombo;
    private JTabbedPane tabs;
    private JPanel singleEvalContainer;
    private DefaultTableModel cohortModel;
    private JLabel cohortSummaryLabel;

    public EligibilityPanel(BiConsumer<String, String> onNavigateWithDrive) {
        this.onNavigateWithDrive = onNavigateWithDrive;
        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));
        buildUi();
    }

    public void setInitialSelection(String studentId, String driveId) {
        if (studentId != null && !studentId.isBlank()) this.pendingStudentId = studentId;
        if (driveId != null && !driveId.isBlank()) this.pendingDriveId = driveId;
    }

    public void setInitialDriveId(String driveId) {
        if (driveId != null && !driveId.isBlank()) this.pendingDriveId = driveId;
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        subtitleLabel.setText("Loading academic, skill, and drive cutoffs from MySQL...");
        controller.loadPlacementBundleAsync(actor, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.allStudents = bundle.students;
            this.allDrives = bundle.drives;
            this.allSkills = bundle.skills;
            this.allApplications = bundle.applications;
            populateSelectors(actor);
            renderEvaluation();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        populateSelectors(AppSession.getInstance().getCurrentUser());
        renderEvaluation();
        revalidate();
        repaint();
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Automated Placement Eligibility Engine");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("Deterministic evaluation of student CGPA, branch, backlogs, passing batch, and verified skills.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(subtitleLabel);

        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        header.add(titleCol, BorderLayout.WEST);
        header.add(refreshBtn, BorderLayout.EAST);

        // Selectors Card
        RoundedCard selectorCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        selectorCard.setLayout(new BoxLayout(selectorCard, BoxLayout.Y_AXIS));
        selectorCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        statusBanner = new AlertBanner();
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel selectorGrid = new JPanel(new GridLayout(1, 2, 14, 0));
        selectorGrid.setOpaque(false);
        selectorGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        studentCombo = new JComboBox<>();
        studentCombo.setPreferredSize(new Dimension(260, 34));
        studentCombo.addActionListener(e -> {
            if (!suppressCombos) {
                StudentOption sel = (StudentOption) studentCombo.getSelectedItem();
                if (sel != null) pendingStudentId = sel.student().getId();
                renderEvaluation();
            }
        });

        driveCombo = new JComboBox<>();
        driveCombo.setPreferredSize(new Dimension(260, 34));
        driveCombo.addActionListener(e -> {
            if (!suppressCombos) {
                DriveOption sel = (DriveOption) driveCombo.getSelectedItem();
                if (sel != null) pendingDriveId = sel.drive().getId();
                renderEvaluation();
            }
        });

        selectorGrid.add(labeled("Select Student", studentCombo));
        selectorGrid.add(labeled("Select Target Placement Drive", driveCombo));

        selectorCard.add(statusBanner);
        selectorCard.add(Box.createVerticalStrut(2));
        selectorCard.add(selectorGrid);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        selectorCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(header);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(selectorCard);

        // Tabbed center: Single Student Breakdown + Cohort Matrix (for non-students)
        singleEvalContainer = new JPanel();
        singleEvalContainer.setOpaque(false);
        singleEvalContainer.setLayout(new BoxLayout(singleEvalContainer, BoxLayout.Y_AXIS));

        JScrollPane singleScroll = new JScrollPane(singleEvalContainer);
        singleScroll.setBorder(null);
        singleScroll.getViewport().setBackground(ThemeManager.surfaceBg());
        singleScroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel cohortTab = buildCohortTab();

        tabs = new JTabbedPane();
        tabs.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        tabs.addTab("Individual Eligibility Evaluation", singleScroll);
        tabs.addTab("Cohort Eligibility Scanner (All Students vs Drive)", cohortTab);

        add(topStack, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel buildCohortTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(ThemeManager.surfaceBg());
        p.setBorder(new EmptyBorder(10, 0, 0, 0));

        cohortSummaryLabel = new JLabel("Evaluating all students against selected drive...");
        cohortSummaryLabel.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        cohortSummaryLabel.setForeground(ThemeManager.primaryText());

        String[] cols = {"Roll Number", "Student Name", "Branch", "Batch", "CGPA", "Backlogs", "Score", "Eligible?", "Reason / Summary"};
        cohortModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable tbl = new JTable(cohortModel);
        tbl.setRowHeight(32);
        tbl.setFont(ThemeManager.font(Font.PLAIN, 12));
        tbl.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11.5f));

        RoundedCard wrap = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        wrap.setLayout(new BorderLayout(0, 8));
        wrap.setBorder(new EmptyBorder(14, 14, 14, 14));
        wrap.add(cohortSummaryLabel, BorderLayout.NORTH);
        wrap.add(new JScrollPane(tbl), BorderLayout.CENTER);
        p.add(wrap, BorderLayout.CENTER);
        return p;
    }

    private void populateSelectors(User actor) {
        suppressCombos = true;
        try {
            studentCombo.removeAllItems();
            StudentOption matchStudent = null;
            for (Student s : allStudents) {
                StudentOption opt = new StudentOption(s);
                studentCombo.addItem(opt);
                if (pendingStudentId != null && pendingStudentId.equals(s.getId())) {
                    matchStudent = opt;
                } else if (matchStudent == null && actor != null && actor.getRole() == UserRole.STUDENT
                        && actor.getStudentRollNumber() != null
                        && actor.getStudentRollNumber().equalsIgnoreCase(s.getRollNumber())) {
                    matchStudent = opt;
                }
            }
            if (matchStudent != null) studentCombo.setSelectedItem(matchStudent);
            else if (studentCombo.getItemCount() > 0) studentCombo.setSelectedIndex(0);

            boolean isStudent = actor != null && actor.getRole() == UserRole.STUDENT;
            studentCombo.setEnabled(!isStudent);
            if (tabs.getTabCount() > 1) {
                tabs.setEnabledAt(1, !isStudent);
                if (isStudent) tabs.setSelectedIndex(0);
            }

            driveCombo.removeAllItems();
            DriveOption matchDrive = null;
            for (PlacementDrive d : allDrives) {
                DriveOption opt = new DriveOption(d);
                driveCombo.addItem(opt);
                if (pendingDriveId != null && pendingDriveId.equals(d.getId())) {
                    matchDrive = opt;
                }
            }
            if (matchDrive != null) driveCombo.setSelectedItem(matchDrive);
            else if (driveCombo.getItemCount() > 0) driveCombo.setSelectedIndex(0);
        } finally {
            suppressCombos = false;
        }
    }

    private void renderEvaluation() {
        singleEvalContainer.removeAll();
        StudentOption stOpt = (StudentOption) studentCombo.getSelectedItem();
        DriveOption drOpt = (DriveOption) driveCombo.getSelectedItem();

        if (stOpt == null || drOpt == null) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setBorder(new EmptyBorder(32, 24, 32, 24));
            JLabel msg = new JLabel("Ensure student records and placement drives exist in MySQL to run eligibility evaluation.");
            msg.setFont(ThemeManager.font(Font.PLAIN, 13));
            msg.setForeground(ThemeManager.mutedText());
            empty.add(msg);
            singleEvalContainer.add(empty);
            singleEvalContainer.revalidate();
            singleEvalContainer.repaint();
            return;
        }

        Student student = stOpt.student();
        PlacementDrive drive = drOpt.drive();
        EligibilityResult res = PlacementEngine.checkEligibility(student, drive, allSkills);
        subtitleLabel.setText(String.format(Locale.US, "Evaluating %s (%s) against %s — %s",
                student.getFullName(), student.getRollNumber(), drive.getCompanyName(), drive.getJobRole()));

        Application existingApp = null;
        for (Application a : allApplications) {
            if (student.getId().equals(a.getStudentId()) && drive.getId().equals(a.getDriveId())) {
                existingApp = a;
                break;
            }
        }

        // Primary Verdict Banner
        Color bannerBg = res.eligible ? ThemeManager.EMERALD_SOFT : ThemeManager.ROSE_SOFT;
        Color bannerBorder = res.eligible ? new Color(167, 243, 208) : new Color(254, 205, 211);
        Color accent = res.eligible ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY;
        if (ThemeManager.isDarkMode()) {
            bannerBg = res.eligible ? new Color(6, 78, 59, 90) : new Color(136, 19, 55, 90);
            bannerBorder = accent;
        }

        RoundedCard verdictCard = new RoundedCard(16, bannerBg, bannerBorder);
        verdictCard.setLayout(new BoxLayout(verdictCard, BoxLayout.Y_AXIS));
        verdictCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        verdictCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel topRow = new JPanel(new BorderLayout(16, 8));
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel infoCol = new JPanel();
        infoCol.setOpaque(false);
        infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));

        JPanel badgeLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgeLine.setOpaque(false);
        badgeLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeLine.add(new BadgeLabel(
                res.eligible ? "ELIGIBLE TO APPLY" : "CURRENTLY NOT ELIGIBLE",
                accent, Color.WHITE, accent, 8
        ));
        badgeLine.add(new BadgeLabel(
                "Preparedness Score: " + res.score + "%",
                ThemeManager.cardBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 8
        ));

        JLabel titleLbl = new JLabel(student.getFullName() + " (" + student.getRollNumber() + ")  vs  " + drive.getCompanyName());
        titleLbl.setFont(ThemeManager.font(Font.BOLD, 17));
        titleLbl.setForeground(ThemeManager.primaryText());
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        String summaryTxt = res.eligible
                ? String.format(Locale.US, "Candidate satisfies all academic benchmarks (CGPA >= %.2f), branch eligibility, backlog bounds, and verified mandatory technical skills.", drive.getMinCgpa())
                : String.format("Candidate does not meet %d mandatory requirement(s) for this position.", res.reasons.size());
        JLabel sumLbl = new JLabel("<html><body style='width:620px;'>" + esc(summaryTxt) + "</body></html>");
        sumLbl.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        sumLbl.setForeground(ThemeManager.secondaryText());
        sumLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoCol.add(badgeLine);
        infoCol.add(Box.createVerticalStrut(8));
        infoCol.add(titleLbl);
        infoCol.add(Box.createVerticalStrut(4));
        infoCol.add(sumLbl);

        // Apply / Status Action Area
        JPanel actionCol = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        actionCol.setOpaque(false);
        User actor = AppSession.getInstance().getCurrentUser();
        boolean driveOpen = PlacementEngine.isDriveOpenForApplications(drive);
        boolean canSubmit = actor != null && (actor.getRole() == UserRole.STUDENT || actor.getRole().isPrivilegedRole());

        if (existingApp != null && existingApp.getCurrentStage() != ApplicationStage.WITHDRAWN) {
            BadgeLabel appliedBadge = new BadgeLabel(
                    "Already Applied (" + existingApp.getCurrentStage().getLabel() + ")",
                    ThemeManager.cardBg(), ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_ELECTRIC, 8
            );
            actionCol.add(appliedBadge);
        } else if (res.eligible && driveOpen && canSubmit) {
            String btnText = (existingApp != null) ? "Re-Submit Application  →" : "Submit Placement Application  →";
            ActionButton applyBtn = new ActionButton(btnText, ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY.darker(), Color.WHITE, null, 10);
            applyBtn.addActionListener(e -> submitApplication(student, drive));
            actionCol.add(applyBtn);
        } else {
            String lockLabel = !driveOpen ? "Drive " + drive.getStatus() : (!res.eligible ? "Application Locked (Ineligible)" : "View Only");
            BadgeLabel lockBadge = new BadgeLabel(lockLabel, ThemeManager.cardBg(), ThemeManager.mutedText(), ThemeManager.cardBorder(), 8);
            actionCol.add(lockBadge);
        }

        ActionButton gapJumpBtn = new ActionButton("Skill Gap Analysis", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        gapJumpBtn.addActionListener(e -> {
            if (onNavigateWithDrive != null) onNavigateWithDrive.accept("skill-gap", drive.getId());
        });
        actionCol.add(gapJumpBtn);

        topRow.add(infoCol, BorderLayout.CENTER);
        topRow.add(actionCol, BorderLayout.EAST);
        verdictCard.add(topRow);

        if (!res.eligible && !res.reasons.isEmpty()) {
            verdictCard.add(Box.createVerticalStrut(12));
            JLabel failHead = new JLabel("Specific Criteria Failures:");
            failHead.setFont(ThemeManager.font(Font.BOLD, 12));
            failHead.setForeground(accent);
            failHead.setAlignmentX(Component.LEFT_ALIGNMENT);
            verdictCard.add(failHead);
            verdictCard.add(Box.createVerticalStrut(4));
            for (String r : res.reasons) {
                JLabel line = new JLabel("•  " + r);
                line.setFont(ThemeManager.font(Font.PLAIN, 12));
                line.setForeground(ThemeManager.primaryText());
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                verdictCard.add(line);
                verdictCard.add(Box.createVerticalStrut(2));
            }
        }

        // Criteria Verification Breakdown Card
        RoundedCard matrixCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        matrixCard.setLayout(new BoxLayout(matrixCard, BoxLayout.Y_AXIS));
        matrixCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        matrixCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel matTitle = new JLabel("DETAILED CRITERIA EVALUATION MATRIX");
        matTitle.setFont(ThemeManager.font(Font.BOLD, 12));
        matTitle.setForeground(ThemeManager.mutedText());
        matTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        matrixCard.add(matTitle);
        matrixCard.add(Box.createVerticalStrut(10));

        matrixCard.add(criteriaRow("1. Cumulative Grade Point Average (CGPA)", res.cgpaMessage,
                res.cgpaPassed, res.cgpaPassed ? "Requirement Satisfied" : "Below Cutoff"));
        matrixCard.add(criteriaRow("2. Degree & Engineering Branch",
                "Permitted: " + (res.allowedBranches.isEmpty() ? "All" : String.join(", ", res.allowedBranches))
                        + "  ·  Candidate: " + res.studentBranch,
                res.branchPassed, res.branchPassed ? "Eligible Branch" : "Non-Permitted Branch"));
        matrixCard.add(criteriaRow("3. Target Passing Batch (Graduation Year)", res.gradYearMessage,
                res.gradYearPassed, res.gradYearPassed ? "Batch Matched" : "Batch Discrepancy"));
        matrixCard.add(criteriaRow("4. Standing Academic Backlogs", res.backlogsMessage,
                res.backlogsPassed, res.backlogsPassed ? "Within Limit" : "Exceeds Backlog Cap"));

        // Section 5: Skills
        JPanel skillsHeader = new JPanel(new BorderLayout());
        skillsHeader.setOpaque(false);
        skillsHeader.setBorder(new EmptyBorder(10, 0, 6, 0));
        skillsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel skTitle = new JLabel("5. Required Technical Skills & Proficiency Levels  ("
                + res.satisfiedSkillsCount + " / " + res.totalRequiredSkills + " satisfied)");
        skTitle.setFont(ThemeManager.font(Font.BOLD, 13));
        skTitle.setForeground(ThemeManager.primaryText());
        BadgeLabel skBadge = new BadgeLabel(
                res.mandatorySkillsSatisfied ? "Mandatory Skills Met" : "Skill Deficit",
                res.mandatorySkillsSatisfied ? ThemeManager.EMERALD_SOFT : ThemeManager.AMBER_SOFT,
                res.mandatorySkillsSatisfied ? ThemeManager.EMERALD_PRIMARY : ThemeManager.AMBER_PRIMARY,
                res.mandatorySkillsSatisfied ? ThemeManager.EMERALD_PRIMARY : ThemeManager.AMBER_PRIMARY,
                8
        );
        skillsHeader.add(skTitle, BorderLayout.WEST);
        skillsHeader.add(skBadge, BorderLayout.EAST);
        matrixCard.add(skillsHeader);

        if (res.skillDetails.isEmpty()) {
            JLabel noneSk = new JLabel("No specific technical skills listed for this drive.");
            noneSk.setFont(ThemeManager.font(Font.PLAIN, 12));
            noneSk.setForeground(ThemeManager.mutedText());
            noneSk.setAlignmentX(Component.LEFT_ALIGNMENT);
            matrixCard.add(noneSk);
        } else {
            JPanel skGrid = new JPanel(new GridLayout(0, 2, 10, 8));
            skGrid.setOpaque(false);
            skGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (SkillCheckDetail d : res.skillDetails) {
                Color bg = d.satisfied ? ThemeManager.EMERALD_SOFT : ThemeManager.ROSE_SOFT;
                Color fg = d.satisfied ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY;
                if (ThemeManager.isDarkMode()) {
                    bg = d.satisfied ? new Color(6, 78, 59, 80) : new Color(136, 19, 55, 80);
                }
                RoundedCard skCard = new RoundedCard(10, bg, fg);
                skCard.setLayout(new BorderLayout(8, 0));
                skCard.setBorder(new EmptyBorder(10, 12, 10, 12));

                JPanel left = new JPanel();
                left.setOpaque(false);
                left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
                JLabel n = new JLabel(d.skillName + (d.mandatory ? "  [Mandatory]" : "  [Optional]"));
                n.setFont(ThemeManager.font(Font.BOLD, 12.5f));
                n.setForeground(ThemeManager.primaryText());
                JLabel det = new JLabel(String.format("Required: L%d (%s)  ·  Student: %s",
                        d.requiredProficiency, PlacementModels.proficiencyLabel(d.requiredProficiency),
                        d.studentProficiency > 0 ? "L" + d.studentProficiency + " (" + PlacementModels.proficiencyLabel(d.studentProficiency) + ")" : "None"));
                det.setFont(ThemeManager.font(Font.PLAIN, 11));
                det.setForeground(ThemeManager.secondaryText());
                left.add(n);
                left.add(Box.createVerticalStrut(2));
                left.add(det);

                BadgeLabel st = new BadgeLabel(
                        d.satisfied ? "Satisfied" : "Gap -" + d.gap,
                        ThemeManager.cardBg(), fg, fg, 6
                );
                skCard.add(left, BorderLayout.CENTER);
                skCard.add(st, BorderLayout.EAST);
                skGrid.add(skCard);
            }
            matrixCard.add(skGrid);
        }

        singleEvalContainer.add(verdictCard);
        singleEvalContainer.add(Box.createVerticalStrut(14));
        singleEvalContainer.add(matrixCard);
        singleEvalContainer.revalidate();
        singleEvalContainer.repaint();

        // Populate Cohort Scanner tab
        populateCohortMatrix(drive);
    }

    private void populateCohortMatrix(PlacementDrive drive) {
        cohortModel.setRowCount(0);
        int eligibleCount = 0;
        for (Student s : allStudents) {
            EligibilityResult r = PlacementEngine.checkEligibility(s, drive, allSkills);
            if (r.eligible) eligibleCount++;
            String reason = r.eligible
                    ? "All cutoffs & skills satisfied"
                    : (!r.reasons.isEmpty() ? r.reasons.get(0) : "Criteria unmet");
            cohortModel.addRow(new Object[]{
                    s.getRollNumber(),
                    s.getFullName(),
                    s.getBranch(),
                    s.getGraduationYear(),
                    String.format(Locale.US, "%.2f", s.getCgpa()),
                    s.getBacklogs(),
                    r.score + "%",
                    r.eligible ? "ELIGIBLE" : "INELIGIBLE",
                    reason
            });
        }
        cohortSummaryLabel.setText(String.format(
                "Drive: %s — %s  |  %d of %d students eligible (%.1f%%)",
                drive.getCompanyName(), drive.getJobRole(), eligibleCount, allStudents.size(),
                allStudents.isEmpty() ? 0.0 : (eligibleCount * 100.0 / allStudents.size())));
    }

    private JPanel criteriaRow(String title, String detail, boolean passed, String badgeText) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(10, 0, 10, 0)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel t = new JLabel((passed ? "✓  " : "✗  ") + title);
        t.setFont(ThemeManager.font(Font.BOLD, 13));
        t.setForeground(passed ? ThemeManager.primaryText() : ThemeManager.ROSE_PRIMARY);
        JLabel d = new JLabel(detail);
        d.setFont(ThemeManager.font(Font.PLAIN, 12));
        d.setForeground(ThemeManager.secondaryText());
        left.add(t);
        left.add(Box.createVerticalStrut(2));
        left.add(d);

        Color fg = passed ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY;
        Color bg = passed ? ThemeManager.EMERALD_SOFT : ThemeManager.ROSE_SOFT;
        BadgeLabel b = new BadgeLabel(badgeText, bg, fg, fg, 8);

        row.add(left, BorderLayout.CENTER);
        row.add(b, BorderLayout.EAST);
        return row;
    }

    private void submitApplication(Student student, PlacementDrive drive) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Submit placement application to " + drive.getCompanyName() + " (" + drive.getJobRole() + ")?\n"
                        + "Candidate: " + student.getFullName() + " (" + student.getRollNumber() + ")",
                "Confirm Placement Application",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        controller.submitApplicationAsync(AppSession.getInstance().getCurrentUser(), drive.getId(), student.getId(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Application Submitted", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Application Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private JPanel labeled(String text, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        l.setForeground(ThemeManager.secondaryText());
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
