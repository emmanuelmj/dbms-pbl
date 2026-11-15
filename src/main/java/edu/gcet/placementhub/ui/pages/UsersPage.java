package edu.gcet.placementhub.ui.pages;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.UserService;
import edu.gcet.placementhub.ui.dialogs.ResetPasswordDialog;
import edu.gcet.placementhub.ui.dialogs.UserFormDialog;
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
 * GCET PLACEMENT HUB - User Management Page
 * Handles enterprise account provisioning, PBKDF2 password resets,
 * status activation/deactivation, and role assignment.
 */
public class UsersPage extends JPanel {
    private final User currentUser;
    private final UserService userService;
    private final UserDao userDao;

    // KPI Cards
    private JLabel lblTotalUsers;
    private JLabel lblActiveUsers;
    private JLabel lblStaffUsers;
    private JLabel lblStudentUsers;

    // Filters & Buttons
    private JTextField txtSearch;
    private JComboBox<String> cmbRoleFilter;
    private JComboBox<String> cmbStatusFilter;
    private JButton btnAddUser;
    private JButton btnEditUser;
    private JButton btnResetPassword;
    private JButton btnToggleStatus;
    private JButton btnDeleteUser;
    private JButton btnRefresh;

    // Table
    private JTable tblUsers;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;
    private final List<User> currentUsers = new ArrayList<>();

    public UsersPage(User currentUser) {
        this.currentUser = currentUser;
        this.userService = new UserService();
        this.userDao = new UserDao();

        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.LIGHT_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        initUI();
        loadUserDataAsync();
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

        JLabel title = new JLabel("User Accounts & Institutional RBAC");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Theme.MIDNIGHT_NAVY);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · Secure Authentication & Identity Directory");
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

        lblTotalUsers = new JLabel("0", JLabel.LEFT);
        lblActiveUsers = new JLabel("0", JLabel.LEFT);
        lblStaffUsers = new JLabel("0", JLabel.LEFT);
        lblStudentUsers = new JLabel("0", JLabel.LEFT);

        p.add(createKpiCard("TOTAL USERS", lblTotalUsers, "Registered credentials", Theme.MIDNIGHT_NAVY));
        p.add(createKpiCard("ACTIVE ACCOUNTS", lblActiveUsers, "Permitted to sign in", Theme.SUCCESS_GREEN));
        p.add(createKpiCard("ADMIN & STAFF", lblStaffUsers, "Officers & Mentors", Theme.ELECTRIC_BLUE));
        p.add(createKpiCard("STUDENT ACCOUNTS", lblStudentUsers, "Self-Service Access", Theme.CYAN_ACCENT));

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
        txtSearch.putClientProperty("JTextField.placeholderText", "Search name, user, email...");
        txtSearch.setPreferredSize(new Dimension(200, 34));
        txtSearch.addActionListener(e -> loadUserDataAsync());
        left.add(txtSearch);

        JButton btnSearch = Theme.createPrimaryButton("Search");
        btnSearch.setPreferredSize(new Dimension(80, 34));
        btnSearch.addActionListener(e -> loadUserDataAsync());
        left.add(btnSearch);

        cmbRoleFilter = new JComboBox<>(new String[]{"All Roles", "ADMIN", "PLACEMENT_OFFICER", "FACULTY", "STUDENT"});
        cmbRoleFilter.setPreferredSize(new Dimension(150, 34));
        cmbRoleFilter.addActionListener(e -> loadUserDataAsync());
        left.add(cmbRoleFilter);

        cmbStatusFilter = new JComboBox<>(new String[]{"All Statuses", "ACTIVE", "INACTIVE"});
        cmbStatusFilter.setPreferredSize(new Dimension(120, 34));
        cmbStatusFilter.addActionListener(e -> loadUserDataAsync());
        left.add(cmbStatusFilter);

        p.add(left, BorderLayout.WEST);

        // Actions
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        btnAddUser = Theme.createPrimaryButton("+ Add User");
        btnAddUser.setPreferredSize(new Dimension(110, 34));
        btnAddUser.setEnabled(currentUser.canManageUsers());
        btnAddUser.addActionListener(e -> openUserForm(null));
        right.add(btnAddUser);

        btnEditUser = Theme.createSecondaryButton("Edit");
        btnEditUser.setPreferredSize(new Dimension(75, 34));
        btnEditUser.setEnabled(false);
        btnEditUser.addActionListener(e -> openUserForm(getSelectedUser()));
        right.add(btnEditUser);

        btnResetPassword = Theme.createSecondaryButton("Reset Pwd");
        btnResetPassword.setPreferredSize(new Dimension(95, 34));
        btnResetPassword.setEnabled(false);
        btnResetPassword.addActionListener(e -> openResetPasswordDialog());
        right.add(btnResetPassword);

        btnToggleStatus = Theme.createSecondaryButton("Toggle Status");
        btnToggleStatus.setPreferredSize(new Dimension(105, 34));
        btnToggleStatus.setEnabled(false);
        btnToggleStatus.addActionListener(e -> toggleStatus());
        right.add(btnToggleStatus);

        btnDeleteUser = Theme.createDangerButton("Delete");
        btnDeleteUser.setPreferredSize(new Dimension(80, 34));
        btnDeleteUser.setEnabled(false);
        btnDeleteUser.addActionListener(e -> deleteUser());
        right.add(btnDeleteUser);

        btnRefresh = Theme.createSecondaryButton("↻");
        btnRefresh.setPreferredSize(new Dimension(42, 34));
        btnRefresh.addActionListener(e -> loadUserDataAsync());
        right.add(btnRefresh);

        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel createTablePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(Theme.SUBTLE_BORDER, 1));

        String[] cols = {"Username", "Full Name", "Role", "Email", "Department", "Roll Number", "Status", "Created At"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        tblUsers = new JTable(tableModel);
        tblUsers.setRowHeight(32);
        tblUsers.setFont(Theme.FONT_BODY);
        tblUsers.getTableHeader().setFont(Theme.FONT_BOLD);
        tblUsers.getTableHeader().setBackground(Theme.LIGHT_BG);
        tblUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblUsers.setShowVerticalLines(false);
        tblUsers.setGridColor(Theme.SUBTLE_BORDER);

        rowSorter = new TableRowSorter<>(tableModel);
        tblUsers.setRowSorter(rowSorter);

        // Custom badge renderer for Role and Status
        tblUsers.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean sel, boolean foc, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, val, sel, foc, row, col);
                String role = val != null ? val.toString() : "";
                if ("ADMIN".equals(role)) l.setForeground(Theme.MIDNIGHT_NAVY);
                else if ("PLACEMENT_OFFICER".equals(role)) l.setForeground(Theme.ELECTRIC_BLUE);
                else if ("FACULTY".equals(role)) l.setForeground(new Color(13, 148, 136));
                else l.setForeground(Color.DARK_GRAY);
                l.setFont(Theme.FONT_BOLD);
                return l;
            }
        });

        tblUsers.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
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

        tblUsers.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateActionButtonsState();
            }
        });

        tblUsers.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tblUsers.getSelectedRow() >= 0 && currentUser.canManageUsers()) {
                    openUserForm(getSelectedUser());
                }
            }
        });

        p.add(new JScrollPane(tblUsers), BorderLayout.CENTER);
        return p;
    }

    private User getSelectedUser() {
        int viewRow = tblUsers.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = tblUsers.convertRowIndexToModel(viewRow);
        if (modelRow >= 0 && modelRow < currentUsers.size()) {
            return currentUsers.get(modelRow);
        }
        return null;
    }

    private void updateActionButtonsState() {
        User u = getSelectedUser();
        boolean hasSel = (u != null);
        boolean isAdmin = currentUser.canManageUsers();

        btnEditUser.setEnabled(hasSel && (isAdmin || currentUser.getId().equals(u.getId())));
        btnResetPassword.setEnabled(hasSel && (isAdmin || currentUser.getId().equals(u.getId())));
        btnToggleStatus.setEnabled(hasSel && isAdmin && !currentUser.getId().equals(u.getId()));
        btnDeleteUser.setEnabled(hasSel && isAdmin && !currentUser.getId().equals(u.getId()));
    }

    public void loadUserDataAsync() {
        String search = txtSearch.getText().trim();
        String role = (String) cmbRoleFilter.getSelectedItem();
        if ("All Roles".equals(role)) role = "ALL";
        String status = (String) cmbStatusFilter.getSelectedItem();
        if ("All Statuses".equals(status)) status = "ALL";

        final String finalRole = role;
        final String finalStatus = status;

        SwingWorker<List<User>, Void> worker = new SwingWorker<>() {
            private Map<String, Integer> metrics;

            @Override
            protected List<User> doInBackground() {
                metrics = userDao.getMetrics();
                return userDao.findAll(search, finalRole, finalStatus);
            }

            @Override
            protected void done() {
                try {
                    List<User> list = get();
                    currentUsers.clear();
                    currentUsers.addAll(list);

                    tableModel.setRowCount(0);
                    for (User u : list) {
                        tableModel.addRow(new Object[]{
                            u.getUsername(),
                            u.getFullName(),
                            u.getRole().name(),
                            u.getEmail(),
                            u.getDepartment() != null ? u.getDepartment() : "-",
                            u.getStudentRollNumber() != null ? u.getStudentRollNumber() : "-",
                            u.isActive() ? "Active" : "Inactive",
                            u.getCreatedAt() != null ? u.getCreatedAt().toString().substring(0, 10) : "-"
                        });
                    }

                    if (metrics != null) {
                        lblTotalUsers.setText(String.valueOf(metrics.getOrDefault("TOTAL", 0)));
                        lblActiveUsers.setText(String.valueOf(metrics.getOrDefault("ACTIVE", 0)));
                        int staff = metrics.getOrDefault("ADMIN", 0) + metrics.getOrDefault("PLACEMENT_OFFICER", 0) + metrics.getOrDefault("FACULTY", 0);
                        lblStaffUsers.setText(String.valueOf(staff));
                        lblStudentUsers.setText(String.valueOf(metrics.getOrDefault("STUDENT", 0)));
                    }

                    updateActionButtonsState();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(UsersPage.this, "Failed to load users: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void openUserForm(User user) {
        UserFormDialog dlg = new UserFormDialog(SwingUtilities.getWindowAncestor(this), currentUser, user);
        dlg.setVisible(true);
        if (dlg.isSaved()) {
            loadUserDataAsync();
        }
    }

    private void openResetPasswordDialog() {
        User u = getSelectedUser();
        if (u == null) return;
        ResetPasswordDialog dlg = new ResetPasswordDialog(SwingUtilities.getWindowAncestor(this), currentUser, u);
        dlg.setVisible(true);
    }

    private void toggleStatus() {
        User u = getSelectedUser();
        if (u == null) return;

        boolean newActive = !u.isActive();
        String action = newActive ? "activate" : "deactivate";
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to " + action + " account '" + u.getUsername() + "'?",
                "Confirm Account " + (newActive ? "Activation" : "Deactivation"), JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                userService.toggleUserStatus(currentUser, u.getId(), newActive);
                loadUserDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteUser() {
        User u = getSelectedUser();
        if (u == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to permanently delete user '" + u.getUsername() + "' (" + u.getFullName() + ")?\nThis action cannot be undone.",
                "Confirm Permanent Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                userService.deleteUser(currentUser, u.getId());
                loadUserDataAsync();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
