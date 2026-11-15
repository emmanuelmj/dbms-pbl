package edu.gcet.placementhub.ui.drives;

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
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Placement Drives & Campus Recruitment Module (Phase 4):
 * Supports company-wise filtering, status filtering, eligibility & skill gap jump links,
 * applicant drill-down, and RBAC-gated Create/Edit/Delete drive operations.
 */
public class DrivesPanel extends JPanel {

    private final PlacementController controller = new PlacementController();
    private final Consumer<String> onCheckEligibility;
    private final Consumer<String> onOpenSkillGap;
    private final Consumer<String> onViewApplications;

    private List<PlacementDrive> allDrives = List.of();
    private List<Company> allCompanies = List.of();
    private List<Skill> allSkills = List.of();
    private List<Application> allApplications = List.of();

    private String pendingCompanyFilterId = "ALL";
    private boolean suppressFilters = false;

    private JLabel subtitleLabel;
    private AlertBanner statusBanner;
    private JTextField searchField;
    private JComboBox<String> companyFilterCombo;
    private JComboBox<String> statusFilterCombo;
    private ActionButton addDriveBtn;
    private JPanel cardsContainer;

    private final Map<String, String> companyComboIdMap = new LinkedHashMap<>();

    public DrivesPanel(
            Consumer<String> onCheckEligibility,
            Consumer<String> onOpenSkillGap,
            Consumer<String> onViewApplications
    ) {
        this.onCheckEligibility = onCheckEligibility;
        this.onOpenSkillGap = onOpenSkillGap;
        this.onViewApplications = onViewApplications;

        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));
        buildUi();
    }

    public void setCompanyFilterId(String companyId) {
        this.pendingCompanyFilterId = (companyId != null && !companyId.isBlank()) ? companyId : "ALL";
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        updateRoleButtons(actor);
        subtitleLabel.setText("Loading campus placement drives from MySQL...");
        controller.loadPlacementBundleAsync(actor, bundle -> {
            if (bundle.error != null) {
                statusBanner.showError(bundle.error);
            } else {
                statusBanner.clear();
            }
            this.allDrives = bundle.drives;
            this.allCompanies = bundle.companies;
            this.allSkills = bundle.skills;
            this.allApplications = bundle.applications;
            populateCompanyFilter();
            applyFiltersAndRender();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        populateCompanyFilter();
        applyFiltersAndRender();
        revalidate();
        repaint();
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setOpaque(false);

        JPanel titleCol = new JPanel();
        titleCol.setOpaque(false);
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Placement Drives & Campus Recruitment");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("Configure recruitment drives, skill requirements, CGPA cutoffs, and deadlines.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        titleCol.add(title);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(subtitleLabel);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionRow.setOpaque(false);
        ActionButton refreshBtn = new ActionButton("↻  Refresh", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        addDriveBtn = new ActionButton("+  Create Placement Drive", ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 10);
        addDriveBtn.addActionListener(e -> openAddDriveDialog());
        actionRow.add(refreshBtn);
        actionRow.add(addDriveBtn);

        header.add(titleCol, BorderLayout.WEST);
        header.add(actionRow, BorderLayout.EAST);

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
        searchField.putClientProperty("JTextField.placeholderText", "Search drives by company name, job role, or branch...");
        searchField.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        searchField.setPreferredSize(new Dimension(240, 34));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void removeUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
            @Override public void changedUpdate(DocumentEvent e) { if (!suppressFilters) applyFiltersAndRender(); }
        });

        companyFilterCombo = new JComboBox<>(new String[]{"All Companies"});
        companyFilterCombo.setPreferredSize(new Dimension(200, 34));
        companyFilterCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        statusFilterCombo = new JComboBox<>(new String[]{
                "All Statuses", "OPEN", "UPCOMING", "PLANNED", "CLOSED", "COMPLETED", "CANCELLED"
        });
        statusFilterCombo.setPreferredSize(new Dimension(165, 34));
        statusFilterCombo.addActionListener(e -> { if (!suppressFilters) applyFiltersAndRender(); });

        gbc.gridx = 0; gbc.weightx = 1.0;
        filterRow.add(searchField, gbc);
        gbc.gridx = 1; gbc.weightx = 0.0;
        filterRow.add(companyFilterCombo, gbc);
        gbc.gridx = 2; gbc.insets = new Insets(0, 0, 0, 0);
        filterRow.add(statusFilterCombo, gbc);

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

        cardsContainer = new JPanel();
        cardsContainer.setOpaque(false);
        cardsContainer.setLayout(new BoxLayout(cardsContainer, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(cardsContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ThemeManager.surfaceBg());
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        add(topStack, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        updateRoleButtons(AppSession.getInstance().getCurrentUser());
    }

    private void updateRoleButtons(User u) {
        boolean canCreate = u != null && (u.getRole().isPrivilegedRole() || u.getRole() == UserRole.COMPANY);
        if (addDriveBtn != null) addDriveBtn.setVisible(canCreate);
    }

    private void populateCompanyFilter() {
        suppressFilters = true;
        try {
            companyFilterCombo.removeAllItems();
            companyComboIdMap.clear();
            companyFilterCombo.addItem("All Companies");
            companyComboIdMap.put("All Companies", "ALL");

            String selectedLabel = "All Companies";
            for (Company c : allCompanies) {
                String label = c.getName();
                companyFilterCombo.addItem(label);
                companyComboIdMap.put(label, c.getId());
                if (c.getId().equals(pendingCompanyFilterId)) {
                    selectedLabel = label;
                }
            }
            companyFilterCombo.setSelectedItem(selectedLabel);
        } finally {
            suppressFilters = false;
        }
    }

    private void applyFiltersAndRender() {
        cardsContainer.removeAll();
        User actor = AppSession.getInstance().getCurrentUser();

        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        String compLabel = companyFilterCombo != null && companyFilterCombo.getSelectedItem() != null
                ? companyFilterCombo.getSelectedItem().toString() : "All Companies";
        String compId = companyComboIdMap.getOrDefault(compLabel, "ALL");
        String status = statusFilterCombo != null && statusFilterCombo.getSelectedItem() != null
                ? statusFilterCombo.getSelectedItem().toString() : "All Statuses";

        List<PlacementDrive> filtered = new ArrayList<>();
        for (PlacementDrive d : allDrives) {
            boolean matchQ = q.isEmpty()
                    || safeLower(d.getCompanyName()).contains(q)
                    || safeLower(d.getJobRole()).contains(q)
                    || String.join(", ", d.getEligibleBranches()).toLowerCase(Locale.ROOT).contains(q);
            boolean matchComp = "ALL".equals(compId) || compId.equals(d.getCompanyId());
            boolean matchStatus = "All Statuses".equals(status) || status.equalsIgnoreCase(d.getStatus());
            if (matchQ && matchComp && matchStatus) {
                filtered.add(d);
            }
        }

        subtitleLabel.setText("Campus recruitment drives · Showing " + filtered.size() + " of " + allDrives.size() + " drives in MySQL.");

        if (filtered.isEmpty()) {
            RoundedCard empty = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
            empty.setBorder(new EmptyBorder(40, 24, 40, 24));
            empty.setLayout(new GridBagLayout());
            JLabel lbl = new JLabel("No placement drives match your filter criteria.");
            lbl.setFont(ThemeManager.font(Font.PLAIN, 13));
            lbl.setForeground(ThemeManager.mutedText());
            empty.add(lbl);
            cardsContainer.add(empty);
        } else {
            for (PlacementDrive d : filtered) {
                cardsContainer.add(buildDriveCard(d, actor));
                cardsContainer.add(Box.createVerticalStrut(12));
            }
        }

        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    private JPanel buildDriveCard(PlacementDrive d, User actor) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder(), ThemeManager.BLUE_ELECTRIC, 1.0f);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Top row: Company + Status badge + Package LPA
        JPanel topRow = new JPanel(new BorderLayout(12, 0));
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel leftTop = new JPanel();
        leftTop.setOpaque(false);
        leftTop.setLayout(new BoxLayout(leftTop, BoxLayout.Y_AXIS));

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        nameRow.setOpaque(false);
        JLabel compName = new JLabel(d.getCompanyName());
        compName.setFont(ThemeManager.font(Font.BOLD, 16));
        compName.setForeground(ThemeManager.primaryText());
        nameRow.add(compName);
        nameRow.add(statusBadge(d.getStatus()));

        JLabel roleLbl = new JLabel(d.getJobRole());
        roleLbl.setFont(ThemeManager.font(Font.BOLD, 13));
        roleLbl.setForeground(ThemeManager.CYAN_DARK);

        JLabel descLbl = new JLabel("<html><body style='width:680px;'>"
                + esc(d.getJobDescription() != null && !d.getJobDescription().isBlank() ? d.getJobDescription() : d.getSelectionProcess())
                + "</body></html>");
        descLbl.setFont(ThemeManager.font(Font.PLAIN, 12));
        descLbl.setForeground(ThemeManager.secondaryText());

        leftTop.add(nameRow);
        leftTop.add(Box.createVerticalStrut(3));
        leftTop.add(roleLbl);
        leftTop.add(Box.createVerticalStrut(4));
        leftTop.add(descLbl);

        JPanel rightTop = new JPanel();
        rightTop.setOpaque(false);
        rightTop.setLayout(new BoxLayout(rightTop, BoxLayout.Y_AXIS));
        JLabel lpaLbl = new JLabel(String.format(Locale.US, "%.1f LPA", d.getSalaryPackageLpa()));
        lpaLbl.setFont(ThemeManager.font(Font.BOLD, 18));
        lpaLbl.setForeground(ThemeManager.primaryText());
        lpaLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JLabel vacLbl = new JLabel(d.getAvailablePositions() + (d.getAvailablePositions() == 1 ? " Vacancy" : " Vacancies"));
        vacLbl.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        vacLbl.setForeground(ThemeManager.mutedText());
        vacLbl.setAlignmentX(Component.RIGHT_ALIGNMENT);
        rightTop.add(lpaLbl);
        rightTop.add(Box.createVerticalStrut(2));
        rightTop.add(vacLbl);

        topRow.add(leftTop, BorderLayout.CENTER);
        topRow.add(rightTop, BorderLayout.EAST);

        // Criteria strip
        JPanel criteriaStrip = new JPanel(new GridLayout(1, 5, 10, 0));
        criteriaStrip.setBackground(ThemeManager.cardSubtleBg());
        criteriaStrip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.cardBorder()),
                new EmptyBorder(8, 12, 8, 12)
        ));
        criteriaStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        criteriaStrip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        criteriaStrip.add(metricCell("Min CGPA", String.format(Locale.US, "%.2f", d.getMinCgpa())));
        criteriaStrip.add(metricCell("Max Backlogs", String.valueOf(d.getMaxBacklogsAllowed())));
        criteriaStrip.add(metricCell("Eligible Branches", d.getEligibleBranches().isEmpty() ? "All" : String.join(", ", d.getEligibleBranches())));
        criteriaStrip.add(metricCell("Deadline", d.getApplicationDeadline() != null ? d.getApplicationDeadline() : "—"));
        criteriaStrip.add(metricCell("Drive Date", d.getDriveDate() != null ? d.getDriveDate() : "—"));

        // Required skills chips
        JPanel skillsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        skillsRow.setOpaque(false);
        skillsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel skTag = new JLabel("Required Skills:");
        skTag.setFont(ThemeManager.font(Font.BOLD, 11));
        skTag.setForeground(ThemeManager.mutedText());
        skillsRow.add(skTag);

        if (d.getRequiredSkills().isEmpty()) {
            JLabel noSk = new JLabel("None specified");
            noSk.setFont(ThemeManager.font(Font.PLAIN, 11));
            noSk.setForeground(ThemeManager.mutedText());
            skillsRow.add(noSk);
        } else {
            for (DriveRequiredSkill req : d.getRequiredSkills()) {
                String name = resolveSkillName(req.getSkillId());
                String txt = name + " · L" + req.getMinProficiency() + (req.isMandatory() ? " *" : "");
                BadgeLabel chip = new BadgeLabel(
                        txt,
                        ThemeManager.cardSubtleBg(),
                        ThemeManager.primaryText(),
                        ThemeManager.cardBorder(),
                        8
                );
                skillsRow.add(chip);
            }
        }

        // Count active applications for this drive
        int appCount = 0;
        for (Application a : allApplications) {
            if (d.getId().equals(a.getDriveId()) && a.getCurrentStage() != ApplicationStage.WITHDRAWN) {
                appCount++;
            }
        }

        // Footer row
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel meta = new JLabel("Applicants: " + appCount + "   ·   Class of " + d.getTargetGraduationYear()
                + (d.getSelectionProcess() != null && !d.getSelectionProcess().isBlank() ? "   ·   " + d.getSelectionProcess() : ""));
        meta.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        meta.setForeground(ThemeManager.mutedText());

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        ActionButton eligBtn = new ActionButton("Check Eligibility", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 8);
        eligBtn.addActionListener(e -> {
            if (onCheckEligibility != null) onCheckEligibility.accept(d.getId());
        });

        ActionButton gapBtn = new ActionButton("Skill Gap", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 8);
        gapBtn.addActionListener(e -> {
            if (onOpenSkillGap != null) onOpenSkillGap.accept(d.getId());
        });

        ActionButton appsBtn = new ActionButton("Applications (" + appCount + ")", ThemeManager.CYAN_DARK, ThemeManager.CYAN_DARK.darker(), Color.WHITE, null, 8);
        appsBtn.addActionListener(e -> {
            if (onViewApplications != null) onViewApplications.accept(d.getId());
        });

        btns.add(eligBtn);
        btns.add(gapBtn);
        btns.add(appsBtn);

        boolean canEditDrive = actor != null && (actor.getRole().isPrivilegedRole()
                || (actor.getRole() == UserRole.COMPANY && actor.getCompanyId() != null && actor.getCompanyId().equals(d.getCompanyId())));
        boolean canDeleteDrive = actor != null && actor.getRole().isPrivilegedRole();

        if (canEditDrive) {
            ActionButton editBtn = new ActionButton("Edit", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 8);
            editBtn.addActionListener(e -> openEditDriveDialog(d));
            btns.add(editBtn);
        }
        if (canDeleteDrive) {
            ActionButton delBtn = new ActionButton("Delete", ThemeManager.ROSE_SOFT, new Color(0xFE, 0xCA, 0xCA), ThemeManager.ROSE_PRIMARY, new Color(0xFC, 0xA5, 0xA5), 8);
            delBtn.addActionListener(e -> deleteDrive(d));
            btns.add(delBtn);
        }

        footer.add(meta, BorderLayout.WEST);
        footer.add(btns, BorderLayout.EAST);

        card.add(topRow);
        card.add(Box.createVerticalStrut(10));
        card.add(criteriaStrip);
        card.add(Box.createVerticalStrut(8));
        card.add(skillsRow);
        card.add(Box.createVerticalStrut(8));
        card.add(footer);
        return card;
    }

    private BadgeLabel statusBadge(String status) {
        String st = status != null ? status.toUpperCase(Locale.ROOT) : "OPEN";
        return switch (st) {
            case "OPEN" -> new BadgeLabel("OPEN", ThemeManager.EMERALD_SOFT, ThemeManager.EMERALD_PRIMARY, ThemeManager.EMERALD_PRIMARY, 8);
            case "UPCOMING", "PLANNED" -> new BadgeLabel(st, new Color(219, 234, 254), ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_ELECTRIC, 8);
            case "CLOSED", "CANCELLED" -> new BadgeLabel(st, ThemeManager.ROSE_SOFT, ThemeManager.ROSE_PRIMARY, ThemeManager.ROSE_PRIMARY, 8);
            default -> new BadgeLabel(st, ThemeManager.cardSubtleBg(), ThemeManager.secondaryText(), ThemeManager.cardBorder(), 8);
        };
    }

    private JPanel metricCell(String label, String val) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel k = new JLabel(label);
        k.setFont(ThemeManager.font(Font.PLAIN, 10.5f));
        k.setForeground(ThemeManager.mutedText());
        JLabel v = new JLabel(val);
        v.setFont(ThemeManager.font(Font.BOLD, 12));
        v.setForeground(ThemeManager.primaryText());
        v.setToolTipText(val);
        p.add(k);
        p.add(Box.createVerticalStrut(2));
        p.add(v);
        return p;
    }

    private String resolveSkillName(String skillId) {
        for (Skill s : allSkills) {
            if (s.getId().equals(skillId)) return s.getName();
        }
        return skillId;
    }

    private void openAddDriveDialog() {
        User actor = AppSession.getInstance().getCurrentUser();
        if (allCompanies.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Register at least one partner company before creating a placement drive.",
                    "No Partner Companies", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        DriveFormDialog dlg = new DriveFormDialog(owner, null, allCompanies, allSkills, actor);
        PlacementDrive draft = dlg.showDialog();
        if (draft == null) return;

        controller.createDriveAsync(actor, draft, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Drive Published", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Drive Creation Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void openEditDriveDialog(PlacementDrive target) {
        User actor = AppSession.getInstance().getCurrentUser();
        Window owner = SwingUtilities.getWindowAncestor(this);
        DriveFormDialog dlg = new DriveFormDialog(owner, target, allCompanies, allSkills, actor);
        PlacementDrive updated = dlg.showDialog();
        if (updated == null) return;

        controller.updateDriveAsync(actor, updated, res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Drive Updated", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Drive Update Failed", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void deleteDrive(PlacementDrive d) {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete placement drive for \"" + d.getCompanyName() + " — " + d.getJobRole() + "\"?\n"
                        + "(Will be rejected if student applications reference this drive.)",
                "Confirm Drive Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        controller.deleteDriveAsync(AppSession.getInstance().getCurrentUser(), d.getId(), res -> {
            if (res.success) {
                JOptionPane.showMessageDialog(this, res.message, "Drive Deleted", JOptionPane.INFORMATION_MESSAGE);
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, res.message, "Cannot Delete Drive", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
