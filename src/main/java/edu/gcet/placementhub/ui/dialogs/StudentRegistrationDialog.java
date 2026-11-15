package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.service.RegistrationApprovalService;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Student Registration Dialog
 * Allows new students to register once with institutional roll number, ID card,
 * and academic section. Enforces uniqueness and routes to faculty review queue.
 */
public class StudentRegistrationDialog extends JDialog {
    private final RegistrationApprovalService approvalService = new RegistrationApprovalService();
    private boolean registered = false;

    private JTextField txtFullName;
    private JTextField txtRollNumber;
    private JTextField txtStudentIdCard;
    private JComboBox<String> cmbBranch;
    private JComboBox<Integer> cmbYear;
    private JTextField txtSection;
    private JTextField txtEmail;
    private JTextField txtPhone;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JPasswordField txtConfirmPassword;
    private JLabel lblError;

    public StudentRegistrationDialog(Window owner) {
        super(owner, "Student Registration · GCET Placement Hub", ModalityType.APPLICATION_MODAL);
        initUI();
    }

    private void initUI() {
        setSize(560, 720);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));

        JLabel title = new JLabel("Student Placement Portal Registration");
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
        form.setBorder(new EmptyBorder(14, 24, 14, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        int row = 0;

        // Info Banner
        JPanel infoBanner = new JPanel(new BorderLayout());
        infoBanner.setBackground(new Color(0x26, 0x84, 0xFF, 0x18));
        infoBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.ELECTRIC_BLUE, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        JLabel lblNotice = new JLabel("<html><b>Single Registration Policy:</b> Please verify your Roll Number and College ID Card. Your profile requires Department Faculty approval before campus drive access is granted.</html>");
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
        form.add(createLabel("Full Name (as per SSC): *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtFullName = new JTextField();
        form.add(txtFullName, gbc);
        row++;

        // Roll Number
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Roll Number: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtRollNumber = new JTextField();
        txtRollNumber.setFont(Theme.FONT_MONO);
        txtRollNumber.setToolTipText("e.g. 21R11A0501 (10-character GCET roll code)");
        form.add(txtRollNumber, gbc);
        row++;

        // Student ID Card
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Student ID Card No:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtStudentIdCard = new JTextField();
        txtStudentIdCard.setFont(Theme.FONT_MONO);
        txtStudentIdCard.setToolTipText("e.g. GCET-2021-0501");
        form.add(txtStudentIdCard, gbc);
        row++;

        // Branch
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Branch / Department: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] branches = {"CSE", "ECE", "EEE", "MECH", "CIVIL", "IT", "AIML", "DS", "IOT", "FED"};
        cmbBranch = new JComboBox<>(branches);
        form.add(cmbBranch, gbc);
        row++;

        // Year of Study & Section in one subpanel
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Year & Section: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        JPanel yearSecPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        yearSecPanel.setOpaque(false);
        cmbYear = new JComboBox<>(new Integer[]{1, 2, 3, 4});
        cmbYear.setSelectedItem(4); // Default to Final Year
        txtSection = new JTextField("A");
        yearSecPanel.add(cmbYear);
        yearSecPanel.add(txtSection);
        form.add(yearSecPanel, gbc);
        row++;

        // Official / Personal Email
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Email Address: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtEmail = new JTextField();
        form.add(txtEmail, gbc);
        row++;

        // Phone
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Mobile Number:"), gbc);
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

        JButton btnSubmit = Theme.createPrimaryButton("Register Student Account");
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
            int year = (Integer) cmbYear.getSelectedItem();
            approvalService.registerStudent(
                txtFullName.getText().trim(),
                txtRollNumber.getText().trim(),
                txtStudentIdCard.getText().trim(),
                (String) cmbBranch.getSelectedItem(),
                year,
                txtSection.getText().trim(),
                txtEmail.getText().trim(),
                txtPhone.getText().trim(),
                txtUsername.getText().trim(),
                p1
            );

            JOptionPane.showMessageDialog(this,
                "Student account registered successfully!\n\n" +
                "Roll Number: " + txtRollNumber.getText().trim().toUpperCase() + "\n" +
                "Status: PENDING FACULTY APPROVAL\n\n" +
                "Your registration has been queued for academic verification by your Department Faculty Coordinator.\n" +
                "You can log in once faculty approval is confirmed.",
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
