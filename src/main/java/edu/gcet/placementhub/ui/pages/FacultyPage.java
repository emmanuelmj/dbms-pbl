package edu.gcet.placementhub.ui.pages;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.FacultyDao;
import edu.gcet.placementhub.model.Faculty;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.FacultyService;
import edu.gcet.placementhub.ui.dialogs.FacultyFormDialog;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GCET PLACEMENT HUB - Faculty & Mentor Management Page
 * Handles departmental placement coordinators, academic mentors, and account linking.
 */
public class FacultyPage extends JPanel {
    private final User currentUser;
    private final FacultyService facultyService;
    private final FacultyDao facultyDao;

    // KPI Cards
    private JLabel lblTotalFaculty;
    private JLabel lblActiveMentors;
    private JLabel lblDepartments;
    private JLabel lblLinkedUsers;

    // Filters & Buttons
    private JTextField txtSearch;
    private JComboBox<String> cmbDeptFilter;
    private JComboBox<String> cmbStatusFilter;
    private JButton btnAddFaculty;
    private JButton btnEditFaculty;
    private JButton btnToggleStatus;
    private JButton btnDeleteFaculty;
    private JButton btnRefresh;

    // Table
    private JTable tblFaculty;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private final List<Faculty> currentFaculty = new ArrayList<>();

    public FacultyPage(User currentUser) {
        this.currentUser = currentUser;
        this.facultyService = new FacultyService();
        this.facultyDao = new FacultyDao();

        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.LIGHT_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        initUI();
        loadFacultyDataAsync();
    }

    private void initUI() {
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setOpaque(false);

        topContainer.add(createTitleHeader());
        topContainer.add(Box.createVerticalStrut(14));
        topContainer.add(createKpiPanel());
        topContainer.add(Box.createVerticalStrut(14));
        topContainer.add(createToolbarPanel());

        add(topContainer, BorderLayout.NORTH);
        add(createTablePanel(), BorderLayout.CENTER);
    }

    private JPanel createTitleHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);

        JPanel left = new JPanel(new GridLayout(2, 1, 2, 2));
        left.setOpaque(false);

        JLabel title = new JLabel("Faculty Mentors & Departmental Coordinators");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Theme.MIDNIGHT_NAVY);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · Career Development Cell (CDC) Academic Network");
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

        lblTotalFaculty = new JLabel("0", JLabel.LEFT);
        lblActiveMentors = new JLabel("0", JLabel.LEFT);
        lblDepartments = new JLabel("0", JLabel.LEFT);
        lblLinkedUsers = new JLabel("0", JLabel.LEFT);

        p.add(createKpiCard("TOTAL FACULTY", lblTotalFaculty, "Registered coordinators", Theme.MIDNIGHT_NAVY));
        p.add(createKpiCard("ACTIVE MENTORS", lblActiveMentors, "Assessing competencies", Theme.SUCCESS_GREEN));
        p.add(createKpiCard("DEPARTMENTS", lblDepartments, "Engineering branches", Theme.ELECTRIC_BLUE));
        p.add(createKpiCard("LINKED PORTAL LOGINS", lblLinkedUsers, "Single Sign-On Active", Theme.CYAN_ACCENT));

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

    private JPanel createToolbarPanel() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);

        // Search & Filters
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        txtSearch = new JTextField(15);
        txtSearch.putClientProperty("JTextField.placeholderText", "Search name, ID, email...");
        txtSearch.setPreferredSize(new Dimension(200, 34));
        txtSearch.addActionListener(e -> loadFacultyDataAsync());
        left.add(txtSearch);

        JButton btnSearch = Theme.createPrimaryButton("Search");
        btnSearch.setPreferredSize(new Dimension(80, 34));
        btnSearch.addActionListener(e -> loadFacultyDataAsync());
        left.add(btnSearch);

        cmbDeptFilter = new JComboBox<>(new String[]{"All Departments", "CSE", "ECE", "EEE", "MECH", "CIVIL", "IT", "AIML", "DS", "IOT", "FED"});
        cmbDeptFilter.setPreferredSize(new Dimension(150, 34));
        cmbDeptFilter.addActionListener(e -> loadFacultyDataAsync());
        left.add(cmbDeptFilter);

        cmbStatusFilter = new JComboBox<>(new String[]{"All Statuses", "ACTIVE", "INACTIVE"});
        cmbStatusFilter.setPreferredSize(new Dimension(120, 34));
        cmbStatusFilter.addActionListener(e -> loadFacultyDataAsync());
        left.add(cmbStatusFilter);

        p.add(left, BorderLayout.WEST);

        // Actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        btnAddFaculty = Theme.createPrimaryButton("+ Add Faculty");
        btnAddFaculty.setPreferredSize(new Dimension(120, 34));
        btnAddFaculty.setEnabled(currentUser.canManageFaculty());
        btnAddFaculty.addActionListener(e -> openFacultyForm(null));
        right.add(btnAddFaculty);

        btnEditFaculty = Theme.createSecondaryButton("Edit");
        btnEditFaculty.setPreferredSize(new Dimension(75, 34));
        btnEditFaculty.setEnabled(false);
        btnEditFaculty.addActionListener(e -> openFacultyForm(getSelectedFaculty()));
        right.add(btnEditFaculty);

        btnToggleStatus = Theme.createSecondaryButton("Toggle Status");
        btnToggleStatus.setPreferredSize(new Dimension(105, 34));
        btnToggleStatus.setEnabled(false);
        btnToggleStatus.addActionListener(e -> toggleStatus());
        right.add(btnToggleStatus);

        btnDeleteFaculty = Theme.createDangerButton("Delete");
        btnDeleteFaculty.setPreferredSize(new Dimension(80, 34));
        btnDeleteFaculty.setEnabled(false);
        btnDeleteFaculty.addActionListener(e -> deleteFaculty());
        right.add(btnDeleteFaculty);

        btnRefresh = Theme.createSecondaryButton("↻");
        btnRefresh.setPreferredSize(new Dimension(42, 34));
        btnRefresh.addActionListener(e -> loadFacultyDataAsync());
        right.add(btnRefresh);

        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel createTablePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1));

        String[] cols = {"Faculty ID", "Full Name", "Department", "Designation", "Institutional Email", "Phone", "Assigned User", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        tblFaculty = new JTable(tableModel);
        tblFaculty.setRowHeight(32);
        tblFaculty.setFont(Theme.FONT_BODY);
        tblFaculty.getTableHeader().setFont(Theme.FONT_BOLD);
        tblFaculty.getTableHeader().setBackground(Theme.LIGHT_BG);
        tblFaculty.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblFaculty.setShowVerticalLines(false);
        tblFaculty.setGridColor(Theme.SUBTLE_BORDER);

        rowSorter = new TableRowSorter<>(tableModel);
        tblFaculty.setRowSorter(rowSorter);

        // Custom badge renderer for Department and Status
        tblFaculty.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, sel, foc, row, col);
                l.setForeground(Theme.ELECTRIC_BLUE);
                l.setFont(Theme.FONT_BOLD);
                return l;
            }
        });

        tblFaculty.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
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

        tblFaculty.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonsState();
            }
        });

        tblFaculty.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblFaculty.getSelectedRow() >= 0 && currentUser.canManageFaculty()) {
                    openFacultyForm(getSelectedFaculty());
                }
            }
        });

        p.add(new JScrollPane(tblFaculty), BorderLayout.CENTER);
        return p;
    }

    private Faculty getSelectedFaculty() {
        int viewRow = tblFaculty.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblFaculty.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < currentFaculty.size()) {
            return currentFaculty.get(modelRow);
        }
        return null;
    }

    private void updateActionButtonsState() {
        Faculty f = getSelectedFaculty();
        boolean hasSel = (f != null);
        boolean canManage = currentUser.canManageFaculty();
        boolean isAdmin = currentUser.canManageUsers();

        btnEditFaculty.setEnabled(hasSel && canManage);
        btnToggleStatus.setEnabled(hasSel && canManage);
        btnDeleteFaculty.setEnabled(hasSel && isAdmin);
    }

    public void loadFacultyDataAsync() {
        String search = txtSearch.getText().trim();
        String dept = (String) cmbDeptFilter.getSelectedItem();
        if ("All Departments".equals(dept)) dept = "ALL";
        String status = (String) cmbStatusFilter.getSelectedItem();
        if ("All Statuses".equals(status)) status = "ALL";

        final String finalDept = dept;
        final String finalStatus = status;

        SwingWorker<List<Faculty>, Void> worker = new SwingWorker<>() {
            private Map<String, Integer> metrics;

            @Override
            protected List<Faculty> doInBackground() {
                metrics = facultyDao.getMetrics();
                return facultyDao.findAll(search, finalDept, finalStatus);
            }

            @Override
            protected void done() {
                try {
                    List<Faculty> list = get();
                    currentFaculty.clear();
                    currentFaculty.addAll(list);

                    tableModel.setRowCount(0);
                    int linkedCount = 0;
                    for (Faculty f : list) {
                        if (f.getUserId() != null) linkedCount++;
                        tableModel.addRow(new Object[]{
                            f.getFacultyId(),
                            f.getFullName(),
                            f.getDepartment(),
                            f.getDesignation(),
                            f.getEmail(),
                            f.getPhone() != null ? f.getPhone() : "-",
                            f.getUsername() != null ? "@" + f.getUsername() : "(Not Linked)",
                            f.isActive() ? "Active" : "Inactive"
                        });
                    }

                    if (metrics != null) {
                        lblTotalFaculty.setText(String.valueOf(metrics.getOrDefault("TOTAL", 0)));
                        lblActiveMentors.setText(String.valueOf(metrics.getOrDefault("ACTIVE", 0)));
                        lblDepartments.setText(String.valueOf(metrics.getOrDefault("DEPARTMENTS", 0)));
                        lblLinkedUsers.setText(String.valueOf(linkedCount));
                    }

                    updateActionButtonsState();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(FacultyPage.this, "Failed to load faculty: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void openFacultyForm(Faculty faculty) {
        FacultyFormDialog dlg = new FacultyFormDialog(SwingUtilities.getWindowAncestor(this), currentUser, faculty);
        dlg.setVisible(true);
        if (dlg.isSaved()) {
            loadFacultyDataAsync();
        }
    }

    private void toggleStatus() {
        Faculty f = getSelectedFaculty();
        if (f == null) return;

        String newStatus = f.isActive() ? "INACTIVE" : "ACTIVE";
        int confirm = JOptionPane.showConfirmDialog(this,
                "Change status of faculty member '" + f.getFullName() + "' to " + newStatus + "?",
                "Confirm Status Change", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                facultyService.toggleFacultyStatus(currentUser, f.getId(), newStatus);
                loadFacultyDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteFaculty() {
        Faculty f = getSelectedFaculty();
        if (f == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to permanently delete faculty member '" + f.getFullName() + "' (" + f.getFacultyId() + ")?\nThis action cannot be undone.",
                "Confirm Permanent Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                facultyService.deleteFaculty(currentUser, f.getId());
                loadFacultyDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
