package edu.gcet.placementhub.ui.drives;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Modal JDialog for creating or editing a Campus Placement Drive,
 * including Eligible Branches multi-selection and Required Skills with proficiency/mandatory rules.
 */
public class DriveFormDialog extends JDialog {

    private static final String[] DRIVE_STATUSES = {
            "OPEN", "UPCOMING", "PLANNED", "CLOSED", "COMPLETED", "CANCELLED"
    };

    private record CompanyItem(String id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }

    private record SkillItem(Skill skill) {
        @Override
        public String toString() {
            return skill.getName() + " (" + skill.getCategory() + ")";
        }
    }

    private final PlacementDrive editingTarget;
    private final List<Company> companies;
    private final List<Skill> allSkills;
    private final User currentUser;
    private PlacementDrive resultDrive;

    private JComboBox<CompanyItem> companyCombo;
    private JTextField jobRoleField;
    private JTextArea jobDescArea;
    private JTextField packageField;
    private JTextField minCgpaField;
    private JTextField maxBacklogsField;
    private JTextField gradYearField;
    private JTextField vacanciesField;
    private JTextField driveDateField;
    private JTextField deadlineField;
    private JComboBox<String> statusCombo;
    private JTextField selectionProcessField;

    private final Map<String, JCheckBox> branchChecks = new LinkedHashMap<>();
    private final List<DriveRequiredSkill> reqSkills = new ArrayList<>();
    private JComboBox<SkillItem> addSkillCombo;
    private JPanel reqSkillsPanel;
    private AlertBanner errorBanner;

    public DriveFormDialog(
            Window owner,
            PlacementDrive editingTarget,
            List<Company> companies,
            List<Skill> allSkills,
            User currentUser
    ) {
        super(owner,
                editingTarget == null ? "Create Campus Placement Drive" : "Edit Placement Drive: " + editingTarget.getJobRole(),
                ModalityType.APPLICATION_MODAL);
        this.editingTarget = editingTarget;
        this.companies = companies != null ? companies : List.of();
        this.allSkills = allSkills != null ? allSkills : List.of();
        this.currentUser = currentUser;

        setSize(760, 740);
        setLocationRelativeTo(owner);
        setResizable(true);
        buildUi();
        populateInitialState();
    }

    public PlacementDrive showDialog() {
        setVisible(true);
        return resultDrive;
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(ThemeManager.cardBg());
        root.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel headCol = new JPanel();
        headCol.setOpaque(false);
        headCol.setLayout(new BoxLayout(headCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(editingTarget == null
                ? "Publish Campus Placement Drive"
                : "Update Placement Drive: " + editingTarget.getCompanyName() + " — " + editingTarget.getJobRole());
        title.setFont(ThemeManager.font(Font.BOLD, 16));
        title.setForeground(ThemeManager.primaryText());
        JLabel sub = new JLabel("Configures eligibility cutoffs, branches, required technical proficiencies, and MySQL deadlines.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sub.setForeground(ThemeManager.mutedText());
        headCol.add(title);
        headCol.add(Box.createVerticalStrut(3));
        headCol.add(sub);

        errorBanner = new AlertBanner();

        JPanel formBody = new JPanel();
        formBody.setOpaque(false);
        formBody.setLayout(new BoxLayout(formBody, BoxLayout.Y_AXIS));

        // Basic Info Grid
        JPanel grid1 = new JPanel(new GridLayout(1, 2, 12, 8));
        grid1.setOpaque(false);
        grid1.setAlignmentX(Component.LEFT_ALIGNMENT);

        companyCombo = new JComboBox<>();
        for (Company c : companies) {
            companyCombo.addItem(new CompanyItem(c.getId(), c.getName()));
        }
        jobRoleField = new JTextField();
        jobRoleField.putClientProperty("JTextField.placeholderText", "e.g. Cloud Engineer / SDE-1");
        grid1.add(labeled("Partner Company *", companyCombo));
        grid1.add(labeled("Job Role Title *", jobRoleField));

        jobDescArea = new JTextArea(2, 40);
        jobDescArea.setLineWrap(true);
        jobDescArea.setWrapStyleWord(true);
        jobDescArea.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        JScrollPane descScroll = new JScrollPane(jobDescArea);
        descScroll.setPreferredSize(new Dimension(200, 62));
        JPanel descPanel = labeled("Job Description & Responsibilities *", descScroll);
        descPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Numeric & Dates Grid
        JPanel grid2 = new JPanel(new GridLayout(2, 4, 10, 8));
        grid2.setOpaque(false);
        grid2.setAlignmentX(Component.LEFT_ALIGNMENT);

        packageField = new JTextField("8.5");
        minCgpaField = new JTextField("7.00");
        maxBacklogsField = new JTextField("0");
        gradYearField = new JTextField("2026");
        vacanciesField = new JTextField("15");
        driveDateField = new JTextField(LocalDate.now().plusDays(14).toString());
        deadlineField = new JTextField(LocalDate.now().plusDays(7).toString());
        statusCombo = new JComboBox<>(DRIVE_STATUSES);

        grid2.add(labeled("Package (LPA) *", packageField));
        grid2.add(labeled("Min CGPA (0-10) *", minCgpaField));
        grid2.add(labeled("Max Backlogs *", maxBacklogsField));
        grid2.add(labeled("Passing Batch *", gradYearField));
        grid2.add(labeled("Vacancies *", vacanciesField));
        grid2.add(labeled("Drive Date (YYYY-MM-DD)", driveDateField));
        grid2.add(labeled("Deadline (YYYY-MM-DD)", deadlineField));
        grid2.add(labeled("Drive Status", statusCombo));

        selectionProcessField = new JTextField("Online Assessment -> Technical Interview -> HR Round");
        JPanel procPanel = labeled("Selection Process Stages", selectionProcessField);
        procPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Eligible Branches
        JLabel brLbl = new JLabel("Eligible Engineering Branches (Select at least one) *");
        brLbl.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        brLbl.setForeground(ThemeManager.secondaryText());
        brLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel branchGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        branchGrid.setBackground(ThemeManager.cardSubtleBg());
        branchGrid.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder()));
        branchGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (String br : DeptNormalizer.GCET_BRANCHES) {
            JCheckBox cb = new JCheckBox(br);
            cb.setOpaque(false);
            cb.setFont(ThemeManager.font(Font.PLAIN, 12));
            cb.setForeground(ThemeManager.primaryText());
            branchChecks.put(br, cb);
            branchGrid.add(cb);
        }

        // Required Skills
        JLabel skLbl = new JLabel("Required Technical Skills & Minimum Proficiency Cutoffs");
        skLbl.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        skLbl.setForeground(ThemeManager.secondaryText());
        skLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel addSkillBar = new JPanel(new BorderLayout(8, 0));
        addSkillBar.setOpaque(false);
        addSkillBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        addSkillBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        addSkillCombo = new JComboBox<>();
        for (Skill s : allSkills) {
            addSkillCombo.addItem(new SkillItem(s));
        }
        ActionButton addSkBtn = new ActionButton("+ Add Skill", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 8);
        addSkBtn.addActionListener(e -> addSelectedSkill());
        addSkillBar.add(addSkillCombo, BorderLayout.CENTER);
        addSkillBar.add(addSkBtn, BorderLayout.EAST);

        reqSkillsPanel = new JPanel();
        reqSkillsPanel.setOpaque(false);
        reqSkillsPanel.setLayout(new BoxLayout(reqSkillsPanel, BoxLayout.Y_AXIS));
        reqSkillsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        errorBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        formBody.add(errorBanner);
        formBody.add(Box.createVerticalStrut(4));
        formBody.add(grid1);
        formBody.add(Box.createVerticalStrut(10));
        formBody.add(descPanel);
        formBody.add(Box.createVerticalStrut(10));
        formBody.add(grid2);
        formBody.add(Box.createVerticalStrut(10));
        formBody.add(procPanel);
        formBody.add(Box.createVerticalStrut(10));
        formBody.add(brLbl);
        formBody.add(Box.createVerticalStrut(4));
        formBody.add(branchGrid);
        formBody.add(Box.createVerticalStrut(10));
        formBody.add(skLbl);
        formBody.add(Box.createVerticalStrut(4));
        formBody.add(addSkillBar);
        formBody.add(Box.createVerticalStrut(6));
        formBody.add(reqSkillsPanel);

        JScrollPane centerScroll = new JScrollPane(formBody);
        centerScroll.setBorder(null);
        centerScroll.getViewport().setBackground(ThemeManager.cardBg());
        centerScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        foot.setOpaque(false);
        ActionButton cancelBtn = new ActionButton("Cancel", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        cancelBtn.addActionListener(e -> {
            resultDrive = null;
            dispose();
        });
        ActionButton saveBtn = new ActionButton(
                editingTarget == null ? "Publish Placement Drive" : "Save Drive Changes",
                ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 10
        );
        saveBtn.addActionListener(e -> handleSave());
        foot.add(cancelBtn);
        foot.add(saveBtn);

        root.add(headCol, BorderLayout.NORTH);
        root.add(centerScroll, BorderLayout.CENTER);
        root.add(foot, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void populateInitialState() {
        // Lock company dropdown if logged in as COMPANY recruiter
        if (currentUser != null && currentUser.getRole() == UserRole.COMPANY && currentUser.getCompanyId() != null) {
            for (int i = 0; i < companyCombo.getItemCount(); i++) {
                if (companyCombo.getItemAt(i).id().equals(currentUser.getCompanyId())) {
                    companyCombo.setSelectedIndex(i);
                    break;
                }
            }
            companyCombo.setEnabled(false);
        }

        if (editingTarget == null) {
            // Sensible defaults for branch + 2 skills
            for (String def : List.of("CSE", "CSE - AIML", "CSE - DS", "IT")) {
                if (branchChecks.containsKey(def)) branchChecks.get(def).setSelected(true);
            }
            for (int i = 0; i < Math.min(2, allSkills.size()); i++) {
                reqSkills.add(new DriveRequiredSkill(allSkills.get(i).getId(), 3, true));
            }
            rebuildReqSkillsUi();
            return;
        }

        for (int i = 0; i < companyCombo.getItemCount(); i++) {
            if (companyCombo.getItemAt(i).id().equals(editingTarget.getCompanyId())) {
                companyCombo.setSelectedIndex(i);
                break;
            }
        }
        jobRoleField.setText(nonNull(editingTarget.getJobRole()));
        jobDescArea.setText(nonNull(editingTarget.getJobDescription()));
        packageField.setText(String.valueOf(editingTarget.getSalaryPackageLpa()));
        minCgpaField.setText(String.valueOf(editingTarget.getMinCgpa()));
        maxBacklogsField.setText(String.valueOf(editingTarget.getMaxBacklogsAllowed()));
        gradYearField.setText(String.valueOf(editingTarget.getTargetGraduationYear()));
        vacanciesField.setText(String.valueOf(editingTarget.getAvailablePositions()));
        driveDateField.setText(nonNull(editingTarget.getDriveDate()));
        deadlineField.setText(nonNull(editingTarget.getApplicationDeadline()));
        if (editingTarget.getStatus() != null) statusCombo.setSelectedItem(editingTarget.getStatus());
        selectionProcessField.setText(nonNull(editingTarget.getSelectionProcess()));

        if (editingTarget.getEligibleBranches() != null) {
            for (String br : editingTarget.getEligibleBranches()) {
                if (branchChecks.containsKey(br)) {
                    branchChecks.get(br).setSelected(true);
                } else {
                    String norm = DeptNormalizer.normalizeDeptCode(br);
                    for (Map.Entry<String, JCheckBox> entry : branchChecks.entrySet()) {
                        if (DeptNormalizer.normalizeDeptCode(entry.getKey()).equalsIgnoreCase(norm)) {
                            entry.getValue().setSelected(true);
                        }
                    }
                }
            }
        }

        if (editingTarget.getRequiredSkills() != null) {
            for (DriveRequiredSkill r : editingTarget.getRequiredSkills()) {
                reqSkills.add(new DriveRequiredSkill(r.getSkillId(), r.getMinProficiency(), r.isMandatory()));
            }
        }
        rebuildReqSkillsUi();
    }

    private void addSelectedSkill() {
        SkillItem item = (SkillItem) addSkillCombo.getSelectedItem();
        if (item == null) return;
        for (DriveRequiredSkill existing : reqSkills) {
            if (existing.getSkillId().equals(item.skill().getId())) {
                return;
            }
        }
        reqSkills.add(new DriveRequiredSkill(item.skill().getId(), 3, true));
        rebuildReqSkillsUi();
    }

    private void rebuildReqSkillsUi() {
        reqSkillsPanel.removeAll();
        if (reqSkills.isEmpty()) {
            JLabel empty = new JLabel("No skill cutoffs added yet (only academic eligibility will apply).");
            empty.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            empty.setForeground(ThemeManager.mutedText());
            reqSkillsPanel.add(empty);
        } else {
            for (DriveRequiredSkill req : reqSkills) {
                Skill meta = null;
                for (Skill s : allSkills) {
                    if (s.getId().equals(req.getSkillId())) {
                        meta = s;
                        break;
                    }
                }
                String skName = meta != null ? meta.getName() : req.getSkillId();

                JPanel row = new JPanel(new BorderLayout(8, 0));
                row.setBackground(ThemeManager.cardSubtleBg());
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(ThemeManager.cardBorder()),
                        new EmptyBorder(6, 10, 6, 10)
                ));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

                JLabel nameLbl = new JLabel(skName);
                nameLbl.setFont(ThemeManager.font(Font.BOLD, 12));
                nameLbl.setForeground(ThemeManager.primaryText());

                JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
                controls.setOpaque(false);

                JComboBox<String> levelBox = new JComboBox<>(new String[]{
                        "1: Beginner", "2: Elementary", "3: Intermediate", "4: Advanced", "5: Expert"
                });
                int idx = Math.max(0, Math.min(4, req.getMinProficiency() - 1));
                levelBox.setSelectedIndex(idx);
                levelBox.addActionListener(e -> req.setMinProficiency(levelBox.getSelectedIndex() + 1));

                JCheckBox mandBox = new JCheckBox("Mandatory", req.isMandatory());
                mandBox.setOpaque(false);
                mandBox.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
                mandBox.addActionListener(e -> req.setMandatory(mandBox.isSelected()));

                ActionButton removeBtn = new ActionButton("✕", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, null, 6);
                removeBtn.setMargin(new Insets(2, 8, 2, 8));
                removeBtn.addActionListener(e -> {
                    reqSkills.remove(req);
                    rebuildReqSkillsUi();
                });

                controls.add(new JLabel("Min Level:"));
                controls.add(levelBox);
                controls.add(mandBox);
                controls.add(removeBtn);

                row.add(nameLbl, BorderLayout.CENTER);
                row.add(controls, BorderLayout.EAST);
                reqSkillsPanel.add(row);
                reqSkillsPanel.add(Box.createVerticalStrut(4));
            }
        }
        reqSkillsPanel.revalidate();
        reqSkillsPanel.repaint();
    }

    private void handleSave() {
        CompanyItem comp = (CompanyItem) companyCombo.getSelectedItem();
        if (comp == null) {
            errorBanner.showError("No Partner Company available. Register a partner company first.");
            return;
        }
        String role = jobRoleField.getText().trim();
        if (role.isEmpty()) {
            errorBanner.showError("Job Role Title is required.");
            return;
        }

        List<String> selectedBranches = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> e : branchChecks.entrySet()) {
            if (e.getValue().isSelected()) selectedBranches.add(e.getKey());
        }
        if (selectedBranches.isEmpty()) {
            errorBanner.showError("Select at least one eligible engineering branch.");
            return;
        }

        double pkg, cgpa;
        int backlogs, gradYear, vacancies;
        try {
            pkg = Double.parseDouble(packageField.getText().trim());
            cgpa = Double.parseDouble(minCgpaField.getText().trim());
            backlogs = Integer.parseInt(maxBacklogsField.getText().trim());
            gradYear = Integer.parseInt(gradYearField.getText().trim());
            vacancies = Integer.parseInt(vacanciesField.getText().trim());
        } catch (NumberFormatException ex) {
            errorBanner.showError("Enter valid numeric values for Package, CGPA, Backlogs, Passing Batch, and Vacancies.");
            return;
        }

        if (pkg <= 0 || cgpa < 0 || cgpa > 10 || backlogs < 0 || vacancies < 1) {
            errorBanner.showError("Check ranges: Package > 0, CGPA 0–10, Backlogs >= 0, Vacancies >= 1.");
            return;
        }

        String dDate = driveDateField.getText().trim();
        String deadDate = deadlineField.getText().trim();
        try {
            LocalDate.parse(dDate);
            LocalDate.parse(deadDate);
        } catch (Exception ex) {
            errorBanner.showError("Dates must use YYYY-MM-DD format (e.g. 2026-11-25).");
            return;
        }

        PlacementDrive d = new PlacementDrive();
        if (editingTarget != null) d.setId(editingTarget.getId());
        d.setCompanyId(comp.id());
        d.setCompanyName(comp.name());
        d.setJobRole(role);
        d.setJobDescription(jobDescArea.getText().trim());
        d.setSalaryPackageLpa(pkg);
        d.setMinCgpa(cgpa);
        d.setMaxBacklogsAllowed(backlogs);
        d.setTargetGraduationYear(gradYear);
        d.setAvailablePositions(vacancies);
        d.setDriveDate(dDate);
        d.setApplicationDeadline(deadDate);
        d.setStatus(statusCombo.getSelectedItem() != null ? statusCombo.getSelectedItem().toString() : "OPEN");
        d.setSelectionProcess(selectionProcessField.getText().trim());
        d.setEligibleBranches(selectedBranches);
        d.setRequiredSkills(new ArrayList<>(reqSkills));

        this.resultDrive = d;
        dispose();
    }

    private JPanel labeled(String text, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        l.setForeground(ThemeManager.secondaryText());
        if (!(field instanceof JScrollPane)) {
            field.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
            field.setPreferredSize(new Dimension(140, 34));
        }
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
