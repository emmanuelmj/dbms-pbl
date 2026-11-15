package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.model.Skill;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.SkillService;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Skill Library Catalog Dialog
 * Form for adding or editing competencies in the institutional skill repository.
 */
public class SkillFormDialog extends JDialog {
    private final SkillService skillService;
    private final User currentUser;
    private final Skill skillToEdit;
    private boolean saved = false;

    private JTextField txtName;
    private JComboBox<String> cmbCategory;
    private JTextArea txtDescription;
    private JComboBox<String> cmbStatus;
    private JLabel lblError;

    public SkillFormDialog(Window owner, User currentUser, Skill skillToEdit) {
        super(owner, skillToEdit == null ? "Add Skill to GCET Library" : "Edit Skill in Catalog", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.skillToEdit = skillToEdit;
        this.skillService = new SkillService();

        initUI();
        if (skillToEdit != null) {
            populateFields(skillToEdit);
        }
    }

    private void initUI() {
        setSize(480, 480);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel(skillToEdit == null ? "Define Technical Competency" : "Update Skill Definition");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("GCET Institutional Skill Matrix & Placement Taxonomy");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(16, 22, 16, 22));
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

        // Skill Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Skill Name: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtName = new JTextField();
        form.add(txtName, gbc);
        row++;

        // Category
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Category: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] categories = {
            "Programming Languages",
            "Data Structures and Algorithms",
            "Database Management",
            "Web Development",
            "Artificial Intelligence",
            "Cloud Computing",
            "DevOps & Tools",
            "Core Engineering",
            "Cybersecurity",
            "Mobile App Development",
            "Soft Skills & Aptitude"
        };
        cmbCategory = new JComboBox<>(categories);
        cmbCategory.setEditable(true);
        form.add(cmbCategory, gbc);
        row++;

        // Description
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        form.add(createLabel("Description:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtDescription = new JTextArea(4, 20);
        txtDescription.setLineWrap(true);
        txtDescription.setWrapStyleWord(true);
        JScrollPane sp = new JScrollPane(txtDescription);
        form.add(sp, gbc);
        row++;
        gbc.anchor = GridBagConstraints.CENTER;

        // Status
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Status:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        cmbStatus = new JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
        form.add(cmbStatus, gbc);

        add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSave = Theme.createPrimaryButton(skillToEdit == null ? "Add to Library" : "Update Skill");
        btnSave.addActionListener(e -> saveSkill());

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

    private void populateFields(Skill s) {
        txtName.setText(s.getName());
        cmbCategory.setSelectedItem(s.getCategory());
        txtDescription.setText(s.getDescription() != null ? s.getDescription() : "");
        cmbStatus.setSelectedItem(s.getStatus());
    }

    private void saveSkill() {
        lblError.setText("");

        try {
            Skill s = skillToEdit != null ? skillToEdit : new Skill();
            s.setName(txtName.getText().trim());
            s.setCategory(cmbCategory.getSelectedItem() != null ? cmbCategory.getSelectedItem().toString() : "");
            s.setDescription(txtDescription.getText().trim());
            s.setStatus((String) cmbStatus.getSelectedItem());

            if (skillToEdit == null) {
                skillService.addSkill(currentUser, s);
                JOptionPane.showMessageDialog(this,
                        "Skill '" + s.getName() + "' added to institutional library.",
                        "Skill Added", JOptionPane.INFORMATION_MESSAGE);
            } else {
                skillService.updateSkill(currentUser, s);
                JOptionPane.showMessageDialog(this,
                        "Skill '" + s.getName() + "' updated in library.",
                        "Skill Updated", JOptionPane.INFORMATION_MESSAGE);
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
