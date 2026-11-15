package edu.gcet.placementhub.ui.students;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal JDialog for Enrolling a New Student or Editing an Existing Student Record.
 */
public class StudentFormDialog extends JDialog {

    private final Student editingTarget;
    private final boolean isSelfStudent;
    private Student resultStudent;

    private JTextField rollField;
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JComboBox<String> branchCombo;
    private JTextField cgpaField;
    private JTextField gradYearField;
    private JTextField tenthField;
    private JTextField interField;
    private JTextField backlogsField;
    private JComboBox<String> statusCombo;
    private JTextField resumeField;
    private AlertBanner errorBanner;

    public StudentFormDialog(Window owner, Student editingTarget, boolean isSelfStudent) {
        super(owner,
                editingTarget == null
                        ? "Enroll New Student in GCET Directory"
                        : (isSelfStudent ? "Update Contact & Resume Details" : "Edit Student Record: " + editingTarget.getRollNumber()),
                ModalityType.APPLICATION_MODAL);
        this.editingTarget = editingTarget;
        this.isSelfStudent = isSelfStudent;

        setSize(620, 620);
        setLocationRelativeTo(owner);
        setResizable(false);
        buildUi();
        populateIfEditing();
    }

    public Student showDialog() {
        setVisible(true);
        return resultStudent;
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(ThemeManager.cardBg());
        root.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JLabel title = new JLabel(editingTarget == null
                ? "Enroll New Student in GCET Directory"
                : (isSelfStudent ? "Update My Contact & Resume Profile" : "Edit Student Record: " + editingTarget.getRollNumber()));
        title.setFont(ThemeManager.font(Font.BOLD, 16));
        title.setForeground(ThemeManager.primaryText());
        JLabel sub = new JLabel(editingTarget == null
                ? "Creates a student profile in `students` and an active login account in `users` (default password: student123)."
                : "Changes persist immediately to MySQL `students` with audit logging.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sub.setForeground(ThemeManager.mutedText());
        JPanel headCol = new JPanel();
        headCol.setOpaque(false);
        headCol.setLayout(new BoxLayout(headCol, BoxLayout.Y_AXIS));
        headCol.add(title);
        headCol.add(Box.createVerticalStrut(3));
        headCol.add(sub);
        head.add(headCol, BorderLayout.CENTER);

        errorBanner = new AlertBanner();

        JPanel form = new JPanel(new GridLayout(0, 2, 12, 10));
        form.setOpaque(false);

        rollField = new JTextField();
        fullNameField = new JTextField();
        emailField = new JTextField();
        phoneField = new JTextField();

        List<String> branches = new ArrayList<>(DeptNormalizer.GCET_BRANCHES);
        if (editingTarget != null && editingTarget.getBranch() != null && !branches.contains(editingTarget.getBranch())) {
            branches.add(editingTarget.getBranch());
        }
        branchCombo = new JComboBox<>(branches.toArray(new String[0]));
        cgpaField = new JTextField("7.50");
        gradYearField = new JTextField("2026");
        tenthField = new JTextField("85.0");
        interField = new JTextField("85.0");
        backlogsField = new JTextField("0");
        statusCombo = new JComboBox<>(new String[]{"UNPLACED", "PLACED", "HIGHER_STUDIES"});
        resumeField = new JTextField();

        form.add(labeled("Roll Number *", rollField));
        form.add(labeled("Full Name *", fullNameField));
        form.add(labeled("Email Address *", emailField));
        form.add(labeled("Phone Number *", phoneField));
        form.add(labeled("Engineering Branch *", branchCombo));
        form.add(labeled("Current CGPA (0 - 10) *", cgpaField));
        form.add(labeled("Graduation Year *", gradYearField));
        form.add(labeled("10th / SSC Percentage *", tenthField));
        form.add(labeled("Inter / Diploma Percentage *", interField));
        form.add(labeled("Active Backlogs", backlogsField));
        form.add(labeled("Placement Status", statusCombo));
        form.add(labeled("Resume / Portfolio URL", resumeField));

        if (editingTarget != null) {
            rollField.setEnabled(false);
            if (isSelfStudent) {
                fullNameField.setEnabled(false);
                emailField.setEnabled(false);
                branchCombo.setEnabled(false);
                cgpaField.setEnabled(false);
                gradYearField.setEnabled(false);
                tenthField.setEnabled(false);
                interField.setEnabled(false);
                backlogsField.setEnabled(false);
                statusCombo.setEnabled(false);
            }
        }

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        errorBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(errorBanner);
        mid.add(Box.createVerticalStrut(6));
        mid.add(form);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        foot.setOpaque(false);
        ActionButton cancelBtn = new ActionButton("Cancel", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        cancelBtn.addActionListener(e -> {
            resultStudent = null;
            dispose();
        });
        ActionButton saveBtn = new ActionButton(
                editingTarget == null ? "Enroll Student in MySQL" : "Save Changes to MySQL",
                ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10
        );
        saveBtn.addActionListener(e -> handleSave());

        foot.add(cancelBtn);
        foot.add(saveBtn);

        root.add(head, BorderLayout.NORTH);
        root.add(mid, BorderLayout.CENTER);
        root.add(foot, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void populateIfEditing() {
        if (editingTarget == null) return;
        rollField.setText(nonNull(editingTarget.getRollNumber()));
        fullNameField.setText(nonNull(editingTarget.getFullName()));
        emailField.setText(nonNull(editingTarget.getEmail()));
        phoneField.setText(nonNull(editingTarget.getPhone()));
        branchCombo.setSelectedItem(editingTarget.getBranch());
        cgpaField.setText(String.valueOf(editingTarget.getCgpa()));
        gradYearField.setText(String.valueOf(editingTarget.getGraduationYear() > 0 ? editingTarget.getGraduationYear() : 2026));
        tenthField.setText(String.valueOf(editingTarget.getTenthPercentage()));
        interField.setText(String.valueOf(editingTarget.getInterPercentage()));
        backlogsField.setText(String.valueOf(editingTarget.getBacklogs()));
        if (editingTarget.getPlacementStatus() != null) {
            statusCombo.setSelectedItem(editingTarget.getPlacementStatus());
        }
        resumeField.setText(nonNull(editingTarget.getResumeUrl()));
    }

    private void handleSave() {
        String roll = rollField.getText().trim().toUpperCase();
        String name = fullNameField.getText().trim();
        String email = emailField.getText().trim().toLowerCase();
        String phone = phoneField.getText().trim();
        String branch = branchCombo.getSelectedItem() != null ? branchCombo.getSelectedItem().toString() : "CSE";

        if (!isSelfStudent && (roll.isEmpty() || name.isEmpty() || email.isEmpty())) {
            errorBanner.showError("Roll Number, Full Name, and Email are required.");
            return;
        }

        double cgpa, tenth, inter;
        int gradYear, backlogs;
        try {
            cgpa = Double.parseDouble(cgpaField.getText().trim());
            tenth = Double.parseDouble(tenthField.getText().trim());
            inter = Double.parseDouble(interField.getText().trim());
            gradYear = Integer.parseInt(gradYearField.getText().trim());
            backlogs = Integer.parseInt(backlogsField.getText().trim());
        } catch (NumberFormatException ex) {
            errorBanner.showError("Please enter valid numeric values for CGPA, Percentages, Graduation Year, and Backlogs.");
            return;
        }

        if (cgpa < 0 || cgpa > 10 || tenth < 0 || tenth > 100 || inter < 0 || inter > 100 || backlogs < 0) {
            errorBanner.showError("CGPA must be 0-10, percentages 0-100, and backlogs >= 0.");
            return;
        }

        Student s = new Student();
        if (editingTarget != null) {
            s.setId(editingTarget.getId());
        }
        s.setRollNumber(roll);
        s.setFullName(name);
        s.setEmail(email);
        s.setPhone(phone);
        s.setBranch(branch);
        s.setCgpa(cgpa);
        s.setTenthPercentage(tenth);
        s.setInterPercentage(inter);
        s.setGraduationYear(gradYear);
        s.setBacklogs(backlogs);
        s.setPlacementStatus(statusCombo.getSelectedItem() != null ? statusCombo.getSelectedItem().toString() : "UNPLACED");
        s.setResumeUrl(resumeField.getText().trim());

        this.resultStudent = s;
        dispose();
    }

    private JPanel labeled(String text, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        l.setForeground(ThemeManager.secondaryText());
        field.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        field.setPreferredSize(new Dimension(180, 34));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
