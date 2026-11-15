package edu.gcet.placementhub.ui.pages;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.model.Student;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.dialogs.StudentFormDialog;
import edu.gcet.placementhub.ui.dialogs.StudentProfileDialog;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GCET PLACEMENT HUB - Professional Student Management Page
 * Geethanjali College of Engineering and Technology (Cheeryal, Keesara, Telangana)
 *
 * Implements Student Management Dashboard, real-time live search, branch filtering,
 * batch filtering, TableRowSorter, non-blocking SwingWorker DB queries, and role-based actions.
 */
public class StudentsPage extends JPanel {
    private final User currentUser;
    private final StudentService studentService;
    private final StudentDao studentDao;

    // KPI Dashboard Labels (Live MySQL-driven)
    private JLabel lblTotalStudents;
    private JLabel lblAverageCgpa;
    private JLabel lblBranchBreakdown;
    private JLabel lblBatchBreakdown;

    // Filters & Actions
    private JTextField txtSearch;
    private JComboBox<String> cmbBranchFilter;
    private JComboBox<String> cmbYearFilter;
    private JButton btnAddStudent;
    private JButton btnEditStudent;
    private JButton btnDeleteStudent;
    private JButton btnViewProfile;
    private JButton btnRefresh;

    // Table
    private JTable tblStudents;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private final List<Student> currentStudentList = new ArrayList<>();

    public StudentsPage(User currentUser) {
        this.currentUser = currentUser;
        this.studentService = new StudentService();
        this.studentDao = new StudentDao();

        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.LIGHT_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        initUI();
        loadStudentDataAsync();
    }

    private void initUI() {
        // Top: Dashboard KPI Header & Page Title
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        topContainer.add(createTitleHeader());
        topContainer.add(Box.createVerticalStrut(14));
        topContainer.add(createDashboardKpiPanel());
        topContainer.add(Box.createVerticalStrut(14));
        topContainer.add(createToolbarPanel());

        add(topContainer, BorderLayout.NORTH);

        // Center: Students JTable
        add(createTablePanel(), BorderLayout.CENTER);
    }

    private JPanel createTitleHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        JPanel left = new JPanel(new GridLayout(2, 1, 2, 2));
        left.setOpaque(false);

        JLabel title = new JLabel("Student Directory & Academic Records");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Theme.MIDNIGHT_NAVY);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · " + AppConfig.AUTONOMOUS_AFFILIATION);
        sub.setFont(Theme.FONT_SMALL);
        sub.setForeground(Theme.DARK_TEXT);

        left.add(title);
        left.add(sub);
        p.add(left, BorderLayout.WEST);

        return p;
    }

    /**
     * Requirement 2: Student Management Dashboard with Total, Branch Count, Avg CGPA, Grad Year Distribution.
     */
    private JPanel createDashboardKpiPanel() {
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 14, 0));
        kpiGrid.setOpaque(false);

        lblTotalStudents = new JLabel("Loading...", JLabel.CENTER);
        lblTotalStudents.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTotalStudents.setForeground(Theme.ELECTRIC_BLUE);
        kpiGrid.add(createKpiCard("TOTAL REGISTERED", lblTotalStudents, "Across 9 GCET Branches"));

        lblAverageCgpa = new JLabel("Loading...", JLabel.CENTER);
        lblAverageCgpa.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblAverageCgpa.setForeground(Theme.CYAN_ACCENT);
        kpiGrid.add(createKpiCard("AVERAGE CGPA", lblAverageCgpa, "Calculated from live records"));

        lblBranchBreakdown = new JLabel("Loading...", JLabel.CENTER);
        lblBranchBreakdown.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblBranchBreakdown.setForeground(Theme.DARK_TEXT);
        kpiGrid.add(createKpiCard("TOP BRANCHES", lblBranchBreakdown, "CSE, AIML, DS, ECE, IT"));

        lblBatchBreakdown = new JLabel("Loading...", JLabel.CENTER);
        lblBatchBreakdown.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblBatchBreakdown.setForeground(Theme.DARK_TEXT);
        kpiGrid.add(createKpiCard("GRADUATION BATCHES", lblBatchBreakdown, "Classes of 2024 - 2027"));

        return kpiGrid;
    }

    private JPanel createKpiCard(String title, JLabel valLabel, String footer) {
        JPanel card = new JPanel(new BorderLayout(4, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
                new EmptyBorder(12, 14, 12, 14)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitle.setForeground(Color.GRAY);

        JLabel lblFooter = new JLabel(footer);
        lblFooter.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblFooter.setForeground(Color.GRAY);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);
        card.add(lblFooter, BorderLayout.SOUTH);
        return card;
    }

    /**
     * Requirement 4: Filter by search, branch, year, action buttons.
     */
    private JPanel createToolbarPanel() {
        JPanel bar = new JPanel(new BorderLayout(10, 8));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        // Left: Search & Filter Dropdowns
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        left.add(new JLabel("Search:"));
        txtSearch = new JTextField(14);
        txtSearch.putClientProperty("JTextField.placeholderText", "Name or Roll Number...");
        txtSearch.addActionListener(e -> loadStudentDataAsync());
        left.add(txtSearch);

        left.add(new JLabel("Branch:"));
        List<String> branches = new ArrayList<>();
        branches.add("All Branches");
        branches.addAll(AppConfig.BRANCHES);
        cmbBranchFilter = new JComboBox<>(branches.toArray(new String[0]));
        cmbBranchFilter.addActionListener(e -> loadStudentDataAsync());
        left.add(cmbBranchFilter);

        left.add(new JLabel("Batch:"));
        List<String> years = new ArrayList<>();
        years.add("All Batches");
        for (Integer y : AppConfig.GRADUATION_YEARS) {
            years.add(String.valueOf(y));
        }
        cmbYearFilter = new JComboBox<>(years.toArray(new String[0]));
        cmbYearFilter.addActionListener(e -> loadStudentDataAsync());
        left.add(cmbYearFilter);

        btnRefresh = Theme.createSecondaryButton("Refresh");
        btnRefresh.addActionListener(e -> loadStudentDataAsync());
        left.add(btnRefresh);

        // Right: Action buttons (Role-based permissions)
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        btnViewProfile = Theme.createSecondaryButton("View Profile");
        btnViewProfile.addActionListener(e -> onViewProfile());
        right.add(btnViewProfile);

        // Edit button
        btnEditStudent = Theme.createSecondaryButton("Edit Student");
        btnEditStudent.addActionListener(e -> onEditStudent());
        right.add(btnEditStudent);

        // Delete button (Restricted to Placement Officers and Admin)
        btnDeleteStudent = Theme.createSecondaryButton("Delete");
        btnDeleteStudent.setForeground(Theme.DANGER_ROSE);
        btnDeleteStudent.addActionListener(e -> onDeleteStudent());
        if (currentUser.canManageStudents()) {
            right.add(btnDeleteStudent);
        }

        // Add Student Button (Restricted to Placement Officers and Admin)
        btnAddStudent = Theme.createPrimaryButton("+ Register Student");
        btnAddStudent.addActionListener(e -> onAddStudent());
        if (currentUser.canManageStudents()) {
            right.add(btnAddStudent);
        }

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel createTablePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1));

        String[] cols = {
                "Student ID", "Roll Number", "Full Name", "Email Address",
                "Branch", "CGPA", "Batch", "Backlogs", "Placement Status"
        };

        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 5) return Double.class;
                if (columnIndex == 6 || columnIndex == 7) return Integer.class;
                return String.class;
            }
        };

        tblStudents = new JTable(tableModel);
        tblStudents.setRowHeight(34);
        tblStudents.setFont(Theme.FONT_BODY);
        tblStudents.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tblStudents.getTableHeader().setBackground(Theme.LIGHT_BG);
        tblStudents.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Setup Sorting
        rowSorter = new TableRowSorter<>(tableModel);
        tblStudents.setRowSorter(rowSorter);

        // Hide Internal UUID column from user view, but keep in model for DB operations
        tblStudents.getColumnModel().getColumn(0).setMinWidth(0);
        tblStudents.getColumnModel().getColumn(0).setMaxWidth(0);
        tblStudents.getColumnModel().getColumn(0).setPreferredWidth(0);

        // Custom Cell Renderers
        tblStudents.getColumnModel().getColumn(1).setCellRenderer(new RollNumberRenderer());
        tblStudents.getColumnModel().getColumn(5).setCellRenderer(new CgpaRenderer());
        tblStudents.getColumnModel().getColumn(8).setCellRenderer(new StatusBadgeRenderer());

        // Double-click row listener to open student profile
        tblStudents.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblStudents.getSelectedRow() != -1) {
                    onViewProfile();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tblStudents);
        scrollPane.setBorder(null);
        p.add(scrollPane, BorderLayout.CENTER);

        return p;
    }

    /**
     * Requirement 11: Non-blocking data loading with SwingWorker
     */
    public void loadStudentDataAsync() {
        final String branchSel = (String) cmbBranchFilter.getSelectedItem();
        final String branchParam = (branchSel == null || branchSel.startsWith("All")) ? null : branchSel;

        final String yearSel = (String) cmbYearFilter.getSelectedItem();
        final Integer yearParam = (yearSel == null || yearSel.startsWith("All")) ? null : Integer.parseInt(yearSel);

        final String searchParam = txtSearch.getText().trim();

        btnRefresh.setEnabled(false);

        SwingWorker<List<Student>, Void> worker = new SwingWorker<>() {
            private int totalCount;
            private double avgCgpa;
            private Map<String, Integer> branchCounts;
            private Map<Integer, Integer> batchCounts;

            @Override
            protected List<Student> doInBackground() throws Exception {
                // 1. Fetch Students
                List<Student> students = studentDao.findWithFilters(branchParam, yearParam, searchParam);

                // 2. Fetch Live Dashboard Metrics
                totalCount = studentDao.getTotalStudentsCount();
                avgCgpa = studentDao.getAverageCgpa();
                branchCounts = studentDao.getBranchWiseCounts();
                batchCounts = studentDao.getGraduationYearDistribution();

                return students;
            }

            @Override
            protected void done() {
                try {
                    List<Student> students = get();
                    currentStudentList.clear();
                    currentStudentList.addAll(students);

                    // Update Table
                    tableModel.setRowCount(0);
                    for (Student s : students) {
                        tableModel.addRow(new Object[]{
                                s.getId(),
                                s.getRollNumber(),
                                s.getFullName(),
                                s.getEmail(),
                                s.getBranch(),
                                s.getCgpa(),
                                s.getGraduationYear(),
                                s.getBacklogs(),
                                s.getPlacementStatus()
                        });
                    }

                    // Update Dashboard KPI cards
                    lblTotalStudents.setText(String.valueOf(totalCount));
                    lblAverageCgpa.setText(String.format("%.2f", avgCgpa));

                    // Top Branches Summary
                    StringBuilder branchSummary = new StringBuilder();
                    int bCount = 0;
                    for (Map.Entry<String, Integer> entry : branchCounts.entrySet()) {
                        if (bCount++ > 0) branchSummary.append(" · ");
                        branchSummary.append(entry.getKey()).append(": ").append(entry.getValue());
                        if (bCount >= 3) break;
                    }
                    lblBranchBreakdown.setText(branchSummary.length() > 0 ? branchSummary.toString() : "0 registered");

                    // Batch Summary
                    StringBuilder batchSummary = new StringBuilder();
                    int yCount = 0;
                    for (Map.Entry<Integer, Integer> entry : batchCounts.entrySet()) {
                        if (yCount++ > 0) batchSummary.append(" · ");
                        batchSummary.append(entry.getKey()).append(": ").append(entry.getValue());
                        if (yCount >= 3) break;
                    }
                    lblBatchBreakdown.setText(batchSummary.length() > 0 ? batchSummary.toString() : "0 batches");

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(StudentsPage.this,
                            "Unable to retrieve records from MySQL database:\n" + ex.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnRefresh.setEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private Student getSelectedStudent() {
        int selectedRow = tblStudents.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this,
                    "Please select a student from the table first.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }

        int modelRow = tblStudents.convertRowIndexToModel(selectedRow);
        String studentId = (String) tableModel.getValueAt(modelRow, 0);

        for (Student s : currentStudentList) {
            if (s.getId().equals(studentId)) {
                return s;
            }
        }
        return null;
    }

    private void onAddStudent() {
        StudentFormDialog dialog = new StudentFormDialog(SwingUtilities.getWindowAncestor(this), currentUser, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadStudentDataAsync();
        }
    }

    private void onEditStudent() {
        Student selected = getSelectedStudent();
        if (selected == null) return;

        // RBAC validation
        if (!currentUser.canEditStudent(selected.getRollNumber())) {
            JOptionPane.showMessageDialog(this,
                    "Permission Denied: You do not have permission to edit this student's record.",
                    "Permission Denied", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StudentFormDialog dialog = new StudentFormDialog(SwingUtilities.getWindowAncestor(this), currentUser, selected);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            loadStudentDataAsync();
        }
    }

    private void onDeleteStudent() {
        Student selected = getSelectedStudent();
        if (selected == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete student '" + selected.getFullName() + "' (" + selected.getRollNumber() + ")?\n" +
                "This action cannot be undone.",
                "Confirm Student Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                studentService.deleteStudent(currentUser, selected.getId());
                JOptionPane.showMessageDialog(this,
                        "Student '" + selected.getRollNumber() + "' was successfully deleted from the database.",
                        "Deletion Completed", JOptionPane.INFORMATION_MESSAGE);
                loadStudentDataAsync();
            } catch (IllegalStateException ex) {
                // Meaningful message if deletion is blocked by recruitment dependencies
                JOptionPane.showMessageDialog(this,
                        ex.getMessage(),
                        "Deletion Blocked", JOptionPane.WARNING_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Error deleting student: " + ex.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onViewProfile() {
        Student selected = getSelectedStudent();
        if (selected == null) return;

        // Fetch full profile including skills from DAO
        try {
            var fullStudentOpt = studentDao.findById(selected.getId());
            Student fullStudent = fullStudentOpt.orElse(selected);

            StudentProfileDialog dialog = new StudentProfileDialog(SwingUtilities.getWindowAncestor(this), fullStudent);
            dialog.setVisible(true);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading student profile: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==========================================
    // CUSTOM TABLE CELL RENDERERS
    // ==========================================

    private static class RollNumberRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(Theme.FONT_MONO);
            if (!isSelected) {
                setForeground(Theme.ELECTRIC_BLUE);
            }
            return this;
        }
    }

    private static class CgpaRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value instanceof Double) {
                double cgpa = (Double) value;
                setText(String.format("%.2f", cgpa));
                setFont(Theme.FONT_BOLD);
                if (!isSelected) {
                    if (cgpa >= 8.5) setForeground(Theme.SUCCESS_GREEN);
                    else if (cgpa >= 7.0) setForeground(Theme.ELECTRIC_BLUE);
                    else setForeground(Theme.DARK_TEXT);
                }
            } else {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
            return this;
        }
    }

    private static class StatusBadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String status = value != null ? value.toString() : "UNPLACED";
            setText(" " + status + " ");
            setFont(Theme.FONT_BOLD);
            if (!isSelected) {
                if ("PLACED".equalsIgnoreCase(status)) {
                    setForeground(Theme.SUCCESS_GREEN);
                } else if ("SHORTLISTED".equalsIgnoreCase(status)) {
                    setForeground(Theme.ELECTRIC_BLUE);
                } else {
                    setForeground(Color.GRAY);
                }
            }
            return this;
        }
    }
}
