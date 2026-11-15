package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.service.RegistrationApprovalService;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Faculty Registration Dialog
 * Handles new faculty signup with mandatory dual-approval requirement notice.
 */
public class FacultyRegistrationDialog extends JDialog {
    private final RegistrationApprovalService approvalService = new RegistrationApprovalService();
    private boolean registered = false;

    private JTextField txtFullName;
    private JTextField txtEmployeeId;
    private JComboBox<String> cmbDept;
    private JComboBox<String> cmbDesignation;
    private JTextField txtEmail;
    private JTextField txtPhone;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JPasswordField txtConfirmPassword;
    private JLabel lblError;

    public FacultyRegistrationDialog(Window owner) {
        super(owner, "Faculty Member Registration · GCET Placement Hub", ModalityType.APPLICATION_MODAL);
        initUI();
    }

    private void initUI() {
        setSize(540, 680);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));

        JLabel title = new JLabel("Faculty Member Registration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · Career Development Cell");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(16, 24, 16, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 4, 5, 4);

        int row = 0;

        // Info Banner
        JPanel infoBanner = new JPanel(new BorderLayout());
        infoBanner.setBackground(new Color(0x26, 0x84, 0xFF, 0x18));
        infoBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.ELECTRIC_BLUE, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        JLabel lblNotice = new JLabel("<html><b>Dual Approval Mandatory:</b> Account requires approval from both an existing faculty coordinator and an administrator before activation.</html>");
        lblNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblNotice.setForeground(Theme.MIDNIGHT_NAVY);
        infoBanner.add(lblNotice, BorderLayout.CENTER);

        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(infoBanner, gbc);

        lblError = new JLabel("");
        lblError.setForeground(Theme.DANGER_ROSE);
        lblError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gbc.gridy = row++;
        form.add(lblError, gbc);
        gbc.gridwidth = 1;

        // Full Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Full Name: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtFullName = new JTextField();
        form.add(txtFullName, gbc);
        row++;

        // Employee ID
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Employee ID: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtEmployeeId = new JTextField();
        txtEmployeeId.setFont(Theme.FONT_MONO);
        form.add(txtEmployeeId, gbc);
        row++;

        // Department
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Department: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] depts = {"CSE", "ECE", "EEE", "MECH", "CIVIL", "IT", "AIML", "DS", "IOT", "FED"};
        cmbDept = new JComboBox<>(depts);
        form.add(cmbDept, gbc);
        row++;

        // Designation
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Designation: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] desigs = {"Professor & Head (HOD)", "Professor", "Associate Professor", "Assistant Professor", "Department Placement SPOC"};
        cmbDesignation = new JComboBox<>(desigs);
        cmbDesignation.setEditable(true);
        form.add(cmbDesignation, gbc);
        row++;

        // Official Email
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Official Email: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtEmail = new JTextField();
        form.add(txtEmail, gbc);
        row++;

        // Phone
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Phone Number:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtPhone = new JTextField();
        form.add(txtPhone, gbc);
        row++;

        // Username
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Portal Username: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtUsername = new JTextField();
        txtUsername.setFont(Theme.FONT_MONO);
        form.add(txtUsername, gbc);
        row++;

        // Password
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Password: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtPassword = new JPasswordField();
        form.add(txtPassword, gbc);
        row++;

        // Confirm Password
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Confirm Password: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtConfirmPassword = new JPasswordField();
        form.add(txtConfirmPassword, gbc);

        add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSubmit = Theme.createPrimaryButton("Submit for Dual Approval");
        btnSubmit.addActionListener(e -> submitForm());

        footer.add(btnCancel);
        footer.add(btnSubmit);
        add(footer, BorderLayout.SOUTH);
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_BOLD);
        l.setForeground(Theme.DARK_TEXT);
        return l;
    }

    private void submitForm() {
        lblError.setText("");
        String p1 = new String(txtPassword.getPassword());
        String p2 = new String(txtConfirmPassword.getPassword());

        if (!p1.equals(p2)) {
            lblError.setText("Passwords do not match.");
            return;
        }

        try {
            approvalService.registerFaculty(
                txtFullName.getText().trim(),
                txtEmployeeId.getText().trim(),
                (String) cmbDept.getSelectedItem(),
                (String) cmbDesignation.getSelectedItem(),
                txtEmail.getText().trim(),
                txtPhone.getText().trim(),
                txtUsername.getText().trim(),
                p1
            );

            JOptionPane.showMessageDialog(this,
                "Faculty registration submitted successfully!\n\n" +
                "Status: PENDING DUAL APPROVAL\n" +
                "Your request has been routed to:\n" +
                "1. Authorized Faculty Coordinators (Peer Validation)\n" +
                "2. College Administration (Administrative Authorization)\n\n" +
                "You will be able to sign in once both approvals are recorded in the central database.",
                "Registration Submitted", JOptionPane.INFORMATION_MESSAGE);

            registered = true;
            dispose();
        } catch (StudentService.ValidationException ex) {
            lblError.setText(ex.getMessage());
        } catch (Exception ex) {
            lblError.setText("Error: " + ex.getMessage());
        }
    }

    public boolean isRegistered() {
        return registered;
    }
}
