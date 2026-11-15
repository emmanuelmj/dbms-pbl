package edu.gcet.placementhub.ui.settings;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.GovernanceController;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.ui.audit.AuditLogsPanel;
import edu.gcet.placementhub.ui.diagnostics.DatabaseDiagnosticsPanel;
import edu.gcet.placementhub.utils.UiComponents.*;
import static edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Phase 5 Institutional Settings Hub:
 *  - Appearance & Theme (Live FlatLaf Light/Dark Toggle + GCET Color Palette)
 *  - Profile & Security (Profile Update + Password Change verifying current password)
 *  - User Accounts & RBAC (Admin-only User Directory, Create & Safe Delete)
 *  - Database Diagnostics (Live read-only 17-table & MySQL health check)
 *  - Audit Logs (RBAC-scoped audit trail & CSV export)
 *  - Application & Session Info (Zero credential/hash exposure)
 */
public class SettingsPanel extends JPanel {

    private enum SettingsTab {
        APPEARANCE, PROFILE_PASSWORD, USER_ACCOUNTS, DIAGNOSTICS, AUDIT_LOGS, APP_SESSION
    }

    private final GovernanceController controller = new GovernanceController();
    private final Runnable onThemeToggled;
    private final Consumer<User> onUserProfileUpdated;
    private final Runnable onOpenDbConfigDialog;
    private final DatabaseDiagnosticsPanel embeddedDiagnosticsPanel;
    private final AuditLogsPanel embeddedAuditPanel;

    private SettingsTab activeTab = SettingsTab.APPEARANCE;
    private final JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final JPanel contentSlot = new JPanel(new BorderLayout());

    public SettingsPanel(
            Runnable onThemeToggled,
            Consumer<User> onUserProfileUpdated,
            Consumer<DbStatus> onDbStatusRefreshed,
            Runnable onOpenDbConfigDialog
    ) {
        this.onThemeToggled = onThemeToggled;
        this.onUserProfileUpdated = onUserProfileUpdated;
        this.onOpenDbConfigDialog = onOpenDbConfigDialog;
        this.embeddedDiagnosticsPanel = new DatabaseDiagnosticsPanel(onDbStatusRefreshed);
        this.embeddedAuditPanel = new AuditLogsPanel();

        setLayout(new BorderLayout(0, 12));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        RoundedCard hdr = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        hdr.setLayout(new BorderLayout(12, 0));
        hdr.setBorder(new EmptyBorder(14, 18, 14, 18));
        hdr.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel hdrText = new JPanel();
        hdrText.setOpaque(false);
        hdrText.setLayout(new BoxLayout(hdrText, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("⚙  Settings, Security, Diagnostics & Governance Tools");
        title.setFont(ThemeManager.font(Font.BOLD, 18));
        title.setForeground(ThemeManager.primaryText());
        JLabel sub = new JLabel("Manage desktop theme appearance, profile & password security, MySQL JDBC diagnostics, audit logs, and session telemetry.");
        sub.setFont(ThemeManager.font(Font.PLAIN, 12));
        sub.setForeground(ThemeManager.mutedText());
        hdrText.add(title);
        hdrText.add(Box.createVerticalStrut(3));
        hdrText.add(sub);
        hdr.add(hdrText, BorderLayout.CENTER);

        top.add(hdr);
        top.add(Box.createVerticalStrut(10));

        tabBar.setOpaque(false);
        tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(tabBar);

        add(top, BorderLayout.NORTH);
        contentSlot.setOpaque(false);
        add(contentSlot, BorderLayout.CENTER);
    }

    public void loadData() {
        renderTabsAndBody();
    }

    public void openDiagnosticsTab() {
        this.activeTab = SettingsTab.DIAGNOSTICS;
        renderTabsAndBody();
    }

    public void openAuditLogsTab() {
        this.activeTab = SettingsTab.AUDIT_LOGS;
        renderTabsAndBody();
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        embeddedDiagnosticsPanel.refreshTheme();
        embeddedAuditPanel.refreshTheme();
        renderTabsAndBody();
    }

    private void renderTabsAndBody() {
        User actor = AppSession.getInstance().getCurrentUser();
        boolean isAdmin = actor != null && actor.getRole().isAdminRole();

        tabBar.removeAll();
        tabBar.add(tabBtn("Appearance & Theme", SettingsTab.APPEARANCE));
        tabBar.add(tabBtn("Profile & Password", SettingsTab.PROFILE_PASSWORD));
        if (isAdmin) {
            tabBar.add(tabBtn("User Accounts (Admin)", SettingsTab.USER_ACCOUNTS));
        } else if (activeTab == SettingsTab.USER_ACCOUNTS) {
            activeTab = SettingsTab.APPEARANCE;
        }
        tabBar.add(tabBtn("Database Diagnostics", SettingsTab.DIAGNOSTICS));
        tabBar.add(tabBtn("Audit Logs", SettingsTab.AUDIT_LOGS));
        tabBar.add(tabBtn("App & Session Info", SettingsTab.APP_SESSION));
        tabBar.revalidate();
        tabBar.repaint();

        contentSlot.removeAll();
        switch (activeTab) {
            case APPEARANCE -> contentSlot.add(buildAppearanceView(), BorderLayout.CENTER);
            case PROFILE_PASSWORD -> contentSlot.add(buildProfileAndPasswordView(actor), BorderLayout.CENTER);
            case USER_ACCOUNTS -> contentSlot.add(buildUserAccountsView(actor), BorderLayout.CENTER);
            case DIAGNOSTICS -> {
                contentSlot.add(embeddedDiagnosticsPanel, BorderLayout.CENTER);
                embeddedDiagnosticsPanel.loadData();
            }
            case AUDIT_LOGS -> {
                contentSlot.add(embeddedAuditPanel, BorderLayout.CENTER);
                embeddedAuditPanel.loadData();
            }
            case APP_SESSION -> contentSlot.add(buildAppAndSessionView(actor), BorderLayout.CENTER);
        }
        contentSlot.revalidate();
        contentSlot.repaint();
    }

    private ActionButton tabBtn(String label, SettingsTab tab) {
        boolean sel = activeTab == tab;
        ActionButton b = new ActionButton(
                label,
                sel ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardBg(),
                sel ? ThemeManager.BLUE_HOVER : ThemeManager.cardSubtleBg(),
                sel ? Color.WHITE : ThemeManager.primaryText(),
                sel ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardBorder(),
                10
        );
        b.setMargin(new Insets(7, 12, 7, 12));
        b.addActionListener(e -> {
            activeTab = tab;
            renderTabsAndBody();
        });
        return b;
    }

    // -------------------------------------------------------------------------
    // TAB 1: APPEARANCE & THEME
    // -------------------------------------------------------------------------

    private JPanel buildAppearanceView() {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 22, 20, 22));

        JLabel title = new JLabel("Desktop Appearance & FlatLaf Theme Mode");
        title.setFont(ThemeManager.font(Font.BOLD, 16));
        title.setForeground(ThemeManager.primaryText());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel("Switch instantly between GCET Institutional Light Mode and Midnight Navy Dark Mode across every workspace screen.");
        desc.setFont(ThemeManager.font(Font.PLAIN, 12));
        desc.setForeground(ThemeManager.mutedText());
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);

        RoundedCard modeStrip = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        modeStrip.setLayout(new BorderLayout(14, 0));
        modeStrip.setBorder(new EmptyBorder(14, 16, 14, 16));
        modeStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeStrip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

        boolean dark = ThemeManager.isDarkMode();
        JLabel curLbl = new JLabel("<html><b>Active Theme:</b> "
                + (dark ? "Midnight Navy Dark (FlatDarkLaf)" : "Clean Institutional Light (FlatLightLaf)")
                + "</html>");
        curLbl.setFont(ThemeManager.font(Font.PLAIN, 13));
        curLbl.setForeground(ThemeManager.primaryText());

        ActionButton toggleBtn = new ActionButton(
                dark ? "☀  Switch to Light Theme" : "☾  Switch to Dark Theme",
                ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10
        );
        toggleBtn.addActionListener(e -> {
            if (onThemeToggled != null) {
                onThemeToggled.run();
            } else {
                ThemeManager.toggleTheme();
                refreshTheme();
            }
        });
        modeStrip.add(curLbl, BorderLayout.CENTER);
        modeStrip.add(toggleBtn, BorderLayout.EAST);

        JLabel palTitle = new JLabel("GCET Institutional Design System Tokens");
        palTitle.setFont(ThemeManager.font(Font.BOLD, 13));
        palTitle.setForeground(ThemeManager.primaryText());
        palTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel swatches = new JPanel(new GridLayout(1, 4, 12, 12));
        swatches.setOpaque(false);
        swatches.setAlignmentX(Component.LEFT_ALIGNMENT);
        swatches.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        swatches.add(swatchCard("Institutional Navy", "#101A35", ThemeManager.NAVY_MIDNIGHT));
        swatches.add(swatchCard("Electric Blue Action", "#2684FF", ThemeManager.BLUE_ELECTRIC));
        swatches.add(swatchCard("GCTC Cyan Accent", "#32C5F4", ThemeManager.CYAN_ACCENT));
        swatches.add(swatchCard("Verified Emerald", "#059669", ThemeManager.EMERALD_PRIMARY));

        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(desc);
        card.add(Box.createVerticalStrut(16));
        card.add(modeStrip);
        card.add(Box.createVerticalStrut(20));
        card.add(palTitle);
        card.add(Box.createVerticalStrut(10));
        card.add(swatches);
        card.add(Box.createVerticalGlue());

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(card, BorderLayout.NORTH);
        return wrap;
    }

    private RoundedCard swatchCard(String name, String hex, Color c) {
        RoundedCard card = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        JPanel colorBar = new JPanel();
        colorBar.setBackground(c);
        colorBar.setPreferredSize(new Dimension(80, 26));
        JLabel l1 = new JLabel(name);
        l1.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        l1.setForeground(ThemeManager.primaryText());
        JLabel l2 = new JLabel(hex);
        l2.setFont(ThemeManager.monoFont(Font.PLAIN, 10.5f));
        l2.setForeground(ThemeManager.mutedText());
        JPanel txt = new JPanel();
        txt.setOpaque(false);
        txt.setLayout(new BoxLayout(txt, BoxLayout.Y_AXIS));
        txt.add(l1);
        txt.add(l2);
        card.add(colorBar, BorderLayout.NORTH);
        card.add(txt, BorderLayout.CENTER);
        return card;
    }

    // -------------------------------------------------------------------------
    // TAB 2: PROFILE & PASSWORD CHANGE
    // -------------------------------------------------------------------------

    private JPanel buildProfileAndPasswordView(User actor) {
        JPanel grid = new JPanel(new GridLayout(1, 2, 16, 16));
        grid.setOpaque(false);

        // Left: Profile Update
        RoundedCard profCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        profCard.setLayout(new BoxLayout(profCard, BoxLayout.Y_AXIS));
        profCard.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel pTitle = new JLabel("User Profile Settings");
        pTitle.setFont(ThemeManager.font(Font.BOLD, 15));
        pTitle.setForeground(ThemeManager.primaryText());
        pTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        AlertBanner profAlert = new AlertBanner();
        profAlert.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel profSuccess = new JLabel("");
        profSuccess.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        profSuccess.setForeground(ThemeManager.EMERALD_PRIMARY);
        profSuccess.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField nameField = createStyledTextField("Full Name");
        JTextField emailField = createStyledTextField("Email Address");
        JTextField phoneField = createStyledTextField("Phone Number");
        JTextField deptField = createStyledTextField("Department / Branch");
        JTextField desigField = createStyledTextField("Designation / Role Title");

        if (actor != null) {
            nameField.setText(nonBlank(actor.getFullName(), ""));
            emailField.setText(nonBlank(actor.getEmail(), ""));
            phoneField.setText(nonBlank(actor.getPhone(), ""));
            deptField.setText(nonBlank(actor.getDepartment(), ""));
            desigField.setText(nonBlank(actor.getDesignation(), ""));
        }

        ActionButton saveProfBtn = new ActionButton("Save Profile Changes to MySQL", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        saveProfBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveProfBtn.addActionListener(e -> {
            profAlert.clear();
            profSuccess.setText("");
            controller.updateProfileAsync(
                    actor,
                    nameField.getText(),
                    emailField.getText(),
                    phoneField.getText(),
                    deptField.getText(),
                    desigField.getText(),
                    res -> {
                        if (res.success && res.data != null) {
                            AppSession.getInstance().startSession(res.data, AppSession.getInstance().getCurrentStudent());
                            if (onUserProfileUpdated != null) onUserProfileUpdated.accept(res.data);
                            profSuccess.setText("✓ " + res.message);
                        } else {
                            profAlert.showError(res.message);
                        }
                    }
            );
        });

        profCard.add(pTitle);
        profCard.add(Box.createVerticalStrut(10));
        profCard.add(profAlert);
        profCard.add(labeled("Full Name", nameField));
        profCard.add(Box.createVerticalStrut(8));
        profCard.add(labeled("Official Email", emailField));
        profCard.add(Box.createVerticalStrut(8));
        profCard.add(labeled("Phone Number", phoneField));
        profCard.add(Box.createVerticalStrut(8));
        profCard.add(labeled("Department / Branch", deptField));
        profCard.add(Box.createVerticalStrut(8));
        profCard.add(labeled("Designation", desigField));
        profCard.add(Box.createVerticalStrut(12));
        profCard.add(saveProfBtn);
        profCard.add(Box.createVerticalStrut(6));
        profCard.add(profSuccess);

        // Right: Password Change (verifies existing password + hashes via BCrypt)
        RoundedCard passCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        passCard.setLayout(new BoxLayout(passCard, BoxLayout.Y_AXIS));
        passCard.setBorder(new EmptyBorder(18, 20, 18, 20));

        JLabel sTitle = new JLabel("Change Account Password (BCrypt Verified)");
        sTitle.setFont(ThemeManager.font(Font.BOLD, 15));
        sTitle.setForeground(ThemeManager.primaryText());
        sTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sNote = new JLabel("<html><body style='width:380px;'>Your current password is verified against the stored hash in MySQL before updating with a fresh BCrypt ($2b$, cost 12) digest. Stored hashes are never displayed.</body></html>");
        sNote.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        sNote.setForeground(ThemeManager.mutedText());
        sNote.setAlignmentX(Component.LEFT_ALIGNMENT);

        AlertBanner passAlert = new AlertBanner();
        passAlert.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel passSuccess = new JLabel("");
        passSuccess.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        passSuccess.setForeground(ThemeManager.EMERALD_PRIMARY);
        passSuccess.setAlignmentX(Component.LEFT_ALIGNMENT);

        PasswordInputField curPassField = new PasswordInputField("Enter current password");
        PasswordInputField newPassField = new PasswordInputField("Minimum 8 characters");
        PasswordInputField confPassField = new PasswordInputField("Re-enter new password");

        ActionButton updatePassBtn = new ActionButton("Verify & Update Password", ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57), Color.WHITE, null, 10);
        updatePassBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        updatePassBtn.addActionListener(e -> {
            passAlert.clear();
            passSuccess.setText("");
            String cur = curPassField.getPasswordText();
            String np = newPassField.getPasswordText();
            String cp = confPassField.getPasswordText();
            if (!np.equals(cp)) {
                passAlert.showError("New password and confirmation do not match.");
                return;
            }
            controller.changePasswordAsync(actor, cur, np, res -> {
                if (res.success) {
                    curPassField.clearPassword();
                    newPassField.clearPassword();
                    confPassField.clearPassword();
                    passSuccess.setText("✓ " + res.message);
                } else {
                    passAlert.showError(res.message);
                }
            });
        });

        passCard.add(sTitle);
        passCard.add(Box.createVerticalStrut(4));
        passCard.add(sNote);
        passCard.add(Box.createVerticalStrut(10));
        passCard.add(passAlert);
        passCard.add(labeled("Current Password", curPassField));
        passCard.add(Box.createVerticalStrut(8));
        passCard.add(labeled("New Password", newPassField));
        passCard.add(Box.createVerticalStrut(8));
        passCard.add(labeled("Confirm New Password", confPassField));
        passCard.add(Box.createVerticalStrut(14));
        passCard.add(updatePassBtn);
        passCard.add(Box.createVerticalStrut(6));
        passCard.add(passSuccess);

        grid.add(profCard);
        grid.add(passCard);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(grid, BorderLayout.NORTH);
        return wrap;
    }

    // -------------------------------------------------------------------------
    // TAB 3: USER ACCOUNTS (ADMIN ONLY)
    // -------------------------------------------------------------------------

    private JPanel buildUserAccountsView(User actor) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        AlertBanner usrAlert = new AlertBanner();

        JPanel topBar = new JPanel(new BorderLayout(10, 0));
        topBar.setOpaque(false);
        JLabel hdr = new JLabel("Institutional User Accounts (`users` in MySQL — password hashes masked)");
        hdr.setFont(ThemeManager.font(Font.BOLD, 13.5f));
        hdr.setForeground(ThemeManager.primaryText());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        ActionButton addBtn = new ActionButton("+  Create User Account", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 9);
        ActionButton delBtn = new ActionButton("✕  Delete Selected", ThemeManager.ROSE_PRIMARY, new Color(0xBE, 0x12, 0x3C), Color.WHITE, null, 9);
        ActionButton refBtn = new ActionButton("↻  Refresh", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 9);
        btns.add(addBtn);
        btns.add(delBtn);
        btns.add(refBtn);

        topBar.add(hdr, BorderLayout.WEST);
        topBar.add(btns, BorderLayout.EAST);

        String[] cols = {"User ID", "Username", "Full Name", "Role", "Email", "Department", "Status", "Active"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(30);
        table.setFont(ThemeManager.font(Font.PLAIN, 12));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11));
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);

        final List<User>[] loadedHolder = new List[]{List.of()};
        Runnable loadUsers = () -> controller.loadUsersForAdminAsync(actor, res -> {
            if (!res.success || res.data == null) {
                usrAlert.showError(res.message);
                return;
            }
            usrAlert.clear();
            loadedHolder[0] = res.data;
            model.setRowCount(0);
            for (User u : res.data) {
                model.addRow(new Object[]{
                        u.getId(),
                        u.getUsername(),
                        nonBlank(u.getFullName(), "-"),
                        u.getRole().name(),
                        nonBlank(u.getEmail(), "-"),
                        nonBlank(u.getDepartment(), "-"),
                        nonBlank(u.getStatus(), "ACTIVE"),
                        u.isActive() ? "YES" : "NO"
                });
            }
        });

        refBtn.addActionListener(e -> loadUsers.run());
        addBtn.addActionListener(e -> showCreateUserDialog(actor, loadUsers, usrAlert));
        delBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= loadedHolder[0].size()) {
                usrAlert.showError("Select a user row to delete.");
                return;
            }
            User target = loadedHolder[0].get(row);
            int ok = JOptionPane.showConfirmDialog(
                    this,
                    "Permanently delete user @" + target.getUsername() + " (" + target.getRole().name() + ") from MySQL?",
                    "Confirm Delete User",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (ok == JOptionPane.YES_OPTION) {
                controller.deleteUserByAdminAsync(actor, target.getId(), res -> {
                    if (res.success) {
                        usrAlert.clear();
                        loadUsers.run();
                    } else {
                        usrAlert.showError(res.message);
                    }
                });
            }
        });

        JPanel hdrWrap = new JPanel();
        hdrWrap.setOpaque(false);
        hdrWrap.setLayout(new BoxLayout(hdrWrap, BoxLayout.Y_AXIS));
        hdrWrap.add(topBar);
        hdrWrap.add(Box.createVerticalStrut(6));
        hdrWrap.add(usrAlert);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new MatteBorder(1, 1, 1, 1, ThemeManager.cardBorder()));
        card.add(hdrWrap, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);

        loadUsers.run();
        return card;
    }

    private void showCreateUserDialog(User actor, Runnable onSuccess, AlertBanner usrAlert) {
        JTextField uField = createStyledTextField("Username (e.g. tpo_coordinator)");
        JTextField eField = createStyledTextField("Email (e.g. tpo@gcet.edu.in)");
        JTextField nField = createStyledTextField("Full Name");
        JComboBox<UserRole> rBox = new JComboBox<>(UserRole.values());
        JTextField dField = createStyledTextField("Department (e.g. CSE, Placement Cell)");
        JTextField gField = createStyledTextField("Designation");
        PasswordInputField pField = new PasswordInputField("Initial password (min 8 chars)");

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setPreferredSize(new Dimension(380, 390));
        form.add(labeled("Username *", uField));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Email *", eField));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Full Name *", nField));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Role *", rBox));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Department", dField));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Designation", gField));
        form.add(Box.createVerticalStrut(6));
        form.add(labeled("Initial Password *", pField));

        int opt = JOptionPane.showConfirmDialog(this, form, "Create New Institutional Account", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opt == JOptionPane.OK_OPTION) {
            controller.createUserByAdminAsync(
                    actor,
                    uField.getText(),
                    eField.getText(),
                    nField.getText(),
                    (UserRole) rBox.getSelectedItem(),
                    dField.getText(),
                    gField.getText(),
                    pField.getPasswordText(),
                    res -> {
                        if (res.success) {
                            usrAlert.clear();
                            onSuccess.run();
                        } else {
                            usrAlert.showError(res.message);
                        }
                    }
            );
        }
    }

    // -------------------------------------------------------------------------
    // TAB 6: APPLICATION & SESSION INFO (NO SENSITIVE CREDENTIALS)
    // -------------------------------------------------------------------------

    private JPanel buildAppAndSessionView(User actor) {
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        JPanel grid = new JPanel(new GridLayout(1, 2, 16, 16));
        grid.setOpaque(false);

        RoundedCard appCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        appCard.setLayout(new BoxLayout(appCard, BoxLayout.Y_AXIS));
        appCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        JLabel aTitle = new JLabel("Application & Stack Information");
        aTitle.setFont(ThemeManager.font(Font.BOLD, 15));
        aTitle.setForeground(ThemeManager.primaryText());
        appCard.add(aTitle);
        appCard.add(Box.createVerticalStrut(10));
        appCard.add(infoRow("Institution", "Geethanjali College of Engineering & Technology (UGC Autonomous)"));
        appCard.add(infoRow("Campus", "Cheeryal (V), Keesara (M), Medchal Dist., Telangana"));
        appCard.add(infoRow("Application", "GCET PLACEMENT HUB — Java 17 Swing Desktop Edition"));
        appCard.add(infoRow("UI Framework", "Java Swing + FlatLaf 3.5.4 + JFreeChart 1.5.5 + OpenPDF 1.3.43"));
        appCard.add(infoRow("Database Driver", "MySQL Connector/J 8.4.0 (Direct JDBC)"));
        appCard.add(infoRow("Target MySQL DB", cfg.getHost() + ":" + cfg.getPort() + " / " + cfg.getDatabase()));
        appCard.add(infoRow("Config Source", nonBlank(cfg.getSourceDescription(), "Environment")));
        appCard.add(Box.createVerticalStrut(12));
        if (onOpenDbConfigDialog != null) {
            ActionButton cfgBtn = new ActionButton("Open Runtime MySQL Connection Settings", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
            cfgBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
            cfgBtn.addActionListener(e -> onOpenDbConfigDialog.run());
            appCard.add(cfgBtn);
        }

        RoundedCard sesCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        sesCard.setLayout(new BoxLayout(sesCard, BoxLayout.Y_AXIS));
        sesCard.setBorder(new EmptyBorder(18, 20, 18, 20));
        JLabel sTitle = new JLabel("Authenticated Session Information");
        sTitle.setFont(ThemeManager.font(Font.BOLD, 15));
        sTitle.setForeground(ThemeManager.primaryText());
        sesCard.add(sTitle);
        sesCard.add(Box.createVerticalStrut(10));
        if (actor != null) {
            sesCard.add(infoRow("User ID", nonBlank(actor.getId(), "-")));
            sesCard.add(infoRow("Username", "@" + nonBlank(actor.getUsername(), "-")));
            sesCard.add(infoRow("Full Name", nonBlank(actor.getFullName(), "-")));
            sesCard.add(infoRow("RBAC Role", actor.getRole().name()));
            sesCard.add(infoRow("Department", nonBlank(actor.getDepartment(), "-")));
            sesCard.add(infoRow("Official Email", nonBlank(actor.getEmail(), "-")));
            sesCard.add(infoRow("Account Status", nonBlank(actor.getStatus(), "ACTIVE")));
            sesCard.add(infoRow("Last Login", nonBlank(actor.getLastLogin(), "Current Session")));
        } else {
            sesCard.add(infoRow("Session", "Not authenticated"));
        }

        grid.add(appCard);
        grid.add(sesCard);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(grid, BorderLayout.NORTH);
        return wrap;
    }

    private JPanel infoRow(String k, String v) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(5, 0, 5, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel kl = new JLabel(k + ":");
        kl.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        kl.setForeground(ThemeManager.mutedText());
        kl.setPreferredSize(new Dimension(135, 20));
        JLabel vl = new JLabel("<html>" + v + "</html>");
        vl.setFont(ThemeManager.font(Font.PLAIN, 12));
        vl.setForeground(ThemeManager.primaryText());
        p.add(kl, BorderLayout.WEST);
        p.add(vl, BorderLayout.CENTER);
        return p;
    }

    private JPanel labeled(String title, Component comp) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(title);
        l.setFont(ThemeManager.font(Font.BOLD, 11));
        l.setForeground(ThemeManager.secondaryText());
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }
}
