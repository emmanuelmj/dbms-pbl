package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.config.ClientConfigManager;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GCET PLACEMENT HUB - Multi-Role Login Portal
 * 
 * Provides 4 role-specific entry gateways:
 * 1. Administrator Portal (Dr. K. Sagar, Mr. R. Harishchandra Reddy, Mr. G.R. Ravinder Reddy)
 *    with strict identity matching against the selected administrator.
 * 2. Faculty Portal (Initial faculty members B. Laxmi Lavanya, P. Aparna, S. Raja Shakar Reddy, etc.)
 *    with registration trigger for mandatory dual approval.
 * 3. Student Portal (Roll Number / Username verification) with registration trigger.
 * 4. Corporate Recruiter Portal with company onboarding trigger.
 */
public class LoginDialog extends JDialog {
    private final UserDao userDao = new UserDao();
    private boolean authenticated = false;
    private User authenticatedUser = null;
    private final AtomicBoolean authInFlight = new AtomicBoolean(false);
    private javax.swing.Timer autoLoginTimer;
    private volatile boolean suppressAutoLogin = false;
    private String lastAttemptedCredKey = null;

    private JTabbedPane tabbedPane;

    // Admin Tab components
    private JComboBox<AdminItem> cmbAdminSelect;
    private JPasswordField txtAdminPassword;
    private JCheckBox chkAdminRemember;
    private JLabel lblAdminError;

    // Faculty Tab components
    private JComboBox<FacultyItem> cmbFacultySelect;
    private JTextField txtFacultyUsername;
    private JPasswordField txtFacultyPassword;
    private JCheckBox chkFacultyRemember;
    private JLabel lblFacultyError;

    // Student Tab components
    private JTextField txtStudentRoll;
    private JPasswordField txtStudentPassword;
    private JCheckBox chkStudentRemember;
    private JLabel lblStudentError;

    // Company Tab components
    private JTextField txtCompanyUser;
    private JPasswordField txtCompanyPassword;
    private JCheckBox chkCompanyRemember;
    private JLabel lblCompanyError;

    public LoginDialog(Frame owner) {
        super(owner, "GCET Placement Hub · Multi-User Portal Login", true);
        initUI();
    }

    private void initUI() {
        setSize(560, 640);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header Banner with GCET Navy Theme
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(18, 24, 18, 24));

        JLabel title = new JLabel(AppConfig.APP_NAME);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(AppConfig.APP_TITLE + " · " + AppConfig.COLLEGE_NAME);
        sub.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sub.setForeground(Theme.CYAN_ACCENT);

        JLabel location = new JLabel(AppConfig.COLLEGE_LOCATION + " · PIN " + AppConfig.COLLEGE_PIN);
        location.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        location.setForeground(Theme.SOFT_WHITE);

        JPanel titles = new JPanel(new GridLayout(3, 1, 2, 2));
        titles.setOpaque(false);
        titles.add(title);
        titles.add(sub);
        titles.add(location);
        header.add(titles, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);

        // Tabbed Login Interface
        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabbedPane.addTab("🏛 Administrator", createAdminPanel());
        tabbedPane.addTab("🎓 Faculty", createFacultyPanel());
        tabbedPane.addTab("👨‍🎓 Student", createStudentPanel());
        tabbedPane.addTab("🏢 Recruiter", createCompanyPanel());

        // Restore last active portal tab from configuration
        String lastTab = ClientConfigManager.getLastPortalTab();
        if ("FACULTY".equalsIgnoreCase(lastTab)) {
            tabbedPane.setSelectedIndex(1);
        } else if ("STUDENT".equalsIgnoreCase(lastTab)) {
            tabbedPane.setSelectedIndex(2);
        } else if ("COMPANY".equalsIgnoreCase(lastTab)) {
            tabbedPane.setSelectedIndex(3);
        } else {
            tabbedPane.setSelectedIndex(0);
        }

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JPanel quickHelp = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 12));
        quickHelp.setOpaque(false);
        JLabel tag = new JLabel("\"" + AppConfig.COLLEGE_TAGLINE + "\"");
        tag.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        tag.setForeground(Theme.MUTED_TEXT);
        quickHelp.add(tag);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        buttons.setOpaque(false);

        JButton btnExit = Theme.createSecondaryButton("Exit");
        btnExit.addActionListener(e -> {
            authenticated = false;
            dispose();
        });

        buttons.add(btnExit);
        footer.add(quickHelp, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);

        add(footer, BorderLayout.SOUTH);
    }

    // ========================================================
    // TAB 1: ADMINISTRATOR PORTAL
    // ========================================================
    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        JPanel notice = new JPanel(new BorderLayout());
        notice.setBackground(new Color(0x26, 0x84, 0xFF, 0x14));
        notice.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.ELECTRIC_BLUE, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        JLabel lblNotice = new JLabel("<html><b>Institutional Administration:</b> Select your official administrative profile. Strict credential verification enforces that passwords match the chosen administrator.</html>");
        lblNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblNotice.setForeground(Theme.MIDNIGHT_NAVY);
        notice.add(lblNotice, BorderLayout.CENTER);

        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(notice, gbc);

        lblAdminError = new JLabel(" ");
        lblAdminError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblAdminError.setForeground(Theme.DANGER_ROSE);
        gbc.gridy = row++;
        form.add(lblAdminError, gbc);
        gbc.gridwidth = 1;

        // Admin Selector
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Administrator Profile:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;

        DefaultComboBoxModel<AdminItem> adminModel = new DefaultComboBoxModel<>();
        List<User> admins = userDao.findAdministrators();
        if (admins.isEmpty()) {
            adminModel.addElement(new AdminItem("principal.sagar", "Principal (Executive Authority)"));
            adminModel.addElement(new AdminItem("vc.harishchandra", "Vice Chairman (Governance & Oversight)"));
            adminModel.addElement(new AdminItem("chairman.ravinder", "Chairman (Governing Body Leadership)"));
            adminModel.addElement(new AdminItem("admin", "Placement System Administrator"));
        } else {
            for (User u : admins) {
                String roleStr = u.getRole() != null ? u.getRole().toString() : "ADMIN";
                String dept = u.getDepartment() != null && !u.getDepartment().isEmpty() ? " · " + u.getDepartment() : "";
                adminModel.addElement(new AdminItem(u.getUsername(), roleStr + dept));
            }
        }
        cmbAdminSelect = new JComboBox<>(adminModel);
        cmbAdminSelect.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Restore last selected admin
        String lastAdmin = ClientConfigManager.getLastAdminUsername();
        if (lastAdmin != null) {
            for (int i = 0; i < adminModel.getSize(); i++) {
                if (adminModel.getElementAt(i).username.equalsIgnoreCase(lastAdmin)) {
                    cmbAdminSelect.setSelectedIndex(i);
                    break;
                }
            }
        }
        form.add(cmbAdminSelect, gbc);
        row++;

        // Password (always empty by default; never auto-filled)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Administrator Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtAdminPassword = new JPasswordField("");
        txtAdminPassword.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        form.add(txtAdminPassword, gbc);
        row++;

        // Clear password field when admin selection changes (populate username/selection only)
        cmbAdminSelect.addActionListener(e -> {
            clearDialogPasswordField(txtAdminPassword);
            lblAdminError.setText(" ");
        });
        attachAutoLogin(txtAdminPassword, () -> ((AdminItem) cmbAdminSelect.getSelectedItem()) != null ? ((AdminItem) cmbAdminSelect.getSelectedItem()).username : "", "ADMIN", this::loginAdministrator);

        // Sign In Button
        gbc.gridx = 1; gbc.gridy = row++;
        JButton btnAdminLogin = Theme.createPrimaryButton("Authenticate Administrator");
        btnAdminLogin.addActionListener(e -> loginAdministrator());
        form.add(btnAdminLogin, gbc);

        // Request Privilege Link
        gbc.gridy = row++;
        JButton btnRequestPrivilege = new JButton("<html><u>Request Administrator Privileges (Principal Review)</u></html>");
        btnRequestPrivilege.setBorderPainted(false);
        btnRequestPrivilege.setContentAreaFilled(false);
        btnRequestPrivilege.setForeground(Theme.ELECTRIC_BLUE);
        btnRequestPrivilege.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRequestPrivilege.addActionListener(e -> {
            AdminRequestDialog dlg = new AdminRequestDialog(this, authenticatedUser);
            dlg.setVisible(true);
        });
        form.add(btnRequestPrivilege, gbc);

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // TAB 2: FACULTY PORTAL
    // ========================================================
    private JPanel createFacultyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        lblFacultyError = new JLabel(" ");
        lblFacultyError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblFacultyError.setForeground(Theme.DANGER_ROSE);
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(lblFacultyError, gbc);
        gbc.gridwidth = 1;

        // Active Faculty Dropdown
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Select Faculty Member:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;

        DefaultComboBoxModel<FacultyItem> facModel = new DefaultComboBoxModel<>();
        List<User> facList = userDao.findActiveFaculty();
        if (facList.isEmpty()) {
            facModel.addElement(new FacultyItem("faculty_lavanya", "CSE Department Coordinator"));
            facModel.addElement(new FacultyItem("faculty_aparna", "ECE Department Coordinator"));
            facModel.addElement(new FacultyItem("faculty_rajashakar", "EEE Department Coordinator"));
            facModel.addElement(new FacultyItem("faculty_cse", "Faculty Mentor (CSE)"));
        } else {
            for (User u : facList) {
                String dept = u.getDepartment() != null ? u.getDepartment() : "Department";
                facModel.addElement(new FacultyItem(u.getUsername(), dept + " Coordinator (" + u.getUsername() + ")"));
            }
        }
        cmbFacultySelect = new JComboBox<>(facModel);
        cmbFacultySelect.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        String lastFacUser = ClientConfigManager.getLastFacultyUsername();
        if (lastFacUser != null) {
            for (int i = 0; i < facModel.getSize(); i++) {
                if (facModel.getElementAt(i).username.equalsIgnoreCase(lastFacUser)) {
                    cmbFacultySelect.setSelectedIndex(i);
                    break;
                }
            }
        }
        form.add(cmbFacultySelect, gbc);
        row++;

        // Or enter Username
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Faculty Username:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtFacultyUsername = new JTextField(lastFacUser != null ? lastFacUser : "faculty_lavanya");
        txtFacultyUsername.setFont(Theme.FONT_MONO);
        form.add(txtFacultyUsername, gbc);
        row++;

        cmbFacultySelect.addActionListener(e -> {
            FacultyItem item = (FacultyItem) cmbFacultySelect.getSelectedItem();
            if (item != null) {
                txtFacultyUsername.setText(item.username);
                clearDialogPasswordField(txtFacultyPassword);
                lblFacultyError.setText(" ");
            }
        });

        // Password (always empty by default)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtFacultyPassword = new JPasswordField("");
        attachAutoLogin(txtFacultyPassword, () -> txtFacultyUsername.getText().trim(), "FACULTY", this::loginFaculty);
        form.add(txtFacultyPassword, gbc);
        row++;

        // Sign In Button
        gbc.gridx = 1; gbc.gridy = row++;
        JButton btnFacLogin = Theme.createPrimaryButton("Sign In as Faculty");
        btnFacLogin.addActionListener(e -> loginFaculty());
        form.add(btnFacLogin, gbc);

        // Register Button
        gbc.gridy = row++;
        JButton btnRegFaculty = Theme.createSecondaryButton("Register New Faculty (Dual Approval)");
        btnRegFaculty.addActionListener(e -> {
            FacultyRegistrationDialog dlg = new FacultyRegistrationDialog(this);
            dlg.setVisible(true);
        });
        form.add(btnRegFaculty, gbc);

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // TAB 3: STUDENT PORTAL
    // ========================================================
    private JPanel createStudentPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        lblStudentError = new JLabel(" ");
        lblStudentError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblStudentError.setForeground(Theme.DANGER_ROSE);
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(lblStudentError, gbc);
        gbc.gridwidth = 1;

        // Roll Number / Username
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Roll No / Username:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String lastStudent = ClientConfigManager.getLastStudentRoll();
        txtStudentRoll = new JTextField(lastStudent != null ? lastStudent : "student_rohit");
        txtStudentRoll.setFont(Theme.FONT_MONO);
        form.add(txtStudentRoll, gbc);
        row++;

        // Password (always empty by default)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Student Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtStudentPassword = new JPasswordField("");
        attachAutoLogin(txtStudentPassword, () -> txtStudentRoll.getText().trim(), "STUDENT", this::loginStudent);
        form.add(txtStudentPassword, gbc);
        row++;

        // Sign In Button
        gbc.gridx = 1; gbc.gridy = row++;
        JButton btnStudentLogin = Theme.createPrimaryButton("Sign In as Student");
        btnStudentLogin.addActionListener(e -> loginStudent());
        form.add(btnStudentLogin, gbc);

        // Register Button
        gbc.gridy = row++;
        JButton btnRegStudent = Theme.createSecondaryButton("Register New Student (Faculty Review)");
        btnRegStudent.addActionListener(e -> {
            StudentRegistrationDialog dlg = new StudentRegistrationDialog(this);
            dlg.setVisible(true);
        });
        form.add(btnRegStudent, gbc);

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // TAB 4: COMPANY / RECRUITER PORTAL
    // ========================================================
    private JPanel createCompanyPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(16, 24, 16, 24));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        lblCompanyError = new JLabel(" ");
        lblCompanyError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblCompanyError.setForeground(Theme.DANGER_ROSE);
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(lblCompanyError, gbc);
        gbc.gridwidth = 1;

        // Recruiter Username
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Recruiter Username:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String lastCompUser = ClientConfigManager.getLastCompanyUsername();
        txtCompanyUser = new JTextField(lastCompUser != null ? lastCompUser : "recruiter_tcs");
        txtCompanyUser.setFont(Theme.FONT_MONO);
        form.add(txtCompanyUser, gbc);
        row++;

        // Password (always empty by default)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Recruiter Password:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtCompanyPassword = new JPasswordField("");
        attachAutoLogin(txtCompanyPassword, () -> txtCompanyUser.getText().trim(), "COMPANY", this::loginCompany);
        form.add(txtCompanyPassword, gbc);
        row++;

        // Sign In Button
        gbc.gridx = 1; gbc.gridy = row++;
        JButton btnCompanyLogin = Theme.createPrimaryButton("Sign In as Recruiter");
        btnCompanyLogin.addActionListener(e -> loginCompany());
        form.add(btnCompanyLogin, gbc);

        // Register Button
        gbc.gridy = row++;
        JButton btnRegCompany = Theme.createSecondaryButton("Register Company / Recruiter Partner");
        btnRegCompany.addActionListener(e -> {
            CompanyRegistrationDialog dlg = new CompanyRegistrationDialog(this);
            dlg.setVisible(true);
        });
        form.add(btnRegCompany, gbc);

        panel.add(form, BorderLayout.CENTER);
        return panel;
    }

    // ========================================================
    // AUTHENTICATION HANDLERS
    // ========================================================

    private void clearDialogPasswordField(JPasswordField field) {
        if (autoLoginTimer != null) autoLoginTimer.stop();
        suppressAutoLogin = true;
        try {
            lastAttemptedCredKey = null;
            field.setText("");
        } finally {
            suppressAutoLogin = false;
        }
    }

    private void attachAutoLogin(JPasswordField passField, java.util.function.Supplier<String> userSupplier, String roleTag, Runnable action) {
        passField.addActionListener(e -> {
            if (autoLoginTimer != null) autoLoginTimer.stop();
            lastAttemptedCredKey = null;
            action.run();
        });
        passField.getDocument().addDocumentListener(new DocumentListener() {
            private void onEdit() {
                if (suppressAutoLogin || authInFlight.get()) return;
                if (autoLoginTimer != null) autoLoginTimer.stop();
                String u = userSupplier.get();
                String p = new String(passField.getPassword());
                if (u == null || u.isEmpty() || p.isEmpty()) return;
                String key = roleTag + "|" + u + "|" + p;
                if (key.equals(lastAttemptedCredKey)) return;
                autoLoginTimer = new javax.swing.Timer(1200, ev -> {
                    if (!suppressAutoLogin && !authInFlight.get()) {
                        String curU = userSupplier.get();
                        String curP = new String(passField.getPassword());
                        String curKey = roleTag + "|" + curU + "|" + curP;
                        if (curU != null && !curU.isEmpty() && !curP.isEmpty() && !curKey.equals(lastAttemptedCredKey)) {
                            action.run();
                        }
                    }
                });
                autoLoginTimer.setRepeats(false);
                autoLoginTimer.start();
            }
            @Override public void insertUpdate(DocumentEvent e) { onEdit(); }
            @Override public void removeUpdate(DocumentEvent e) { onEdit(); }
            @Override public void changedUpdate(DocumentEvent e) { onEdit(); }
        });
    }

    private void loginAdministrator() {
        if (autoLoginTimer != null) autoLoginTimer.stop();
        if (!authInFlight.compareAndSet(false, true)) return;
        try {
            lblAdminError.setText(" ");
            AdminItem selected = (AdminItem) cmbAdminSelect.getSelectedItem();
            if (selected == null) {
                lblAdminError.setText("Please select an administrator profile.");
                return;
            }

            String password = new String(txtAdminPassword.getPassword());
            lastAttemptedCredKey = "ADMIN|" + selected.username + "|" + password;
            UserDao.AuthResult res = userDao.authenticateAdministrator(selected.username, password);

            if (res.success) {
                lastAttemptedCredKey = null;
                ClientConfigManager.setLastPortalTab("ADMIN");
                ClientConfigManager.setLastAdminUsername(selected.username);
                clearDialogPasswordField(txtAdminPassword);
                handleSuccessfulLogin(res.user);
            } else {
                lblAdminError.setText(res.errorMessage);
            }
        } finally {
            authInFlight.set(false);
        }
    }

    private void loginFaculty() {
        if (autoLoginTimer != null) autoLoginTimer.stop();
        if (!authInFlight.compareAndSet(false, true)) return;
        try {
            lblFacultyError.setText(" ");
            String username = txtFacultyUsername.getText().trim();
            String password = new String(txtFacultyPassword.getPassword());
            lastAttemptedCredKey = "FACULTY|" + username + "|" + password;

            UserDao.AuthResult res = userDao.authenticate(username, password, User.Role.FACULTY);
            if (res.success) {
                lastAttemptedCredKey = null;
                ClientConfigManager.setLastPortalTab("FACULTY");
                ClientConfigManager.setLastFacultyUsername(username);
                clearDialogPasswordField(txtFacultyPassword);
                handleSuccessfulLogin(res.user);
            } else {
                lblFacultyError.setText(res.errorMessage);
            }
        } finally {
            authInFlight.set(false);
        }
    }

    private void loginStudent() {
        if (autoLoginTimer != null) autoLoginTimer.stop();
        if (!authInFlight.compareAndSet(false, true)) return;
        try {
            lblStudentError.setText(" ");
            String rollOrUser = txtStudentRoll.getText().trim();
            String password = new String(txtStudentPassword.getPassword());
            lastAttemptedCredKey = "STUDENT|" + rollOrUser + "|" + password;

            UserDao.AuthResult res = userDao.authenticate(rollOrUser, password, User.Role.STUDENT);
            if (res.success) {
                lastAttemptedCredKey = null;
                ClientConfigManager.setLastPortalTab("STUDENT");
                ClientConfigManager.setLastStudentRoll(rollOrUser);
                clearDialogPasswordField(txtStudentPassword);
                handleSuccessfulLogin(res.user);
            } else {
                lblStudentError.setText(res.errorMessage);
            }
        } finally {
            authInFlight.set(false);
        }
    }

    private void loginCompany() {
        if (autoLoginTimer != null) autoLoginTimer.stop();
        if (!authInFlight.compareAndSet(false, true)) return;
        try {
            lblCompanyError.setText(" ");
            String username = txtCompanyUser.getText().trim();
            String password = new String(txtCompanyPassword.getPassword());
            lastAttemptedCredKey = "COMPANY|" + username + "|" + password;

            UserDao.AuthResult res = userDao.authenticate(username, password, User.Role.COMPANY);
            if (res.success) {
                lastAttemptedCredKey = null;
                ClientConfigManager.setLastPortalTab("COMPANY");
                ClientConfigManager.setLastCompanyUsername(username);
                clearDialogPasswordField(txtCompanyPassword);
                handleSuccessfulLogin(res.user);
            } else {
                lblCompanyError.setText(res.errorMessage);
            }
        } finally {
            authInFlight.set(false);
        }
    }

    private void handleSuccessfulLogin(User user) {
        // Enforce mandatory password rotation if bootstrap account
        if (user.isMustChangePassword()) {
            ChangePasswordDialog pwdDialog = new ChangePasswordDialog(this, user, true);
            pwdDialog.setVisible(true);
            if (!pwdDialog.isChanged()) {
                // User refused to set password
                JOptionPane.showMessageDialog(this,
                    "Password update is required for first-time login of this administrator account.",
                    "Password Setup Required", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        this.authenticated = true;
        this.authenticatedUser = user;
        dispose();
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_BOLD);
        l.setForeground(Theme.DARK_TEXT);
        return l;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public User getAuthenticatedUser() {
        return authenticatedUser;
    }

    // Helper item classes for dropdowns
    private static class AdminItem {
        final String username;
        final String displayName;

        AdminItem(String username, String displayName) {
            this.username = username;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static class FacultyItem {
        final String username;
        final String displayName;

        FacultyItem(String username, String displayName) {
            this.username = username;
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
