package edu.gcet.placementhub.services;

import edu.gcet.placementhub.dao.ApplicationDao;
import edu.gcet.placementhub.dao.AuditLogDao;
import edu.gcet.placementhub.dao.CompanyDao;
import edu.gcet.placementhub.dao.DriveDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.utils.DeptNormalizer;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure JDBC dashboard data aggregation service matching `DashboardPage.tsx`.
 * Strictly zero mock/fabricated database records; all numbers are computed live from MySQL.
 */
public class DashboardService {

    private final StudentDao studentDao = new StudentDao();
    private final CompanyDao companyDao = new CompanyDao();
    private final DriveDao driveDao = new DriveDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    public record BranchStat(String branch, int total, int placed, int unplaced, double avgCgpa) {}

    public record CompanyStat(
            Company company,
            int drivesCount,
            int offersCount,
            String maxPackage,
            boolean hasActiveDrive
    ) {}

    public record PackageBracket(
            String id,
            String label,
            String description,
            int count,
            java.awt.Color barStart,
            java.awt.Color barEnd
    ) {}

    public record MonthlyTrend(String month, int drives, int offers) {}

    public record FunnelMetrics(
            int total,
            int academicallyEligible,
            int studentIdsApplied,
            int studentIdsShortlisted,
            int finalPlaced
    ) {}

    public static class DashboardData {
        public final User currentUser;
        public final Student currentStudentRecord;
        public final DbStatus dbStatus;
        public final String loadError;

        public final List<Student> students;
        public final List<Company> companies;
        public final List<PlacementDrive> drives;
        public final List<Application> applications;
        public final List<AuditLog> recentActivities;
        public final List<GctcAnnouncement> recentAnnouncements;

        public final int totalStudents;
        public final int totalCompanies;
        public final int activeDrives;
        public final int upcomingDrives;
        public final int placedCount;
        public final String placementPercentage;
        public final String avgPackage;
        public final String highestPackage;

        public final Map<String, BranchStat> branchAnalytics;
        public final List<CompanyStat> companyAnalytics;
        public final List<PackageBracket> packageDistribution;
        public final List<MonthlyTrend> monthlyTrends;
        public final FunnelMetrics funnelMetrics;
        public final List<PlacementDrive> upcomingAndOpenDrives;

        public DashboardData(
                User currentUser,
                Student currentStudentRecord,
                DbStatus dbStatus,
                String loadError,
                List<Student> students,
                List<Company> companies,
                List<PlacementDrive> drives,
                List<Application> applications,
                List<AuditLog> recentActivities,
                List<GctcAnnouncement> recentAnnouncements,
                int totalStudents,
                int totalCompanies,
                int activeDrives,
                int upcomingDrives,
                int placedCount,
                String placementPercentage,
                String avgPackage,
                String highestPackage,
                Map<String, BranchStat> branchAnalytics,
                List<CompanyStat> companyAnalytics,
                List<PackageBracket> packageDistribution,
                List<MonthlyTrend> monthlyTrends,
                FunnelMetrics funnelMetrics,
                List<PlacementDrive> upcomingAndOpenDrives
        ) {
            this.currentUser = currentUser;
            this.currentStudentRecord = currentStudentRecord;
            this.dbStatus = dbStatus;
            this.loadError = loadError;
            this.students = students;
            this.companies = companies;
            this.drives = drives;
            this.applications = applications;
            this.recentActivities = recentActivities;
            this.recentAnnouncements = recentAnnouncements;
            this.totalStudents = totalStudents;
            this.totalCompanies = totalCompanies;
            this.activeDrives = activeDrives;
            this.upcomingDrives = upcomingDrives;
            this.placedCount = placedCount;
            this.placementPercentage = placementPercentage;
            this.avgPackage = avgPackage;
            this.highestPackage = highestPackage;
            this.branchAnalytics = branchAnalytics;
            this.companyAnalytics = companyAnalytics;
            this.packageDistribution = packageDistribution;
            this.monthlyTrends = monthlyTrends;
            this.funnelMetrics = funnelMetrics;
            this.upcomingAndOpenDrives = upcomingAndOpenDrives;
        }
    }

    public DashboardData loadDashboardData(User currentUser, Student fallbackStudent) {
        DbStatus status = DatabaseConnection.getInstance().pingAndInspect();
        List<Student> students = List.of();
        List<Company> companies = List.of();
        List<PlacementDrive> drives = List.of();
        List<Application> applications = List.of();
        List<AuditLog> recentActivities = List.of();
        Student currentStudent = fallbackStudent;
        String error = null;

        if (status.isConnected()) {
            try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
                students = studentDao.findAll(conn);
                companies = companyDao.findAll(conn);
                drives = driveDao.findAll(conn);
                applications = applicationDao.findAllForUser(conn, currentUser);
                recentActivities = auditLogDao.findRecent(conn, 6);

                if (currentUser != null && currentUser.getRole() == UserRole.STUDENT) {
                    currentStudent = resolveCurrentStudent(currentUser, students, fallbackStudent);
                }
            } catch (SQLException ex) {
                error = "Failed to query MySQL dashboard data: " + ex.getMessage();
            }
        } else {
            error = status.getMessage();
        }

        // Core KPIs (exact parity with DashboardPage.tsx)
        int totalStudents = students.size();
        int totalCompanies = companies.size();
        int activeDrives = (int) drives.stream().filter(d -> "OPEN".equalsIgnoreCase(d.getStatus())).count();
        int upcomingDrives = (int) drives.stream()
                .filter(d -> "UPCOMING".equalsIgnoreCase(d.getStatus()) || "PLANNED".equalsIgnoreCase(d.getStatus()))
                .count();

        List<Student> placedStudents = students.stream()
                .filter(s -> "PLACED".equalsIgnoreCase(s.getPlacementStatus()))
                .toList();
        int placedCount = placedStudents.size();

        String placementPct = totalStudents == 0
                ? "0.0"
                : String.format(Locale.US, "%.1f", (placedCount * 100.0) / totalStudents);

        double pkgSum = 0;
        int pkgCount = 0;
        for (Student s : placedStudents) {
            if (s.getPlacedPackageLpa() != null && s.getPlacedPackageLpa() > 0) {
                pkgSum += s.getPlacedPackageLpa();
                pkgCount++;
            }
        }
        String avgPackage = pkgCount == 0 ? "0.00" : String.format(Locale.US, "%.2f", pkgSum / pkgCount);

        double maxPkg = 0;
        for (PlacementDrive d : drives) {
            if (d.getSalaryPackageLpa() > maxPkg) maxPkg = d.getSalaryPackageLpa();
        }
        for (Student s : placedStudents) {
            if (s.getPlacedPackageLpa() != null && s.getPlacedPackageLpa() > maxPkg) {
                maxPkg = s.getPlacedPackageLpa();
            }
        }
        String highestPackage = maxPkg > 0 ? String.format(Locale.US, "%.1f", maxPkg) : "0.0";

        // Branch analytics
        Map<String, BranchStat> branchMap = computeBranchAnalytics(students);

        // Company analytics
        List<CompanyStat> companyStats = computeCompanyAnalytics(companies, drives, applications);

        // Package brackets
        List<PackageBracket> brackets = computePackageDistribution(placedStudents);

        // Monthly trends (Aug-Mar)
        List<MonthlyTrend> trends = computeMonthlyTrends(drives, applications);

        // Eligibility funnel
        FunnelMetrics funnel = computeFunnel(students, applications, placedCount);

        // Upcoming & open drives (top 5)
        List<PlacementDrive> upcomingAndOpen = drives.stream()
                .filter(d -> "OPEN".equalsIgnoreCase(d.getStatus())
                        || "UPCOMING".equalsIgnoreCase(d.getStatus())
                        || "PLANNED".equalsIgnoreCase(d.getStatus()))
                .limit(5)
                .toList();

        return new DashboardData(
                currentUser,
                currentStudent,
                status,
                error,
                students,
                companies,
                drives,
                applications,
                recentActivities,
                getArchivedGctcAnnouncements(),
                totalStudents,
                totalCompanies,
                activeDrives,
                upcomingDrives,
                placedCount,
                placementPct,
                avgPackage,
                highestPackage,
                branchMap,
                companyStats,
                brackets,
                trends,
                funnel,
                upcomingAndOpen
        );
    }

    private Student resolveCurrentStudent(User user, List<Student> students, Student fallback) {
        if (user.getStudentRollNumber() != null) {
            for (Student s : students) {
                if (user.getStudentRollNumber().equalsIgnoreCase(s.getRollNumber())) {
                    return s;
                }
            }
        }
        if (fallback != null) return fallback;
        return students.isEmpty() ? null : students.get(0);
    }

    private Map<String, BranchStat> computeBranchAnalytics(List<Student> students) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Double> cgpaTotals = new LinkedHashMap<>();
        for (Student s : students) {
            String br = s.getBranch() != null && !s.getBranch().isBlank() ? s.getBranch() : "CSE";
            int[] arr = counts.computeIfAbsent(br, k -> new int[3]); // 0=total, 1=placed, 2=unplaced
            arr[0]++;
            if ("PLACED".equalsIgnoreCase(s.getPlacementStatus())) {
                arr[1]++;
            } else {
                arr[2]++;
            }
            cgpaTotals.merge(br, s.getCgpa(), Double::sum);
        }
        Map<String, BranchStat> result = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> e : counts.entrySet()) {
            String br = e.getKey();
            int[] arr = e.getValue();
            double avg = arr[0] > 0 ? Math.round((cgpaTotals.getOrDefault(br, 0.0) / arr[0]) * 100.0) / 100.0 : 0.0;
            result.put(br, new BranchStat(br, arr[0], arr[1], arr[2], avg));
        }
        return result;
    }

    private List<CompanyStat> computeCompanyAnalytics(
            List<Company> companies, List<PlacementDrive> drives, List<Application> applications) {
        List<CompanyStat> list = new ArrayList<>();
        for (Company c : companies) {
            List<PlacementDrive> compDrives = drives.stream()
                    .filter(d -> Objects.equals(d.getCompanyId(), c.getId())
                            || (d.getCompanyName() != null && c.getName() != null
                            && d.getCompanyName().equalsIgnoreCase(c.getName())))
                    .toList();
            List<Application> compApps = applications.stream()
                    .filter(a -> Objects.equals(a.getCompanyId(), c.getId())
                            || (a.getCompanyName() != null && c.getName() != null
                            && a.getCompanyName().equalsIgnoreCase(c.getName())))
                    .toList();
            int offersCount = (int) compApps.stream()
                    .filter(a -> a.getCurrentStage() != null && a.getCurrentStage().isOfferOrSelected())
                    .count();
            double maxPkg = 0;
            boolean active = false;
            for (PlacementDrive d : compDrives) {
                if (d.getSalaryPackageLpa() > maxPkg) maxPkg = d.getSalaryPackageLpa();
                if ("OPEN".equalsIgnoreCase(d.getStatus())) active = true;
            }
            String maxPkgLabel = maxPkg > 0
                    ? (maxPkg == Math.floor(maxPkg) ? ((long) maxPkg) + " LPA" : String.format(Locale.US, "%.1f LPA", maxPkg))
                    : "TBD";
            list.add(new CompanyStat(c, compDrives.size(), offersCount, maxPkgLabel, active));
        }
        return list;
    }

    private List<PackageBracket> computePackageDistribution(List<Student> placedStudents) {
        int b1 = 0, b2 = 0, b3 = 0, b4 = 0;
        for (Student s : placedStudents) {
            double pkg = s.getPlacedPackageLpa() != null ? s.getPlacedPackageLpa() : 0;
            if (pkg > 0 && pkg < 4.5) b1++;
            else if (pkg >= 4.5 && pkg < 7.0) b2++;
            else if (pkg >= 7.0 && pkg < 10.0) b3++;
            else if (pkg >= 10.0) b4++;
        }
        return List.of(
                new PackageBracket("b1", "< 4.5 LPA", "Mass IT & Foundation Services", b1,
                        new java.awt.Color(0x3B, 0x82, 0xF6), new java.awt.Color(0x06, 0xB6, 0xD4)),
                new PackageBracket("b2", "4.5 - 7.0 LPA", "Core Engineering & Product IT", b2,
                        new java.awt.Color(0x06, 0xB6, 0xD4), new java.awt.Color(0x14, 0xB8, 0xA6)),
                new PackageBracket("b3", "7.0 - 10.0 LPA", "High-Growth Tech / Marquee", b3,
                        new java.awt.Color(0x63, 0x66, 0xF1), new java.awt.Color(0x25, 0x63, 0xEB)),
                new PackageBracket("b4", "10.0+ LPA", "Dream & Super Dream Offers", b4,
                        new java.awt.Color(0x10, 0xB9, 0x81), new java.awt.Color(0x16, 0xA3, 0x4A))
        );
    }

    private List<MonthlyTrend> computeMonthlyTrends(List<PlacementDrive> drives, List<Application> applications) {
        String[] labels = {"Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar"};
        int[] driveCounts = new int[8];
        int[] offerCounts = new int[8];
        // Map Calendar month (1..12 -> java.time Month 1..12) to Aug=0..Mar=7
        Map<Integer, Integer> monthIdxMap = Map.of(
                8, 0, 9, 1, 10, 2, 11, 3, 12, 4, 1, 5, 2, 6, 3, 7
        );

        for (PlacementDrive d : drives) {
            Integer m = extractMonthNumber(d.getDriveDate());
            if (m != null && monthIdxMap.containsKey(m)) {
                driveCounts[monthIdxMap.get(m)]++;
            }
        }
        for (Application a : applications) {
            if (a.getCurrentStage() != null && a.getCurrentStage().isOfferOrSelected()) {
                Integer m = extractMonthNumber(a.getAppliedAt());
                if (m != null && monthIdxMap.containsKey(m)) {
                    offerCounts[monthIdxMap.get(m)]++;
                }
            }
        }

        List<MonthlyTrend> list = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            list.add(new MonthlyTrend(labels[i], driveCounts[i], offerCounts[i]));
        }
        return list;
    }

    private Integer extractMonthNumber(String isoOrDate) {
        if (isoOrDate == null || isoOrDate.isBlank()) return null;
        try {
            if (isoOrDate.length() >= 10 && isoOrDate.charAt(4) == '-') {
                return LocalDate.parse(isoOrDate.substring(0, 10)).getMonthValue();
            }
            Instant inst = Instant.parse(isoOrDate);
            return inst.atZone(ZoneId.systemDefault()).getMonthValue();
        } catch (Exception e) {
            try {
                return OffsetDateTime.parse(isoOrDate).getMonthValue();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private FunnelMetrics computeFunnel(List<Student> students, List<Application> applications, int placedCount) {
        int total = students.size();
        int eligible = (int) students.stream().filter(s -> s.getCgpa() >= 6.5 && s.getBacklogs() == 0).count();
        Set<String> appliedIds = new HashSet<>();
        Set<String> shortlistedIds = new HashSet<>();
        for (Application a : applications) {
            if (a.getStudentId() != null) {
                appliedIds.add(a.getStudentId());
                if (a.getCurrentStage() != null && a.getCurrentStage().isShortlistedOrBeyond()) {
                    shortlistedIds.add(a.getStudentId());
                }
            }
        }
        return new FunnelMetrics(total, eligible, appliedIds.size(), shortlistedIds.size(), placedCount);
    }

    // Role classification matching DashboardPage.tsx + seedMandatoryAccounts
    public static boolean isPrincipal(User u) {
        if (u == null) return false;
        String uname = safeLower(u.getUsername());
        String full = safeLower(u.getFullName());
        return "principal.sagar".equals(uname)
                || u.getRole() == UserRole.PRINCIPAL
                || (u.getRole() == UserRole.ADMIN && (full.contains("principal") || full.contains("udaya kumar")));
    }

    public static boolean isChairman(User u) {
        if (u == null) return false;
        String uname = safeLower(u.getUsername());
        String full = safeLower(u.getFullName());
        return "chairman.gcet".equals(uname)
                || "chairman.ravinder".equals(uname)
                || u.getRole() == UserRole.CHAIRMAN
                || (full.contains("chairman") && !full.contains("vice"));
    }

    public static boolean isViceChairman(User u) {
        if (u == null) return false;
        String uname = safeLower(u.getUsername());
        String full = safeLower(u.getFullName());
        return "vicechairman.gcet".equals(uname)
                || "vc.harishchandra".equals(uname)
                || u.getRole() == UserRole.VICE_CHAIRMAN
                || full.contains("vice chairman");
    }

    public static boolean isPlacementAdmin(User u) {
        if (u == null) return false;
        if (isPrincipal(u) || isChairman(u) || isViceChairman(u)) return false;
        return u.getRole() == UserRole.ADMIN
                || u.getRole() == UserRole.PLACEMENT_OFFICER
                || "placement.admin".equals(safeLower(u.getUsername()));
    }

    public static int countFacultyCohortStudents(User facultyUser, List<Student> students) {
        if (facultyUser == null || students == null) return 0;
        List<String> depts = facultyUser.getAuthorizedDepartments();
        if (depts == null || depts.isEmpty()) {
            depts = DeptNormalizer.getFacultyAuthorizedDepts(facultyUser.getUsername());
        }
        if (depts != null && !depts.isEmpty()) {
            Set<String> normDepts = new HashSet<>();
            for (String d : depts) normDepts.add(DeptNormalizer.normalizeDeptCode(d));
            return (int) students.stream()
                    .filter(s -> normDepts.contains(DeptNormalizer.normalizeDeptCode(s.getBranch())))
                    .count();
        }
        String fallbackDept = DeptNormalizer.normalizeDeptCode(
                facultyUser.getDepartment() != null && !facultyUser.getDepartment().isBlank() ? facultyUser.getDepartment() : "CSE"
        );
        return (int) students.stream()
                .filter(s -> fallbackDept.equals(DeptNormalizer.normalizeDeptCode(s.getBranch())))
                .count();
    }

    public static String formatRelativeTime(String isoString) {
        if (isoString == null || isoString.isBlank()) return "Recently";
        try {
            Instant then = Instant.parse(isoString);
            Instant now = Instant.now();
            long sec = ChronoUnit.SECONDS.between(then, now);
            if (sec < 60) return "Just now";
            long min = sec / 60;
            if (min < 60) return min + "m ago";
            long hr = min / 60;
            if (hr < 24) return hr + "h ago";
            long day = hr / 24;
            if (day == 1) return "Yesterday";
            if (day < 7) return day + "d ago";
            return DateTimeFormatter.ofPattern("MMM dd").withZone(ZoneId.systemDefault()).format(then);
        } catch (Exception e) {
            return "Recently";
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Exact top-3 Official GCTC Academic Circulars from `campusConnectService.ts`.
     */
    private List<GctcAnnouncement> getArchivedGctcAnnouncements() {
        return List.of(
                new GctcAnnouncement(
                        "ann-01",
                        "B.Tech, MBA & M.Tech Autonomous Revaluation Examination Results Published",
                        "28 Oct 2024",
                        "EXAMINATION",
                        "Autonomous examination revaluation outcomes for B.Tech II & III Year (AR20, AR22 Regulations) and PG programs have been finalized and uploaded to the GCTC examination database.",
                        "https://www.gctcportal.in/",
                        true,
                        "Revaluation_Results_Notification_Oct24.pdf",
                        "Controller of Examinations, GCET"
                ),
                new GctcAnnouncement(
                        "ann-02",
                        "IV B.Tech I Semester Mid-I Examination Timetables & CBT Schedules Released",
                        "24 Oct 2024",
                        "EXAMINATION",
                        "Detailed session schedules, room allotments, and Computer Based Test (CBT) slot timings for IV B.Tech I Semester students across all branches are now active.",
                        "https://www.gctcportal.in/",
                        true,
                        "IV_BTech_Mid1_Timetable_Schedule.pdf",
                        "Academic & Examination Section"
                ),
                new GctcAnnouncement(
                        "ann-03",
                        "Autonomous Regular & Supplementary Examination Fee Circular (AR20, AR22, AR24)",
                        "18 Oct 2024",
                        "EXAMINATION",
                        "Notification regarding payment of examination fees for upcoming Autonomous Odd Semester End Examinations. Online payment enabled via State Bank Collect gateway.",
                        "https://www.gctcportal.in/",
                        false,
                        "Exam_Fee_Circular_Autonomous_2024.pdf",
                        "Office of the Principal"
                )
        );
    }
}
