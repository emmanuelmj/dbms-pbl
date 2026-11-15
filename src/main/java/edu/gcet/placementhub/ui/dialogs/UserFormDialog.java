package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.service.UserService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - User Creation & Edit Dialog
 * Implements role assignment, account credential generation, and validations.
 */
public class UserFormDialog extends JDialog {
    private final UserService userService;
    private final User currentUser;
    private final User userToEdit;
    private boolean saved = false;

    private JTextField txtUsername;
    private JTextField txtFullName;
    private JTextField txtEmail;
    private JComboBox<User.Role> cmbRole;
    private JComboBox<String> cmbDept;
    private JTextField txtRollNo;
    private JPasswordField txtPassword;
    private JCheckBox chkShowPassword;
    private JCheckBox chkActive;
    private JLabel lblError;
    private JLabel lblPasswordNotice;

    public UserFormDialog(Window owner, User currentUser, User userToEdit) {
        super(owner, userToEdit == null ? "Create New System User - GCET" : "Modify User Account - GCET", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.userToEdit = userToEdit;
        this.userService = new UserService();

        initUI();
        if (userToEdit != null) {
            populateFields(userToEdit);
        }
    }

    private void initUI() {
        setSize(520, 620);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));

        JLabel title = new JLabel(userToEdit == null ? "Provision New Institutional User" : "Update User Account");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · Identity & Access Management");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Form Fields
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(18, 24, 18, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        lblError = new JLabel("");
        lblError.setForeground(Theme.DANGER_ROSE);
        lblError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(lblError, gbc);
        gbc.gridwidth = 1;

        // Username
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Username: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtUsername = new JTextField();
        txtUsername.setFont(Theme.FONT_MONO);
        if (userToEdit != null) {
            txtUsername.setEditable(false);
            txtUsername.setBackground(Theme.LIGHT_BG);
        }
        form.add(txtUsername, gbc);
        row++;

        // Full Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Full Name: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtFullName = new JTextField();
        form.add(txtFullName, gbc);
        row++;

        // Email
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Institutional Email: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtEmail = new JTextField();
        form.add(txtEmail, gbc);
        row++;

        // Institutional Role
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("System Role: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        cmbRole = new JComboBox<>(User.Role.values());
        cmbRole.addActionListener(e -> onRoleChanged());
        form.add(cmbRole, gbc);
        row++;

        // Department
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Department / Unit:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] depts = {"Administration", "Placement Cell (CDC)", "CSE", "ECE", "EEE", "MECH", "CIVIL", "IT", "AIML", "DS", "IOT", "Freshman Engg (FED)"};
        cmbDept = new JComboBox<>(depts);
        cmbDept.setEditable(true);
        form.add(cmbDept, gbc);
        row++;

        // Student Roll Number (Conditional)
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Student Roll No:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtRollNo = new JTextField();
        txtRollNo.setFont(Theme.FONT_MONO);
        form.add(txtRollNo, gbc);
        row++;

        // Initial Password (only shown on creation)
        if (userToEdit == null) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
            form.add(createLabel("Initial Password: *"), gbc);
            gbc.gridx = 1; gbc.weightx = 0.65;
            JPanel passPanel = new JPanel(new BorderLayout(6, 0));
            passPanel.setOpaque(false);
            txtPassword = new JPasswordField();
            chkShowPassword = new JCheckBox("Show");
            chkShowPassword.setOpaque(false);
            chkShowPassword.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            chkShowPassword.addActionListener(e -> {
                txtPassword.setEchoChar(chkShowPassword.isSelected() ? (char) 0 : '•');
            });
            passPanel.add(txtPassword, BorderLayout.CENTER);
            passPanel.add(chkShowPassword, BorderLayout.EAST);
            form.add(passPanel, gbc);
            row++;
        } else {
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
            lblPasswordNotice = new JLabel("<html><i>Note: To update password, use the 'Reset Password' button on the user list.</i></html>");
            lblPasswordNotice.setForeground(Color.GRAY);
            lblPasswordNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            form.add(lblPasswordNotice, gbc);
            gbc.gridwidth = 1;
            row++;
        }

        // Active Status
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Account Status:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        chkActive = new JCheckBox("Account is Active (Can Authenticate)", true);
        chkActive.setOpaque(false);
        form.add(chkActive, gbc);

        add(form, BorderLayout.CENTER);

        // Footer Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSave = Theme.createPrimaryButton(userToEdit == null ? "Create User" : "Save Changes");
        btnSave.addActionListener(e -> saveUser());

        footer.add(btnCancel);
        footer.add(btnSave);
        add(footer, BorderLayout.SOUTH);

        onRoleChanged();
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_BOLD);
        l.setForeground(Theme.DARK_TEXT);
        return l;
    }

    private void onRoleChanged() {
        User.Role selected = (User.Role) cmbRole.getSelectedItem();
        boolean isStudent = (selected == User.Role.STUDENT);
        txtRollNo.setEnabled(isStudent);
        if (!isStudent && (txtRollNo.getText().trim().isEmpty() || userToEdit == null)) {
            txtRollNo.setText("");
        }
    }

    private void populateFields(User u) {
        txtUsername.setText(u.getUsername());
        txtFullName.setText(u.getFullName());
        txtEmail.setText(u.getEmail());
        cmbRole.setSelectedItem(u.getRole());
        if (u.getDepartment() != null) {
            cmbDept.setSelectedItem(u.getDepartment());
        }
        txtRollNo.setText(u.getStudentRollNumber() != null ? u.getStudentRollNumber() : "");
        chkActive.setSelected(u.isActive());
        onRoleChanged();
    }

    private void saveUser() {
        lblError.setText("");

        try {
            User u = userToEdit != null ? userToEdit : new User();
            if (userToEdit == null) {
                u.setUsername(txtUsername.getText().trim());
            }
            u.setFullName(txtFullName.getText().trim());
            u.setEmail(txtEmail.getText().trim());
            u.setRole((User.Role) cmbRole.getSelectedItem());
            u.setDepartment(cmbDept.getSelectedItem() != null ? cmbDept.getSelectedItem().toString() : "");
            u.setStudentRollNumber(txtRollNo.getText().trim().isEmpty() ? null : txtRollNo.getText().trim());
            u.setActive(chkActive.isSelected());

            String pass = txtPassword != null ? new String(txtPassword.getPassword()) : null;

            if (userToEdit == null) {
                userService.addUser(currentUser, u, pass);
                JOptionPane.showMessageDialog(this,
                        "User account '" + u.getUsername() + "' (" + u.getRole() + ") successfully created in MySQL.",
                        "User Created", JOptionPane.INFORMATION_MESSAGE);
            } else {
                userService.updateUser(currentUser, u);
                JOptionPane.showMessageDialog(this,
                        "User account '" + u.getUsername() + "' successfully updated in MySQL.",
                        "User Updated", JOptionPane.INFORMATION_MESSAGE);
            }

            saved = true;
            dispose();
        } catch (StudentService.ValidationException ex) {
            lblError.setText("Validation: " + ex.getMessage());
        } catch (SecurityException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Permission Denied", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
