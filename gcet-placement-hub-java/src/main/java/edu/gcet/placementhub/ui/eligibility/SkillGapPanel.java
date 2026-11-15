package edu.gcet.placementhub.ui.eligibility;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.PlacementController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.SkillGapAnalysis;
import edu.gcet.placementhub.models.PlacementModels.SkillGapItem;
import edu.gcet.placementhub.services.PlacementEngine;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Skill Gap Analysis & Learning Roadmap Module (Phase 4):
 * Compares actual student MySQL skills against target drive skill proficiencies,
 * identifies missing/partial gaps, and provides curriculum prescriptions + skills to add.
 */
public class SkillGapPanel extends JPanel {

    private record StudentOption(Student student) {
        @Override
        public String toString() {
            return String.format("%s - %s (%s)", student.getRollNumber(), student.getFullName(), student.getBranch());
        }
    }

    private record DriveOption(PlacementDrive drive) {
        @Override
        public String toString() {
            return String.format(Locale.US, "%s - %s (%.1f LPA)",
                    drive.getCompanyName(), drive.getJobRole(), drive.getSalaryPackageLpa());
        }
    }

    private final PlacementController controller = new PlacementController();
    private final Consumer<String> onNavigatePage;

    private List<Student> allStudents = List.of();
    private List<PlacementDrive> allDrives = List.of();
    private List<Skill> allSkills = List.of();

    private String pendingStudentId;
    private String pendingDriveId;
    private boolean suppressCombos = false;

    private JLabel subtitleLabel;
    private AlertBanner statusBanner;
    private JComboBox<StudentOption> studentCombo;
    private JComboBox<DriveOption> driveCombo;
    private JPanel contentContainer;

    public SkillGapPanel(Consumer<String> onNavigatePage) {
        this.onNavigatePage = onNavigatePage;
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
        subtitleLabel.setText("Analyzing student vs company technical skill benchmarks from MySQL...");
        controller.loadPlacementBundleAsync(actor, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.allStudents = bundle.students;
            this.allDrives = bundle.drives;
            this.allSkills = bundle.skills;
            populateSelectors(actor);
            renderAnalysis();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        populateSelectors(AppSession.getInstance().getCurrentUser());
        renderAnalysis();
        revalidate();
        repaint();
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Skill Gap Analysis & Learning Roadmap");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("Quantifies technical deficiencies between student skill profiles and placement drive requisites.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(subtitleLabel);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);
        ActionButton matrixBtn = new ActionButton("Open Skill Matrix  →", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        matrixBtn.addActionListener(e -> {
            if (onNavigatePage != null) onNavigatePage.accept("skills");
        });
        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        rightBtns.add(matrixBtn);
        rightBtns.add(refreshBtn);

        header.add(titleCol, BorderLayout.WEST);
        header.add(rightBtns, BorderLayout.EAST);

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
                StudentOption s = (StudentOption) studentCombo.getSelectedItem();
                if (s != null) pendingStudentId = s.student().getId();
                renderAnalysis();
            }
        });

        driveCombo = new JComboBox<>();
        driveCombo.setPreferredSize(new Dimension(260, 34));
        driveCombo.addActionListener(e -> {
            if (!suppressCombos) {
                DriveOption d = (DriveOption) driveCombo.getSelectedItem();
                if (d != null) pendingDriveId = d.drive().getId();
                renderAnalysis();
            }
        });

        selectorGrid.add(labeled("Target Student", studentCombo));
        selectorGrid.add(labeled("Target Job Opportunity", driveCombo));

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

        contentContainer = new JPanel();
        contentContainer.setOpaque(false);
        contentContainer.setLayout(new BoxLayout(contentContainer, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(contentContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ThemeManager.surfaceBg());
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        add(topStack, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
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
            studentCombo.setEnabled(actor == null || actor.getRole() != UserRole.STUDENT);

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

    private void renderAnalysis() {
        contentContainer.removeAll();
        StudentOption stOpt = (StudentOption) studentCombo.getSelectedItem();
        DriveOption drOpt = (DriveOption) driveCombo.getSelectedItem();

        if (stOpt == null || drOpt == null) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setBorder(new EmptyBorder(32, 24, 32, 24));
            JLabel msg = new JLabel("Select student and placement drive records to conduct skill gap evaluation.");
            msg.setFont(ThemeManager.font(Font.PLAIN, 13));
            msg.setForeground(ThemeManager.mutedText());
            empty.add(msg);
            contentContainer.add(empty);
            contentContainer.revalidate();
            contentContainer.repaint();
            return;
        }

        Student student = stOpt.student();
        PlacementDrive drive = drOpt.drive();
        SkillGapAnalysis analysis = PlacementEngine.analyzeSkillGap(student, drive, allSkills);
        subtitleLabel.setText(String.format("Preparedness Index: %d%% for %s vs %s (%s)",
                analysis.readinessPercentage, student.getFullName(), drive.getCompanyName(), drive.getJobRole()));

        // Summary Readiness Banner
        RoundedCard summaryCard = new RoundedCard(16, ThemeManager.cardBg(), ThemeManager.cardBorder());
        summaryCard.setLayout(new BorderLayout(18, 0));
        summaryCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        summaryCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftSum = new JPanel();
        leftSum.setOpaque(false);
        leftSum.setLayout(new BoxLayout(leftSum, BoxLayout.Y_AXIS));
        JLabel tag = new JLabel("TECHNICAL PREPAREDNESS INDEX");
        tag.setFont(ThemeManager.font(Font.BOLD, 11));
        tag.setForeground(ThemeManager.CYAN_DARK);
        JLabel pct = new JLabel(analysis.readinessPercentage + "% Technical Skill Match");
        pct.setFont(ThemeManager.font(Font.BOLD, 22));
        pct.setForeground(ThemeManager.primaryText());
        String detailTxt = String.format("Candidate satisfies %d of %d required skills.%s",
                analysis.skillsSatisfiedCount, analysis.totalRequiredSkills,
                analysis.skillsMissingCount > 0 ? "  " + analysis.skillsMissingCount + " skill(s) currently missing." : "");
        JLabel det = new JLabel(detailTxt);
        det.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        det.setForeground(ThemeManager.secondaryText());
        leftSum.add(tag);
        leftSum.add(Box.createVerticalStrut(4));
        leftSum.add(pct);
        leftSum.add(Box.createVerticalStrut(4));
        leftSum.add(det);

        JPanel kpiStrip = new JPanel(new GridLayout(1, 3, 10, 0));
        kpiStrip.setOpaque(false);
        kpiStrip.add(kpiBox(String.valueOf(analysis.skillsSatisfiedCount), "Fully Met", ThemeManager.EMERALD_SOFT, ThemeManager.EMERALD_PRIMARY));
        kpiBoxCountFix(kpiStrip, analysis);

        summaryCard.add(leftSum, BorderLayout.CENTER);
        summaryCard.add(kpiStrip, BorderLayout.EAST);

        // Detailed Comparison Card
        RoundedCard listCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        listCard.setLayout(new BoxLayout(listCard, BoxLayout.Y_AXIS));
        listCard.setBorder(new EmptyBorder(16, 18, 16, 18));
        listCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel listTitle = new JLabel("PROFICIENCY GAP EVALUATION & CURRICULUM PRESCRIPTIONS");
        listTitle.setFont(ThemeManager.font(Font.BOLD, 12));
        listTitle.setForeground(ThemeManager.mutedText());
        listTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        listCard.add(listTitle);
        listCard.add(Box.createVerticalStrut(10));

        if (analysis.skills.isEmpty()) {
            JLabel noReq = new JLabel("This placement drive has no required skills configured in MySQL.");
            noReq.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
            noReq.setForeground(ThemeManager.mutedText());
            noReq.setAlignmentX(Component.LEFT_ALIGNMENT);
            listCard.add(noReq);
        } else {
            for (SkillGapItem item : analysis.skills) {
                listCard.add(buildGapItemRow(item));
            }
        }

        // Strategic Roadmap Dark Card
        RoundedCard roadmapCard = new RoundedCard(14, ThemeManager.NAVY_MIDNIGHT, ThemeManager.NAVY_DEEP);
        roadmapCard.setLayout(new BoxLayout(roadmapCard, BoxLayout.Y_AXIS));
        roadmapCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        roadmapCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel roadHead = new JLabel("STRATEGIC IMPROVEMENT ROADMAP FOR " + student.getFullName().toUpperCase(Locale.ROOT));
        roadHead.setFont(ThemeManager.font(Font.BOLD, 12));
        roadHead.setForeground(ThemeManager.CYAN_ACCENT);
        roadHead.setAlignmentX(Component.LEFT_ALIGNMENT);
        roadmapCard.add(roadHead);
        roadmapCard.add(Box.createVerticalStrut(10));

        for (int i = 0; i < analysis.actionPlan.size(); i++) {
            JPanel stepRow = new JPanel(new BorderLayout(10, 0));
            stepRow.setOpaque(false);
            stepRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            stepRow.setBorder(new EmptyBorder(3, 0, 3, 0));
            BadgeLabel num = new BadgeLabel(String.valueOf(i + 1), ThemeManager.NAVY_DEEP, ThemeManager.CYAN_ACCENT, ThemeManager.CYAN_ACCENT, 99);
            JLabel stepTxt = new JLabel("<html><body style='width:680px;'>" + esc(analysis.actionPlan.get(i)) + "</body></html>");
            stepTxt.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
            stepTxt.setForeground(ThemeManager.SLATE_200);
            stepRow.add(num, BorderLayout.WEST);
            stepRow.add(stepTxt, BorderLayout.CENTER);
            roadmapCard.add(stepRow);
        }

        if (!analysis.recommendedFocusSkills.isEmpty()) {
            roadmapCard.add(Box.createVerticalStrut(10));
            JLabel recTitle = new JLabel("Recommended Skills to Add / Upskill:");
            recTitle.setFont(ThemeManager.font(Font.BOLD, 11.5f));
            recTitle.setForeground(Color.WHITE);
            recTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            roadmapCard.add(recTitle);
            roadmapCard.add(Box.createVerticalStrut(4));

            JPanel recWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            recWrap.setOpaque(false);
            recWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (String rs : analysis.recommendedFocusSkills) {
                recWrap.add(new BadgeLabel(rs, ThemeManager.NAVY_DEEP, ThemeManager.SLATE_200, ThemeManager.SLATE_600, 8));
            }
            roadmapCard.add(recWrap);
        }

        contentContainer.add(summaryCard);
        contentContainer.add(Box.createVerticalStrut(14));
        contentContainer.add(listCard);
        contentContainer.add(Box.createVerticalStrut(14));
        contentContainer.add(roadmapCard);
        contentContainer.revalidate();
        contentContainer.repaint();
    }

    private void kpiBoxCountFix(JPanel kpiStrip, SkillGapAnalysis analysis) {
        kpiStrip.add(kpiBox(String.valueOf(analysis.skillsPartialCount), "Partial Gap", ThemeManager.AMBER_SOFT, ThemeManager.AMBER_PRIMARY));
        kpiStrip.add(kpiBox(String.valueOf(analysis.skillsMissingCount), "Missing", ThemeManager.ROSE_SOFT, ThemeManager.ROSE_PRIMARY));
    }

    private JPanel kpiBox(String val, String label, Color bg, Color fg) {
        if (ThemeManager.isDarkMode()) {
            bg = ThemeManager.cardSubtleBg();
        }
        RoundedCard box = new RoundedCard(12, bg, fg);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(10, 16, 10, 16));
        box.setPreferredSize(new Dimension(102, 66));
        JLabel v = new JLabel(val);
        v.setFont(ThemeManager.font(Font.BOLD, 20));
        v.setForeground(fg);
        v.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.BOLD, 11));
        l.setForeground(fg);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(v);
        box.add(Box.createVerticalStrut(2));
        box.add(l);
        return box;
    }

    private JPanel buildGapItemRow(SkillGapItem item) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(12, 0, 12, 0)
        ));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel name = new JLabel(item.skillName);
        name.setFont(ThemeManager.font(Font.BOLD, 14));
        name.setForeground(ThemeManager.primaryText());
        String statusText;
        Color fg, bg;
        if (item.status == SkillGapItem.Status.MET) {
            statusText = "Requirement Met";
            fg = ThemeManager.EMERALD_PRIMARY;
            bg = ThemeManager.EMERALD_SOFT;
        } else if (item.status == SkillGapItem.Status.PARTIAL) {
            statusText = "Partial Gap (-" + item.gapLevel + " Level)";
            fg = ThemeManager.AMBER_PRIMARY;
            bg = ThemeManager.AMBER_SOFT;
        } else {
            statusText = "Completely Missing";
            fg = ThemeManager.ROSE_PRIMARY;
            bg = ThemeManager.ROSE_SOFT;
        }
        JLabel cat = new JLabel("[" + item.category + "]");
        cat.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        cat.setForeground(ThemeManager.mutedText());
        left.add(name);
        left.add(new BadgeLabel(statusText, bg, fg, fg, 8));
        left.add(cat);

        JLabel levels = new JLabel(String.format("Candidate: %s   →   Required: L%d (%s)",
                item.studentProficiency > 0
                        ? "L" + item.studentProficiency + " (" + PlacementModels.proficiencyLabel(item.studentProficiency) + ")"
                        : "Not Listed (L0)",
                item.requiredProficiency, PlacementModels.proficiencyLabel(item.requiredProficiency)));
        levels.setFont(ThemeManager.font(Font.BOLD, 12));
        levels.setForeground(ThemeManager.secondaryText());

        top.add(left, BorderLayout.WEST);
        top.add(levels, BorderLayout.EAST);

        // Visual progress comparison bar
        JProgressBar bar = new JProgressBar(0, Math.max(1, item.requiredProficiency));
        bar.setValue(Math.min(item.studentProficiency, item.requiredProficiency));
        bar.setStringPainted(true);
        bar.setString(String.format("Proficiency %d / %d", item.studentProficiency, item.requiredProficiency));
        bar.setForeground(fg);
        bar.setPreferredSize(new Dimension(200, 18));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        RoundedCard recBox = new RoundedCard(8, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        recBox.setLayout(new BorderLayout());
        recBox.setBorder(new EmptyBorder(8, 12, 8, 12));
        recBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel recLbl = new JLabel("<html><body style='width:680px;'><b>Curriculum Action Plan:</b> " + esc(item.recommendation) + "</body></html>");
        recLbl.setFont(ThemeManager.font(Font.PLAIN, 12));
        recLbl.setForeground(ThemeManager.secondaryText());
        recBox.add(recLbl, BorderLayout.CENTER);

        row.add(top);
        row.add(Box.createVerticalStrut(6));
        row.add(bar);
        row.add(Box.createVerticalStrut(6));
        row.add(recBox);
        return row;
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
