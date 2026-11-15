package edu.gcet.placementhub.ui.shell;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Exact Java Swing recreation of HeaderBar.tsx:
 * Top institutional bar (#101A35) with circular GCET logo, active module chip,
 * current date, clickable MySQL 8 live status badge, GCTC link, Alerts popup,
 * Light/Dark theme toggle, and prominent LOG OUT button with confirmation modal.
 */
public class HeaderBarPanel extends JPanel {

    private final JLabel activeModuleLabel;
    private final ActionButton dbStatusBtn;
    private final ActionButton themeBtn;
    private DbStatus lastDbStatus;
    private User currentUser;

    public HeaderBarPanel(
            Runnable onLogoutConfirmed,
            Runnable onToggleTheme,
            Runnable onOpenDbDiagnostics
    ) {
        setLayout(new BorderLayout());
        setBackground(ThemeManager.NAVY_MIDNIGHT);
        setPreferredSize(new Dimension(1200, 64));
        setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.NAVY_DEEP),
                new EmptyBorder(8, 18, 8, 18)
        ));

        // Left: GCET Crest + Brand + Active Module
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        left.setOpaque(false);

        left.add(new JLabel(ResourceLoader.createCircularLogoIcon(42, new Color(50, 197, 244, 150))));

        JPanel brandCol = new JPanel();
        brandCol.setOpaque(false);
        brandCol.setLayout(new BoxLayout(brandCol, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topRow.setOpaque(false);
        JLabel brandLbl = new JLabel("GCET PLACEMENT HUB");
        brandLbl.setFont(ThemeManager.font(Font.BOLD, 15));
        brandLbl.setForeground(Color.WHITE);

        BadgeLabel locBadge = new BadgeLabel("Cheeryal, Telangana", ThemeManager.NAVY_DEEP, ThemeManager.CYAN_ACCENT, new Color(59, 130, 246, 70), 6);
        locBadge.setFont(ThemeManager.font(Font.BOLD, 10));
        topRow.add(brandLbl);
        topRow.add(locBadge);

        JLabel tagLbl = new JLabel("<html>Smart Placement Cell &amp; Skill Matrix Portal · <i style='color:#32C5F4;'>\"Your Skills. Your Opportunities. Your Future.\"</i></html>");
        tagLbl.setFont(ThemeManager.font(Font.PLAIN, 10.8f));
        tagLbl.setForeground(ThemeManager.SLATE_300);
        tagLbl.setBorder(new EmptyBorder(0, 8, 0, 0));

        brandCol.add(topRow);
        brandCol.add(tagLbl);
        left.add(brandCol);

        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 24));
        sep.setForeground(ThemeManager.NAVY_DEEP);
        left.add(sep);

        JLabel modPrefix = new JLabel("Module:");
        modPrefix.setFont(ThemeManager.font(Font.PLAIN, 11));
        modPrefix.setForeground(ThemeManager.SLATE_400);
        activeModuleLabel = new BadgeLabel("Dashboard", ThemeManager.NAVY_DEEP, Color.WHITE, new Color(96, 165, 250, 60), 8);
        activeModuleLabel.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        left.add(modPrefix);
        left.add(activeModuleLabel);

        // Right: Date + MySQL badge + GCTC + Bell + Theme + LOG OUT
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        right.setOpaque(false);

        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH));
        BadgeLabel dateChip = new BadgeLabel("📅  " + todayStr, new Color(23, 43, 80, 160), ThemeManager.SLATE_200, ThemeManager.NAVY_DEEP, 8);
        dateChip.setFont(ThemeManager.font(Font.PLAIN, 11));
        dateChip.setBorder(new EmptyBorder(6, 10, 6, 10));

        dbStatusBtn = new ActionButton(
                "●  MongoDB: Checking...",
                new Color(23, 43, 80, 170),
                ThemeManager.NAVY_DEEP,
                ThemeManager.SLATE_200,
                ThemeManager.NAVY_DEEP,
                8
        );
        dbStatusBtn.setFont(ThemeManager.monoFont(Font.BOLD, 11));
        dbStatusBtn.setMargin(new Insets(6, 10, 6, 10));
        dbStatusBtn.addActionListener(e -> onOpenDbDiagnostics.run());

        ActionButton gctcBtn = new ActionButton(
                "gctcportal.in ↗",
                new Color(38, 132, 255, 45),
                new Color(38, 132, 255, 80),
                ThemeManager.CYAN_ACCENT,
                new Color(38, 132, 255, 110),
                8
        );
        gctcBtn.setFont(ThemeManager.font(Font.BOLD, 11));
        gctcBtn.setMargin(new Insets(6, 10, 6, 10));
        gctcBtn.addActionListener(e -> ResourceLoader.openExternalUrl(this, "https://www.gctcportal.in/"));

        ActionButton alertsBtn = new ActionButton(
                "🔔",
                new Color(23, 43, 80, 170),
                ThemeManager.NAVY_DEEP,
                ThemeManager.SLATE_200,
                ThemeManager.NAVY_DEEP,
                8
        );
        alertsBtn.setMargin(new Insets(6, 10, 6, 10));
        alertsBtn.setToolTipText("Placement Alerts & Updates");
        alertsBtn.addActionListener(e -> showAlertsPopup(alertsBtn));

        themeBtn = new ActionButton(
                ThemeManager.isDarkMode() ? "☀ Light" : "☾ Dark",
                new Color(23, 43, 80, 170),
                ThemeManager.NAVY_DEEP,
                ThemeManager.SLATE_200,
                ThemeManager.NAVY_DEEP,
                8
        );
        themeBtn.setFont(ThemeManager.font(Font.BOLD, 11));
        themeBtn.setMargin(new Insets(6, 10, 6, 10));
        themeBtn.addActionListener(e -> {
            onToggleTheme.run();
            themeBtn.setText(ThemeManager.isDarkMode() ? "☀ Light" : "☾ Dark");
        });

        ActionButton logoutBtn = new ActionButton(
                "⎋  LOG OUT",
                ThemeManager.BLUE_ELECTRIC,
                ThemeManager.BLUE_HOVER,
                Color.WHITE,
                new Color(96, 165, 250, 130),
                12
        );
        logoutBtn.setFont(ThemeManager.font(Font.BOLD, 12));
        logoutBtn.setMargin(new Insets(7, 16, 7, 16));
        logoutBtn.addActionListener(e -> confirmAndLogout(onLogoutConfirmed));

        right.add(dateChip);
        right.add(dbStatusBtn);
        right.add(gctcBtn);
        right.add(alertsBtn);
        right.add(themeBtn);
        right.add(logoutBtn);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void setActiveModuleTitle(String title) {
        activeModuleLabel.setText(title);
    }

    public void syncThemeButton() {
        themeBtn.setText(ThemeManager.isDarkMode() ? "☀ Light" : "☾ Dark");
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public void updateDbStatus(DbStatus status) {
        this.lastDbStatus = status;
        if (status != null && status.isConnected()) {
            String label = status.isUsingLocalFallback() ? "●  MongoDB: Local" : (status.isAtlas() ? "●  MongoDB: Atlas" : "●  MongoDB: Connected");
            dbStatusBtn.setText(label);
            dbStatusBtn.setForeground(new Color(0x34, 0xD3, 0x99));
        } else {
            dbStatusBtn.setText("●  MongoDB: Offline");
            dbStatusBtn.setForeground(new Color(0xFB, 0x71, 0x85));
        }
    }

    public DbStatus getLastDbStatus() {
        return lastDbStatus;
    }

    private void showAlertsPopup(Component invoker) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(ThemeManager.NAVY_MIDNIGHT);
        popup.setBorder(BorderFactory.createLineBorder(ThemeManager.NAVY_DEEP, 1));

        JPanel content = new JPanel();
        content.setBackground(ThemeManager.NAVY_MIDNIGHT);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel h = new JLabel("Placement Alerts");
        h.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        h.setForeground(Color.WHITE);
        content.add(h);
        content.add(Box.createVerticalStrut(8));

        content.add(createAlertItem("Placement Drive Schedule", "Review open campus placement drives and student eligibility criteria."));
        content.add(Box.createVerticalStrut(6));
        content.add(createAlertItem("Skill Matrix Verification", "Department faculty coordinators verify submitted student technical skills."));

        popup.add(content);
        popup.show(invoker, -180, invoker.getHeight() + 6);
    }

    private JPanel createAlertItem(String title, String msg) {
        RoundedCard card = new RoundedCard(8, new Color(23, 43, 80, 140), ThemeManager.NAVY_DEEP);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(8, 10, 8, 10));
        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        t.setForeground(ThemeManager.CYAN_ACCENT);
        JLabel m = new JLabel("<html><body style='width:240px;'>" + msg + "</body></html>");
        m.setFont(ThemeManager.font(Font.PLAIN, 11));
        m.setForeground(ThemeManager.SLATE_300);
        card.add(t);
        card.add(Box.createVerticalStrut(2));
        card.add(m);
        return card;
    }

    private void confirmAndLogout(Runnable onLogoutConfirmed) {
        String userCtx = currentUser != null
                ? "\n\nCurrent Session: " + currentUser.getFullName() + " [" + currentUser.getRole().name() + "]"
                : "";
        int choice = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                "Are you sure you want to log out of GCET Placement Hub?" + userCtx
                        + "\nYour session will be cleared and you will return to the Public Welcome Portal.",
                "Confirm Session Logout — GCET Placement Hub",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (choice == JOptionPane.YES_OPTION) {
            onLogoutConfirmed.run();
        }
    }
}
