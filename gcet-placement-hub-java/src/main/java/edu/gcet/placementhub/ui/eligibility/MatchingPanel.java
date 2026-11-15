package edu.gcet.placementhub.ui.eligibility;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.PlacementController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.PlacementModels.CompanyMatch;
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
 * Intelligent Company Matching Engine (Phase 4):
 * Ranks open/upcoming campus placement drives against real student academic records and verified skills,
 * showing transparent fit breakdowns and eligibility-gated direct Apply triggers.
 */
public class MatchingPanel extends JPanel {

    private record StudentOption(Student student) {
        @Override
        public String toString() {
            return String.format(Locale.US, "%s - %s (%s, CGPA: %.2f)",
                    student.getRollNumber(), student.getFullName(), student.getBranch(), student.getCgpa());
        }
    }

    private final PlacementController controller = new PlacementController();
    private final Consumer<String> onViewBreakdown;

    private List<Student> allStudents = List.of();
    private List<PlacementDrive> allDrives = List.of();
    private List<Company> allCompanies = List.of();
    private List<Skill> allSkills = List.of();
    private List<Application> allApplications = List.of();

    private String pendingStudentId;
    private boolean suppressEvents = false;

    private JLabel subtitleLabel;
    private AlertBanner statusBanner;
    private JComboBox<StudentOption> studentCombo;
    private JCheckBox eligibleOnlyCheck;
    private JPanel matchesContainer;

    public MatchingPanel(Consumer<String> onViewBreakdown) {
        this.onViewBreakdown = onViewBreakdown;
        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));
        buildUi();
    }

    public void setInitialStudentId(String studentId) {
        if (studentId != null && !studentId.isBlank()) this.pendingStudentId = studentId;
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        subtitleLabel.setText("Computing live placement opportunity fit scores from MySQL...");
        controller.loadPlacementBundleAsync(actor, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.allStudents = bundle.students;
            this.allDrives = bundle.drives;
            this.allCompanies = bundle.companies;
            this.allSkills = bundle.skills;
            this.allApplications = bundle.applications;
            populateStudentSelector(actor);
            renderMatches();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        populateStudentSelector(AppSession.getInstance().getCurrentUser());
        renderMatches();
        revalidate();
        repaint();
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Intelligent Company Matching Engine");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("Ranks active drives by academic thresholds, branch fit, and technical skill proficiency overlap.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(subtitleLabel);

        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        header.add(titleCol, BorderLayout.WEST);
        header.add(refreshBtn, BorderLayout.EAST);

        RoundedCard filterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));
        filterCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        statusBanner = new AlertBanner();
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftBar.setOpaque(false);
        JLabel lbl = new JLabel("Student Profile:");
        lbl.setFont(ThemeManager.font(Font.BOLD, 12));
        lbl.setForeground(ThemeManager.secondaryText());
        studentCombo = new JComboBox<>();
        studentCombo.setPreferredSize(new Dimension(380, 34));
        studentCombo.addActionListener(e -> {
            if (!suppressEvents) {
                StudentOption s = (StudentOption) studentCombo.getSelectedItem();
                if (s != null) pendingStudentId = s.student().getId();
                renderMatches();
            }
        });
        leftBar.add(lbl);
        leftBar.add(studentCombo);

        eligibleOnlyCheck = new JCheckBox("Show 100% Eligible Opportunities Only");
        eligibleOnlyCheck.setOpaque(false);
        eligibleOnlyCheck.setFont(ThemeManager.font(Font.BOLD, 12));
        eligibleOnlyCheck.setForeground(ThemeManager.primaryText());
        eligibleOnlyCheck.addActionListener(e -> renderMatches());

        bar.add(leftBar, BorderLayout.WEST);
        bar.add(eligibleOnlyCheck, BorderLayout.EAST);

        filterCard.add(statusBanner);
        filterCard.add(Box.createVerticalStrut(2));
        filterCard.add(bar);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(header);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(filterCard);

        matchesContainer = new JPanel();
        matchesContainer.setOpaque(false);
        matchesContainer.setLayout(new BoxLayout(matchesContainer, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(matchesContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ThemeManager.surfaceBg());
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        add(topStack, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    private void populateStudentSelector(User actor) {
        suppressEvents = true;
        try {
            studentCombo.removeAllItems();
            StudentOption match = null;
            for (Student s : allStudents) {
                StudentOption opt = new StudentOption(s);
                studentCombo.addItem(opt);
                if (pendingStudentId != null && pendingStudentId.equals(s.getId())) {
                    match = opt;
                } else if (match == null && actor != null && actor.getRole() == UserRole.STUDENT
                        && actor.getStudentRollNumber() != null
                        && actor.getStudentRollNumber().equalsIgnoreCase(s.getRollNumber())) {
                    match = opt;
                }
            }
            if (match != null) studentCombo.setSelectedItem(match);
            else if (studentCombo.getItemCount() > 0) studentCombo.setSelectedIndex(0);
            studentCombo.setEnabled(actor == null || actor.getRole() != UserRole.STUDENT);
        } finally {
            suppressEvents = false;
        }
    }

    private void renderMatches() {
        matchesContainer.removeAll();
        StudentOption stOpt = (StudentOption) studentCombo.getSelectedItem();
        if (stOpt == null) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setBorder(new EmptyBorder(32, 24, 32, 24));
            JLabel msg = new JLabel("No student records available in MySQL to compute matches.");
            msg.setFont(ThemeManager.font(Font.PLAIN, 13));
            msg.setForeground(ThemeManager.mutedText());
            empty.add(msg);
            matchesContainer.add(empty);
            matchesContainer.revalidate();
            matchesContainer.repaint();
            return;
        }

        Student student = stOpt.student();
        List<CompanyMatch> matches = PlacementEngine.findMatchesForStudent(student, allDrives, allCompanies, allSkills);
        boolean eligibleOnly = eligibleOnlyCheck != null && eligibleOnlyCheck.isSelected();
        if (eligibleOnly) {
            matches = matches.stream().filter(m -> m.eligibility.eligible).toList();
        }

        long eligibleCount = matches.stream().filter(m -> m.eligibility.eligible).count();
        subtitleLabel.setText(String.format("Ranked %d active drive(s) for %s (%s) — %d directly eligible.",
                matches.size(), student.getFullName(), student.getRollNumber(), eligibleCount));

        if (matches.isEmpty()) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setBorder(new EmptyBorder(36, 24, 36, 24));
            JLabel msg = new JLabel("No matching open/upcoming placement drives found for the selected filter.");
            msg.setFont(ThemeManager.font(Font.PLAIN, 13));
            msg.setForeground(ThemeManager.mutedText());
            empty.add(msg);
            matchesContainer.add(empty);
        } else {
            User actor = AppSession.getInstance().getCurrentUser();
            for (CompanyMatch m : matches) {
                matchesContainer.add(buildMatchCard(student, m, actor));
                matchesContainer.add(Box.createVerticalStrut(12));
            }
        }

        matchesContainer.revalidate();
        matchesContainer.repaint();
    }

    private JPanel buildMatchCard(Student student, CompanyMatch match, User actor) {
        boolean elig = match.eligibility.eligible;
        Color borderCol = elig ? ThemeManager.cardBorder() : ThemeManager.cardBorder();
        RoundedCard card = new RoundedCard(14,
                elig ? ThemeManager.cardBg() : ThemeManager.cardSubtleBg(),
                borderCol,
                elig ? ThemeManager.CYAN_DARK : null,
                1.0f);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleLine.setOpaque(false);
        JLabel compName = new JLabel(match.company.getName());
        compName.setFont(ThemeManager.font(Font.BOLD, 16));
        compName.setForeground(ThemeManager.primaryText());
        titleLine.add(compName);
        titleLine.add(new BadgeLabel(
                match.company.getTier() != null ? match.company.getTier() : "Standard IT",
                ThemeManager.cardSubtleBg(), ThemeManager.secondaryText(), ThemeManager.cardBorder(), 8
        ));
        titleLine.add(new BadgeLabel(
                elig ? "Eligible to Apply" : "Ineligible",
                elig ? ThemeManager.EMERALD_SOFT : ThemeManager.ROSE_SOFT,
                elig ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY,
                elig ? ThemeManager.EMERALD_PRIMARY : ThemeManager.ROSE_PRIMARY,
                8
        ));

        JLabel roleLine = new JLabel(String.format(Locale.US, "%s  ·  %.1f LPA",
                match.drive.getJobRole(), match.drive.getSalaryPackageLpa()));
        roleLine.setFont(ThemeManager.font(Font.BOLD, 13));
        roleLine.setForeground(ThemeManager.CYAN_DARK);

        JLabel expLine = new JLabel("<html><body style='width:680px;'>" + esc(match.explanation) + "</body></html>");
        expLine.setFont(ThemeManager.font(Font.PLAIN, 12));
        expLine.setForeground(ThemeManager.secondaryText());

        left.add(titleLine);
        left.add(Box.createVerticalStrut(3));
        left.add(roleLine);
        left.add(Box.createVerticalStrut(4));
        left.add(expLine);

        JPanel scoreCol = new JPanel();
        scoreCol.setOpaque(false);
        scoreCol.setLayout(new BoxLayout(scoreCol, BoxLayout.Y_AXIS));
        JLabel scoreVal = new JLabel(match.matchScore + "%");
        scoreVal.setFont(ThemeManager.font(Font.BOLD, 24));
        scoreVal.setForeground(elig ? ThemeManager.EMERALD_PRIMARY : ThemeManager.primaryText());
        scoreVal.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JLabel scoreTag = new JLabel("Fit Score");
        scoreTag.setFont(ThemeManager.font(Font.PLAIN, 11));
        scoreTag.setForeground(ThemeManager.mutedText());
        scoreTag.setAlignmentX(Component.RIGHT_ALIGNMENT);
        scoreCol.add(scoreVal);
        scoreCol.add(scoreTag);

        top.add(left, BorderLayout.CENTER);
        top.add(scoreCol, BorderLayout.EAST);

        // Matched vs Missing Skills split
        JPanel overlapRow = new JPanel(new GridLayout(1, 2, 12, 0));
        overlapRow.setOpaque(false);
        overlapRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, ThemeManager.cardBorder()),
                new EmptyBorder(8, 0, 8, 0)
        ));
        overlapRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        overlapRow.add(chipGroup("✓ Matching Skills (" + match.skillsMatched.size() + ")",
                match.skillsMatched, "None satisfied yet",
                ThemeManager.EMERALD_SOFT, ThemeManager.EMERALD_PRIMARY));
        overlapRow.add(chipGroup("✗ Skills to Bridge (" + match.skillsMissing.size() + ")",
                match.skillsMissing, "All required skills satisfied!",
                ThemeManager.ROSE_SOFT, ThemeManager.ROSE_PRIMARY));

        // Footer
        JPanel foot = new JPanel(new BorderLayout());
        foot.setOpaque(false);
        foot.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel cutoffLbl = new JLabel(String.format(Locale.US,
                "Cutoff: Min CGPA %.2f  ·  Max Backlogs %d  ·  Deadline: %s",
                match.drive.getMinCgpa(), match.drive.getMaxBacklogsAllowed(), match.drive.getApplicationDeadline()));
        cutoffLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        cutoffLbl.setForeground(ThemeManager.mutedText());

        Application existingApp = null;
        for (Application a : allApplications) {
            if (student.getId().equals(a.getStudentId())
                    && match.drive.getId().equals(a.getDriveId())
                    && a.getCurrentStage() != ApplicationStage.WITHDRAWN) {
                existingApp = a;
                break;
            }
        }

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        ActionButton breakdownBtn = new ActionButton("View Breakdown", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 8);
        breakdownBtn.addActionListener(e -> {
            if (onViewBreakdown != null) onViewBreakdown.accept(match.drive.getId());
        });
        btns.add(breakdownBtn);

        boolean canApply = actor != null && (actor.getRole() == UserRole.STUDENT || actor.getRole().isPrivilegedRole());
        if (existingApp != null) {
            btns.add(new BadgeLabel("Applied (" + existingApp.getCurrentStage().getLabel() + ")",
                    ThemeManager.cardSubtleBg(), ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_ELECTRIC, 8));
        } else if (elig && canApply) {
            ActionButton applyBtn = new ActionButton("Apply Now  →", ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY.darker(), Color.WHITE, null, 8);
            applyBtn.addActionListener(e -> handleApply(student, match));
            btns.add(applyBtn);
        }

        foot.add(cutoffLbl, BorderLayout.WEST);
        foot.add(btns, BorderLayout.EAST);

        card.add(top);
        card.add(Box.createVerticalStrut(10));
        card.add(overlapRow);
        card.add(Box.createVerticalStrut(8));
        card.add(foot);
        return card;
    }

    private JPanel chipGroup(String title, List<String> items, String emptyMsg, Color bg, Color fg) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel head = new JLabel(title);
        head.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        head.setForeground(fg);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(head);
        p.add(Box.createVerticalStrut(4));

        JPanel flow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        flow.setOpaque(false);
        flow.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (items.isEmpty()) {
            JLabel em = new JLabel(emptyMsg);
            em.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            em.setForeground(ThemeManager.mutedText());
            flow.add(em);
        } else {
            for (String s : items) {
                flow.add(new BadgeLabel(s, bg, fg, fg, 6));
            }
        }
        p.add(flow);
        return p;
    }

    private void handleApply(Student student, CompanyMatch match) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Submit placement application for " + match.company.getName() + " (" + match.drive.getJobRole() + ")?\n"
                        + "Candidate: " + student.getFullName() + " (" + student.getRollNumber() + ")",
                "Confirm Direct Application",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        controller.submitApplicationAsync(AppSession.getInstance().getCurrentUser(), match.drive.getId(), student.getId(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Applied Successfully", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Cannot Apply", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
