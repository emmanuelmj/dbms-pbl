package edu.gcet.placementhub.ui.shell;

import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.models.UserRole;
import edu.gcet.placementhub.utils.ResourceLoader;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Exact Java Swing recreation of Sidebar.tsx:
 * Collapsible Dark Navy (#101A35) navigation sidebar filtered dynamically by UserRole.
 */
public class SidebarPanel extends JPanel {

    public record NavItem(String id, String label, String icon, String badge, boolean adminFacultyOnly) {}

    private static final List<NavItem> NAV_ITEMS = List.of(
            new NavItem("dashboard", "Dashboard", "▣", null, false),
            new NavItem("campus-connect", "Campus Connect", "★", "GCTC", false),
            new NavItem("approvals", "Approval Center", "⛨", "RBAC", true),
            new NavItem("students", "Students", "👥", null, true),
            new NavItem("faculty", "Faculty Directory", "◈", null, true),
            new NavItem("profile", "My Profile", "✓", null, false),
            new NavItem("skills", "Skill Matrix", "◎", null, false),
            new NavItem("companies", "Companies", "❖", null, false),
            new NavItem("drives", "Placement Drives", "⚑", null, false),
            new NavItem("eligibility", "Eligibility Checker", "☑", null, false),
            new NavItem("skill-gap", "Skill Gap Analysis", "◫", null, false),
            new NavItem("matching", "Company Matching", "⌖", null, false),
            new NavItem("applications", "Applications", "▤", null, false),
            new NavItem("reports", "Reports & Analytics", "▥", null, false),
            new NavItem("audit-logs", "Audit Logs", "∿", null, true),
            new NavItem("diagnostics", "DB Diagnostics", "DB", null, true),
            new NavItem("settings", "Settings & Security", "⚙", null, false)
    );

    private final BiConsumer<String, String> onNavigate;
    private final JPanel navContainer;
    private final JPanel brandTextPanel;
    private final JButton collapseBtn;
    private final JLabel footerTitleLabel;
    private final JLabel footerSubLabel;
    private final Map<String, ActionButton> navButtons = new LinkedHashMap<>();
    private final Map<String, String> navLabels = new LinkedHashMap<>();

    private boolean collapsed = false;
    private String activeId = "dashboard";
    private UserRole currentRole = UserRole.ADMIN;

    public SidebarPanel(BiConsumer<String, String> onNavigate) {
        this.onNavigate = onNavigate;
        setLayout(new BorderLayout());
        setBackground(ThemeManager.NAVY_MIDNIGHT);
        setPreferredSize(new Dimension(245, 700));
        setBorder(new MatteBorder(0, 0, 0, 1, ThemeManager.NAVY_DEEP));

        // Top sub-strip
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBackground(ThemeManager.NAVY_MIDNIGHT);
        top.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, ThemeManager.NAVY_DEEP),
                new EmptyBorder(12, 12, 12, 10)
        ));

        JPanel brandLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandLeft.setOpaque(false);
        brandLeft.add(new JLabel(ResourceLoader.createCircularLogoIcon(32, new Color(50, 197, 244, 110))));

        brandTextPanel = new JPanel();
        brandTextPanel.setOpaque(false);
        brandTextPanel.setLayout(new BoxLayout(brandTextPanel, BoxLayout.Y_AXIS));
        JLabel cellLbl = new JLabel("GCET PLACEMENT CELL");
        cellLbl.setFont(ThemeManager.font(Font.BOLD, 10));
        cellLbl.setForeground(ThemeManager.CYAN_ACCENT);
        JLabel ugcLbl = new JLabel("UGC Autonomous · NBA");
        ugcLbl.setFont(ThemeManager.font(Font.PLAIN, 10));
        ugcLbl.setForeground(ThemeManager.SLATE_400);
        brandTextPanel.add(cellLbl);
        brandTextPanel.add(ugcLbl);
        brandLeft.add(brandTextPanel);

        collapseBtn = new ActionButton("◀", ThemeManager.NAVY_MIDNIGHT, ThemeManager.NAVY_DEEP, ThemeManager.SLATE_300, null, 8);
        collapseBtn.setMargin(new Insets(4, 8, 4, 8));
        collapseBtn.addActionListener(e -> toggleCollapsed());

        top.add(brandLeft, BorderLayout.CENTER);
        top.add(collapseBtn, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Navigation links
        navContainer = new JPanel();
        navContainer.setBackground(ThemeManager.NAVY_MIDNIGHT);
        navContainer.setLayout(new BoxLayout(navContainer, BoxLayout.Y_AXIS));
        navContainer.setBorder(new EmptyBorder(8, 8, 8, 8));

        JScrollPane navScroll = new JScrollPane(navContainer);
        navScroll.setBorder(null);
        navScroll.getViewport().setBackground(ThemeManager.NAVY_MIDNIGHT);
        navScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(navScroll, BorderLayout.CENTER);

        // Bottom strip
        JPanel footer = new JPanel();
        footer.setBackground(new Color(0x0C, 0x14, 0x2B));
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, ThemeManager.NAVY_DEEP),
                new EmptyBorder(10, 12, 10, 12)
        ));
        footerTitleLabel = new JLabel("Java 17 Swing Architecture");
        footerTitleLabel.setFont(ThemeManager.font(Font.BOLD, 11));
        footerTitleLabel.setForeground(ThemeManager.SLATE_200);
        footerSubLabel = new JLabel("FlatLaf 3.5.4 · JFreeChart · JDBC");
        footerSubLabel.setFont(ThemeManager.font(Font.PLAIN, 10));
        footerSubLabel.setForeground(ThemeManager.SLATE_400);
        footer.add(footerTitleLabel);
        footer.add(Box.createVerticalStrut(2));
        footer.add(footerSubLabel);
        add(footer, BorderLayout.SOUTH);

        rebuildNavForRole(UserRole.ADMIN);
    }

    public void rebuildNavForRole(UserRole role) {
        this.currentRole = role != null ? role : UserRole.STUDENT;
        navContainer.removeAll();
        navButtons.clear();
        navLabels.clear();

        boolean isPrivilegedOrFaculty = currentRole.isPrivilegedRole() || currentRole == UserRole.FACULTY;
        for (NavItem item : NAV_ITEMS) {
            if (item.adminFacultyOnly() && !isPrivilegedOrFaculty) {
                continue;
            }
            String label = item.label();
            if ("profile".equals(item.id())) {
                label = (currentRole == UserRole.STUDENT) ? "My Profile" : "Student Profiles";
            }
            navLabels.put(item.id(), label);

            String text = collapsed ? item.icon() : formatButtonText(item.icon(), label, item.badge());
            ActionButton btn = new ActionButton(
                    text,
                    ThemeManager.NAVY_MIDNIGHT,
                    ThemeManager.NAVY_DEEP,
                    ThemeManager.SLATE_300,
                    null,
                    10
            );
            btn.setHorizontalAlignment(collapsed ? SwingConstants.CENTER : SwingConstants.LEFT);
            btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            btn.setPreferredSize(new Dimension(collapsed ? 52 : 220, 36));
            btn.setToolTipText(label);
            String finalLabel = label;
            btn.addActionListener(e -> {
                setActivePage(item.id());
                onNavigate.accept(item.id(), finalLabel);
            });
            navButtons.put(item.id(), btn);
            navContainer.add(btn);
            navContainer.add(Box.createVerticalStrut(3));
        }

        setActivePage(activeId);
        navContainer.revalidate();
        navContainer.repaint();
    }

    public void setActivePage(String pageId) {
        this.activeId = pageId;
        for (Map.Entry<String, ActionButton> e : navButtons.entrySet()) {
            boolean active = e.getKey().equals(pageId);
            e.getValue().updateColors(
                    active ? ThemeManager.BLUE_ELECTRIC : ThemeManager.NAVY_MIDNIGHT,
                    active ? ThemeManager.BLUE_ELECTRIC : ThemeManager.NAVY_DEEP,
                    active ? Color.WHITE : ThemeManager.SLATE_300,
                    active ? new Color(96, 165, 250, 90) : null
            );
        }
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        setPreferredSize(new Dimension(collapsed ? 72 : 245, 700));
        brandTextPanel.setVisible(!collapsed);
        footerTitleLabel.setText(collapsed ? "J17" : "Java 17 Swing Architecture");
        footerSubLabel.setVisible(!collapsed);
        collapseBtn.setText(collapsed ? "▶" : "◀");
        rebuildNavForRole(currentRole);
        revalidate();
    }

    private String formatButtonText(String icon, String label, String badge) {
        if (badge == null) {
            return icon + "   " + label;
        }
        return icon + "   " + label + "   [" + badge + "]";
    }

    public String getLabelFor(String pageId) {
        return navLabels.getOrDefault(pageId, "Dashboard");
    }
}
