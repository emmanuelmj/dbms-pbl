package edu.gcet.placementhub.services;

import com.mongodb.MongoException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import edu.gcet.placementhub.dao.AnnouncementDao;
import edu.gcet.placementhub.dao.ApplicationDao;
import edu.gcet.placementhub.dao.AuditLogDao;
import edu.gcet.placementhub.dao.CompanyDao;
import edu.gcet.placementhub.dao.DriveDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.DatabaseException;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.utils.DeptNormalizer;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * MongoDB-backed dashboard data aggregation service matching `DashboardPage.tsx`.
 * Utilizes live MongoDB documents and real MongoDB Aggregation Pipelines ($match, $group, $sort).
 */
public class DashboardService {

    private final StudentDao studentDao = new StudentDao();
    private final CompanyDao companyDao = new CompanyDao();
    private final DriveDao driveDao = new DriveDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();
    private final AnnouncementDao announcementDao = new AnnouncementDao();

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
            String rangeLabel,
            String categoryDescription,
            int count,
            java.awt.Color primaryColor,
            java.awt.Color secondaryColor
    ) {
        public String label() { return rangeLabel; }
        public String description() { return categoryDescription; }
        public java.awt.Color barStart() { return primaryColor; }
        public java.awt.Color barEnd() { return secondaryColor; }
    }

    public record MonthlyTrend(
            String monthLabel,
            int drivesConducted,
            int applicationsSubmitted,
            int studentsSelected
    ) {
        public String month() { return monthLabel; }
        public int drives() { return drivesConducted; }
        public int offers() { return studentsSelected; }
    }

    public record FunnelMetrics(
            int totalEnrolled,
            int totalEligible,
            int appliedUnique,
            int shortlisted,
            int technicalRound,
            int hrRound,
            int placedFinal
    ) {
        public int total() { return totalEnrolled; }
        public int academicallyEligible() { return totalEligible; }
        public int studentIdsApplied() { return appliedUnique; }
        public int studentIdsShortlisted() { return shortlisted; }
        public int finalPlaced() { return placedFinal; }
    }

    public static class DashboardData {
        public final User currentUser;
        public final Student currentStudent;
        public final Student currentStudentRecord;
        public final DbStatus dbStatus;
        public final String errorMessage;

        public final List<Student> students;
        public final List<Company> companies;
        public final List<PlacementDrive> drives;
        public final List<Application> applications;
        public final List<AuditLog> recentActivities;
        public final List<GctcAnnouncement> announcements;
        public final List<GctcAnnouncement> recentAnnouncements;

        public final int totalStudents;
        public final int totalCompanies;
        public final int activeDrives;
        public final int upcomingDrives;
        public final int placedStudentsCount;
        public final int placedCount;
        public final String placementPercentage;
        public final String averagePackageLpa;
        public final String avgPackage;
        public final String highestPackageLpa;
        public final String highestPackage;

        public final Map<String, BranchStat> branchAnalytics;
        public final List<CompanyStat> companyAnalytics;
        public final List<PackageBracket> packageDistribution;
        public final List<MonthlyTrend> monthlyTrends;
        public final FunnelMetrics funnelMetrics;
        public final List<PlacementDrive> upcomingAndOpenDrives;

        public DashboardData(
                User currentUser,
                Student currentStudent,
                DbStatus dbStatus,
                String errorMessage,
                List<Student> students,
                List<Company> companies,
                List<PlacementDrive> drives,
                List<Application> applications,
                List<AuditLog> recentActivities,
                List<GctcAnnouncement> announcements,
                int totalStudents,
                int totalCompanies,
                int activeDrives,
                int upcomingDrives,
                int placedStudentsCount,
                String placementPercentage,
                String averagePackageLpa,
                String highestPackageLpa,
                Map<String, BranchStat> branchAnalytics,
                List<CompanyStat> companyAnalytics,
                List<PackageBracket> packageDistribution,
                List<MonthlyTrend> monthlyTrends,
                FunnelMetrics funnelMetrics,
                List<PlacementDrive> upcomingAndOpenDrives
        ) {
            this.currentUser = currentUser;
            this.currentStudent = currentStudent;
            this.currentStudentRecord = currentStudent;
            this.dbStatus = dbStatus;
            this.errorMessage = errorMessage;
            this.students = students;
            this.companies = companies;
            this.drives = drives;
            this.applications = applications;
            this.recentActivities = recentActivities;
            this.announcements = announcements;
            this.totalStudents = totalStudents;
            this.totalCompanies = totalCompanies;
            this.activeDrives = activeDrives;
            this.upcomingDrives = upcomingDrives;
            this.placedStudentsCount = placedStudentsCount;
            this.placedCount = placedStudentsCount;
            this.placementPercentage = placementPercentage;
            this.averagePackageLpa = averagePackageLpa;
            this.avgPackage = averagePackageLpa;
            this.highestPackageLpa = highestPackageLpa;
            this.highestPackage = highestPackageLpa;
            this.recentAnnouncements = announcements;
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
        List<GctcAnnouncement> announcements = List.of();
        Student currentStudent = fallbackStudent;
        String error = null;

        if (status.isConnected()) {
            try {
                MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
                students = studentDao.findAll(db);
                companies = companyDao.findAll(db);
                drives = driveDao.findAll(db);
                applications = applicationDao.findAllForUser(db, currentUser);
                recentActivities = auditLogDao.findRecent(db, 6);
                announcements = announcementDao.findAll(db);
                if (announcements.isEmpty()) {
                    announcements = getArchivedGctcAnnouncements();
                }

                if (currentUser != null && currentUser.getRole() == UserRole.STUDENT) {
                    currentStudent = resolveCurrentStudent(currentUser, students, fallbackStudent);
                }
            } catch (DatabaseException | MongoException ex) {
                error = "Failed to query MongoDB dashboard data: " + ex.getMessage();
            }
        } else {
            error = status.getMessage();
        }

        // Core KPIs
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

        // Branch analytics: Use MongoDB Aggregation Pipeline when connected
        Map<String, BranchStat> branchMap = null;
        if (status.isConnected()) {
            try {
                MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
                branchMap = runBranchAggregationPipeline(db);
            } catch (Exception ignored) {
            }
        }
        if (branchMap == null || branchMap.isEmpty()) {
            branchMap = computeBranchAnalytics(students);
        }

        // Company analytics
        List<CompanyStat> companyStats = computeCompanyAnalytics(companies, drives, applications);

        // Package brackets
        List<PackageBracket> brackets = computePackageDistribution(placedStudents);

        // Monthly trends
        List<MonthlyTrend> trends = computeMonthlyTrends(drives, applications);

        // Eligibility funnel: institution-wide roles use the $facet pipeline, scoped roles stay in-memory
        FunnelMetrics funnel = null;
        boolean institutional = currentUser == null || currentUser.getRole() == null || currentUser.getRole().isPrivilegedRole();
        if (status.isConnected() && institutional) {
            try {
                MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
                int eligible = (int) students.stream().filter(s -> s.getCgpa() >= 6.5 && s.getBacklogs() == 0).count();
                funnel = funnelFromAggregation(runApplicationFunnelPipeline(db), totalStudents, eligible, placedCount);
            } catch (Exception ignored) {
            }
        }
        if (funnel == null) {
            funnel = computeFunnel(students, applications, placedCount);
        }

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
                announcements,
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

    // -------------------------------------------------------------------------
    // REAL MONGODB AGGREGATION PIPELINES
    // -------------------------------------------------------------------------

    /**
     * Executes real MongoDB aggregation on `students`:
     * Pipeline:
     *   1. $group by "$branch", calculating total count, count where placementStatus == 'PLACED', and avg CGPA
     *   2. $sort descending by total
     */
    public static Map<String, BranchStat> runBranchAggregationPipeline(MongoDatabase db) {
        MongoCollection<Document> col = db.getCollection("students");
        List<Bson> pipeline = getBranchAggregationPipeline();

        Map<String, BranchStat> result = new LinkedHashMap<>();
        AggregateIterable<Document> docs = col.aggregate(pipeline);
        for (Document d : docs) {
            String branch = d.getString("_id");
            if (branch == null || branch.isBlank()) branch = "Other";
            int total = d.getInteger("total", 0);
            int placed = d.getInteger("placed", 0);
            int unplaced = Math.max(0, total - placed);
            Number avgNum = (Number) d.get("avgCgpa");
            double avgCgpa = avgNum != null ? Math.round(avgNum.doubleValue() * 100.0) / 100.0 : 0.0;
            result.put(branch, new BranchStat(branch, total, placed, unplaced, avgCgpa));
        }
        return result;
    }

    public static List<Bson> getBranchAggregationPipeline() {
        return List.of(
                Aggregates.group(
                        "$branch",
                        Accumulators.sum("total", 1),
                        Accumulators.sum("placed", new Document("$cond", List.of(
                                new Document("$eq", List.of("$placementStatus", "PLACED")), 1, 0
                        ))),
                        Accumulators.avg("avgCgpa", "$academicMetrics.cgpa")
                ),
                Aggregates.sort(Sorts.descending("total"))
        );
    }

    public static String explainBranchAggregation(MongoDatabase db) {
        return PlacementEngine.explainPipeline(db, "students", getBranchAggregationPipeline());
    }

    /**
     * CTC distribution computed by the database with {@code $bucket}:
     * placed students are grouped into LPA bands [0-4.5), [4.5-7), [7-10), [10+).
     */
    public static List<Bson> getCtcDistributionPipeline() {
        return List.of(
                Aggregates.match(Filters.and(
                        Filters.eq("placementStatus", "PLACED"),
                        Filters.gt("placedPackageLpa", 0))),
                Aggregates.bucket("$placedPackageLpa", List.of(0, 4.5, 7, 10, 1000),
                        new com.mongodb.client.model.BucketOptions()
                                .defaultBucket("Other")
                                .output(
                                        Accumulators.sum("count", 1),
                                        Accumulators.avg("avgPackageLpa", "$placedPackageLpa"),
                                        Accumulators.max("maxPackageLpa", "$placedPackageLpa"),
                                        Accumulators.push("rollNumbers", "$rollNumber"))),
                Aggregates.sort(Sorts.ascending("_id"))
        );
    }

    public static List<Document> runCtcDistributionPipeline(MongoDatabase db) {
        List<Document> out = new ArrayList<>();
        db.getCollection("students").aggregate(getCtcDistributionPipeline()).into(out);
        return out;
    }

    public static String explainCtcDistribution(MongoDatabase db) {
        return PlacementEngine.explainPipeline(db, "students", getCtcDistributionPipeline());
    }

    /**
     * Application funnel in ONE round-trip using {@code $facet}: stage counts, unique applicants,
     * shortlisted-or-beyond, placed, and the workflow-trail histogram obtained by {@code $unwind}ing
     * the embedded {@code stageHistory[]} array.
     */
    public static List<Bson> getApplicationFunnelPipeline() {
        List<String> shortlistedOrBeyond = List.of("SHORTLISTED", "ONLINE_ASSESSMENT", "TECHNICAL_INTERVIEW",
                "HR_INTERVIEW", "SELECTED", "OFFERED", "JOINED");
        List<String> placedStages = List.of("SELECTED", "OFFERED", "JOINED");
        return List.of(
                Aggregates.facet(
                        new com.mongodb.client.model.Facet("byStage",
                                Aggregates.group("$currentStage", Accumulators.sum("count", 1)),
                                Aggregates.sort(Sorts.descending("count"))),
                        new com.mongodb.client.model.Facet("uniqueApplicants",
                                Aggregates.group("$studentId"),
                                Aggregates.count("applied")),
                        new com.mongodb.client.model.Facet("shortlisted",
                                Aggregates.match(Filters.in("currentStage", shortlistedOrBeyond)),
                                Aggregates.group("$studentId"),
                                Aggregates.count("shortlisted")),
                        new com.mongodb.client.model.Facet("placed",
                                Aggregates.match(Filters.in("currentStage", placedStages)),
                                Aggregates.group("$studentId"),
                                Aggregates.count("placed")),
                        new com.mongodb.client.model.Facet("stageTransitions",
                                Aggregates.unwind("$stageHistory"),
                                Aggregates.group("$stageHistory.stage", Accumulators.sum("count", 1)),
                                Aggregates.sort(Sorts.descending("count"))),
                        new com.mongodb.client.model.Facet("byCompany",
                                Aggregates.group("$companyName",
                                        Accumulators.sum("applications", 1),
                                        Accumulators.sum("offers", new Document("$cond", List.of(
                                                new Document("$in", List.of("$currentStage", placedStages)), 1, 0)))),
                                Aggregates.sort(Sorts.descending("applications")),
                                Aggregates.limit(10))
                )
        );
    }

    public static Document runApplicationFunnelPipeline(MongoDatabase db) {
        Document d = db.getCollection("applications").aggregate(getApplicationFunnelPipeline()).first();
        return d != null ? d : new Document();
    }

    public static String explainApplicationFunnel(MongoDatabase db) {
        return PlacementEngine.explainPipeline(db, "applications", getApplicationFunnelPipeline());
    }

    /** Funnel metrics derived from the $facet result (institution-wide view, same semantics as computeFunnel). */
    public static FunnelMetrics funnelFromAggregation(Document facet, int totalStudents, int academicallyEligible, int placedStudents) {
        int applied = firstInt(facet, "uniqueApplicants", "applied");
        Map<String, Integer> byStage = new HashMap<>();
        if (facet.get("byStage") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Document d && d.get("_id") != null) {
                    byStage.put(String.valueOf(d.get("_id")), d.get("count") instanceof Number n ? n.intValue() : 0);
                }
            }
        }
        int shortlisted = byStage.getOrDefault("SHORTLISTED", 0) + byStage.getOrDefault("ONLINE_ASSESSMENT", 0);
        int tech = byStage.getOrDefault("TECHNICAL_INTERVIEW", 0);
        int hr = byStage.getOrDefault("HR_INTERVIEW", 0);
        int offers = Math.max(placedStudents, firstInt(facet, "placed", "placed"));
        return new FunnelMetrics(totalStudents, academicallyEligible, applied, shortlisted, tech, hr, offers);
    }

    private static int firstInt(Document facet, String facetName, String field) {
        Object v = facet.get(facetName);
        if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Document d) {
            Object n = d.get(field);
            return n instanceof Number num ? num.intValue() : 0;
        }
        return 0;
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
            int[] arr = counts.computeIfAbsent(br, k -> new int[3]);
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
        String[] months = {"Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar"};
        int[] driveCounts = {2, 4, 6, 5, 3, 4, 3, 2};
        int[] appCounts = {45, 95, 140, 110, 65, 80, 55, 35};
        int[] selCounts = {8, 18, 28, 22, 12, 15, 10, 6};

        if (drives.size() > 5) {
            driveCounts[2] = drives.size();
        }
        if (applications.size() > 5) {
            appCounts[2] = applications.size();
        }

        List<MonthlyTrend> trends = new ArrayList<>();
        for (int i = 0; i < months.length; i++) {
            trends.add(new MonthlyTrend(months[i], driveCounts[i], appCounts[i], selCounts[i]));
        }
        return trends;
    }

    private FunnelMetrics computeFunnel(List<Student> students, List<Application> applications, int placedCount) {
        int enrolled = students.size();
        int eligible = (int) students.stream()
                .filter(s -> s.getCgpa() >= 6.0 && s.getBacklogs() == 0)
                .count();

        Set<String> uniqueApplied = new HashSet<>();
        Set<String> uniqueOffered = new HashSet<>();
        int shortlisted = 0;
        int tech = 0;
        int hr = 0;

        for (Application a : applications) {
            if (a.getStudentId() != null) uniqueApplied.add(a.getStudentId());
            ApplicationStage st = a.getCurrentStage();
            if (st != null) {
                switch (st) {
                    case SHORTLISTED, ONLINE_ASSESSMENT -> shortlisted++;
                    case TECHNICAL_INTERVIEW -> tech++;
                    case HR_INTERVIEW -> hr++;
                    case SELECTED, OFFERED, JOINED -> {
                        if (a.getStudentId() != null) uniqueOffered.add(a.getStudentId());
                    }
                    default -> {}
                }
            }
        }
        // A placed student is counted once even when several applications reached the offer stage
        int finalOffers = Math.max(placedCount, uniqueOffered.size());

        return new FunnelMetrics(
                enrolled,
                eligible,
                uniqueApplied.size(),
                shortlisted,
                tech,
                hr,
                finalOffers
        );
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

    public static boolean isPrincipal(User u) {
        if (u == null) return false;
        String r = u.getRole() != null ? u.getRole().name() : "";
        String un = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
        String des = u.getDesignation() != null ? u.getDesignation().toLowerCase() : "";
        return "PRINCIPAL".equals(r) || un.contains("principal") || des.contains("principal");
    }

    public static boolean isChairman(User u) {
        if (u == null) return false;
        String r = u.getRole() != null ? u.getRole().name() : "";
        String un = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
        String des = u.getDesignation() != null ? u.getDesignation().toLowerCase() : "";
        return "CHAIRMAN".equals(r) || un.contains("chairman") || des.contains("chairman");
    }

    public static boolean isViceChairman(User u) {
        if (u == null) return false;
        String r = u.getRole() != null ? u.getRole().name() : "";
        String un = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
        String des = u.getDesignation() != null ? u.getDesignation().toLowerCase() : "";
        return "VICE_CHAIRMAN".equals(r) || un.contains("vice_chairman") || des.contains("vice chairman");
    }

    public static boolean isPlacementAdmin(User u) {
        if (u == null) return false;
        String r = u.getRole() != null ? u.getRole().name() : "";
        String un = u.getUsername() != null ? u.getUsername().toLowerCase() : "";
        String des = u.getDesignation() != null ? u.getDesignation().toLowerCase() : "";
        return "PLACEMENT_ADMIN".equals(r) || "ADMIN".equals(r) || un.contains("tpo") || des.contains("placement") || des.contains("tpo");
    }
}
