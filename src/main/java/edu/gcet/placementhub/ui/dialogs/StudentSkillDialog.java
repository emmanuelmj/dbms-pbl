package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.model.Skill;
import edu.gcet.placementhub.model.StudentSkill;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.SkillService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * GCET PLACEMENT HUB - Student Skill Endorsement Dialog
 * Allows students or faculty mentors to record skill proficiencies (Levels 1-4),
 * experience duration, and certifications.
 */
public class StudentSkillDialog extends JDialog {
    private final SkillService skillService;
    private final User currentUser;
    private final String studentId;
    private final StudentSkill existingSkill;
    private boolean saved = false;

    private JComboBox<SkillItem> cmbSkill;
    private JComboBox<String> cmbProficiency;
    private JComboBox<String> cmbExperience;
    private JTextField txtCertification;
    private JCheckBox chkVerified;

    private static class SkillItem {
        final String id;
        final String name;
        final String category;

        SkillItem(String id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category;
        }

        @Override
        public String toString() {
            return name + " (" + category + ")";
        }
    }

    public StudentSkillDialog(Window owner, User currentUser, String studentId, StudentSkill existingSkill) {
        super(owner, existingSkill == null ? "Add Technical Competency" : "Update Competency Rating", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.studentId = studentId;
        this.existingSkill = existingSkill;
        this.skillService = new SkillService();

        initUI();
        loadSkills();
        if (existingSkill != null) {
            populateFields(existingSkill);
        }
    }

    private void initUI() {
        setSize(480, 460);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel(existingSkill == null ? "Assess Technical Competency" : "Update Skill Rating");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Proficiency Framework: 1 (Beginner) to 4 (Expert)");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(18, 20, 18, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        // Skill
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(new JLabel("Technical Skill: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        cmbSkill = new JComboBox<>();
        if (existingSkill != null) {
            cmbSkill.setEnabled(false);
        }
        form.add(cmbSkill, gbc);
        row++;

        // Proficiency
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(new JLabel("Proficiency Level: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] proficiencies = {
            "Level 1: Beginner (Foundational syntax & concepts)",
            "Level 2: Intermediate (Independent problem solving)",
            "Level 3: Advanced (Production projects & system design)",
            "Level 4: Expert (Architecture & performance tuning)"
        };
        cmbProficiency = new JComboBox<>(proficiencies);
        form.add(cmbProficiency, gbc);
        row++;

        // Experience Duration
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(new JLabel("Experience Duration:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] durations = {"< 3 months", "3 - 6 months", "6 - 12 months", "1 - 2 years", "2+ years"};
        cmbExperience = new JComboBox<>(durations);
        cmbExperience.setSelectedIndex(1);
        form.add(cmbExperience, gbc);
        row++;

        // Certification Evidence
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(new JLabel("Certifications / Proof:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtCertification = new JTextField();
        txtCertification.setToolTipText("e.g. AWS Certified Developer, NPTEL Elite, LeetCode profile");
        form.add(txtCertification, gbc);
        row++;

        // Verified by Faculty
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(new JLabel("Faculty Verification:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        chkVerified = new JCheckBox("Mark as Faculty Verified");
        chkVerified.setOpaque(false);
        chkVerified.setEnabled(currentUser.canAssessSkills());
        form.add(chkVerified, gbc);

        add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSave = Theme.createPrimaryButton("Save Competency");
        btnSave.addActionListener(e -> saveRating());

        footer.add(btnCancel);
        footer.add(btnSave);
        add(footer, BorderLayout.SOUTH);
    }

    private void loadSkills() {
        cmbSkill.removeAllItems();
        List<Skill> skills = skillService.getSkills("", "ALL", "ACTIVE");
        for (Skill s : skills) {
            cmbSkill.addItem(new SkillItem(s.getId(), s.getName(), s.getCategory()));
        }
    }

    private void populateFields(StudentSkill ss) {
        for (int i = 0; i < cmbSkill.getItemCount(); i++) {
            SkillItem item = cmbSkill.getItemAt(i);
            if (item.id.equals(ss.getSkillId())) {
                cmbSkill.setSelectedIndex(i);
                break;
            }
        }

        int prof = ss.getProficiency();
        if (prof >= 1 && prof <= 4) {
            cmbProficiency.setSelectedIndex(prof - 1);
        }

        if (ss.getExperienceDuration() != null) {
            cmbExperience.setSelectedItem(ss.getExperienceDuration());
        }

        txtCertification.setText(ss.getCertificationEvidence() != null ? ss.getCertificationEvidence() : "");
        chkVerified.setSelected(ss.isVerifiedByFaculty());
    }

    private void saveRating() {
        SkillItem selectedSkill = (SkillItem) cmbSkill.getSelectedItem();
        if (selectedSkill == null) {
            JOptionPane.showMessageDialog(this, "Please select a technical skill.", "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int proficiency = cmbProficiency.getSelectedIndex() + 1; // 1 to 4
        String experience = (String) cmbExperience.getSelectedItem();
        String cert = txtCertification.getText().trim();

        try {
            StudentSkill ss = existingSkill != null ? existingSkill : new StudentSkill();
            ss.setStudentId(studentId);
            ss.setSkillId(selectedSkill.id);
            ss.setSkillName(selectedSkill.name);
            ss.setProficiency(proficiency);
            ss.setExperienceDuration(experience);
            ss.setCertificationEvidence(cert);

            if (chkVerified.isSelected()) {
                ss.setVerifiedByFaculty(true);
                ss.setVerifiedByFacultyId(currentUser.getId());
                ss.setVerifiedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            }

            skillService.saveStudentSkill(currentUser, ss);
            JOptionPane.showMessageDialog(this,
                    "Technical competency '" + selectedSkill.name + "' (Level " + proficiency + ") saved.",
                    "Competency Recorded", JOptionPane.INFORMATION_MESSAGE);
            saved = true;
            dispose();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error saving skill rating: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
