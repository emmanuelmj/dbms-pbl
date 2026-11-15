package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.model.Student;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Student Registration & Edit Dialog
 */
public class StudentFormDialog extends JDialog {
    private final StudentService studentService;
    private final User currentUser;
    private final Student studentToEdit;
    private boolean saved = false;

    private JTextField txtRollNumber;
    private JTextField txtFullName;
    private JTextField txtEmail;
    private JTextField txtPhone;
    private JComboBox<String> cmbBranch;
    private JSpinner spCgpa;
    private JSpinner spGradYear;
    private JSpinner spBacklogs;
    private JSpinner spTenth;
    private JSpinner spInter;
    private JComboBox<String> cmbStatus;
    private JLabel lblError;

    public StudentFormDialog(Window owner, User currentUser, Student studentToEdit) {
        super(owner, studentToEdit == null ? "Register New Student - GCET" : "Edit Student Information - GCET", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.studentToEdit = studentToEdit;
        this.studentService = new StudentService();

        initUI();
        if (studentToEdit != null) {
            populateFields(studentToEdit);
        }
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

        JLabel title = new JLabel(studentToEdit == null ? "Register Engineering Student" : "Update Student Record");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · Academic Records");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Form fields container
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(18, 24, 18, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        // Error message banner
        lblError = new JLabel("");
        lblError.setForeground(Theme.DANGER_ROSE);
        lblError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        form.add(lblError, gbc);
        gbc.gridwidth = 1;

        // Roll Number
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("Roll Number: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtRollNumber = new JTextField();
        txtRollNumber.setFont(Theme.FONT_MONO);
        form.add(txtRollNumber, gbc);
        row++;

        // Full Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("Full Name: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtFullName = new JTextField();
        form.add(txtFullName, gbc);
        row++;

        // Email
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("College Email: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtEmail = new JTextField();
        form.add(txtEmail, gbc);
        row++;

        // Phone
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("Contact Phone:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        txtPhone = new JTextField();
        form.add(txtPhone, gbc);
        row++;

        // Branch
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("GCET Branch: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        cmbBranch = new JComboBox<>(AppConfig.BRANCHES.toArray(new String[0]));
        form.add(cmbBranch, gbc);
        row++;

        // CGPA
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("CGPA (0.00 - 10.00): *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spCgpa = new JSpinner(new SpinnerNumberModel(8.00, 0.00, 10.00, 0.05));
        form.add(spCgpa, gbc);
        row++;

        // Graduation Year
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("Graduation Year: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spGradYear = new JSpinner(new SpinnerNumberModel(2026, 2020, 2035, 1));
        form.add(spGradYear, gbc);
        row++;

        // Active Backlogs
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("Active Backlogs:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spBacklogs = new JSpinner(new SpinnerNumberModel(0, 0, 30, 1));
        form.add(spBacklogs, gbc);
        row++;

        // 10th & Inter %
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("10th / SSC (%):"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spTenth = new JSpinner(new SpinnerNumberModel(85.0, 35.0, 100.0, 0.5));
        form.add(spTenth, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("Inter / Diploma (%):"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        spInter = new JSpinner(new SpinnerNumberModel(85.0, 35.0, 100.0, 0.5));
        form.add(spInter, gbc);
        row++;

        // Placement Status
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        form.add(createLabel("Placement Status:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.7;
        cmbStatus = new JComboBox<>(new String[]{"UNPLACED", "SHORTLISTED", "PLACED", "OPTED_OUT"});
        form.add(cmbStatus, gbc);

        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Buttons Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSave = Theme.createPrimaryButton(studentToEdit == null ? "Save & Register Student" : "Update Student Record");
        btnSave.addActionListener(e -> saveStudent());

        footer.add(btnCancel);
        footer.add(btnSave);
        add(footer, BorderLayout.SOUTH);
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_BOLD);
        l.setForeground(Theme.DARK_TEXT);
        return l;
    }

    private void populateFields(Student s) {
        txtRollNumber.setText(s.getRollNumber());
        txtFullName.setText(s.getFullName());
        txtEmail.setText(s.getEmail());
        txtPhone.setText(s.getPhone() != null ? s.getPhone() : "");
        cmbBranch.setSelectedItem(s.getBranch());
        spCgpa.setValue(s.getCgpa());
        spGradYear.setValue(s.getGraduationYear());
        spBacklogs.setValue(s.getBacklogs());
        spTenth.setValue(s.getTenthPercentage() > 0 ? s.getTenthPercentage() : 85.0);
        spInter.setValue(s.getInterPercentage() > 0 ? s.getInterPercentage() : 85.0);
        cmbStatus.setSelectedItem(s.getPlacementStatus());
    }

    private void saveStudent() {
        lblError.setText("");

        try {
            Student s = studentToEdit != null ? studentToEdit : new Student();
            s.setRollNumber(txtRollNumber.getText());
            s.setFullName(txtFullName.getText());
            s.setEmail(txtEmail.getText());
            s.setPhone(txtPhone.getText().trim());
            s.setBranch((String) cmbBranch.getSelectedItem());
            s.setCgpa(((Number) spCgpa.getValue()).doubleValue());
            s.setGraduationYear(((Number) spGradYear.getValue()).intValue());
            s.setBacklogs(((Number) spBacklogs.getValue()).intValue());
            s.setTenthPercentage(((Number) spTenth.getValue()).doubleValue());
            s.setInterPercentage(((Number) spInter.getValue()).doubleValue());
            s.setPlacementStatus((String) cmbStatus.getSelectedItem());

            if (studentToEdit == null) {
                studentService.addStudent(currentUser, s);
                JOptionPane.showMessageDialog(this,
                        "Student '" + s.getFullName() + "' (" + s.getRollNumber() + ") successfully registered in MySQL database.",
                        "Registration Successful", JOptionPane.INFORMATION_MESSAGE);
            } else {
                studentService.updateStudent(currentUser, s);
                JOptionPane.showMessageDialog(this,
                        "Student record for '" + s.getRollNumber() + "' successfully updated in MySQL database.",
                        "Update Successful", JOptionPane.INFORMATION_MESSAGE);
            }

            saved = true;
            dispose();
        } catch (StudentService.ValidationException ex) {
            lblError.setText("Validation Error: " + ex.getMessage());
        } catch (SecurityException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Permission Denied", JOptionPane.WARNING_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Database Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
