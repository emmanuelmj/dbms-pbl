package edu.gcet.placementhub.ui.companies;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.PlacementController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.utils.UiComponents.ActionButton;
import edu.gcet.placementhub.utils.UiComponents.AlertBanner;
import edu.gcet.placementhub.utils.UiComponents.BadgeLabel;
import edu.gcet.placementhub.utils.UiComponents.RoundedCard;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Campus Recruitment Partners Module (Phase 4):
 * Interactive Company Directory, Company Profile & HR Contact Inspector,
 * Approval Status badge/workflow, and authorized Add/Edit/Delete operations.
 */
public class CompaniesPanel extends JPanel {

    private final PlacementController controller = new PlacementController();
    private final Consumer<String> onViewCompanyDrives;

    private List<Company> allCompanies = List.of();
    private List<Company> filteredCompanies = List.of();
    private List<PlacementDrive> allDrives = List.of();

    private JLabel subtitleLabel;
    private AlertBanner statusBanner;
    private JTextField searchField;
    private JComboBox<String> tierCombo;
    private JComboBox<String> approvalCombo;
    private ActionButton addBtn;
    private ActionButton editBtn;
    private ActionButton approveBtn;
    private ActionButton deleteBtn;

    private JTable table;
    private DefaultTableModel tableModel;
    private JPanel detailContainer;
    private boolean suppressFilters = false;

    public CompaniesPanel(Consumer<String> onViewCompanyDrives) {
        this.onViewCompanyDrives = onViewCompanyDrives;
        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));
        buildUi();
    }

    public void loadData() {
        User currentUser = AppSession.getInstance().getCurrentUser();
        updateRoleButtons(currentUser);
        subtitleLabel.setText("Loading campus recruitment partners from MySQL...");
        controller.loadPlacementBundleAsync(currentUser, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.allCompanies = bundle.companies;
            this.allDrives = bundle.drives;
            applyFiltersAndRender();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        applyFiltersAndRender();
        revalidate();
        repaint();
    }

    private void buildUi() {
        // Header
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Campus Recruitment Partners");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("Partner corporate organizations participating in GCET placement drives.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(subtitleLabel);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionRow.setOpaque(false);
        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        addBtn = new ActionButton("+  Add Partner Company", ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 10);
        addBtn.addActionListener(e -> openAddDialog());
        actionRow.add(refreshBtn);
        actionRow.add(addBtn);

        header.add(titleCol, BorderLayout.WEST);
        header.add(actionRow, BorderLayout.EAST);

        // Filter Card
        RoundedCard filterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));
        filterCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        statusBanner = new AlertBanner();
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel filterRow = new JPanel(new GridBagLayout());
        filterRow.setOpaque(false);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 10);

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search company by name, industry, location, or HR...");
        searchField.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        searchField.setPreferredSize(new Dimension(260, 34));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void removeUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void changedUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
        });

        tierCombo = new JComboBox<>(new String[]{
                "All Tiers", "Tier 1 (Dream)", "Tier 2 (Super Dream)", "Core", "Standard IT"
        });
        tierCombo.setPreferredSize(new Dimension(170, 34));
        tierCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        approvalCombo = new JComboBox<>(new String[]{
                "All Approval Statuses", "ACTIVE", "APPROVED", "PENDING_APPROVAL", "REJECTED"
        });
        approvalCombo.setPreferredSize(new Dimension(175, 34));
        approvalCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        gbc.gridx = 0; gbc.weightx = 1.0;
        filterRow.add(searchField, gbc);
        gbc.gridx = 1; gbc.weightx = 0.0;
        filterRow.add(tierCombo, gbc);
        gbc.gridx = 2; gbc.insets = new Insets(0, 0, 0, 0);
        filterRow.add(approvalCombo, gbc);

        filterCard.add(statusBanner);
        filterCard.add(Box.createVerticalStrut(2));
        filterCard.add(filterRow);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(header);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(filterCard);

        // Main Split: Left Company Directory Table + Right Profile Inspector
        RoundedCard tableCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tableCard.setLayout(new BorderLayout(0, 8));
        tableCard.setBorder(new EmptyBorder(12, 14, 12, 14));

        String[] cols = {"Company Name", "Industry", "Hiring Tier", "Location", "Drives", "Approval Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(36);
        table.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                renderSelectedInspector();
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder()));

        JPanel rowBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rowBar.setOpaque(false);
        editBtn = new ActionButton("Edit Profile", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        editBtn.addActionListener(e -> openEditSelectedDialog());
        approveBtn = new ActionButton("Review Approval", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        approveBtn.addActionListener(e -> reviewApprovalSelected());
        deleteBtn = new ActionButton("Delete", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, new Color(0xFC, 0xA5, 0xA5), 10);
        deleteBtn.addActionListener(e -> deleteSelectedCompany());

        rowBar.add(editBtn);
        rowBar.add(approveBtn);
        rowBar.add(deleteBtn);

        tableCard.add(tableScroll, BorderLayout.CENTER);
        tableCard.add(rowBar, BorderLayout.SOUTH);

        detailContainer = new JPanel(new BorderLayout());
        detailContainer.setOpaque(false);
        detailContainer.setPreferredSize(new Dimension(370, 500));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableCard, detailContainer);
        split.setResizeWeight(0.64);
        split.setDividerSize(10);
        split.setBorder(null);
        split.setOpaque(false);

        add(topStack, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        updateRoleButtons(AppSession.getInstance().getCurrentUser());
        renderSelectedInspector();
    }

    private void updateRoleButtons(User u) {
        boolean isPrivileged = u != null && u.getRole().isPrivilegedRole();
        boolean isCompany = u != null && u.getRole() == UserRole.COMPANY;
        if (addBtn != null) addBtn.setVisible(isPrivileged);
        if (editBtn != null) editBtn.setVisible(isPrivileged || isCompany);
        if (approveBtn != null) approveBtn.setVisible(isPrivileged);
        if (deleteBtn != null) deleteBtn.setVisible(isPrivileged);
    }

    private void applyFiltersAndRender() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String tier = tierCombo != null && tierCombo.getSelectedItem() != null ? tierCombo.getSelectedItem().toString() : "All Tiers";
        String approval = approvalCombo != null && approvalCombo.getSelectedItem() != null ? approvalCombo.getSelectedItem().toString() : "All Approval Statuses";

        List<Company> list = new ArrayList<>();
        for (Company c : allCompanies) {
            boolean matchQ = q.isEmpty()
                    || safeLower(c.getName()).contains(q)
                    || safeLower(c.getIndustry()).contains(q)
                    || safeLower(c.getLocation()).contains(q)
                    || safeLower(c.getHrName()).contains(q)
                    || safeLower(c.getHrEmail()).contains(q);
            boolean matchTier = "All Tiers".equals(tier) || tier.equalsIgnoreCase(c.getTier());
            String st = c.getApprovalStatus() != null && !c.getApprovalStatus().isBlank() ? c.getApprovalStatus() : c.getStatus();
            boolean matchApproval = "All Approval Statuses".equals(approval) || approval.equalsIgnoreCase(st);
            if (matchQ && matchTier && matchApproval) {
                list.add(c);
            }
        }

        this.filteredCompanies = list;
        subtitleLabel.setText("Partner corporate organizations · Showing " + list.size() + " of " + allCompanies.size() + " partner companies in MySQL.");

        tableModel.setRowCount(0);
        for (Company c : filteredCompanies) {
            int drvCount = 0;
            int openCount = 0;
            for (PlacementDrive d : allDrives) {
                if (c.getId().equals(d.getCompanyId())) {
                    drvCount++;
                    if ("OPEN".equalsIgnoreCase(d.getStatus())) openCount++;
                }
            }
            String st = c.getApprovalStatus() != null && !c.getApprovalStatus().isBlank() ? c.getApprovalStatus() : "ACTIVE";
            tableModel.addRow(new Object[]{
                    c.getName(),
                    c.getIndustry(),
                    c.getTier(),
                    c.getLocation(),
                    drvCount + " (" + openCount + " Open)",
                    st
            });
        }
        if (!filteredCompanies.isEmpty() && table.getSelectedRow() < 0) {
            table.setRowSelectionInterval(0, 0);
        } else {
            renderSelectedInspector();
        }
    }

    private Company getSelectedCompany() {
        int row = table != null ? table.getSelectedRow() : -1;
        if (row < 0 || row >= filteredCompanies.size()) return null;
        return filteredCompanies.get(row);
    }

    private void renderSelectedInspector() {
        detailContainer.removeAll();
        Company c = getSelectedCompany();
        User actor = AppSession.getInstance().getCurrentUser();

        boolean canEditThis = c != null && actor != null && (actor.getRole().isPrivilegedRole()
                || (actor.getRole() == UserRole.COMPANY && actor.getCompanyId() != null && actor.getCompanyId().equals(c.getId())));
        if (editBtn != null) editBtn.setEnabled(canEditThis);
        if (approveBtn != null) approveBtn.setEnabled(c != null && actor != null && actor.getRole().isPrivilegedRole());
        if (deleteBtn != null) deleteBtn.setEnabled(c != null && actor != null && actor.getRole().isPrivilegedRole());

        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(18, 18, 18, 18));

        if (c == null) {
            JLabel empty = new JLabel("Select a company from the directory to view corporate profile & HR contact.");
            empty.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
            empty.setForeground(ThemeManager.mutedText());
            card.add(empty);
            detailContainer.add(card, BorderLayout.CENTER);
            detailContainer.revalidate();
            detailContainer.repaint();
            return;
        }

        String appStatus = c.getApprovalStatus() != null && !c.getApprovalStatus().isBlank() ? c.getApprovalStatus() : "ACTIVE";
        Color badgeBg = "REJECTED".equalsIgnoreCase(appStatus)
                ? ThemeManager.ROSE_SOFT
                : ("PENDING_APPROVAL".equalsIgnoreCase(appStatus) ? ThemeManager.AMBER_SOFT : ThemeManager.EMERALD_SOFT);
        Color badgeFg = "REJECTED".equalsIgnoreCase(appStatus)
                ? ThemeManager.ROSE_PRIMARY
                : ("PENDING_APPROVAL".equalsIgnoreCase(appStatus) ? ThemeManager.AMBER_PRIMARY : ThemeManager.EMERALD_PRIMARY);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.add(new BadgeLabel(c.getTier() != null ? c.getTier() : "Standard IT", ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 8));
        badgeRow.add(new BadgeLabel(appStatus, badgeBg, badgeFg, badgeFg, 8));

        JLabel nameLbl = new JLabel("<html><body style='width:295px;'>" + esc(c.getName()) + "</body></html>");
        nameLbl.setFont(ThemeManager.font(Font.BOLD, 17));
        nameLbl.setForeground(ThemeManager.primaryText());
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel indLbl = new JLabel(nonBlank(c.getIndustry(), "Information Technology"));
        indLbl.setFont(ThemeManager.font(Font.BOLD, 12));
        indLbl.setForeground(ThemeManager.CYAN_DARK);
        indLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLbl = new JLabel("<html><body style='width:300px; line-height:1.45;'>"
                + esc(nonBlank(c.getDescription(), "No corporate overview added yet.")) + "</body></html>");
        descLbl.setFont(ThemeManager.font(Font.PLAIN, 12));
        descLbl.setForeground(ThemeManager.secondaryText());
        descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(badgeRow);
        card.add(Box.createVerticalStrut(10));
        card.add(nameLbl);
        card.add(Box.createVerticalStrut(3));
        card.add(indLbl);
        card.add(Box.createVerticalStrut(10));
        card.add(descLbl);
        card.add(Box.createVerticalStrut(14));

        RoundedCard metaCard = new RoundedCard(10, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        metaCard.setLayout(new BoxLayout(metaCard, BoxLayout.Y_AXIS));
        metaCard.setBorder(new EmptyBorder(12, 12, 12, 12));
        metaCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaCard.add(infoRow("Location", nonBlank(c.getLocation(), "—")));
        metaCard.add(infoRow("Website", nonBlank(c.getWebsite(), "—")));
        metaCard.add(infoRow("HR Recruiter", nonBlank(c.getHrName(), "Not specified")));
        metaCard.add(infoRow("HR Email", nonBlank(c.getHrEmail(), "—")));
        metaCard.add(infoRow("HR Phone", nonBlank(c.getHrPhone(), "—")));
        if (c.getApprovedByName() != null && !c.getApprovedByName().isBlank()) {
            metaCard.add(infoRow("Reviewed By", c.getApprovedByName()));
        }
        if (c.getRejectionReason() != null && !c.getRejectionReason().isBlank()) {
            metaCard.add(infoRow("Rejection Reason", c.getRejectionReason()));
        }
        card.add(metaCard);
        card.add(Box.createVerticalStrut(14));

        // Associated Drives list summary
        List<PlacementDrive> compDrives = new ArrayList<>();
        for (PlacementDrive d : allDrives) {
            if (c.getId().equals(d.getCompanyId())) compDrives.add(d);
        }
        JLabel drvHead = new JLabel("Placement Drives (" + compDrives.size() + ")");
        drvHead.setFont(ThemeManager.font(Font.BOLD, 12.5f));
        drvHead.setForeground(ThemeManager.primaryText());
        drvHead.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(drvHead);
        card.add(Box.createVerticalStrut(6));

        for (int i = 0; i < Math.min(3, compDrives.size()); i++) {
            PlacementDrive d = compDrives.get(i);
            JLabel line = new JLabel(String.format(Locale.US, "• %s (%.1f LPA · %s)", d.getJobRole(), d.getSalaryPackageLpa(), d.getStatus()));
            line.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            line.setForeground(ThemeManager.secondaryText());
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(line);
            card.add(Box.createVerticalStrut(2));
        }
        if (compDrives.isEmpty()) {
            JLabel none = new JLabel("No placement drives announced yet for this partner.");
            none.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
            none.setForeground(ThemeManager.mutedText());
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.add(none);
        }

        card.add(Box.createVerticalStrut(14));
        ActionButton viewDrivesBtn = new ActionButton("View Company Drives  →", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        viewDrivesBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        viewDrivesBtn.addActionListener(e -> {
            if (onViewCompanyDrives != null) {
                onViewCompanyDrives.accept(c.getId());
            }
        });
        card.add(viewDrivesBtn);

        JScrollPane sp = new JScrollPane(card);
        sp.setBorder(null);
        sp.getViewport().setOpaque(false);
        sp.setOpaque(false);
        detailContainer.add(sp, BorderLayout.CENTER);
        detailContainer.revalidate();
        detailContainer.repaint();
    }

    private JPanel infoRow(String key, String val) {
        JPanel r = new JPanel(new BorderLayout(8, 0));
        r.setOpaque(false);
        r.setBorder(new EmptyBorder(3, 0, 3, 0));
        JLabel k = new JLabel(key + ":");
        k.setFont(ThemeManager.font(Font.BOLD, 11));
        k.setForeground(ThemeManager.mutedText());
        k.setPreferredSize(new Dimension(102, 16));
        JLabel v = new JLabel("<html><body style='width:185px;'>" + esc(val) + "</body></html>");
        v.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        v.setForeground(ThemeManager.primaryText());
        r.add(k, BorderLayout.WEST);
        r.add(v, BorderLayout.CENTER);
        return r;
    }

    private void openAddDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        CompanyFormDialog dlg = new CompanyFormDialog(owner, null);
        Company draft = dlg.showDialog();
        if (draft == null) return;

        controller.createCompanyAsync(AppSession.getInstance().getCurrentUser(), draft, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Company Registered", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Registration Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openEditSelectedDialog() {
        Company target = getSelectedCompany();
        if (target == null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        CompanyFormDialog dlg = new CompanyFormDialog(owner, target);
        Company updated = dlg.showDialog();
        if (updated == null) return;

        controller.updateCompanyAsync(AppSession.getInstance().getCurrentUser(), updated, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Company Updated", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Update Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void reviewApprovalSelected() {
        Company target = getSelectedCompany();
        if (target == null) return;
        String[] options = {"ACTIVE", "APPROVED", "PENDING_APPROVAL", "REJECTED"};
        Object choice = JOptionPane.showInputDialog(
                this,
                "Select Approval Status for " + target.getName() + ":",
                "Company Approval Review",
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                target.getApprovalStatus() != null ? target.getApprovalStatus() : "ACTIVE"
        );
        if (choice == null) return;
        String status = choice.toString();
        String reason = null;
        if ("REJECTED".equalsIgnoreCase(status)) {
            reason = JOptionPane.showInputDialog(this, "Enter rejection reason (mandatory):");
            if (reason == null || reason.trim().isEmpty()) return;
        }
        controller.updateCompanyApprovalAsync(AppSession.getInstance().getCurrentUser(), target.getId(), status, reason, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Approval Updated", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Action Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteSelectedCompany() {
        Company target = getSelectedCompany();
        if (target == null) return;
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete company \"" + target.getName() + "\" from MySQL?\n"
                        + "(Deletion will be rejected if any placement drives exist for this company.)",
                "Confirm Company Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        controller.deleteCompanyAsync(AppSession.getInstance().getCurrentUser(), target.getId(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Company Deleted", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Cannot Delete Company", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
