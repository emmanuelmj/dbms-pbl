package edu.gcet.placementhub.utils;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.GovernanceModels.*;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure Java CSV (RFC-4180) and GCET-Branded OpenPDF Report Exporter for Phase 5.
 * Exports only records present in the RBAC-scoped ReportBundle / AuditLog list.
 */
public final class ReportExporter {

    public enum ReportKind {
        STUDENT_PLACEMENT("Student Placement Report"),
        COMPANY_PARTICIPATION("Company Participation Report"),
        PLACEMENT_DRIVES("Placement Drive Report"),
        APPLICATION_PIPELINE("Application Pipeline Report"),
        DEPARTMENT_WISE("Department-Wise Placement Report");

        private final String title;

        ReportKind(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a z").withZone(ZoneId.systemDefault());
    private static final Color GCET_NAVY = new Color(0x10, 0x1A, 0x35);
    private static final Color GCET_BLUE = new Color(0x26, 0x84, 0xFF);
    private static final Color ROW_ALT = new Color(0xF8, 0xFA, 0xFC);
    private static final Color BORDER_LIGHT = new Color(0xCB, 0xD5, 0xE1);

    private ReportExporter() {
    }

    // -------------------------------------------------------------------------
    // CSV EXPORTS
    // -------------------------------------------------------------------------

    public static void exportReportCsv(File file, ReportKind kind, ReportBundle bundle, User actor) throws IOException {
        List<String[]> rows = new ArrayList<>();
        switch (kind) {
            case STUDENT_PLACEMENT -> {
                rows.add(new String[]{
                        "Roll Number", "Full Name", "Branch", "Academic Year", "Grad Year",
                        "CGPA", "Backlogs", "Placement Status", "Package (LPA)", "Approval Status"
                });
                for (Student s : bundle.scopedStudents()) {
                    rows.add(new String[]{
                            nz(s.getRollNumber()),
                            nz(s.getFullName()),
                            nz(s.getBranch()),
                            String.valueOf(s.getAcademicYear() > 0 ? s.getAcademicYear() : s.getYearOfStudy()),
                            String.valueOf(s.getGraduationYear()),
                            String.format(Locale.US, "%.2f", s.getCgpa()),
                            String.valueOf(s.getBacklogs()),
                            nz(s.getPlacementStatus()),
                            s.getPlacedPackageLpa() != null ? String.format(Locale.US, "%.2f", s.getPlacedPackageLpa()) : "-",
                            nz(s.getApprovalStatus())
                    });
                }
            }
            case COMPANY_PARTICIPATION -> {
                rows.add(new String[]{
                        "Company Name", "Industry", "Tier", "Approval Status",
                        "Drives Held", "Applicants", "Offers Made", "Max Package (LPA)"
                });
                for (CompanyReportRow c : bundle.companyRows()) {
                    rows.add(new String[]{
                            c.companyName(),
                            c.industry(),
                            c.tier(),
                            c.approvalStatus(),
                            String.valueOf(c.drivesCount()),
                            String.valueOf(c.applicantsCount()),
                            String.valueOf(c.offersCount()),
                            String.format(Locale.US, "%.2f", c.maxPackageLpa())
                    });
                }
            }
            case PLACEMENT_DRIVES -> {
                rows.add(new String[]{
                        "Company", "Job Role", "Status", "Drive Date", "Deadline",
                        "Min CGPA", "Max Backlogs", "Grad Year", "Package (LPA)",
                        "Positions", "Applicants", "Selected"
                });
                for (DriveReportRow d : bundle.driveRows()) {
                    rows.add(new String[]{
                            d.companyName(),
                            d.jobRole(),
                            d.status(),
                            d.driveDate(),
                            d.deadline(),
                            String.format(Locale.US, "%.2f", d.minCgpa()),
                            String.valueOf(d.maxBacklogs()),
                            String.valueOf(d.targetGradYear()),
                            String.format(Locale.US, "%.2f", d.salaryPackageLpa()),
                            String.valueOf(d.positions()),
                            String.valueOf(d.applicantsCount()),
                            String.valueOf(d.selectedCount())
                    });
                }
            }
            case APPLICATION_PIPELINE -> {
                rows.add(new String[]{"Pipeline Stage", "Stage Code", "Applications Count", "Share (%)"});
                for (PipelineReportRow p : bundle.pipelineRows()) {
                    rows.add(new String[]{
                            p.stageLabel(),
                            p.stageCode(),
                            String.valueOf(p.count()),
                            String.format(Locale.US, "%.1f%%", p.percentage())
                    });
                }
            }
            case DEPARTMENT_WISE -> {
                rows.add(new String[]{
                        "Branch / Department", "Total Students", "Eligible Students", "Placed",
                        "Unplaced", "Placement Rate (%)", "Avg CGPA", "Avg Package (LPA)", "Highest Package (LPA)"
                });
                for (BranchReportRow b : bundle.branchRows()) {
                    rows.add(new String[]{
                            b.branch(),
                            String.valueOf(b.totalStudents()),
                            String.valueOf(b.eligibleStudents()),
                            String.valueOf(b.placedStudents()),
                            String.valueOf(b.unplacedStudents()),
                            String.format(Locale.US, "%.1f%%", b.placementRatePct()),
                            String.format(Locale.US, "%.2f", b.avgCgpa()),
                            String.format(Locale.US, "%.2f", b.avgPackageLpa()),
                            String.format(Locale.US, "%.2f", b.highestPackageLpa())
                    });
                }
            }
        }
        writeCsv(file, rows);
    }

    public static void exportAuditLogsCsv(File file, List<AuditLog> logs) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Timestamp", "Action", "Actor Username", "Actor Role", "Actor ID", "Details"});
        for (AuditLog log : logs) {
            rows.add(new String[]{
                    nz(log.getCreatedAt()),
                    nz(log.getAction()),
                    nz(log.getActorUsername()),
                    nz(log.getActorRole()),
                    nz(log.getActorId()),
                    nz(log.getDetails())
            });
        }
        writeCsv(file, rows);
    }

    private static void writeCsv(File file, List<String[]> rows) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            // Write UTF-8 BOM for clean Microsoft Excel compatibility
            bw.write('\uFEFF');
            for (String[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) bw.write(',');
                    bw.write(csvEscape(row[i]));
                }
                bw.newLine();
            }
        }
    }

    private static String csvEscape(String value) {
        if (value == null) return "\"\"";
        String cleaned = value.replace("\r", " ").replace("\n", " ");
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("'")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    // -------------------------------------------------------------------------
    // GCET-BRANDED PDF EXPORTS (OpenPDF + JFreeChart snapshot)
    // -------------------------------------------------------------------------

    public static void exportReportPdf(
            File file,
            ReportKind kind,
            ReportBundle bundle,
            User actor,
            JFreeChart optionalChart
    ) throws IOException, DocumentException {
        boolean landscape = kind == ReportKind.STUDENT_PLACEMENT
                || kind == ReportKind.PLACEMENT_DRIVES
                || kind == ReportKind.DEPARTMENT_WISE;
        Rectangle pageSize = landscape ? PageSize.A4.rotate() : PageSize.A4;
        Document doc = new Document(pageSize, 28, 28, 28, 28);

        try (FileOutputStream fos = new FileOutputStream(file)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();

            // 1. GCET Institutional Header Banner
            PdfPTable banner = new PdfPTable(1);
            banner.setWidthPercentage(100);
            PdfPCell hdrCell = new PdfPCell();
            hdrCell.setBackgroundColor(GCET_NAVY);
            hdrCell.setPadding(14);
            hdrCell.setBorder(Rectangle.NO_BORDER);

            Font instFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(0x32, 0xC5, 0xF4));
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.WHITE);
            Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0xCB, 0xD5, 0xE1));

            Paragraph p1 = new Paragraph("GEETHANJALI COLLEGE OF ENGINEERING AND TECHNOLOGY (UGC AUTONOMOUS)", instFont);
            Paragraph p2 = new Paragraph("GCET PLACEMENT HUB — " + kind.getTitle(), titleFont);
            p2.setSpacingBefore(3);
            Paragraph p3 = new Paragraph(
                    "Cheeryal (V), Keesara (M), Medchal Dist., Telangana  ·  Live MySQL JDBC Verified Report",
                    subFont
            );
            p3.setSpacingBefore(2);
            hdrCell.addElement(p1);
            hdrCell.addElement(p2);
            hdrCell.addElement(p3);
            banner.addCell(hdrCell);
            doc.add(banner);

            // 2. Generation Metadata & Applied Filters Strip
            Font metaBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, GCET_NAVY);
            Font metaNorm = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);

            PdfPTable metaTbl = new PdfPTable(2);
            metaTbl.setWidthPercentage(100);
            metaTbl.setSpacingBefore(10);
            metaTbl.setSpacingAfter(10);
            addMetaCell(metaTbl, "Generated On: ", TS_FMT.format(ZonedDateTime.now()), metaBold, metaNorm);
            String actorText = actor != null
                    ? actor.getFullName() + " (@" + actor.getUsername() + " · " + actor.getRole().name() + ")"
                    : "Authenticated Session";
            addMetaCell(metaTbl, "Generated By: ", actorText, metaBold, metaNorm);

            ReportFilter f = bundle.appliedFilter();
            String filterSummary = "Academic Year: " + nz(f.academicYear(), "ALL")
                    + "  |  Grad Year: " + nz(f.graduationYear(), "ALL")
                    + "  |  Branch: " + nz(f.branch(), "ALL")
                    + ((!nz(f.fromDate()).isEmpty() || !nz(f.toDate()).isEmpty())
                    ? "  |  Date Range: " + nz(f.fromDate(), "*") + " to " + nz(f.toDate(), "*")
                    : "  |  Date Range: All Dates");
            addMetaCell(metaTbl, "Active Filters: ", filterSummary, metaBold, metaNorm);
            addMetaCell(
                    metaTbl,
                    "KPI Summary: ",
                    String.format(
                            Locale.US,
                            "Students: %d  |  Eligible: %d  |  Placed: %d (%.1f%%)  |  Avg: %.2f LPA  |  Peak: %.2f LPA",
                            bundle.totalStudents(),
                            bundle.eligibleStudents(),
                            bundle.placedStudents(),
                            bundle.placementRatePct(),
                            bundle.avgPackageLpa(),
                            bundle.highestPackageLpa()
                    ),
                    metaBold,
                    metaNorm
            );
            doc.add(metaTbl);

            // 3. Optional JFreeChart Snapshot
            if (optionalChart != null) {
                try {
                    BufferedImage chartImg = optionalChart.createBufferedImage(landscape ? 760 : 520, 260);
                    byte[] png = ChartUtils.encodeAsPNG(chartImg);
                    Image pdfImg = Image.getInstance(png);
                    pdfImg.setAlignment(Element.ALIGN_CENTER);
                    pdfImg.scaleToFit(landscape ? 740 : 510, 230);
                    pdfImg.setSpacingAfter(10);
                    doc.add(pdfImg);
                } catch (Exception ignored) {
                }
            }

            // 4. Data Table
            PdfPTable dataTable = buildPdfTableForKind(kind, bundle);
            doc.add(dataTable);

            Font footFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
            Paragraph footer = new Paragraph(
                    "Confidential Institutional Report · Generated directly from MySQL database gcet_placement_hub (" + bundle.scopeNotice() + ")",
                    footFont
            );
            footer.setSpacingBefore(10);
            doc.add(footer);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
    }

    private static void addMetaCell(PdfPTable tbl, String key, String val, Font keyFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(ROW_ALT);
        cell.setBorderColor(BORDER_LIGHT);
        cell.setPadding(6);
        Phrase ph = new Phrase();
        ph.add(new Phrase(key, keyFont));
        ph.add(new Phrase(val, valFont));
        cell.setPhrase(ph);
        tbl.addCell(cell);
    }

    private static PdfPTable buildPdfTableForKind(ReportKind kind, ReportBundle bundle) throws DocumentException {
        Font hdrFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Color.DARK_GRAY);
        Font emptyFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY);

        String[] headers;
        float[] widths;
        List<String[]> body = new ArrayList<>();

        switch (kind) {
            case STUDENT_PLACEMENT -> {
                headers = new String[]{"Roll No", "Full Name", "Branch", "Yr", "Grad", "CGPA", "Backlogs", "Status", "LPA", "Approval"};
                widths = new float[]{12, 20, 14, 5, 7, 7, 8, 11, 7, 9};
                for (Student s : bundle.scopedStudents()) {
                    body.add(new String[]{
                            nz(s.getRollNumber()),
                            nz(s.getFullName()),
                            nz(s.getBranch()),
                            String.valueOf(s.getAcademicYear() > 0 ? s.getAcademicYear() : s.getYearOfStudy()),
                            String.valueOf(s.getGraduationYear()),
                            String.format(Locale.US, "%.2f", s.getCgpa()),
                            String.valueOf(s.getBacklogs()),
                            nz(s.getPlacementStatus()),
                            s.getPlacedPackageLpa() != null ? String.format(Locale.US, "%.2f", s.getPlacedPackageLpa()) : "-",
                            nz(s.getApprovalStatus())
                    });
                }
            }
            case COMPANY_PARTICIPATION -> {
                headers = new String[]{"Company", "Industry", "Tier", "Approval", "Drives", "Applicants", "Offers", "Max LPA"};
                widths = new float[]{22, 18, 13, 14, 8, 9, 8, 8};
                for (CompanyReportRow c : bundle.companyRows()) {
                    body.add(new String[]{
                            c.companyName(), c.industry(), c.tier(), c.approvalStatus(),
                            String.valueOf(c.drivesCount()), String.valueOf(c.applicantsCount()),
                            String.valueOf(c.offersCount()), String.format(Locale.US, "%.2f", c.maxPackageLpa())
                    });
                }
            }
            case PLACEMENT_DRIVES -> {
                headers = new String[]{"Company", "Job Role", "Status", "Drive Date", "Deadline", "Min CGPA", "LPA", "Pos", "Apps", "Selected"};
                widths = new float[]{17, 20, 9, 10, 10, 8, 8, 6, 6, 6};
                for (DriveReportRow d : bundle.driveRows()) {
                    body.add(new String[]{
                            d.companyName(), d.jobRole(), d.status(), d.driveDate(), d.deadline(),
                            String.format(Locale.US, "%.2f", d.minCgpa()),
                            String.format(Locale.US, "%.2f", d.salaryPackageLpa()),
                            String.valueOf(d.positions()), String.valueOf(d.applicantsCount()),
                            String.valueOf(d.selectedCount())
                    });
                }
            }
            case APPLICATION_PIPELINE -> {
                headers = new String[]{"Pipeline Stage", "Stage Code", "Applications", "Share (%)"};
                widths = new float[]{35, 35, 15, 15};
                for (PipelineReportRow p : bundle.pipelineRows()) {
                    body.add(new String[]{
                            p.stageLabel(), p.stageCode(),
                            String.valueOf(p.count()), String.format(Locale.US, "%.1f%%", p.percentage())
                    });
                }
            }
            case DEPARTMENT_WISE -> {
                headers = new String[]{"Department / Branch", "Total", "Eligible", "Placed", "Unplaced", "Rate (%)", "Avg CGPA", "Avg LPA", "Peak LPA"};
                widths = new float[]{22, 8, 9, 9, 10, 10, 10, 11, 11};
                for (BranchReportRow b : bundle.branchRows()) {
                    body.add(new String[]{
                            b.branch(),
                            String.valueOf(b.totalStudents()),
                            String.valueOf(b.eligibleStudents()),
                            String.valueOf(b.placedStudents()),
                            String.valueOf(b.unplacedStudents()),
                            String.format(Locale.US, "%.1f%%", b.placementRatePct()),
                            String.format(Locale.US, "%.2f", b.avgCgpa()),
                            String.format(Locale.US, "%.2f", b.avgPackageLpa()),
                            String.format(Locale.US, "%.2f", b.highestPackageLpa())
                    });
                }
            }
            default -> throw new IllegalStateException("Unsupported report kind: " + kind);
        }

        PdfPTable table = new PdfPTable(headers.length);
        table.setWidthPercentage(100);
        table.setWidths(widths);
        table.setHeaderRows(1);

        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, hdrFont));
            c.setBackgroundColor(GCET_BLUE);
            c.setBorderColor(GCET_NAVY);
            c.setPadding(6);
            table.addCell(c);
        }

        if (body.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase("No matching records found in MySQL for the current filters and RBAC scope.", emptyFont));
            empty.setColspan(headers.length);
            empty.setPadding(16);
            empty.setHorizontalAlignment(Element.ALIGN_CENTER);
            empty.setBorderColor(BORDER_LIGHT);
            table.addCell(empty);
            return table;
        }

        for (int r = 0; r < body.size(); r++) {
            String[] row = body.get(r);
            Color bg = (r % 2 == 1) ? ROW_ALT : Color.WHITE;
            for (String val : row) {
                PdfPCell c = new PdfPCell(new Phrase(val != null ? val : "-", cellFont));
                c.setBackgroundColor(bg);
                c.setBorderColor(BORDER_LIGHT);
                c.setPadding(5);
                table.addCell(c);
            }
        }
        return table;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nz(String s, String def) {
        return (s != null && !s.isBlank()) ? s.trim() : def;
    }
}
