package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.RegistrationApprovalService;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Administrator Privilege Request Dialog
 * Allows authorized portal users to request promotion to Administrator or Placement Officer.
 * Explicitly addressed to the Principal, Dr. K. Sagar, who holds exclusive approval authority.
 */
public class AdminRequestDialog extends JDialog {
    private final RegistrationApprovalService approvalService = new RegistrationApprovalService();
    private final User currentUser;
    private boolean submitted = false;

    private JComboBox<String> cmbRole;
    private JTextArea txtReason;
    private JLabel lblError;

    public AdminRequestDialog(Window owner, User currentUser) {
        super(owner, "Request Administrator Privileges · GCET Placement Hub", ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        initUI();
    }

    private void initUI() {
        setSize(540, 520);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 22, 16, 22));

        JLabel title = new JLabel("Administrator Privilege Request");
        title.setFont(new Font("Segoe UI", Font.BOLD, 17));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Addressed to: Principal Dr. K. Sagar · Executive Authority");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Content
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 24, 16, 24));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        // Info Banner
        JPanel infoBanner = new JPanel(new BorderLayout());
        infoBanner.setBackground(new Color(0xD9, 0x77, 0x06, 0x18));
        infoBanner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.AMBER_WARN, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        JLabel lblNotice = new JLabel("<html><b>Principal Exclusive Authority:</b> Under GCET Placement Hub governance, only Principal <b>Dr. K. Sagar</b> has constitutional authority to grant or revoke administrative roles.</html>");
        lblNotice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblNotice.setForeground(Theme.MIDNIGHT_NAVY);
        infoBanner.add(lblNotice, BorderLayout.CENTER);

        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        body.add(infoBanner, gbc);

        lblError = new JLabel("");
        lblError.setForeground(Theme.DANGER_ROSE);
        lblError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gbc.gridy = row++;
        body.add(lblError, gbc);
        gbc.gridwidth = 1;

        // Applicant Details
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        body.add(createLabel("Applicant:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        JLabel lblName = new JLabel(currentUser != null ? currentUser.getFullName() + " (" + currentUser.getUsername() + ")" : "Current User");
        lblName.setFont(Theme.FONT_BOLD);
        body.add(lblName, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        body.add(createLabel("Current Role & Dept:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        JLabel lblRole = new JLabel((currentUser != null ? currentUser.getRole().name() : "USER") + " · " + (currentUser != null && currentUser.getDepartment() != null ? currentUser.getDepartment() : "General"));
        lblRole.setFont(Theme.FONT_REGULAR);
        body.add(lblRole, gbc);
        row++;

        // Requested Role
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        body.add(createLabel("Requested Role: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        String[] roles = {"ADMIN", "PLACEMENT_OFFICER"};
        cmbRole = new JComboBox<>(roles);
        body.add(cmbRole, gbc);
        row++;

        // Justification
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.35;
        body.add(createLabel("Official Justification: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.65;
        txtReason = new JTextArea(4, 20);
        txtReason.setLineWrap(true);
        txtReason.setWrapStyleWord(true);
        JScrollPane scrollReason = new JScrollPane(txtReason);
        body.add(scrollReason, gbc);

        add(body, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 14));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSubmit = Theme.createPrimaryButton("Submit Request to Principal");
        btnSubmit.addActionListener(e -> submitRequest());

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

    private void submitRequest() {
        lblError.setText("");
        String reason = txtReason.getText().trim();
        if (reason.length() < 10) {
            lblError.setText("Please provide a justification (at least 10 characters).");
            return;
        }

        try {
            String role = (String) cmbRole.getSelectedItem();
            approvalService.requestAdminPrivilege(currentUser, role, reason);

            JOptionPane.showMessageDialog(this,
                "Administrator Privilege Request submitted successfully!\n\n" +
                "Target Role: " + role + "\n" +
                "Routing: Directly dispatched to Principal Dr. K. Sagar's Approval Queue.\n\n" +
                "You will receive administrative permissions once Principal approval is confirmed.",
                "Request Dispatched to Principal", JOptionPane.INFORMATION_MESSAGE);

            submitted = true;
            dispose();
        } catch (StudentService.ValidationException ex) {
            lblError.setText(ex.getMessage());
        } catch (Exception ex) {
            lblError.setText("Error: " + ex.getMessage());
        }
    }

    public boolean isSubmitted() {
        return submitted;
    }
}
