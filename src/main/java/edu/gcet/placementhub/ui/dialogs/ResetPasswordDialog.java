package edu.gcet.placementhub.ui.dialogs;

import edu.gcet.placementhub.model.User;
import edu.gcet.placementhub.service.StudentService;
import edu.gcet.placementhub.service.UserService;
import edu.gcet.placementhub.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * GCET PLACEMENT HUB - Password Reset Dialog
 * Secure password change with PBKDF2 hashing.
 */
public class ResetPasswordDialog extends JDialog {
    private final UserService userService;
    private final User currentUser;
    private final User targetUser;
    private boolean success = false;

    private JPasswordField txtNewPassword;
    private JPasswordField txtConfirmPassword;
    private JCheckBox chkShow;
    private JLabel lblError;

    public ResetPasswordDialog(Window owner, User currentUser, User targetUser) {
        super(owner, "Reset Password · " + targetUser.getUsername(), ModalityType.APPLICATION_MODAL);
        this.currentUser = currentUser;
        this.targetUser = targetUser;
        this.userService = new UserService();

        initUI();
    }

    private void initUI() {
        setSize(440, 360);
        setLocationRelativeTo(getOwner());
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.MIDNIGHT_NAVY);
        header.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel title = new JLabel("Reset User Password");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("User: " + targetUser.getFullName() + " (" + targetUser.getUsername() + ")");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sub.setForeground(Theme.CYAN_ACCENT);

        header.add(title, BorderLayout.NORTH);
        header.add(sub, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(16, 20, 16, 20));
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

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.4;
        form.add(new JLabel("New Password: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.6;
        txtNewPassword = new JPasswordField();
        form.add(txtNewPassword, gbc);
        row++;

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.4;
        form.add(new JLabel("Confirm Password: *"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.6;
        txtConfirmPassword = new JPasswordField();
        form.add(txtConfirmPassword, gbc);
        row++;

        gbc.gridx = 1; gbc.gridy = row++;
        chkShow = new JCheckBox("Show Password");
        chkShow.setOpaque(false);
        chkShow.addActionListener(e -> {
            char echo = chkShow.isSelected() ? (char) 0 : '•';
            txtNewPassword.setEchoChar(echo);
            txtConfirmPassword.setEchoChar(echo);
        });
        form.add(chkShow, gbc);

        add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setBackground(Theme.LIGHT_BG);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.SUBTLE_BORDER));

        JButton btnCancel = Theme.createSecondaryButton("Cancel");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSubmit = Theme.createPrimaryButton("Update Password");
        btnSubmit.addActionListener(e -> doReset());

        footer.add(btnCancel);
        footer.add(btnSubmit);
        add(footer, BorderLayout.SOUTH);
    }

    private void doReset() {
        lblError.setText("");
        String p1 = new String(txtNewPassword.getPassword());
        String p2 = new String(txtConfirmPassword.getPassword());

        if (p1.trim().length() < 6) {
            lblError.setText("Password must be at least 6 characters long.");
            return;
        }

        if (!p1.equals(p2)) {
            lblError.setText("Passwords do not match.");
            return;
        }

        try {
            userService.resetPassword(currentUser, targetUser.getId(), p1);
            JOptionPane.showMessageDialog(this,
                    "Password for '" + targetUser.getUsername() + "' updated successfully.",
                    "Password Changed", JOptionPane.INFORMATION_MESSAGE);
            success = true;
            dispose();
        } catch (StudentService.ValidationException | SecurityException ex) {
            lblError.setText(ex.getMessage());
        } catch (Exception ex) {
            lblError.setText("Error: " + ex.getMessage());
        }
    }

    public boolean isSuccess() {
        return success;
    }
}
