package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.service.RegistrationApprovalService;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Company / Recruiter Registration Dialog
 * Allows prospective campus recruiters to register their corporate profile.
 * Requires College Administrator approval before posting drives or viewing candidates.
 */
public class CompanyRegistrationDialog extends JDialog {
    private final RegistrationApprovalService approvalService = new RegistrationApprovalService();
    private boolean registered = false;

    private JTextField txtCompanyName;
    private JComboBox<String> cmbIndustry;
    private JTextField txtWebsite;
    private JTextField txtLocation;
    private JTextField txtHrName;
    private JTextField txtHrEmail;
    private JTextField txtHrPhone;
    private JTextArea txtDescription;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JPasswordField txtConfirmPassword;
    private JLabel lblError;

    public CompanyRegistrationDialog(Window owner) {
        super(owner, "Corporate Recruiter Registration · GCET Placement Hub", ModalityType.APPLICATION_MODAL);
        initUI();
    }

    private void initUI() {
        setSize(580, 750);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));

        JLabel title = new JLabel("Corporate Recruiter Partner Registration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel(AppConfig.COLLEGE_NAME + " · Industry Relations & Placement Cell");
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
        JLabel lblNotice = new JLabel("<html><b>Institutional Verification:</b> Recruiter accounts are reviewed by the GCET Placement Cell & College Administration. Drive scheduling and candidate search unlock immediately upon approval.</html>");
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

        // Company Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Company Name: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtCompanyName = new JTextField();
        form.add(txtCompanyName, gbc);
        row++;

        // Industry
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Industry Sector: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] industries = {
            "Information Technology & Services",
            "Software & Product Development",
            "Core Engineering & Manufacturing",
            "Electronics & Semiconductor",
            "Banking, Financial Services & Insurance (BFSI)",
            "Data Analytics & AI",
            "Consulting & Professional Services",
            "Telecommunications & Cloud",
            "Healthcare & Biotech"
        };
        cmbIndustry = new JComboBox<>(industries);
        cmbIndustry.setEditable(true);
        form.add(cmbIndustry, gbc);
        row++;

        // Website
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Website URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtWebsite = new JTextField("https://");
        form.add(txtWebsite, gbc);
        row++;

        // Location / HQ
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Headquarters / Office:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtLocation = new JTextField("Hyderabad, Telangana");
        form.add(txtLocation, gbc);
        row++;

        // HR Contact Person
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("HR / Recruiter Name: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtHrName = new JTextField();
        form.add(txtHrName, gbc);
        row++;

        // HR Email
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Official HR Email: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtHrEmail = new JTextField();
        form.add(txtHrEmail, gbc);
        row++;

        // HR Phone
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Contact Phone:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtHrPhone = new JTextField();
        form.add(txtHrPhone, gbc);
        row++;

        // Company Brief / About
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Company Overview:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtDescription = new JTextArea(2, 20);
        txtDescription.setLineWrap(true);
        txtDescription.setWrapStyleWord(true);
        JScrollPane scrollDesc = new JScrollPane(txtDescription);
        scrollDesc.setPreferredSize(new Dimension(100, 52));
        form.add(scrollDesc, gbc);
        row++;

        // Username
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        form.add(createLabel("Recruiter Username: *"), gbc);
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

        JButton btnSubmit = Theme.createPrimaryButton("Submit Corporate Registration");
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
            approvalService.registerCompany(
                txtCompanyName.getText().trim(),
                (String) cmbIndustry.getSelectedItem(),
                txtWebsite.getText().trim(),
                txtLocation.getText().trim(),
                txtHrName.getText().trim(),
                txtHrEmail.getText().trim(),
                txtHrPhone.getText().trim(),
                txtDescription.getText().trim(),
                txtUsername.getText().trim(),
                p1
            );

            JOptionPane.showMessageDialog(this,
                "Company registration submitted successfully!\n\n" +
                "Organization: " + txtCompanyName.getText().trim() + "\n" +
                "Status: PENDING ADMINISTRATOR APPROVAL\n\n" +
                "The GCET Career Development & Placement Cell has received your request.\n" +
                "Upon administrative clearance, your account will be activated and you will be notified.",
                "Recruiter Registered", JOptionPane.INFORMATION_MESSAGE);

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
