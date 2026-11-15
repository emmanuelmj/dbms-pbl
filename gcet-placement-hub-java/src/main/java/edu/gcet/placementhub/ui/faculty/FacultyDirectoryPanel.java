package edu.gcet.placementhub.ui.faculty;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.StudentController;
import edu.gcet.placementhub.models.Faculty;
import edu.gcet.placementhub.models.User;
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
import java.util.*;
import java.util.List;

/**
 * Real MySQL JDBC Faculty Coordinator Directory (`faculty` + `users` tables)
 * displaying departmental assignments, verification authorities, and approval statuses.
 */
public class FacultyDirectoryPanel extends JPanel {

    private final StudentController controller = new StudentController();

    private List<Faculty> allFaculty = List.of();
    private List<Faculty> filteredFaculty = List.of();

    private JLabel subtitleLabel;
    private AlertBanner errorBanner;
    private JTextField searchField;
    private JComboBox<String> deptCombo;
    private JComboBox<String> statusCombo;

    private JLabel totalValLbl;
    private JLabel activeValLbl;
    private JLabel pendingValLbl;
    private JLabel deptCountLbl;

    private JTable table;
    private DefaultTableModel tableModel;
    private JPanel detailCardBody;
    private boolean suppressFilters = false;

    public FacultyDirectoryPanel() {
        setLayout(new BorderLayout(0, 14));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(20, 22, 22, 22));
        buildUi();
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        subtitleLabel.setText("Loading faculty coordinator records from MySQL...");
        controller.loadFacultyDirectoryAsync(actor, res -> {
            if (!res.success) {
                errorBanner.showError(res.message);
                this.allFaculty = List.of();
            } else {
                errorBanner.clear();
                this.allFaculty = res.data != null ? res.data : List.of();
            }
            populateDeptFilter();
            updateStats();
            applyFilters();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        removeAll();
        buildUi();
        populateDeptFilter();
        updateStats();
        applyFilters();
        revalidate();
        repaint();
    }

    private void buildUi() {
        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel tCol = new JPanel();
        tCol.setOpaque(false);
        tCol.setLayout(new BoxLayout(tCol, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("GCET Faculty Coordinator Directory");
        title.setFont(ThemeManager.font(Font.BOLD, 20));
        title.setForeground(ThemeManager.primaryText());
        subtitleLabel = new JLabel("Departmental mentors & skill/student verification coordinators in MySQL.");
        subtitleLabel.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        subtitleLabel.setForeground(ThemeManager.mutedText());
        tCol.add(title);
        tCol.add(Box.createVerticalStrut(3));
        tCol.add(subtitleLabel);

        ActionButton refreshBtn = new ActionButton("↻  Refresh MySQL", ThemeManager.cardBg(), ThemeManager.cardSubtleBg(), ThemeManager.primaryText(), ThemeManager.cardBorder(), 10);
        refreshBtn.addActionListener(e -> loadData());
        header.add(tCol, BorderLayout.WEST);
        header.add(refreshBtn, BorderLayout.EAST);

        // KPI strip
        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 12, 0));
        kpiGrid.setOpaque(false);
        totalValLbl = new JLabel("0");
        activeValLbl = new JLabel("0");
        pendingValLbl = new JLabel("0");
        deptCountLbl = new JLabel("0");
        kpiGrid.add(statCard("Total Faculty Records", totalValLbl, ThemeManager.BLUE_ELECTRIC));
        kpiGrid.add(statCard("Approved & Active", activeValLbl, ThemeManager.EMERALD_PRIMARY));
        kpiGrid.add(statCard("Pending Admin Approval", pendingValLbl, ThemeManager.AMBER_PRIMARY));
        kpiGrid.add(statCard("Departments Covered", deptCountLbl, ThemeManager.CYAN_DARK));

        // Filters
        RoundedCard filterCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new BoxLayout(filterCard, BoxLayout.Y_AXIS));
        filterCard.setBorder(new EmptyBorder(12, 16, 12, 16));
        errorBanner = new AlertBanner();
        errorBanner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel fRow = new JPanel(new GridLayout(1, 3, 12, 0));
        fRow.setOpaque(false);
        fRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        fRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search Name, Employee ID, Username, Email...");
        searchField.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { if (!suppressFilters) applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { if (!suppressFilters) applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { if (!suppressFilters) applyFilters(); }
        });

        deptCombo = new JComboBox<>(new String[]{"All Departments"});
        deptCombo.addActionListener(e -> { if (!suppressFilters) applyFilters(); });

        statusCombo = new JComboBox<>(new String[]{"All Statuses", "APPROVED", "ACTIVE", "PENDING", "REJECTED"});
        statusCombo.addActionListener(e -> { if (!suppressFilters) applyFilters(); });

        fRow.add(searchField);
        fRow.add(deptCombo);
        fRow.add(statusCombo);

        filterCard.add(errorBanner);
        filterCard.add(Box.createVerticalStrut(4));
        filterCard.add(fRow);

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        kpiGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(header);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(kpiGrid);
        topStack.add(Box.createVerticalStrut(12));
        topStack.add(filterCard);

        // Table + Detail Card
        RoundedCard tableCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tableCard.setLayout(new BorderLayout(0, 10));
        tableCard.setBorder(new EmptyBorder(12, 14, 12, 14));

        String[] cols = {"Employee ID", "Faculty Name", "Designation", "Primary Dept", "Authorized Scope", "Email", "Status"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(tableModel);
        table.setRowHeight(34);
        table.setFont(ThemeManager.font(Font.PLAIN, 12.5f));
        table.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 12));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.getSelectionModel().addListSelectionListener(e -> renderSelectedFacultyDetails());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeManager.cardBorder()));

        RoundedCard detailWrap = new RoundedCard(12, ThemeManager.cardSubtleBg(), ThemeManager.cardBorder());
        detailWrap.setLayout(new BorderLayout());
        detailWrap.setBorder(new EmptyBorder(12, 16, 12, 16));
        detailWrap.setPreferredSize(new Dimension(600, 128));
        detailCardBody = new JPanel(new BorderLayout(16, 6));
        detailCardBody.setOpaque(false);
        detailWrap.add(detailCardBody, BorderLayout.CENTER);

        tableCard.add(scroll, BorderLayout.CENTER);
        tableCard.add(detailWrap, BorderLayout.SOUTH);

        add(topStack, BorderLayout.NORTH);
        add(tableCard, BorderLayout.CENTER);
    }

    private JPanel statCard(String title, JLabel valLabel, Color accent) {
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        JLabel t = new JLabel(title);
        t.setFont(ThemeManager.font(Font.BOLD, 11.5f));
        t.setForeground(ThemeManager.mutedText());
        valLabel.setFont(ThemeManager.font(Font.BOLD, 22));
        valLabel.setForeground(accent);
        card.add(t);
        card.add(Box.createVerticalStrut(4));
        card.add(valLabel);
        return card;
    }

    private void populateDeptFilter() {
        suppressFilters = true;
        try {
            Object prev = deptCombo.getSelectedItem();
            Set<String> depts = new TreeSet<>();
            for (Faculty f : allFaculty) {
                if (f.getDepartment() != null && !f.getDepartment().isBlank()) depts.add(f.getDepartment());
            }
            deptCombo.removeAllItems();
            deptCombo.addItem("All Departments");
            for (String d : depts) deptCombo.addItem(d);
            if (prev != null) deptCombo.setSelectedItem(prev);
        } finally {
            suppressFilters = false;
        }
    }

    private void updateStats() {
        int total = allFaculty.size();
        int active = 0;
        int pending = 0;
        Set<String> depts = new HashSet<>();
        for (Faculty f : allFaculty) {
            if ("PENDING".equalsIgnoreCase(f.getStatus()) || "PENDING_APPROVAL".equalsIgnoreCase(f.getStatus())) {
                pending++;
            } else if ("APPROVED".equalsIgnoreCase(f.getStatus()) || "ACTIVE".equalsIgnoreCase(f.getStatus())) {
                active++;
            }
            if (f.getDepartment() != null && !f.getDepartment().isBlank()) {
                depts.add(f.getDepartment().trim());
            }
        }
        totalValLbl.setText(String.valueOf(total));
        activeValLbl.setText(String.valueOf(active));
        pendingValLbl.setText(String.valueOf(pending));
        deptCountLbl.setText(String.valueOf(depts.size()));
    }

    private void applyFilters() {
        String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
        String dept = deptCombo.getSelectedItem() != null ? deptCombo.getSelectedItem().toString() : "All Departments";
        String st = statusCombo.getSelectedItem() != null ? statusCombo.getSelectedItem().toString() : "All Statuses";

        List<Faculty> list = new ArrayList<>();
        for (Faculty f : allFaculty) {
            boolean matchQ = q.isEmpty()
                    || safeLower(f.getFullName()).contains(q)
                    || safeLower(f.getEmployeeId()).contains(q)
                    || safeLower(f.getUsername()).contains(q)
                    || safeLower(f.getEmail()).contains(q);
            boolean matchDept = "All Departments".equals(dept) || dept.equalsIgnoreCase(f.getDepartment());
            boolean matchSt = "All Statuses".equals(st) || st.equalsIgnoreCase(f.getStatus());
            if (matchQ && matchDept && matchSt) list.add(f);
        }

        this.filteredFaculty = list;
        subtitleLabel.setText("Showing " + list.size() + " of " + allFaculty.size() + " faculty coordinators in MySQL.");

        tableModel.setRowCount(0);
        for (Faculty f : filteredFaculty) {
            String scope = f.getAuthorizedDepartments().isEmpty() ? f.getDepartment() : String.join(", ", f.getAuthorizedDepartments());
            tableModel.addRow(new Object[]{
                    nonNull(f.getEmployeeId()),
                    nonNull(f.getFullName()),
                    nonNull(f.getDesignation()),
                    nonNull(f.getDepartment()),
                    scope,
                    nonNull(f.getEmail()),
                    nonNull(f.getStatus())
            });
        }
        if (!filteredFaculty.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        renderSelectedFacultyDetails();
    }

    private void renderSelectedFacultyDetails() {
        detailCardBody.removeAll();
        int row = table.getSelectedRow();
        if (row < 0 || row >= filteredFaculty.size()) {
            JLabel empty = new JLabel("Select any faculty coordinator row above to inspect authorized verification branches and approval audit metadata.");
            empty.setFont(ThemeManager.font(Font.PLAIN, 12));
            empty.setForeground(ThemeManager.mutedText());
            detailCardBody.add(empty, BorderLayout.CENTER);
        } else {
            Faculty f = filteredFaculty.get(row);
            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

            JLabel name = new JLabel(nonNull(f.getFullName()) + "  (@" + nonNull(f.getUsername()) + ")");
            name.setFont(ThemeManager.font(Font.BOLD, 14));
            name.setForeground(ThemeManager.primaryText());

            JLabel meta = new JLabel(nonNull(f.getDesignation()) + "  ·  Dept: " + nonNull(f.getDepartment())
                    + "  ·  Phone: " + (f.getPhone() != null && !f.getPhone().isBlank() ? f.getPhone() : "On Campus"));
            meta.setFont(ThemeManager.font(Font.PLAIN, 12));
            meta.setForeground(ThemeManager.secondaryText());

            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
            chips.setOpaque(false);
            JLabel authLbl = new JLabel("Authorized Branches:");
            authLbl.setFont(ThemeManager.font(Font.BOLD, 11));
            authLbl.setForeground(ThemeManager.mutedText());
            chips.add(authLbl);
            for (String d : f.getAuthorizedDepartments()) {
                chips.add(new BadgeLabel(d, new Color(38, 132, 255, 25), ThemeManager.BLUE_ELECTRIC, null, 8));
            }

            left.add(name);
            left.add(Box.createVerticalStrut(3));
            left.add(meta);
            left.add(Box.createVerticalStrut(4));
            left.add(chips);

            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            boolean approved = "APPROVED".equalsIgnoreCase(f.getStatus()) || "ACTIVE".equalsIgnoreCase(f.getStatus());
            BadgeLabel stBadge = new BadgeLabel(
                    approved ? "VERIFIED COORDINATOR" : nonNull(f.getStatus()),
                    approved ? new Color(16, 185, 129, 25) : new Color(217, 119, 6, 25),
                    approved ? ThemeManager.EMERALD_PRIMARY : ThemeManager.AMBER_PRIMARY,
                    null,
                    999
            );
            JLabel appBy = new JLabel("Approved By: " + (f.getApprovedByName() != null && !f.getApprovedByName().isBlank() ? f.getApprovedByName() : "College Administration"));
            appBy.setFont(ThemeManager.font(Font.PLAIN, 11));
            appBy.setForeground(ThemeManager.mutedText());
            right.add(stBadge);
            right.add(Box.createVerticalStrut(6));
            right.add(appBy);

            detailCardBody.add(left, BorderLayout.CENTER);
            detailCardBody.add(right, BorderLayout.EAST);
        }
        detailCardBody.revalidate();
        detailCardBody.repaint();
    }

    private static String nonNull(String s) { return s == null ? "-" : s; }
    private static String safeLower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
}
