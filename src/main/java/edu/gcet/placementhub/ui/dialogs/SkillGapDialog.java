package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.model.Student;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.SkillService;
import edu.gcet.placementhub.service.SkillService.SkillGapItem;
import edu.gcet.placementhub.service.SkillService.SkillGapReport;
import edu.gcet.placementhub.service.SkillService.SkillMatchItem;
import edu.gcet.placementhub.service.SkillService.SkillRequirement;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GCET PLACEMENT HUB - Skill Gap Analysis Engine Dialog
 * Computes exact mathematical alignment between student competencies and industry drive requirements.
 */
public class SkillGapDialog extends JDialog {
    private final SkillService skillService;
    private final StudentDao studentDao;
    private final User currentUser;
    private final String initialStudentId;

    private JComboBox<StudentItem> cmbStudents;
    private JComboBox<String> cmbRoleProfiles;
    private JLabel lblReadinessGauge;
    private JLabel lblReadinessSub;

    private JTable tblMatched;
    private JTable tblImprove;
    private JTable tblMissing;
    private DefaultTableModel modelMatched;
    private DefaultTableModel modelImprove;
    private DefaultTableModel modelMissing;

    private static class StudentItem {
        final String id;
        final String display;

        StudentItem(String id, String display) {
            this.id = id;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public SkillGapDialog(Window owner, User currentUser, String initialStudentId) {
        super(owner, "Skill Gap Analysis Engine · GCET Placement Cell", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.initialStudentId = initialStudentId;
        this.skillService = new SkillService();
        this.studentDao = new StudentDao();

        initUI();
        loadStudents();
        runAnalysis();
    }

    private void initUI() {
        setSize(880, 680);
        setLocationRelativeTo(getOwner());
        setMinimumSize(new Dimension(800, 600));
        setLayout(new BorderLayout());
        getContentPane().setBackground(Theme.LIGHT_BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 24, 16, 24));

        JLabel title = new JLabel("Target Placement Drive · Skill Gap Assessment");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Real-time mathematical competency mapping based on actual GCET academic and skill ratings");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Top Controls: Student Selector & Job Profile Target
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 12));
        topPanel.setBackground(Color.WHITE);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.SUBTLE_BORDER));

        topPanel.add(new JLabel("Student:"));
        cmbStudents = new JComboBox<>();
        cmbStudents.setPreferredSize(new Dimension(280, 32));
        cmbStudents.addActionListener(e -> runAnalysis());
        topPanel.add(cmbStudents);

        topPanel.add(new JLabel("Target Role Benchmark:"));
        String[] profiles = {
            "Full Stack Java Engineer (Tier 1)",
            "Data Science & AI Specialist",
            "Cloud & DevOps Associate",
            "Embedded Systems & VLSI Engineer",
            "Core Software Developer (Standard IT)"
        };
        cmbRoleProfiles = new JComboBox<>(profiles);
        cmbRoleProfiles.setPreferredSize(new Dimension(260, 32));
        cmbRoleProfiles.addActionListener(e -> runAnalysis());
        topPanel.add(cmbRoleProfiles);

        JButton btnAnalyze = Theme.createPrimaryButton("Re-Calculate");
        btnAnalyze.addActionListener(e -> runAnalysis());
        topPanel.add(btnAnalyze);

        // Center: Metrics Banner + Breakdown Tabs
        JPanel centerPanel = new JPanel(new BorderLayout(0, 10));
        centerPanel.setBorder(new EmptyBorder(14, 16, 14, 16));
        centerPanel.setOpaque(false);
        centerPanel.add(topPanel, BorderLayout.NORTH);

        // Score Card
        JPanel scoreCard = new JPanel(new BorderLayout(14, 0));
        scoreCard.setBackground(Color.WHITE);
        scoreCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.SUBTLE_BORDER),
            new EmptyBorder(16, 20, 16, 20)
        ));

        lblReadinessGauge = new JLabel("Readiness: 0.0%", JLabel.LEFT);
        lblReadinessGauge.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblReadinessGauge.setForeground(Theme.ELECTRIC_BLUE);

        lblReadinessSub = new JLabel("Detailed competency breakdown across benchmark criteria.", JLabel.LEFT);
        lblReadinessSub.setFont(Theme.FONT_BODY);
        lblReadinessSub.setForeground(Theme.DARK_TEXT);

        scoreCard.add(lblReadinessGauge, BorderLayout.NORTH);
        scoreCard.add(lblReadinessSub, BorderLayout.SOUTH);

        // Tabbed breakdown
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.FONT_BOLD);

        // 1. Matched Table
        modelMatched = new DefaultTableModel(new String[]{"Competency", "Student Level", "Target Requirement", "Faculty Verified"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblMatched = new JTable(modelMatched);
        tblMatched.setRowHeight(26);
        tblMatched.getTableHeader().setFont(Theme.FONT_BOLD);
        tabs.addTab("✓ Matching Competencies", new JScrollPane(tblMatched));

        // 2. Needs Improvement Table
        modelImprove = new DefaultTableModel(new String[]{"Competency", "Current Level", "Target Requirement", "Gap (Levels)", "Target Action"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblImprove = new JTable(modelImprove);
        tblImprove.setRowHeight(26);
        tblImprove.getTableHeader().setFont(Theme.FONT_BOLD);
        tabs.addTab("⚠ Needs Improvement", new JScrollPane(tblImprove));

        // 3. Missing Skills Table
        modelMissing = new DefaultTableModel(new String[]{"Missing Competency", "Target Requirement", "Priority", "Recommended GCET CDC Resource"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblMissing = new JTable(modelMissing);
        tblMissing.setRowHeight(26);
        tblMissing.getTableHeader().setFont(Theme.FONT_BOLD);
        tabs.addTab("✕ Missing Competencies", new JScrollPane(tblMissing));

        JPanel middle = new JPanel(new BorderLayout(0, 10));
        middle.setOpaque(false);
        middle.add(scoreCard, BorderLayout.NORTH);
        middle.add(tabs, BorderLayout.CENTER);

        centerPanel.add(middle, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnClose = Theme.createSecondaryButton("Close Analysis");
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose);

        add(footer, BorderLayout.SOUTH);
    }

    private void loadStudents() {
        cmbStudents.removeAllItems();
        try {
            List<Student> list = studentDao.findWithFilters(null, null, null);
            for (Student s : list) {
                StudentItem item = new StudentItem(s.getId(), s.getFullName() + " (" + s.getRollNumber() + " - " + s.getBranch() + ")");
                cmbStudents.addItem(item);
                if (initialStudentId != null && initialStudentId.equals(s.getId())) {
                    cmbStudents.setSelectedItem(item);
                }
            }
        } catch (Exception ignored) {}
    }

    private void runAnalysis() {
        StudentItem selected = (StudentItem) cmbStudents.getSelectedItem();
        if (selected == null) return;

        List<SkillRequirement> requirements = getRequirementsForProfile((String) cmbRoleProfiles.getSelectedItem());
        SkillGapReport report = skillService.calculateSkillGap(selected.id, requirements);

        // Update score
        double score = report.getReadinessPercentage();
        lblReadinessGauge.setText("Drive Alignment Score: " + score + "%");
        if (score >= 75.0) {
            lblReadinessGauge.setForeground(Theme.SUCCESS_GREEN);
            lblReadinessSub.setText("High Eligibility: Student strongly meets technical prerequisites for " + cmbRoleProfiles.getSelectedItem());
        } else if (score >= 50.0) {
            lblReadinessGauge.setForeground(Theme.WARNING_AMBER);
            lblReadinessSub.setText("Moderate Eligibility: Student meets " + report.getFullyMatched() + " of " + report.getTotalRequired() + " competencies. Targeted training recommended.");
        } else {
            lblReadinessGauge.setForeground(Theme.DANGER_ROSE);
            lblReadinessSub.setText("Significant Gap: Student requires focused skill development across key areas before application.");
        }

        // Populate Matched Table
        modelMatched.setRowCount(0);
        for (SkillMatchItem item : report.getMatchingSkills()) {
            modelMatched.addRow(new Object[]{
                item.getSkillName(),
                "Level " + item.getActualProficiency(),
                "Level " + item.getRequiredProficiency(),
                item.isVerified() ? "✓ Faculty Verified" : "Self-Reported"
            });
        }

        // Populate Improvement Table
        modelImprove.setRowCount(0);
        for (SkillGapItem item : report.getImprovementSkills()) {
            modelImprove.addRow(new Object[]{
                item.getSkillName(),
                "Level " + item.getActualProficiency(),
                "Level " + item.getRequiredProficiency(),
                "+" + item.getGap() + " Level",
                "Complete advanced project & submit code evidence"
            });
        }

        // Populate Missing Table
        modelMissing.setRowCount(0);
        for (SkillRequirement req : report.getMissingSkills()) {
            modelMissing.addRow(new Object[]{
                req.getSkillName(),
                "Level " + req.getMinProficiency(),
                "High",
                "Enroll in CDC Special Interest Group or NPTEL Course"
            });
        }
    }

    private List<SkillRequirement> getRequirementsForProfile(String profile) {
        List<SkillRequirement> list = new ArrayList<>();
        if (profile == null) profile = "";

        if (profile.contains("Full Stack Java")) {
            list.add(new SkillRequirement("Java", 3));
            list.add(new SkillRequirement("Spring Boot", 2));
            list.add(new SkillRequirement("SQL & Relational DBs", 2));
            list.add(new SkillRequirement("React & TypeScript", 2));
            list.add(new SkillRequirement("Data Structures & Algorithms", 3));
            list.add(new SkillRequirement("Git & CI/CD", 2));
        } else if (profile.contains("Data Science")) {
            list.add(new SkillRequirement("Python", 3));
            list.add(new SkillRequirement("Machine Learning", 3));
            list.add(new SkillRequirement("SQL & Relational DBs", 2));
            list.add(new SkillRequirement("Data Structures & Algorithms", 2));
        } else if (profile.contains("Cloud")) {
            list.add(new SkillRequirement("AWS Cloud Fundamentals", 3));
            list.add(new SkillRequirement("Git & CI/CD", 3));
            list.add(new SkillRequirement("Python", 2));
            list.add(new SkillRequirement("SQL & Relational DBs", 2));
        } else if (profile.contains("Embedded")) {
            list.add(new SkillRequirement("Embedded Systems & IoT", 3));
            list.add(new SkillRequirement("Verilog & VLSI", 3));
            list.add(new SkillRequirement("C++", 3));
        } else {
            list.add(new SkillRequirement("Java", 2));
            list.add(new SkillRequirement("Python", 2));
            list.add(new SkillRequirement("SQL & Relational DBs", 2));
            list.add(new SkillRequirement("Data Structures & Algorithms", 2));
        }
        return list;
    }
}
