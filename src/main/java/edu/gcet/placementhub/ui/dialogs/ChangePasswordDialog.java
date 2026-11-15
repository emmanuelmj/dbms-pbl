package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.auth.PasswordUtil;
import edu.gcet.placementhub.config.AppConfig;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Change Password Dialog
 * Mandatory on first login for bootstrap accounts (Dr. K. Sagar, Mr. R. Harishchandra Reddy,
 * Mr. G.R. Ravinder Reddy) and whenever password reset is required.
 */
public class ChangePasswordDialog extends JDialog {
    private final UserDao userDao = new UserDao();
    private final User user;
    private boolean changed = false;

    private JPasswordField txtNewPass;
    private JPasswordField txtConfirmPass;
    private JLabel lblError;

    public ChangePasswordDialog(Window owner, User user, boolean isMandatoryFirstLogin) {
        super(owner, (isMandatoryFirstLogin ? "Mandatory Password Setup · " : "Change Password · ") + AppConfig.APP_NAME, ModalityType.APPLICATION_MODAL);
        this.user = user;
        initUI(isMandatoryFirstLogin);
    }

    private void initUI(boolean isMandatory) {
        setSize(460, 360);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        if (isMandatory) {
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        }

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel(isMandatory ? "First-Time Security Setup" : "Update Password");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("User: " + (user != null ? user.getFullName() + " (" + user.getUsername() + ")" : ""));
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        // Body
        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 20, 16, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        int row = 0;

        if (isMandatory) {
            JLabel lblWarn = new JLabel("<html><b>Security Notice:</b> You are logging in with bootstrap credentials. For institutional security, you must define a new personal password before accessing the system.</html>");
            lblWarn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblWarn.setForeground(Theme.DARK_TEXT);
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            body.add(lblWarn, gbc);
        }

        lblError = new JLabel("");
        lblError.setForeground(Theme.DANGER_ROSE);
        lblError.setFont(new Font("Segoe UI", Font.BOLD, 11));
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        body.add(lblError, gbc);
        gbc.gridwidth = 1;

        // New Password
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.4;
        JLabel l1 = new JLabel("New Password: *");
        l1.setFont(Theme.FONT_BOLD);
        body.add(l1, gbc);
        gbc.gridx = 1; gbc.weightx = 0.6;
        txtNewPass = new JPasswordField();
        body.add(txtNewPass, gbc);
        row++;

        // Confirm Password
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.4;
        JLabel l2 = new JLabel("Confirm Password: *");
        l2.setFont(Theme.FONT_BOLD);
        body.add(l2, gbc);
        gbc.gridx = 1; gbc.weightx = 0.6;
        txtConfirmPass = new JPasswordField();
        body.add(txtConfirmPass, gbc);

        add(body, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        if (!isMandatory) {
            JButton btnCancel = Theme.createSecondaryButton("Cancel");
            btnCancel.addActionListener(e -> dispose());
            footer.add(btnCancel);
        }

        JButton btnSave = Theme.createPrimaryButton("Update & Proceed");
        btnSave.addActionListener(e -> savePassword());
        footer.add(btnSave);

        add(footer, BorderLayout.SOUTH);
    }

    private void savePassword() {
        lblError.setText("");
        String p1 = new String(txtNewPass.getPassword());
        String p2 = new String(txtConfirmPass.getPassword());

        if (p1.length() < 6) {
            lblError.setText("Password must be at least 6 characters.");
            return;
        }
        if (!p1.equals(p2)) {
            lblError.setText("Passwords do not match.");
            return;
        }

        try {
            boolean ok = userDao.resetPassword(user.getId(), p1);
            if (ok) {
                userDao.updateMustChangePassword(user.getId(), false);
                user.setMustChangePassword(false);
                changed = true;
                JOptionPane.showMessageDialog(this,
                    "Password updated successfully!\nYou may now continue to your workspace.",
                    "Password Updated", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            } else {
                lblError.setText("Failed to update password in database.");
            }
        } catch (Exception ex) {
            lblError.setText("Error: " + ex.getMessage());
        }
    }

    public boolean isChanged() {
        return changed;
    }
}
