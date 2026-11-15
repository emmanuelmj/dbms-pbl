package edu.gcet.placementhub.ui.pages;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.SkillDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.model.Skill;
import edu.gcet.placementhub.model.Student;
import edu.gcet.placementhub.model.StudentSkill;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.SkillService;
import edu.gcet.placementhub.ui.dialogs.SkillFormDialog;
import edu.gcet.placementhub.ui.dialogs.SkillGapDialog;
import edu.gcet.placementhub.ui.dialogs.StudentSkillDialog;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GCET PLACEMENT HUB - Complete Technical Skill Matrix Management Page
 * Geethanjali College of Engineering and Technology (Cheeryal, Keesara, Telangana)
 *
 * Implements:
 * 1. Skill Matrix Visualization (Students, Competency Ratings 1-4, Faculty Verifications)
 * 2. Institutional Skill Library (Add, Edit, Search, Category filter, Activation)
 * 3. Exact Mathematical Skill Gap Analysis Engine
 * 4. Matrix CSV Export
 */
public class SkillMatrixPage extends JPanel {
    private final User currentUser;
    private final SkillService skillService;
    private final SkillDao skillDao;
    private final StudentDao studentDao;

    // KPI Cards
    private JLabel lblTotalSkills;
    private JLabel lblActiveSkills;
    private JLabel lblCategories;
    private JLabel lblVerifiedAssessments;

    // Tabbed interface: [1] Student Skill Matrix [2] Skill Library Catalog
    private JTabbedPane mainTabs;

    // --- TAB 1: Student Skill Matrix Controls ---
    private JComboBox<String> cmbMatrixBranch;
    private JComboBox<String> cmbMatrixCategory;
    private JComboBox<String> cmbMinProficiency;
    private JTextField txtMatrixSearch;
    private JTable tblStudentSkills;
    private DefaultTableModel modelStudentSkills;
    private final List<StudentSkillRow> currentMatrixRows = new ArrayList<>();

    private JButton btnAddRating;
    private JButton btnVerifyRating;
    private JButton btnSkillGap;
    private JButton btnExportCsv;

    // --- TAB 2: Skill Library Catalog Controls ---
    private JTextField txtLibrarySearch;
    private JComboBox<String> cmbLibraryCategory;
    private JTable tblLibrary;
    private DefaultTableModel modelLibrary;
    private final List<Skill> currentSkills = new ArrayList<>();

    private JButton btnAddLibrarySkill;
    private JButton btnEditLibrarySkill;
    private JButton btnToggleLibraryStatus;

    public static class StudentSkillRow {
        public final String studentId;
        public final String rollNumber;
        public final String studentName;
        public final String branch;
        public final StudentSkill skill;

        public StudentSkillRow(String studentId, String rollNumber, String studentName, String branch, StudentSkill skill) {
            this.studentId = studentId;
            this.rollNumber = rollNumber;
            this.studentName = studentName;
            this.branch = branch;
            this.skill = skill;
        }
    }

    public SkillMatrixPage(User currentUser) {
        this.currentUser = currentUser;
        this.skillService = new SkillService();
        this.skillDao = new SkillDao();
        this.studentDao = new StudentDao();

        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.LIGHT_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        initUI();
        loadAllDataAsync();
    }

    private void initUI() {
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        topContainer.add(createTitleHeader());
        topContainer.add(Box.createVerticalStrut(14));
        topContainer.add(createKpiPanel());

        add(topContainer, BorderLayout.NORTH);

        // Center Tabbed Interface
        mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainTabs.setBackground(Color.WHITE);

        mainTabs.addTab("📊 Student Skill Matrix Grid", createMatrixTabPanel());
        mainTabs.addTab("📚 Institutional Skill Library", createLibraryTabPanel());

        add(mainTabs, BorderLayout.CENTER);
    }

    private JPanel createTitleHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        JPanel left = new JPanel(new GridLayout(2, 1, 2, 2));
        left.setOpaque(false);

        JLabel title = new JLabel("Technical Competency Matrix & Placement Skill Index");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Theme.MIDNIGHT_NAVY);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · 4-Level Competency Framework with Faculty Verification");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(Color.GRAY);

        left.add(title);
        left.add(sub);
        p.add(left, BorderLayout.WEST);

        return p;
    }

    private JPanel createKpiPanel() {
        JPanel p = new JPanel(new GridLayout(1, 4, 14, 0));
        p.setOpaque(false);

        lblTotalSkills = new JLabel("0", JLabel.LEFT);
        lblActiveSkills = new JLabel("0", JLabel.LEFT);
        lblCategories = new JLabel("0", JLabel.LEFT);
        lblVerifiedAssessments = new JLabel("0", JLabel.LEFT);

        p.add(createKpiCard("LIBRARY COMPETENCIES", lblTotalSkills, "Institutional catalog", Theme.MIDNIGHT_NAVY));
        p.add(createKpiCard("ACTIVE CURRICULUM", lblActiveSkills, "Available for drives", Theme.SUCCESS_GREEN));
        p.add(createKpiCard("TAXONOMY CATEGORIES", lblCategories, "Programming, AI, Core", Theme.ELECTRIC_BLUE));
        p.add(createKpiCard("VERIFIED ENDORSEMENTS", lblVerifiedAssessments, "Faculty validated", Theme.CYAN_ACCENT));

        return p;
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, String subtitle, Color accent) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
            new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitle.setForeground(accent);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        valueLabel.setForeground(Theme.MIDNIGHT_NAVY);

        JLabel lblSub = new JLabel(subtitle);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(Color.GRAY);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(lblSub, BorderLayout.SOUTH);
        return card;
    }

    // ========================================================
    // TAB 1: Student Skill Matrix Panel
    // ========================================================

    private JPanel createMatrixTabPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        // Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        txtMatrixSearch = new JTextField(12);
        txtMatrixSearch.putClientProperty("JTextField.placeholderText", "Search student, skill...");
        txtMatrixSearch.setPreferredSize(new Dimension(160, 32));
        txtMatrixSearch.addActionListener(e -> loadMatrixDataAsync());
        left.add(txtMatrixSearch);

        cmbMatrixBranch = new JComboBox<>();
        cmbMatrixBranch.addItem("All Branches");
        for (String b : AppConfig.BRANCHES) cmbMatrixBranch.addItem(b);
        cmbMatrixBranch.setPreferredSize(new Dimension(130, 32));
        cmbMatrixBranch.addActionListener(e -> loadMatrixDataAsync());
        left.add(cmbMatrixBranch);

        cmbMatrixCategory = new JComboBox<>();
        cmbMatrixCategory.addItem("All Categories");
        cmbMatrixCategory.setPreferredSize(new Dimension(150, 32));
        cmbMatrixCategory.addActionListener(e -> loadMatrixDataAsync());
        left.add(cmbMatrixCategory);

        cmbMinProficiency = new JComboBox<>(new String[]{"Any Level", "Level 1+ (Beginner)", "Level 2+ (Intermediate)", "Level 3+ (Advanced)", "Level 4 (Expert)"});
        cmbMinProficiency.setPreferredSize(new Dimension(150, 32));
        cmbMinProficiency.addActionListener(e -> loadMatrixDataAsync());
        left.add(cmbMinProficiency);

        toolbar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        btnAddRating = Theme.createPrimaryButton("+ Assess / Add Skill");
        btnAddRating.setPreferredSize(new Dimension(150, 32));
        btnAddRating.addActionListener(e -> openAddSkillRating());
        right.add(btnAddRating);

        btnVerifyRating = Theme.createSecondaryButton("✓ Verify");
        btnVerifyRating.setPreferredSize(new Dimension(85, 32));
        btnVerifyRating.setEnabled(false);
        btnVerifyRating.addActionListener(e -> verifySelectedRating());
        right.add(btnVerifyRating);

        btnSkillGap = Theme.createPrimaryButton("🎯 Skill Gap Engine");
        btnSkillGap.setPreferredSize(new Dimension(150, 32));
        btnSkillGap.setBackground(Theme.MIDNIGHT_NAVY);
        btnSkillGap.addActionListener(e -> openSkillGapDialog());
        right.add(btnSkillGap);

        btnExportCsv = Theme.createSecondaryButton("Export CSV");
        btnExportCsv.setPreferredSize(new Dimension(95, 32));
        btnExportCsv.addActionListener(e -> exportMatrixToCsv());
        right.add(btnExportCsv);

        toolbar.add(right, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        // Table
        String[] cols = {"Roll No", "Student Name", "Branch", "Skill Name", "Category", "Proficiency Level", "Experience", "Certifications", "Verification"};
        modelStudentSkills = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        tblStudentSkills = new JTable(modelStudentSkills);
        tblStudentSkills.setRowHeight(30);
        tblStudentSkills.setFont(Theme.FONT_BODY);
        tblStudentSkills.getTableHeader().setFont(Theme.FONT_BOLD);
        tblStudentSkills.getTableHeader().setBackground(Theme.LIGHT_BG);
        tblStudentSkills.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblStudentSkills.setShowVerticalLines(false);
        tblStudentSkills.setGridColor(Theme.SUBTLE_BORDER);

        tblStudentSkills.setRowSorter(new TableRowSorter<>(modelStudentSkills));

        // Proficiency badge renderer
        tblStudentSkills.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, sel, foc, row, col);
                String txt = val != null ? val.toString() : "";
                if (txt.contains("Level 4") || txt.contains("Expert")) l.setForeground(new Color(126, 34, 206));
                else if (txt.contains("Level 3") || txt.contains("Advanced")) l.setForeground(Theme.ELECTRIC_BLUE);
                else if (txt.contains("Level 2") || txt.contains("Intermediate")) l.setForeground(Theme.SUCCESS_GREEN);
                else l.setForeground(Color.DARK_GRAY);
                l.setFont(Theme.FONT_BOLD);
                return l;
            }
        });

        // Verification renderer
        tblStudentSkills.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, sel, foc, row, col);
                String txt = val != null ? val.toString() : "";
                if (txt.startsWith("✓")) {
                    l.setForeground(Theme.SUCCESS_GREEN);
                } else {
                    l.setForeground(Theme.WARNING_AMBER);
                }
                l.setFont(Theme.FONT_BOLD);
                return l;
            }
        });

        tblStudentSkills.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateMatrixButtonStates();
            }
        });

        panel.add(new JScrollPane(tblStudentSkills), BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // TAB 2: Institutional Skill Library Panel
    // ========================================================

    private JPanel createLibraryTabPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        // Toolbar
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        txtLibrarySearch = new JTextField(15);
        txtLibrarySearch.putClientProperty("JTextField.placeholderText", "Search skill library...");
        txtLibrarySearch.setPreferredSize(new Dimension(200, 32));
        txtLibrarySearch.addActionListener(e -> loadLibraryDataAsync());
        left.add(txtLibrarySearch);

        cmbLibraryCategory = new JComboBox<>();
        cmbLibraryCategory.addItem("All Categories");
        cmbLibraryCategory.setPreferredSize(new Dimension(170, 32));
        cmbLibraryCategory.addActionListener(e -> loadLibraryDataAsync());
        left.add(cmbLibraryCategory);

        toolbar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        btnAddLibrarySkill = Theme.createPrimaryButton("+ Add Skill Definition");
        btnAddLibrarySkill.setPreferredSize(new Dimension(170, 32));
        btnAddLibrarySkill.setEnabled(currentUser.canManageSkillLibrary());
        btnAddLibrarySkill.addActionListener(e -> openLibraryForm(null));
        right.add(btnAddLibrarySkill);

        btnEditLibrarySkill = Theme.createSecondaryButton("Edit");
        btnEditLibrarySkill.setPreferredSize(new Dimension(75, 32));
        btnEditLibrarySkill.setEnabled(false);
        btnEditLibrarySkill.addActionListener(e -> openLibraryForm(getSelectedLibrarySkill()));
        right.add(btnEditLibrarySkill);

        btnToggleLibraryStatus = Theme.createSecondaryButton("Toggle Status");
        btnToggleLibraryStatus.setPreferredSize(new Dimension(105, 32));
        btnToggleLibraryStatus.setEnabled(false);
        btnToggleLibraryStatus.addActionListener(e -> toggleLibrarySkillStatus());
        right.add(btnToggleLibraryStatus);

        toolbar.add(right, BorderLayout.EAST);
        panel.add(toolbar, BorderLayout.NORTH);

        // Table
        String[] cols = {"Skill Name", "Category", "Description", "Status", "Date Added"};
        modelLibrary = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        tblLibrary = new JTable(modelLibrary);
        tblLibrary.setRowHeight(30);
        tblLibrary.setFont(Theme.FONT_BODY);
        tblLibrary.getTableHeader().setFont(Theme.FONT_BOLD);
        tblLibrary.getTableHeader().setBackground(Theme.LIGHT_BG);
        tblLibrary.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblLibrary.setShowVerticalLines(false);
        tblLibrary.setGridColor(Theme.SUBTLE_BORDER);

        tblLibrary.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, sel, foc, row, col);
                String st = val != null ? val.toString() : "";
                if ("Active".equalsIgnoreCase(st)) {
                    l.setForeground(Theme.SUCCESS_GREEN);
                    l.setText("● Active");
                } else {
                    l.setForeground(Theme.DANGER_ROSE);
                    l.setText("○ Inactive");
                }
                l.setFont(Theme.FONT_BOLD);
                return l;
            }
        });

        tblLibrary.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Skill s = getSelectedLibrarySkill();
                boolean hasSel = (s != null);
                btnEditLibrarySkill.setEnabled(hasSel && currentUser.canManageSkillLibrary());
                btnToggleLibraryStatus.setEnabled(hasSel && currentUser.canManageSkillLibrary());
            }
        });

        panel.add(new JScrollPane(tblLibrary), BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // Async Data Loading & Operations
    // ========================================================

    public void loadAllDataAsync() {
        loadCategories();
        loadMatrixDataAsync();
        loadLibraryDataAsync();
    }

    private void loadCategories() {
        List<String> cats = skillService.getCategories();
        cmbMatrixCategory.removeAllItems();
        cmbMatrixCategory.addItem("All Categories");
        cmbLibraryCategory.removeAllItems();
        cmbLibraryCategory.addItem("All Categories");

        for (String c : cats) {
            cmbMatrixCategory.addItem(c);
            cmbLibraryCategory.addItem(c);
        }
    }

    public void loadMatrixDataAsync() {
        String search = txtMatrixSearch.getText().trim().toLowerCase();
        String branch = (String) cmbMatrixBranch.getSelectedItem();
        String cat = (String) cmbMatrixCategory.getSelectedItem();
        int minProf = cmbMinProficiency.getSelectedIndex(); // 0: Any, 1: Level 1, 2: Level 2, 3: Level 3, 4: Level 4

        SwingWorker<List<StudentSkillRow>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<StudentSkillRow> doInBackground() {
                List<StudentSkillRow> rows = new ArrayList<>();
                try {
                    List<Student> students = studentDao.findWithFilters(
                        "All Branches".equals(branch) ? null : branch, null, null
                    );

                    for (Student st : students) {
                        List<StudentSkill> skills = skillService.getStudentSkills(st.getId());
                        for (StudentSkill ss : skills) {
                            if (!"All Categories".equals(cat) && !cat.equalsIgnoreCase(ss.getSkillCategory())) {
                                continue;
                            }
                            if (minProf > 0 && ss.getProficiency() < minProf) {
                                continue;
                            }
                            if (!search.isEmpty()) {
                                String combined = (st.getFullName() + " " + st.getRollNumber() + " " + ss.getSkillName()).toLowerCase();
                                if (!combined.contains(search)) continue;
                            }
                            rows.add(new StudentSkillRow(st.getId(), st.getRollNumber(), st.getFullName(), st.getBranch(), ss));
                        }
                    }
                } catch (Exception ignored) {}
                return rows;
            }

            @Override
            protected void done() {
                try {
                    List<StudentSkillRow> rows = get();
                    currentMatrixRows.clear();
                    currentMatrixRows.addAll(rows);

                    modelStudentSkills.setRowCount(0);
                    for (StudentSkillRow r : rows) {
                        StudentSkill s = r.skill;
                        String ver = s.isVerifiedByFaculty() ? "✓ Faculty Verified (" + (s.getVerifierName() != null ? s.getVerifierName() : "Faculty") + ")" : "○ Self-Reported";
                        modelStudentSkills.addRow(new Object[]{
                            r.rollNumber,
                            r.studentName,
                            r.branch,
                            s.getSkillName(),
                            s.getSkillCategory(),
                            "Level " + s.getProficiency() + " (" + StudentSkill.getProficiencyLabel(s.getProficiency()) + ")",
                            s.getExperienceDuration() != null ? s.getExperienceDuration() : "-",
                            s.getCertificationEvidence() != null && !s.getCertificationEvidence().isEmpty() ? s.getCertificationEvidence() : "(None)",
                            ver
                        });
                    }
                    updateMatrixButtonStates();
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    public void loadLibraryDataAsync() {
        String search = txtLibrarySearch.getText().trim();
        String cat = (String) cmbLibraryCategory.getSelectedItem();
        if ("All Categories".equals(cat)) cat = "ALL";

        final String finalCat = cat;

        SwingWorker<List<Skill>, Void> worker = new SwingWorker<>() {
            private Map<String, Integer> metrics;

            @Override
            protected List<Skill> doInBackground() {
                metrics = skillDao.getSkillMetrics();
                return skillDao.findAllSkills(search, finalCat, "ALL");
            }

            @Override
            protected void done() {
                try {
                    List<Skill> list = get();
                    currentSkills.clear();
                    currentSkills.addAll(list);

                    modelLibrary.setRowCount(0);
                    for (Skill s : list) {
                        modelLibrary.addRow(new Object[]{
                            s.getName(),
                            s.getCategory(),
                            s.getDescription() != null ? s.getDescription() : "",
                            s.isActive() ? "Active" : "Inactive",
                            s.getCreatedAt() != null ? s.getCreatedAt().toString().substring(0, 10) : "-"
                        });
                    }

                    if (metrics != null) {
                        lblTotalSkills.setText(String.valueOf(metrics.getOrDefault("TOTAL_SKILLS", 0)));
                        lblActiveSkills.setText(String.valueOf(metrics.getOrDefault("ACTIVE_SKILLS", 0)));
                        lblCategories.setText(String.valueOf(metrics.getOrDefault("CATEGORIES", 0)));
                        lblVerifiedAssessments.setText(String.valueOf(metrics.getOrDefault("VERIFIED_ASSESSMENTS", 0)));
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private StudentSkillRow getSelectedMatrixRow() {
        int viewRow = tblStudentSkills.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblStudentSkills.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < currentMatrixRows.size()) {
            return currentMatrixRows.get(modelRow);
        }
        return null;
    }

    private Skill getSelectedLibrarySkill() {
        int viewRow = tblLibrary.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblLibrary.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < currentSkills.size()) {
            return currentSkills.get(modelRow);
        }
        return null;
    }

    private void updateMatrixButtonStates() {
        StudentSkillRow r = getSelectedMatrixRow();
        boolean hasSel = (r != null);
        btnVerifyRating.setEnabled(hasSel && currentUser.canAssessSkills() && !r.skill.isVerifiedByFaculty());
    }

    private void openAddSkillRating() {
        StudentSkillRow sel = getSelectedMatrixRow();
        String stId = sel != null ? sel.studentId : null;

        StudentSkillDialog dlg = new StudentSkillDialog(SwingUtilities.getWindowAncestor(this), currentUser, stId, null);
        dlg.setVisible(true);
        if (dlg.isSaved()) {
            loadMatrixDataAsync();
            loadLibraryDataAsync();
        }
    }

    private void verifySelectedRating() {
        StudentSkillRow r = getSelectedMatrixRow();
        if (r == null) return;

        try {
            skillService.verifyStudentSkill(currentUser, r.studentId, r.skill.getSkillId());
            JOptionPane.showMessageDialog(this,
                    "Skill '" + r.skill.getSkillName() + "' for student " + r.studentName + " verified by faculty.",
                    "Verification Recorded", JOptionPane.INFORMATION_MESSAGE);
            loadMatrixDataAsync();
            loadLibraryDataAsync();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openSkillGapDialog() {
        StudentSkillRow r = getSelectedMatrixRow();
        String stId = r != null ? r.studentId : null;
        SkillGapDialog dlg = new SkillGapDialog(SwingUtilities.getWindowAncestor(this), currentUser, stId);
        dlg.setVisible(true);
    }

    private void exportMatrixToCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Skill Matrix to CSV");
        chooser.setSelectedFile(new File("GCET_Skill_Matrix_Export.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Comma Delimited (*.csv)", "csv"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if (!f.getName().toLowerCase().endsWith(".csv")) {
                f = new File(f.getAbsolutePath() + ".csv");
            }

            try (FileWriter fw = new FileWriter(f)) {
                // Header
                fw.write("Roll Number,Student Name,Branch,Skill Name,Category,Proficiency Level,Experience,Certifications,Verification Status\n");
                for (StudentSkillRow r : currentMatrixRows) {
                    StudentSkill s = r.skill;
                    String cert = s.getCertificationEvidence() != null ? s.getCertificationEvidence().replace(",", ";") : "";
                    String ver = s.isVerifiedByFaculty() ? "Verified" : "Self-Reported";
                    fw.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\"\n",
                        r.rollNumber, r.studentName, r.branch, s.getSkillName(), s.getSkillCategory(),
                        s.getProficiency(), s.getExperienceDuration(), cert, ver
                    ));
                }
                JOptionPane.showMessageDialog(this,
                        "Successfully exported " + currentMatrixRows.size() + " skill matrix records to:\n" + f.getAbsolutePath(),
                        "CSV Export Completed", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error exporting CSV: " + ex.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openLibraryForm(Skill skill) {
        SkillFormDialog dlg = new SkillFormDialog(SwingUtilities.getWindowAncestor(this), currentUser, skill);
        dlg.setVisible(true);
        if (dlg.isSaved()) {
            loadCategories();
            loadLibraryDataAsync();
        }
    }

    private void toggleLibrarySkillStatus() {
        Skill s = getSelectedLibrarySkill();
        if (s == null) return;

        String newStatus = s.isActive() ? "INACTIVE" : "ACTIVE";
        int confirm = JOptionPane.showConfirmDialog(this,
                "Change status of skill '" + s.getName() + "' to " + newStatus + "?",
                "Confirm Skill Status Toggle", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                skillService.toggleSkillStatus(currentUser, s.getId(), newStatus);
                loadLibraryDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
