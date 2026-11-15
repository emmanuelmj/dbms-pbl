package edu.gcet.placementhub.ui.companies;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.Company;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Modal JDialog for registering a new Partner Company or editing an existing Company profile.
 */
public class CompanyFormDialog extends JDialog {

    private static final String[] HIRING_TIERS = {
            "Standard IT",
            "Tier 1 (Dream)",
            "Tier 2 (Super Dream)",
            "Core"
    };

    private final Company editingTarget;
    private Company resultCompany;

    private JTextField nameField;
    private JTextField industryField;
    private JComboBox<String> tierCombo;
    private JTextField websiteField;
    private JTextField locationField;
    private JTextArea descriptionArea;
    private JTextField hrNameField;
    private JTextField hrEmailField;
    private JTextField hrPhoneField;
    private AlertBanner errorBanner;

    public CompanyFormDialog(Window owner, Company editingTarget) {
        super(owner,
                editingTarget == null ? "Register Partner Company" : "Edit Company: " + editingTarget.getName(),
                ModalityType.APPLICATION_MODAL);
        this.editingTarget = editingTarget;

        setSize(610, 580);
        setLocationRelativeTo(owner);
        setResizable(false);
        buildUi();
        populateIfEditing();
    }

    public Company showDialog() {
        setVisible(true);
        return resultCompany;
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(ThemeManager.cardBg());
        root.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel headCol = new JPanel();
        headCol.setOpaque(false);
        headCol.setLayout(new BoxLayout(headCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(editingTarget == null
                ? "Register Campus Recruitment Partner"
                : "Edit Company Profile: " + editingTarget.getName());
        title.setFont(ThemeManager.font(Font.BOLD, 16));
        title.setForeground(ThemeManager.primaryText());
        JLabel sub = new JLabel("Persists directly into MySQL `companies` table with recruiter contact metadata.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sub.setForeground(ThemeManager.mutedText());
        headCol.add(title);
        headCol.add(Box.createVerticalStrut(3));
        headCol.add(sub);

        errorBanner = new AlertBanner();

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 0.5;

        nameField = new JTextField();
        nameField.putClientProperty("JTextField.placeholderText", "e.g. Amazon Web Services (AWS)");
        industryField = new JTextField("Information Technology");
        tierCombo = new JComboBox<>(HIRING_TIERS);
        websiteField = new JTextField("https://");
        locationField = new JTextField("Hyderabad, Telangana");
        descriptionArea = new JTextArea(3, 30);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(ThemeManager.font(Font.PLAIN, 12.5f));

        hrNameField = new JTextField();
        hrEmailField = new JTextField();
        hrPhoneField = new JTextField();

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        form.add(labeled("Company Name *", nameField), gbc);

        gbc.gridy = 1; gbc.gridwidth = 1; gbc.gridx = 0;
        form.add(labeled("Industry Domain *", industryField), gbc);
        gbc.gridx = 1;
        form.add(labeled("Hiring Tier *", tierCombo), gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        form.add(labeled("Website URL", websiteField), gbc);
        gbc.gridx = 1;
        form.add(labeled("Office Location", locationField), gbc);

        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(200, 74));
        form.add(labeled("Company Overview & Culture", descScroll), gbc);

        gbc.gridy = 4; gbc.gridwidth = 1; gbc.gridx = 0;
        form.add(labeled("HR Recruiter Name", hrNameField), gbc);
        gbc.gridx = 1;
        form.add(labeled("HR Contact Email", hrEmailField), gbc);

        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2;
        form.add(labeled("HR Phone Number", hrPhoneField), gbc);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        errorBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        mid.add(errorBanner);
        mid.add(Box.createVerticalStrut(4));
        mid.add(form);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        foot.setOpaque(false);
        ActionButton cancelBtn = new ActionButton("Cancel", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        cancelBtn.addActionListener(e -> {
            resultCompany = null;
            dispose();
        });
        ActionButton saveBtn = new ActionButton(
                editingTarget == null ? "Register Company" : "Save Changes",
                ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 10
        );
        saveBtn.addActionListener(e -> handleSave());
        foot.add(cancelBtn);
        foot.add(saveBtn);

        root.add(headCol, BorderLayout.NORTH);
        root.add(mid, BorderLayout.CENTER);
        root.add(foot, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void populateIfEditing() {
        if (editingTarget == null) return;
        nameField.setText(nonNull(editingTarget.getName()));
        industryField.setText(nonNull(editingTarget.getIndustry()));
        if (editingTarget.getTier() != null) {
            boolean found = false;
            for (int i = 0; i < tierCombo.getItemCount(); i++) {
                if (tierCombo.getItemAt(i).equalsIgnoreCase(editingTarget.getTier())) {
                    tierCombo.setSelectedIndex(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                tierCombo.addItem(editingTarget.getTier());
                tierCombo.setSelectedItem(editingTarget.getTier());
            }
        }
        websiteField.setText(nonNull(editingTarget.getWebsite()));
        locationField.setText(nonNull(editingTarget.getLocation()));
        descriptionArea.setText(nonNull(editingTarget.getDescription()));
        hrNameField.setText(nonNull(editingTarget.getHrName()));
        hrEmailField.setText(nonNull(editingTarget.getHrEmail()));
        hrPhoneField.setText(nonNull(editingTarget.getHrPhone()));
    }

    private void handleSave() {
        String name = nameField.getText().trim();
        String industry = industryField.getText().trim();
        if (name.isEmpty() || industry.isEmpty()) {
            errorBanner.showError("Company Name and Industry Domain are required.");
            return;
        }

        Company c = new Company();
        if (editingTarget != null) {
            c.setId(editingTarget.getId());
            c.setStatus(editingTarget.getStatus());
            c.setApprovalStatus(editingTarget.getApprovalStatus());
        } else {
            c.setStatus("ACTIVE");
            c.setApprovalStatus("ACTIVE");
        }
        c.setName(name);
        c.setIndustry(industry);
        c.setTier(tierCombo.getSelectedItem() != null ? tierCombo.getSelectedItem().toString() : "Standard IT");
        c.setWebsite(websiteField.getText().trim());
        c.setLocation(locationField.getText().trim());
        c.setDescription(descriptionArea.getText().trim());
        c.setHrName(hrNameField.getText().trim());
        c.setHrEmail(hrEmailField.getText().trim());
        c.setHrPhone(hrPhoneField.getText().trim());

        this.resultCompany = c;
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
            field.setPreferredSize(new Dimension(180, 34));
        }
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }
}
