package edu.gcet.placementhub.ui.reports;

import edu.gcet.placementhub.config.AppSession;
import edu.gcet.placementhub.config.ThemeManager;
import edu.gcet.placementhub.controllers.GovernanceController;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.GovernanceModels.*;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.ReportExporter;
import edu.gcet.placementhub.utils.ReportExporter.ReportKind;
import edu.gcet.placementhub.utils.UiComponents.*;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 5 Institutional Analytics & Reports Center (Java Swing + JFreeChart 1.5.5 + OpenPDF + CSV).
 * Computes every chart & report table strictly from live MySQL records with zero fabricated data.
 */
public class ReportsCenterPanel extends JPanel {

    private enum ReportTab {
        DEPARTMENT_ELIGIBLE,
        PIPELINE,
        COMPANIES,
        DRIVES,
        SKILLS,
        STUDENTS
    }

    private final GovernanceController controller = new GovernanceController();
    private ReportBundle bundle;
    private ReportTab activeTab = ReportTab.DEPARTMENT_ELIGIBLE;
    private JFreeChart activeChart;

    private final JComboBox<String> academicYearBox;
    private final JComboBox<String> gradYearBox;
    private final JComboBox<String> branchBox;
    private final JTextField fromDateField;
    private final JTextField toDateField;
    private final JComboBox<ReportKind> exportKindBox;

    private final JLabel scopeLabel = new JLabel("Loading role scope...");
    private final AlertBanner alertBanner = new AlertBanner();

    private final JLabel kpiTotalStu = new JLabel("0");
    private final JLabel kpiEligibleStu = new JLabel("0");
    private final JLabel kpiPlacedStu = new JLabel("0 (0.0%)");
    private final JLabel kpiAvgPkg = new JLabel("0.00 LPA");
    private final JLabel kpiPeakPkg = new JLabel("0.00 LPA");

    private final JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
    private final JPanel bodySlot = new JPanel(new BorderLayout());

    public ReportsCenterPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(ThemeManager.surfaceBg());
        setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel topStack = new JPanel();
        topStack.setOpaque(false);
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));

        // 1. Header + Export Controls
        RoundedCard headerCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        headerCard.setLayout(new BorderLayout(12, 6));
        headerCard.setBorder(new EmptyBorder(14, 18, 14, 18));
        headerCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel hdrLeft = new JPanel();
        hdrLeft.setOpaque(false);
        hdrLeft.setLayout(new BoxLayout(hdrLeft, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("▥  Institutional Placement Analytics & Reports Center");
        title.setFont(ThemeManager.font(Font.BOLD, 18));
        title.setForeground(ThemeManager.primaryText());
        scopeLabel.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        scopeLabel.setForeground(ThemeManager.CYAN_DARK);
        hdrLeft.add(title);
        hdrLeft.add(Box.createVerticalStrut(3));
        hdrLeft.add(scopeLabel);

        JPanel exportBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        exportBar.setOpaque(false);
        exportKindBox = new JComboBox<>(ReportKind.values());
        exportKindBox.setPreferredSize(new Dimension(225, 34));
        exportKindBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ReportKind rk) lbl.setText(rk.getTitle());
                return lbl;
            }
        });

        ActionButton csvBtn = new ActionButton("⇩  Export CSV", ThemeManager.EMERALD_PRIMARY, new Color(0x04, 0x78, 0x57), Color.WHITE, null, 10);
        csvBtn.addActionListener(e -> exportSelectedReport(false));

        ActionButton pdfBtn = new ActionButton("⇩  Export GCET PDF", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 10);
        pdfBtn.addActionListener(e -> exportSelectedReport(true));

        exportBar.add(exportKindBox);
        exportBar.add(csvBtn);
        exportBar.add(pdfBtn);

        headerCard.add(hdrLeft, BorderLayout.CENTER);
        headerCard.add(exportBar, BorderLayout.EAST);
        topStack.add(headerCard);
        topStack.add(Box.createVerticalStrut(10));

        // 2. Filters Card (Academic Year, Graduation Year, Branch, Date Range)
        RoundedCard filterCard = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        filterCard.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filterCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        academicYearBox = new JComboBox<>(new String[]{"ALL", "1", "2", "3", "4"});
        academicYearBox.setPreferredSize(new Dimension(85, 32));

        gradYearBox = new JComboBox<>(new String[]{"ALL", "2024", "2025", "2026", "2027", "2028"});
        gradYearBox.setPreferredSize(new Dimension(95, 32));

        List<String> branches = new ArrayList<>();
        branches.add("ALL");
        branches.addAll(DeptNormalizer.GCET_BRANCHES);
        branchBox = new JComboBox<>(branches.toArray(new String[0]));
        branchBox.setPreferredSize(new Dimension(160, 32));

        fromDateField = createStyledTextField("YYYY-MM-DD");
        fromDateField.setPreferredSize(new Dimension(115, 32));
        toDateField = createStyledTextField("YYYY-MM-DD");
        toDateField.setPreferredSize(new Dimension(115, 32));

        ActionButton applyBtn = new ActionButton("Apply Filters", ThemeManager.BLUE_ELECTRIC, ThemeManager.BLUE_HOVER, Color.WHITE, null, 9);
        applyBtn.addActionListener(e -> loadData());

        ActionButton resetBtn = new ActionButton("Reset", ThemeManager.cardSubtleBg(), ThemeManager.cardBorder(), ThemeManager.secondaryText(), ThemeManager.cardBorder(), 9);
        resetBtn.addActionListener(e -> {
            academicYearBox.setSelectedIndex(0);
            gradYearBox.setSelectedIndex(0);
            branchBox.setSelectedIndex(0);
            fromDateField.setText("");
            toDateField.setText("");
            loadData();
        });

        filterCard.add(inlineLbl("Academic Yr:"));
        filterCard.add(academicYearBox);
        filterCard.add(inlineLbl("Grad Yr:"));
        filterCard.add(gradYearBox);
        filterCard.add(inlineLbl("Branch:"));
        filterCard.add(branchBox);
        filterCard.add(inlineLbl("From:"));
        filterCard.add(fromDateField);
        filterCard.add(inlineLbl("To:"));
        filterCard.add(toDateField);
        filterCard.add(applyBtn);
        filterCard.add(resetBtn);

        topStack.add(filterCard);
        topStack.add(Box.createVerticalStrut(10));

        // 3. KPI Summary Strip
        JPanel kpis = new JPanel(new GridLayout(1, 5, 10, 10));
        kpis.setOpaque(false);
        kpis.setAlignmentX(Component.LEFT_ALIGNMENT);
        kpis.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        kpis.add(kpiCard("TOTAL STUDENTS", kpiTotalStu, ThemeManager.primaryText()));
        kpis.add(kpiCard("ACADEMICALLY ELIGIBLE", kpiEligibleStu, ThemeManager.BLUE_ELECTRIC));
        kpis.add(kpiCard("PLACED STUDENTS", kpiPlacedStu, ThemeManager.EMERALD_PRIMARY));
        kpis.add(kpiCard("AVERAGE PACKAGE", kpiAvgPkg, ThemeManager.CYAN_DARK));
        kpis.add(kpiCard("HIGHEST PACKAGE", kpiPeakPkg, ThemeManager.AMBER_PRIMARY));
        topStack.add(kpis);
        topStack.add(Box.createVerticalStrut(8));

        alertBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(alertBanner);

        tabBar.setOpaque(false);
        tabBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        topStack.add(tabBar);

        add(topStack, BorderLayout.NORTH);

        bodySlot.setOpaque(false);
        add(bodySlot, BorderLayout.CENTER);
    }

    public void loadData() {
        User actor = AppSession.getInstance().getCurrentUser();
        if (actor == null) return;
        alertBanner.clear();
        ReportFilter filter = new ReportFilter(
                (String) academicYearBox.getSelectedItem(),
                (String) gradYearBox.getSelectedItem(),
                (String) branchBox.getSelectedItem(),
                fromDateField.getText().trim(),
                toDateField.getText().trim()
        );

        controller.loadReportBundleAsync(actor, filter, b -> {
            this.bundle = b;
            if (b.error() != null && !b.error().isBlank()) {
                alertBanner.showError(b.error());
            }
            scopeLabel.setText(b.scopeNotice());
            kpiTotalStu.setText(String.valueOf(b.totalStudents()));
            kpiEligibleStu.setText(String.valueOf(b.eligibleStudents()));
            kpiPlacedStu.setText(b.placedStudents() + " (" + String.format(Locale.US, "%.1f%%", b.placementRatePct()) + ")");
            kpiAvgPkg.setText(String.format(Locale.US, "%.2f LPA", b.avgPackageLpa()));
            kpiPeakPkg.setText(String.format(Locale.US, "%.2f LPA", b.highestPackageLpa()));
            renderActiveTab();
        });
    }

    public void refreshTheme() {
        setBackground(ThemeManager.surfaceBg());
        renderActiveTab();
    }

    private void renderActiveTab() {
        tabBar.removeAll();
        tabBar.add(tabBtn("Department & Eligible vs Placed", ReportTab.DEPARTMENT_ELIGIBLE));
        tabBar.add(tabBtn("Application Pipeline", ReportTab.PIPELINE));
        tabBar.add(tabBtn("Company Participation", ReportTab.COMPANIES));
        tabBar.add(tabBtn("Placement Drives", ReportTab.DRIVES));
        tabBar.add(tabBtn("Skill Distribution", ReportTab.SKILLS));
        tabBar.add(tabBtn("Student Placement Roster", ReportTab.STUDENTS));
        tabBar.revalidate();
        tabBar.repaint();

        bodySlot.removeAll();
        if (bundle == null) {
            bodySlot.add(emptyState("Loading MySQL analytics...", "Querying real student, company, drive, and application tables."), BorderLayout.CENTER);
            bodySlot.revalidate();
            bodySlot.repaint();
            return;
        }

        JPanel panel = switch (activeTab) {
            case DEPARTMENT_ELIGIBLE -> buildDepartmentView();
            case PIPELINE -> buildPipelineView();
            case COMPANIES -> buildCompanyView();
            case DRIVES -> buildDriveView();
            case SKILLS -> buildSkillView();
            case STUDENTS -> buildStudentRosterView();
        };
        bodySlot.add(panel, BorderLayout.CENTER);
        bodySlot.revalidate();
        bodySlot.repaint();
    }

    private ActionButton tabBtn(String text, ReportTab tab) {
        boolean sel = activeTab == tab;
        ActionButton b = new ActionButton(
                text,
                sel ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardBg(),
                sel ? ThemeManager.BLUE_HOVER : ThemeManager.cardSubtleBg(),
                sel ? Color.WHITE : ThemeManager.primaryText(),
                sel ? ThemeManager.BLUE_ELECTRIC : ThemeManager.cardBorder(),
                10
        );
        b.setMargin(new Insets(6, 12, 6, 12));
        b.addActionListener(e -> {
            activeTab = tab;
            renderActiveTab();
        });
        return b;
    }

    // -------------------------------------------------------------------------
    // SUB-VIEWS (JFREECHART + TABLES)
    // -------------------------------------------------------------------------

    private JPanel buildDepartmentView() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        for (BranchReportRow b : bundle.branchRows()) {
            ds.addValue(b.totalStudents(), "Registered", b.branch());
            ds.addValue(b.eligibleStudents(), "Eligible", b.branch());
            ds.addValue(b.placedStudents(), "Placed", b.branch());
        }
        activeChart = buildStyledBarChart(
                "Department-Wise Placement & Eligible vs Placed Students",
                "Department / Branch",
                "Student Count",
                ds,
                new Color[]{ThemeManager.BLUE_ELECTRIC, ThemeManager.CYAN_ACCENT, ThemeManager.EMERALD_PRIMARY}
        );

        String[] cols = {"Department", "Total", "Eligible", "Placed", "Unplaced", "Placement %", "Avg CGPA", "Avg LPA", "Peak LPA"};
        DefaultTableModel m = readOnlyModel(cols);
        for (BranchReportRow b : bundle.branchRows()) {
            m.addRow(new Object[]{
                    b.branch(), b.totalStudents(), b.eligibleStudents(), b.placedStudents(),
                    b.unplacedStudents(), String.format(Locale.US, "%.1f%%", b.placementRatePct()),
                    String.format(Locale.US, "%.2f", b.avgCgpa()),
                    String.format(Locale.US, "%.2f", b.avgPackageLpa()),
                    String.format(Locale.US, "%.2f", b.highestPackageLpa())
            });
        }
        return splitChartAndTable(activeChart, m, "No department student records match the active filters.");
    }

    private JPanel buildPipelineView() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        int nonZero = 0;
        for (PipelineReportRow p : bundle.pipelineRows()) {
            ds.addValue(p.count(), "Applications", shortStage(p.stageLabel()));
            if (p.count() > 0) nonZero++;
        }
        activeChart = nonZero == 0 ? null : buildStyledBarChart(
                "Recruitment Application Pipeline Stage Funnel",
                "Pipeline Stage",
                "Applications",
                ds,
                new Color[]{ThemeManager.BLUE_ELECTRIC}
        );

        String[] cols = {"Pipeline Stage", "Stage Code", "Applications Count", "Share (%)"};
        DefaultTableModel m = readOnlyModel(cols);
        for (PipelineReportRow p : bundle.pipelineRows()) {
            m.addRow(new Object[]{
                    p.stageLabel(), p.stageCode(), p.count(), String.format(Locale.US, "%.1f%%", p.percentage())
            });
        }
        return splitChartAndTable(activeChart, m, "No applications recorded for the selected filters.");
    }

    private JPanel buildCompanyView() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        int shown = 0;
        for (CompanyReportRow c : bundle.companyRows()) {
            if (shown >= 12) break;
            ds.addValue(c.drivesCount(), "Drives Held", c.companyName());
            ds.addValue(c.offersCount(), "Offers Made", c.companyName());
            shown++;
        }
        activeChart = bundle.companyRows().isEmpty() ? null : buildStyledBarChart(
                "Corporate Hiring Participation (Drives & Offers)",
                "Corporate Partner",
                "Count",
                ds,
                new Color[]{ThemeManager.PURPLE_PRIMARY, ThemeManager.EMERALD_PRIMARY}
        );

        String[] cols = {"Company Partner", "Industry", "Tier", "Approval", "Drives", "Applicants", "Offers", "Max Package (LPA)"};
        DefaultTableModel m = readOnlyModel(cols);
        for (CompanyReportRow c : bundle.companyRows()) {
            m.addRow(new Object[]{
                    c.companyName(), c.industry(), c.tier(), c.approvalStatus(),
                    c.drivesCount(), c.applicantsCount(), c.offersCount(),
                    String.format(Locale.US, "%.2f", c.maxPackageLpa())
            });
        }
        return splitChartAndTable(activeChart, m, "No company participation records match the active filters.");
    }

    private JPanel buildDriveView() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        int shown = 0;
        for (DriveReportRow d : bundle.driveRows()) {
            if (shown >= 10) break;
            String label = d.companyName() + " (" + truncate(d.jobRole(), 14) + ")";
            ds.addValue(d.applicantsCount(), "Applicants", label);
            ds.addValue(d.selectedCount(), "Selected", label);
            shown++;
        }
        activeChart = bundle.driveRows().isEmpty() ? null : buildStyledBarChart(
                "Placement Drive Demand & Selection Yield",
                "Placement Drive",
                "Candidates",
                ds,
                new Color[]{ThemeManager.CYAN_DARK, ThemeManager.EMERALD_PRIMARY}
        );

        String[] cols = {"Company", "Job Role", "Status", "Drive Date", "Deadline", "Min CGPA", "Package (LPA)", "Positions", "Applicants", "Selected"};
        DefaultTableModel m = readOnlyModel(cols);
        for (DriveReportRow d : bundle.driveRows()) {
            m.addRow(new Object[]{
                    d.companyName(), d.jobRole(), d.status(), d.driveDate(), d.deadline(),
                    String.format(Locale.US, "%.2f", d.minCgpa()),
                    String.format(Locale.US, "%.2f", d.salaryPackageLpa()),
                    d.positions(), d.applicantsCount(), d.selectedCount()
            });
        }
        return splitChartAndTable(activeChart, m, "No placement drives match the active filters.");
    }

    private JPanel buildSkillView() {
        DefaultCategoryDataset ds = new DefaultCategoryDataset();
        int shown = 0;
        for (SkillReportRow s : bundle.skillRows()) {
            if (shown >= 12) break;
            ds.addValue(s.studentsWithSkill(), "Students With Skill", s.skillName());
            ds.addValue(s.verifiedCount(), "Faculty Verified", s.skillName());
            ds.addValue(s.drivesRequiringSkill(), "Drives Requiring", s.skillName());
            shown++;
        }
        activeChart = bundle.skillRows().isEmpty() ? null : buildStyledBarChart(
                "Technical Skill Distribution (Student Supply vs Drive Demand)",
                "Skill",
                "Count",
                ds,
                new Color[]{ThemeManager.BLUE_ELECTRIC, ThemeManager.EMERALD_PRIMARY, ThemeManager.AMBER_PRIMARY}
        );

        String[] cols = {"Skill Name", "Category", "Students With Skill", "Faculty Verified", "Drives Requiring Skill"};
        DefaultTableModel m = readOnlyModel(cols);
        for (SkillReportRow s : bundle.skillRows()) {
            m.addRow(new Object[]{
                    s.skillName(), s.category(), s.studentsWithSkill(), s.verifiedCount(), s.drivesRequiringSkill()
            });
        }
        return splitChartAndTable(activeChart, m, "No skill supply/demand records found for the active filters.");
    }

    private JPanel buildStudentRosterView() {
        activeChart = null;
        String[] cols = {"Roll No", "Full Name", "Branch", "Acad Yr", "Grad Yr", "CGPA", "Backlogs", "Placement Status", "Package LPA", "Approval"};
        DefaultTableModel m = readOnlyModel(cols);
        for (Student s : bundle.scopedStudents()) {
            m.addRow(new Object[]{
                    nonBlank(s.getRollNumber(), "-"),
                    nonBlank(s.getFullName(), "-"),
                    nonBlank(s.getBranch(), "-"),
                    s.getAcademicYear() > 0 ? s.getAcademicYear() : s.getYearOfStudy(),
                    s.getGraduationYear(),
                    String.format(Locale.US, "%.2f", s.getCgpa()),
                    s.getBacklogs(),
                    nonBlank(s.getPlacementStatus(), "UNPLACED"),
                    s.getPlacedPackageLpa() != null ? String.format(Locale.US, "%.2f", s.getPlacedPackageLpa()) : "-",
                    nonBlank(s.getApprovalStatus(), "PENDING")
            });
        }
        RoundedCard card = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(12, 14, 12, 14));
        if (m.getRowCount() == 0) {
            card.add(emptyState("No student placement records match your active filters and RBAC scope.",
                    "Adjust Academic Year, Graduation Year, Branch, or Date Range filters above."), BorderLayout.CENTER);
        } else {
            card.add(wrapTable(m), BorderLayout.CENTER);
        }
        return card;
    }

    private JPanel splitChartAndTable(JFreeChart chart, DefaultTableModel model, String emptyMsg) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setOpaque(false);
        split.setBorder(null);
        split.setResizeWeight(0.52);

        RoundedCard chartCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        chartCard.setLayout(new BorderLayout());
        chartCard.setBorder(new EmptyBorder(10, 12, 10, 12));
        if (chart != null && model.getRowCount() > 0) {
            ChartPanel cp = new ChartPanel(chart);
            cp.setOpaque(false);
            cp.setPreferredSize(new Dimension(700, 260));
            chartCard.add(cp, BorderLayout.CENTER);
        } else {
            chartCard.add(emptyState(emptyMsg, "Zero fabricated data: chart renders once real matching MySQL rows exist."), BorderLayout.CENTER);
        }

        RoundedCard tableCard = new RoundedCard(14, ThemeManager.cardBg(), ThemeManager.cardBorder());
        tableCard.setLayout(new BorderLayout());
        tableCard.setBorder(new EmptyBorder(10, 12, 10, 12));
        if (model.getRowCount() == 0) {
            tableCard.add(emptyState(emptyMsg, "Try resetting filters or verifying MySQL table records."), BorderLayout.CENTER);
        } else {
            tableCard.add(wrapTable(model), BorderLayout.CENTER);
        }

        split.setTopComponent(chartCard);
        split.setBottomComponent(tableCard);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(split, BorderLayout.CENTER);
        return wrap;
    }

    private JFreeChart buildStyledBarChart(
            String title,
            String xLabel,
            String yLabel,
            DefaultCategoryDataset ds,
            Color[] palette
    ) {
        JFreeChart chart = ChartFactory.createBarChart(
                title, xLabel, yLabel, ds, PlotOrientation.VERTICAL, true, true, false
        );
        chart.setBackgroundPaint(ThemeManager.cardBg());
        chart.getTitle().setPaint(ThemeManager.primaryText());
        chart.getTitle().setFont(ThemeManager.font(Font.BOLD, 14));
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(ThemeManager.cardBg());
            chart.getLegend().setItemPaint(ThemeManager.secondaryText());
            chart.getLegend().setItemFont(ThemeManager.font(Font.PLAIN, 11));
        }

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(ThemeManager.cardSubtleBg());
        plot.setRangeGridlinePaint(ThemeManager.cardBorder());
        plot.setOutlineVisible(false);
        plot.getDomainAxis().setTickLabelPaint(ThemeManager.secondaryText());
        plot.getDomainAxis().setLabelPaint(ThemeManager.primaryText());
        plot.getRangeAxis().setTickLabelPaint(ThemeManager.secondaryText());
        plot.getRangeAxis().setLabelPaint(ThemeManager.primaryText());

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setBarPainter(new StandardBarPainter());
        renderer.setShadowVisible(false);
        renderer.setItemMargin(0.08);
        for (int i = 0; i < palette.length; i++) {
            renderer.setSeriesPaint(i, palette[i]);
        }
        return chart;
    }

    // -------------------------------------------------------------------------
    // EXPORT HANDLER (RBAC SCOPED)
    // -------------------------------------------------------------------------

    private void exportSelectedReport(boolean pdf) {
        if (bundle == null) {
            alertBanner.showError("Load placement analytics first before exporting.");
            return;
        }
        User actor = AppSession.getInstance().getCurrentUser();
        ReportKind kind = (ReportKind) exportKindBox.getSelectedItem();
        if (kind == null) kind = ReportKind.DEPARTMENT_WISE;

        // Enforce strict RBAC on sensitive cross-company/peer exports
        if (actor != null && actor.getRole() == UserRole.COMPANY
                && (kind == ReportKind.DEPARTMENT_WISE)) {
            alertBanner.showError("Recruiter RBAC Scope: Full institutional department comparison exports are restricted to College Faculty, TPO, and Administrators.");
            return;
        }

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle((pdf ? "Export GCET PDF — " : "Export CSV — ") + kind.getTitle());
        String defaultExt = pdf ? "pdf" : "csv";
        String baseName = "gcet_" + kind.name().toLowerCase(Locale.ROOT);
        fc.setSelectedFile(new File(baseName + "." + defaultExt));
        fc.setFileFilter(new FileNameExtensionFilter(pdf ? "PDF Documents (*.pdf)" : "CSV Spreadsheets (*.csv)", defaultExt));

        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = fc.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith("." + defaultExt)) {
            target = new File(target.getAbsolutePath() + "." + defaultExt);
        }

        try {
            if (pdf) {
                ReportExporter.exportReportPdf(target, kind, bundle, actor, activeChart);
            } else {
                ReportExporter.exportReportCsv(target, kind, bundle, actor);
            }
            alertBanner.clear();
            JOptionPane.showMessageDialog(
                    this,
                    kind.getTitle() + " exported successfully to:\n" + target.getAbsolutePath(),
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            alertBanner.showError("Failed to export report: " + ex.getMessage());
        }
    }

    private DefaultTableModel readOnlyModel(String[] cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private JScrollPane wrapTable(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setRowHeight(30);
        t.setFont(ThemeManager.font(Font.PLAIN, 12));
        t.getTableHeader().setFont(ThemeManager.font(Font.BOLD, 11));
        t.setFillsViewportHeight(true);
        t.setShowVerticalLines(false);
        JScrollPane sp = new JScrollPane(t);
        sp.setBorder(new MatteBorder(1, 1, 1, 1, ThemeManager.cardBorder()));
        return sp;
    }

    private RoundedCard kpiCard(String label, JLabel val, Color color) {
        RoundedCard c = new RoundedCard(12, ThemeManager.cardBg(), ThemeManager.cardBorder());
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel l = new JLabel(label);
        l.setFont(ThemeManager.font(Font.BOLD, 10));
        l.setForeground(ThemeManager.mutedText());
        val.setFont(ThemeManager.font(Font.BOLD, 18));
        val.setForeground(color);
        c.add(l);
        c.add(Box.createVerticalStrut(4));
        c.add(val);
        return c;
    }

    private JPanel emptyState(String heading, String sub) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(36, 16, 36, 16));
        JLabel h = new JLabel(heading, SwingConstants.CENTER);
        h.setFont(ThemeManager.font(Font.BOLD, 13));
        h.setForeground(ThemeManager.secondaryText());
        h.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel s = new JLabel(sub, SwingConstants.CENTER);
        s.setFont(ThemeManager.font(Font.PLAIN, 11.5f));
        s.setForeground(ThemeManager.mutedText());
        s.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(h);
        p.add(Box.createVerticalStrut(4));
        p.add(s);
        return p;
    }

    private JLabel inlineLbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(ThemeManager.font(Font.BOLD, 11));
        l.setForeground(ThemeManager.secondaryText());
        return l;
    }

    private static String shortStage(String label) {
        if (label == null) return "-";
        return label.replace("Application ", "App ").replace(" Interview", "");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }
}
