package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.model.Faculty;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.FacultyService;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * GCET PLACEMENT HUB - Faculty Registration & Edit Dialog
 * Links departmental mentors to system user accounts.
 */
public class FacultyFormDialog extends JDialog {
    private final FacultyService facultyService;
    private final User currentUser;
    private final Faculty facultyToEdit;
    private boolean saved = false;

    private JTextField txtFacultyId;
    private JTextField txtFullName;
    private JComboBox<String> cmbDept;
    private JComboBox<String> cmbDesignation;
    private JTextField txtEmail;
    private JTextField txtPhone;
    private JComboBox<UserItem> cmbUserLink;
    private JComboBox<String> cmbStatus;
    private JLabel lblError;

    private static class UserItem {
        final String id;
        final String display;

        UserItem(String id, String display) {
            this.id = id;
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    public FacultyFormDialog(Window owner, User currentUser, Faculty facultyToEdit) {
        super(owner, facultyToEdit == null ? "Register New Faculty Member - GCET" : "Edit Faculty Details - GCET", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.facultyToEdit = facultyToEdit;
        this.facultyService = new FacultyService();

        initUI();
        loadUserLinks();
        if (facultyToEdit != null) {
            populateFields(facultyToEdit);
        }
    }

    private void initUI() {
        setSize(520, 580);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));

        JLabel title = new JLabel(facultyToEdit == null ? "Register Faculty Coordinator" : "Update Faculty Member");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · Academic Mentorship");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Form
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

        // Faculty ID
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Faculty Employee ID: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtFacultyId = new JTextField();
        txtFacultyId.setFont(Theme.FONT_MONO);
        form.add(txtFacultyId, gbc);
        row++;

        // Full Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Full Name (with title): *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtFullName = new JTextField();
        form.add(txtFullName, gbc);
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
        String[] desigs = {
            "Professor & Head (HOD)", "Professor", "Associate Professor",
            "Assistant Professor", "Department Placement Coordinator", "CDC Faculty Mentor"
        };
        cmbDesignation = new JComboBox<>(desigs);
        cmbDesignation.setEditable(true);
        form.add(cmbDesignation, gbc);
        row++;

        // Email
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("College Email: *"), gbc);
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

        // Link User Account
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Linked User Account:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        cmbUserLink = new JComboBox<>();
        form.add(cmbUserLink, gbc);
        row++;

        // Status
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Status:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        cmbStatus = new JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
        form.add(cmbStatus, gbc);

        add(form, BorderLayout.CENTER);

        // Footer Buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSave = Theme.createPrimaryButton(facultyToEdit == null ? "Save Faculty" : "Update Details");
        btnSave.addActionListener(e -> saveFaculty());

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

    private void loadUserLinks() {
        cmbUserLink.removeAllItems();
        cmbUserLink.addItem(new UserItem(null, "-- None (Unlinked) --"));

        try {
            UserDao userDao = new UserDao();
            List<User> users = userDao.findAll("", "FACULTY", "ACTIVE");
            for (User u : users) {
                cmbUserLink.addItem(new UserItem(u.getId(), u.getFullName() + " (@" + u.getUsername() + ")"));
            }
        } catch (Exception ignored) {}
    }

    private void populateFields(Faculty f) {
        txtFacultyId.setText(f.getFacultyId());
        txtFullName.setText(f.getFullName());
        cmbDept.setSelectedItem(f.getDepartment());
        cmbDesignation.setSelectedItem(f.getDesignation());
        txtEmail.setText(f.getEmail());
        txtPhone.setText(f.getPhone() != null ? f.getPhone() : "");
        cmbStatus.setSelectedItem(f.getStatus());

        if (f.getUserId() != null) {
            for (int i = 0; i < cmbUserLink.getItemCount(); i++) {
                UserItem item = cmbUserLink.getItemAt(i);
                if (f.getUserId().equals(item.id)) {
                    cmbUserLink.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void saveFaculty() {
        lblError.setText("");

        try {
            Faculty f = facultyToEdit != null ? facultyToEdit : new Faculty();
            f.setFacultyId(txtFacultyId.getText().trim());
            f.setFullName(txtFullName.getText().trim());
            f.setDepartment((String) cmbDept.getSelectedItem());
            f.setDesignation((String) cmbDesignation.getSelectedItem());
            f.setEmail(txtEmail.getText().trim());
            f.setPhone(txtPhone.getText().trim());
            f.setStatus((String) cmbStatus.getSelectedItem());

            UserItem selectedUser = (UserItem) cmbUserLink.getSelectedItem();
            f.setUserId(selectedUser != null ? selectedUser.id : null);

            if (facultyToEdit == null) {
                facultyService.addFaculty(currentUser, f);
                JOptionPane.showMessageDialog(this,
                        "Faculty member '" + f.getFullName() + "' (" + f.getFacultyId() + ") added successfully.",
                        "Faculty Added", JOptionPane.INFORMATION_MESSAGE);
            } else {
                facultyService.updateFaculty(currentUser, f);
                JOptionPane.showMessageDialog(this,
                        "Faculty member '" + f.getFullName() + "' updated successfully.",
                        "Faculty Updated", JOptionPane.INFORMATION_MESSAGE);
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
