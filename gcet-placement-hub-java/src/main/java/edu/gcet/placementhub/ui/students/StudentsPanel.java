package edu.gcet.placementhub.ui.students;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.StudentController;
import edu.gcet.placementhub.models.Skill;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Exact Java Swing recreation of StudentsPage.tsx:
 * Searchable, filterable, sortable Student Academic Directory backed by live MySQL JDBC records,
 * with CSV export, Add/Edit/Delete student actions (RBAC-gated), and direct Profile drill-down.
 */
public class StudentsPanel extends JPanel {

    private final StudentController controller = new StudentController();
    private final Consumer<String> onSelectStudentProfile;
    private final Consumer<String> onNavigatePage;

    private List<Student> allStudents = List.of();
    private List<Student> filteredStudents = List.of();
    private List<Skill> allSkills = List.of();

    private JLabel subtitleLabel;
    private AlertBanner statusBanner;
    private JTextField searchField;
    private JComboBox<String> branchCombo;
    private JComboBox<String> statusCombo;
    private JComboBox<String> yearCombo;
    private JComboBox<String> sortFieldCombo;
    private ActionButton sortDirBtn;
    private ActionButton addStudentBtn;
    private ActionButton facultyDirBtn;
    private ActionButton viewProfileBtn;
    private ActionButton editBtn;
    private ActionButton deleteBtn;

    private JTable table;
    private DefaultTableModel tableModel;
    private boolean sortAsc = true;
    private boolean suppressFilters = false;

    public StudentsPanel(Consumer<String> onSelectStudentProfile, Consumer<String> onNavigatePage) {
        this.onSelectStudentProfile = onSelectStudentProfile;
        this.onNavigatePage = onNavigatePage;

        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));

        buildUi();
    }

    public void loadData() {
        User currentUser = AppSession.getInstance().getCurrentUser();
        updateRoleButtons(currentUser);
        subtitleLabel.setText("Querying live MySQL student records...");
        controller.loadDirectoryBundleAsync(currentUser, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.allStudents = bundle.students != null ? bundle.students : List.of();
            this.allSkills = bundle.skills != null ? bundle.skills : List.of();
            populateFilterOptions();
            applyFiltersAndRender();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        populateFilterOptions();
        applyFiltersAndRender();
        revalidate();
        repaint();
    }

    private void buildUi() {
        // Header Row
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("GCET Student Academic Directory");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("Real-time database records synchronized with MySQL.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(subtitleLabel);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionRow.setOpaque(false);

        facultyDirBtn = new ActionButton("Faculty Directory", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        facultyDirBtn.addActionListener(e -> onNavigatePage.accept("faculty"));

        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());

        ActionButton exportBtn = new ActionButton("Export CSV", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        exportBtn.addActionListener(e -> exportCsv());

        addStudentBtn = new ActionButton("+  Enroll Student", ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 10);
        addStudentBtn.addActionListener(e -> openAddStudentDialog());

        actionRow.add(facultyDirBtn);
        actionRow.add(refreshBtn);
        actionRow.add(exportBtn);
        actionRow.add(addStudentBtn);

        header.add(titleCol, BorderLayout.WEST);
        header.add(actionRow, BorderLayout.EAST);

        // Filter Card
        RoundedCard filterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));
        filterCard.setBorder(new EmptyBorder(14, 16, 14, 16));

        statusBanner = new AlertBanner();
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filterRow = new JPanel(new GridLayout(1, 5, 10, 0));
        filterRow.setOpaque(false);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search Name, Roll No, Email...");
        searchField.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void removeUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void changedUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
        });

        branchCombo = new JComboBox<>(new String[]{"All Branches"});
        branchCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        statusCombo = new JComboBox<>(new String[]{"All Statuses", "UNPLACED", "PLACED", "HIGHER_STUDIES"});
        statusCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        yearCombo = new JComboBox<>(new String[]{"All Graduation Years"});
        yearCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        JPanel sortPanel = new JPanel(new BorderLayout(6, 0));
        sortPanel.setOpaque(false);
        sortFieldCombo = new JComboBox<>(new String[]{"Sort: Roll Number", "Sort: Full Name", "Sort: CGPA"});
        sortFieldCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });
        sortDirBtn = new ActionButton("↑", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 8);
        sortDirBtn.setPreferredSize(new Dimension(38, 34));
        sortDirBtn.setToolTipText("Toggle Ascending / Descending Sort");
        sortDirBtn.addActionListener(e -> {
            sortAsc = !sortAsc;
            sortDirBtn.setText(sortAsc ? "↑" : "↓");
            applyFiltersAndRender();
        });
        sortPanel.add(sortFieldCombo, BorderLayout.CENTER);
        sortPanel.add(sortDirBtn, BorderLayout.EAST);

        filterRow.add(searchField);
        filterRow.add(branchCombo);
        filterRow.add(statusCombo);
        filterRow.add(yearCombo);
        filterRow.add(sortPanel);

        filterCard.add(statusBanner);
        filterCard.add(Box.createVerticalStrut(4));
        filterCard.add(filterRow);

        // Top wrapper
        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(header);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(filterCard);

        // Table Card
        RoundedCard tableCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.setBorder(new EmptyBorder(12, 14, 12, 14));

        String[] cols = {"Roll Number", "Student Name", "Email", "Branch", "CGPA", "10th / Inter", "Backlogs", "Skills & Portfolio", "Placement", "Approval"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        table = new JTable(tableModel);
        table.setRowHeight(36);
        table.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.getSelectionModel().addListSelectionListener(e -> updateRowActionState());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelectedProfile();
                }
            }
        });

        DefaultTableCellRenderer centerRend = new DefaultTableCellRenderer();
        centerRend.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(4).setCellRenderer(centerRend);
        table.getColumnModel().getColumn(6).setCellRenderer(centerRend);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder()));

        // Row action toolbar
        JPanel rowBar = new JPanel(new BorderLayout());
        rowBar.setOpaque(false);
        JLabel hint = new JLabel("Tip: Select a student row (or double-click) to inspect their Skill Matrix, Projects, Certifications, and Internships.");
        hint.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        hint.setForeground(ThemeManager.mutedText());

        JPanel rowBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rowBtns.setOpaque(false);
        viewProfileBtn = new ActionButton("View Full Profile  →", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        viewProfileBtn.addActionListener(e -> openSelectedProfile());
        editBtn = new ActionButton("Edit Record", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        editBtn.addActionListener(e -> openEditSelectedDialog());
        deleteBtn = new ActionButton("Delete", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, new Color(0xFC, 0xA5, 0xA5), 10);
        deleteBtn.addActionListener(e -> deleteSelectedStudent());

        rowBtns.add(viewProfileBtn);
        rowBtns.add(editBtn);
        rowBtns.add(deleteBtn);

        rowBar.add(hint, BorderLayout.WEST);
        rowBar.add(rowBtns, BorderLayout.EAST);

        tableCard.add(tableScroll, BorderLayout.CENTER);
        tableCard.add(rowBar, BorderLayout.SOUTH);

        add(topStack, BorderLayout.NORTH);
        add(tableCard, BorderLayout.CENTER);

        updateRoleButtons(AppSession.getInstance().getCurrentUser());
        updateRowActionState();
    }

    private void updateRoleButtons(User u) {
        boolean canManage = u != null && (u.getRole().isPrivilegedRole() || u.getRole() == UserRole.FACULTY);
        boolean canDelete = u != null && u.getRole().isPrivilegedRole();
        boolean canSeeFaculty = u != null && (u.getRole().isPrivilegedRole() || u.getRole() == UserRole.FACULTY);
        if (addStudentBtn != null) addStudentBtn.setVisible(canManage);
        if (editBtn != null) editBtn.setVisible(canManage || (u != null && u.getRole() == UserRole.STUDENT));
        if (deleteBtn != null) deleteBtn.setVisible(canDelete);
        if (facultyDirBtn != null) facultyDirBtn.setVisible(canSeeFaculty);
    }

    private void updateRowActionState() {
        boolean hasSelection = table != null && table.getSelectedRow() >= 0 && table.getSelectedRow() < filteredStudents.size();
        if (viewProfileBtn != null) viewProfileBtn.setEnabled(hasSelection);
        if (editBtn != null) editBtn.setEnabled(hasSelection);
        if (deleteBtn != null) deleteBtn.setEnabled(hasSelection);
    }

    private void populateFilterOptions() {
        suppressFilters = true;
        try {
            Object prevBranch = branchCombo.getSelectedItem();
            Object prevYear = yearCombo.getSelectedItem();

            Set<String> branches = new TreeSet<>();
            Set<Integer> years = new TreeSet<>();
            for (Student s : allStudents) {
                if (s.getBranch() != null && !s.getBranch().isBlank()) branches.add(s.getBranch());
                if (s.getGraduationYear() > 0) years.add(s.getGraduationYear());
            }

            branchCombo.removeAllItems();
            branchCombo.addItem("All Branches");
            for (String b : branches) branchCombo.addItem(b);
            if (prevBranch != null) branchCombo.setSelectedItem(prevBranch);

            yearCombo.removeAllItems();
            yearCombo.addItem("All Graduation Years");
            for (Integer y : years) yearCombo.addItem(String.valueOf(y));
            if (prevYear != null) yearCombo.setSelectedItem(prevYear);
        } finally {
            suppressFilters = false;
        }
    }

    private void applyFiltersAndRender() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String br = branchCombo != null && branchCombo.getSelectedItem() != null ? branchCombo.getSelectedItem().toString() : "All Branches";
        String st = statusCombo != null && statusCombo.getSelectedItem() != null ? statusCombo.getSelectedItem().toString() : "All Statuses";
        String yr = yearCombo != null && yearCombo.getSelectedItem() != null ? yearCombo.getSelectedItem().toString() : "All Graduation Years";

        List<Student> list = new ArrayList<>();
        for (Student s : allStudents) {
            boolean matchSearch = q.isEmpty()
                    || safeLower(s.getFullName()).contains(q)
                    || safeLower(s.getRollNumber()).contains(q)
                    || safeLower(s.getEmail()).contains(q);
            boolean matchBranch = "All Branches".equals(br) || br.equalsIgnoreCase(s.getBranch());
            boolean matchStatus = "All Statuses".equals(st) || st.equalsIgnoreCase(s.getPlacementStatus());
            boolean matchYear = "All Graduation Years".equals(yr) || yr.equals(String.valueOf(s.getGraduationYear()));
            if (matchSearch && matchBranch && matchStatus && matchYear) {
                list.add(s);
            }
        }

        int sortIdx = sortFieldCombo != null ? sortFieldCombo.getSelectedIndex() : 0;
        list.sort((a, b) -> {
            int cmp = switch (sortIdx) {
                case 1 -> safeStr(a.getFullName()).compareToIgnoreCase(safeStr(b.getFullName()));
                case 2 -> Double.compare(a.getCgpa(), b.getCgpa());
                default -> safeStr(a.getRollNumber()).compareToIgnoreCase(safeStr(b.getRollNumber()));
            };
            return sortAsc ? cmp : -cmp;
        });

        this.filteredStudents = list;
        subtitleLabel.setText("Real-time database records · Showing " + list.size() + " of " + allStudents.size() + " students in MySQL"
                + (!"All Branches".equals(br) ? " · Branch: " + br : ""));

        tableModel.setRowCount(0);
        for (Student s : filteredStudents) {
            String portfolio = s.getSkills().size() + " Skills · " + s.getProjects().size() + " Proj · " + s.getCertifications().size() + " Cert";
            String statusText = safeStr(s.getPlacementStatus()).isEmpty() ? "UNPLACED" : s.getPlacementStatus();
            if ("PLACED".equalsIgnoreCase(statusText) && s.getPlacedPackageLpa() != null) {
                statusText = String.format(Locale.US, "PLACED (%.1f LPA)", s.getPlacedPackageLpa());
            }
            tableModel.addRow(new Object[]{
                    s.getRollNumber(),
                    s.getFullName(),
                    s.getEmail(),
                    s.getBranch(),
                    String.format(Locale.US, "%.2f", s.getCgpa()),
                    String.format(Locale.US, "%.0f%% / %.0f%%", s.getTenthPercentage(), s.getInterPercentage()),
                    s.getBacklogs() == 0 ? "Clear" : s.getBacklogs() + " Active",
                    portfolio,
                    statusText,
                    s.getApprovalStatus() != null ? s.getApprovalStatus() : "APPROVED"
            });
        }
        updateRowActionState();
    }

    private Student getSelectedStudent() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredStudents.size()) return null;
        return filteredStudents.get(row);
    }

    private void openSelectedProfile() {
        Student s = getSelectedStudent();
        if (s != null && onSelectStudentProfile != null) {
            onSelectStudentProfile.accept(s.getId());
        }
    }

    private void openAddStudentDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        StudentFormDialog dlg = new StudentFormDialog(owner, null, false);
        Student draft = dlg.showDialog();
        if (draft == null) return;

        User actor = AppSession.getInstance().getCurrentUser();
        controller.createStudentAsync(actor, draft, "student123", res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Student Enrolled", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Enrollment Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openEditSelectedDialog() {
        Student target = getSelectedStudent();
        if (target == null) return;
        User actor = AppSession.getInstance().getCurrentUser();
        boolean isSelfStudent = actor != null && actor.getRole() == UserRole.STUDENT;

        Window owner = SwingUtilities.getWindowAncestor(this);
        StudentFormDialog dlg = new StudentFormDialog(owner, target, isSelfStudent);
        Student updated = dlg.showDialog();
        if (updated == null) return;

        controller.updateStudentAsync(actor, updated, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Student Updated", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Update Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteSelectedStudent() {
        Student target = getSelectedStudent();
        if (target == null) return;
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete student " + target.getFullName() + " (" + target.getRollNumber() + ") from MySQL?\n"
                        + "This permanently removes their record and linked portfolio entries.",
                "Confirm Permanent Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        User actor = AppSession.getInstance().getCurrentUser();
        controller.deleteStudentAsync(actor, target.getId(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Record Deleted", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Cannot Delete Student", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void exportCsv() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("gcet_students_directory.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File file = fc.getSelectedFile();
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            pw.println("Roll Number,Full Name,Email,Phone,Branch,CGPA,10th %,Inter %,Backlogs,Graduation Year,Placement Status,Package (LPA),Verified Skills Count");
            for (Student s : filteredStudents) {
                long verifiedCount = s.getSkills().stream().filter(sk -> sk.isVerifiedByFaculty()).count();
                pw.printf("%s,%s,%s,%s,%s,%.2f,%.1f,%.1f,%d,%d,%s,%s,%d%n",
                        csv(s.getRollNumber()),
                        csv(s.getFullName()),
                        csv(s.getEmail()),
                        csv(s.getPhone()),
                        csv(s.getBranch()),
                        s.getCgpa(),
                        s.getTenthPercentage(),
                        s.getInterPercentage(),
                        s.getBacklogs(),
                        s.getGraduationYear(),
                        csv(s.getPlacementStatus()),
                        s.getPlacedPackageLpa() != null ? s.getPlacedPackageLpa().toString() : "",
                        verifiedCount
                );
            }
            JOptionPane.showMessageDialog(this, "Exported " + filteredStudents.size() + " student records to:\n" + file.getAbsolutePath(),
                    "CSV Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "CSV Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String safeStr(String s) {
        return s == null ? "" : s;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
