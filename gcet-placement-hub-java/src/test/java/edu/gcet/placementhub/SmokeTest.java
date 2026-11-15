package edu.gcet.placementhub;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoDatabase;
import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.dao.AnnouncementDao;
import edu.gcet.placementhub.dao.ApplicationDao;
import edu.gcet.placementhub.dao.ApprovalDao;
import edu.gcet.placementhub.dao.AuditLogDao;
import edu.gcet.placementhub.dao.CompanyDao;
import edu.gcet.placementhub.dao.DriveDao;
import edu.gcet.placementhub.dao.FacultyDao;
import edu.gcet.placementhub.dao.SkillDao;
import edu.gcet.placementhub.dao.StudentDao;
import edu.gcet.placementhub.dao.UserDao;
import edu.gcet.placementhub.database.BsonUtil;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.MongoSchemaInitializer;
import edu.gcet.placementhub.database.MongoSeedData;
import edu.gcet.placementhub.models.Application;
import edu.gcet.placementhub.models.ApplicationStage;
import edu.gcet.placementhub.models.DbStatus;
import edu.gcet.placementhub.models.GovernanceModels.ExtendedDbStatus;
import edu.gcet.placementhub.models.GovernanceModels.ReportBundle;
import edu.gcet.placementhub.models.GovernanceModels.ReportFilter;
import edu.gcet.placementhub.models.PlacementDrive;
import edu.gcet.placementhub.models.PlacementModels.PlacementBundle;
import edu.gcet.placementhub.models.PortalCategory;
import edu.gcet.placementhub.models.Skill;
import edu.gcet.placementhub.models.Student;
import edu.gcet.placementhub.models.StudentProject;
import edu.gcet.placementhub.models.User;
import edu.gcet.placementhub.services.AuthService;
import edu.gcet.placementhub.services.DashboardService;
import edu.gcet.placementhub.services.GovernanceService;
import edu.gcet.placementhub.services.PlacementEngine;
import edu.gcet.placementhub.services.PlacementManagementService;
import edu.gcet.placementhub.services.StudentManagementService;
import edu.gcet.placementhub.utils.OperationResult;
import org.bson.Document;

import java.util.List;
import java.util.Map;

/**
 * Headless end-to-end verification against the live MONGO_URI from .env (Atlas or local).
 *
 * Run from gcet-placement-hub-java/:
 * <pre>
 *   ./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=edu.gcet.placementhub.SmokeTest
 * </pre>
 * Exit code 0 = every check passed. It never drops collections; the only writes are the idempotent seed
 * plus one application / project round-trip that is cleaned up afterwards.
 */
public final class SmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    private SmokeTest() {
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  [PASS] " + name + (detail != null ? "  -> " + detail : ""));
        } else {
            failed++;
            System.out.println("  [FAIL] " + name + (detail != null ? "  -> " + detail : ""));
        }
    }

    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        DatabaseConfig cfg = DatabaseConfig.getInstance();
        if (args.length > 0 && "--reset-local".equals(args[0])) {
            // Safety: a full database drop is only ever allowed against a LOCAL MongoDB, never Atlas.
            if (!DatabaseConfig.isLocalUri(cfg.getMongoUri())) {
                System.out.println("Refusing --reset-local: MONGO_URI is not a local server (" + cfg.getMaskedUri() + ")");
                System.exit(2);
            }
            try (com.mongodb.client.MongoClient c = com.mongodb.client.MongoClients.create(cfg.getMongoUri())) {
                c.getDatabase(cfg.getDatabaseName()).drop();
                System.out.println("Dropped local database " + cfg.getDatabaseName() + " for a clean re-seed.");
            }
        }
        System.out.println("=== GCET Placement Hub - MongoDB Smoke Test ===");
        System.out.println("Config source : " + cfg.getSourceDescription());
        System.out.println("Cluster type  : " + cfg.getClusterType());
        System.out.println("URI (masked)  : " + cfg.getMaskedUri());
        System.out.println("Database      : " + cfg.getDatabaseName());
        System.out.println();

        // 1. Connection + schema + seed (all happen inside getDatabase())
        System.out.println("[1] Connection, $jsonSchema validators, indexes, seed");
        DatabaseConnection conn = DatabaseConnection.getInstance();
        MongoDatabase db = conn.getDatabase();
        DbStatus ping = conn.pingAndInspect();
        check("ping {ping:1}", ping.isConnected(), ping.getMessage());
        check("no local fallback needed", !ping.isUsingLocalFallback() || DatabaseConfig.isLocalUri(cfg.getMongoUri()),
                "usingLocalFallback=" + ping.isUsingLocalFallback());

        ExtendedDbStatus ext = conn.inspectExtendedDiagnostics();
        check("all collections present", ext.availableCollectionCount() == ext.totalRequiredCollections(),
                ext.availableCollectionCount() + "/" + ext.totalRequiredCollections());
        Map<String, Document> validators = MongoSchemaInitializer.currentValidators(db);
        check("$jsonSchema on students/users/placement_drives/applications",
                validators.keySet().containsAll(List.of("students", "users", "placement_drives", "applications")),
                String.valueOf(validators.keySet()));
        int totalIndexes = ext.indexesByCollection().values().stream().mapToInt(List::size).sum();
        check("indexes created", totalIndexes >= 40, totalIndexes + " indexes across " + ext.indexesByCollection().size() + " collections");
        check("unique compound index applications(studentId, driveId)",
                ext.indexesByCollection().getOrDefault("applications", List.of()).stream().anyMatch(s -> s.contains("uq_applications_student_drive")), null);
        check("text index on companies", ext.indexesByCollection().getOrDefault("companies", List.of()).stream().anyMatch(s -> s.contains("[text]")), null);
        check("text index on placement_drives", ext.indexesByCollection().getOrDefault("placement_drives", List.of()).stream().anyMatch(s -> s.contains("[text]")), null);
        check("dbStats returned", ext.dbStats() != null && !ext.dbStats().isEmpty(), String.valueOf(ext.dbStats()));

        // 2. Seed idempotency
        System.out.println("[2] Seed idempotency");
        long studentsBefore = db.getCollection("students").countDocuments();
        long usersBefore = db.getCollection("users").countDocuments();
        String second = MongoSeedData.seedIfEmpty(db);
        MongoSchemaInitializer.apply(db);
        check("second seed run is a no-op", second.toLowerCase().contains("skipped"), second);
        check("document counts unchanged after re-seed",
                studentsBefore == db.getCollection("students").countDocuments() && usersBefore == db.getCollection("users").countDocuments(),
                "students=" + studentsBefore + " users=" + usersBefore);
        check("seeded students >= 30", studentsBefore >= 30, "students=" + studentsBefore);
        check("seeded applications > 0", db.getCollection("applications").countDocuments() > 0, null);

        // 3. Validator enforcement
        System.out.println("[3] $jsonSchema enforcement");
        boolean rejected = false;
        String rejectMsg = "";
        try {
            db.getCollection("students").insertOne(new Document("rollNumber", "ZZ-INVALID")
                    .append("fullName", "Invalid CGPA").append("email", "invalid@gcet.edu.in").append("branch", "CSE")
                    .append("academicMetrics", new Document("cgpa", 11.5).append("backlogs", 0).append("graduationYear", 2026))
                    .append("approvalStatus", "APPROVED").append("placementStatus", "UNPLACED").append("createdAt", new java.util.Date()));
            db.getCollection("students").deleteOne(new Document("rollNumber", "ZZ-INVALID"));
        } catch (MongoWriteException ex) {
            rejected = true;
            rejectMsg = ex.getError().getMessage();
        }
        check("insert with cgpa=11.5 rejected by validator", rejected, rejectMsg);

        // 4. DAO reads
        System.out.println("[4] DAO reads (embedded + referenced documents)");
        UserDao userDao = new UserDao();
        StudentDao studentDao = new StudentDao();
        User principal = userDao.findByLoginIdentifier(db, "principal.sagar");
        check("UserDao.findByLoginIdentifier principal.sagar", principal != null, principal != null ? principal.getFullName() : null);
        List<Student> students = studentDao.findAll(db);
        Student akhil = studentDao.findByRollNumber(db, "22r11a0501");
        check("StudentDao.findByRollNumber (case-insensitive)", akhil != null && !akhil.getSkills().isEmpty(),
                akhil != null ? akhil.getFullName() + " skills=" + akhil.getSkills().size() + " projects=" + akhil.getProjects().size() : null);
        check("StudentDao.findByIdOrRoll by ObjectId", akhil != null && studentDao.findByIdOrRoll(db, akhil.getId()) != null, null);
        check("CompanyDao.findAll", new CompanyDao().findAll(db).size() >= 12, null);
        List<PlacementDrive> drives = new DriveDao().findAll(db);
        check("DriveDao.findAll with embedded eligibilityCriteria/requiredSkills",
                !drives.isEmpty() && !drives.get(0).getEligibleBranches().isEmpty() && drives.get(0).getCompanyTier() != null,
                drives.isEmpty() ? null : drives.get(0).getCompanyName() + " tier=" + drives.get(0).getCompanyTier());
        List<Application> apps = new ApplicationDao().findAllForUser(db, principal);
        check("ApplicationDao.findAllForUser ($lookup students)", !apps.isEmpty() && apps.get(0).getStudentName() != null && !apps.get(0).getStageHistory().isEmpty(),
                apps.isEmpty() ? null : apps.get(0).getStudentName() + " -> " + apps.get(0).getCompanyName() + " trail=" + apps.get(0).getStageHistory().size());
        check("FacultyDao.findAll", new FacultyDao().findAll(db).size() >= 3, null);
        check("SkillDao.findAllSkills", new SkillDao().findAllSkills(db).size() >= 20, null);
        check("ApprovalDao history", !new ApprovalDao().findApprovalHistory(db, 50).isEmpty(), null);
        check("ApprovalDao admin requests", !new ApprovalDao().findAllAdminRequests(db).isEmpty(), null);
        check("AuditLogDao.findRecent", !new AuditLogDao().findRecent(db, 5).isEmpty(), null);
        check("AnnouncementDao.findAll", !new AnnouncementDao().findAll(db).isEmpty(), null);
        check("CompanyDao text search", !new CompanyDao().searchText(db, "Amazon").isEmpty(), null);
        check("DriveDao text search", !new DriveDao().searchText(db, "Engineer").isEmpty(), null);

        // 5. Authentication
        System.out.println("[5] Authentication (BCrypt) for seeded accounts");
        AuthService auth = new AuthService();
        login(auth, "principal.sagar", cfg.getBootstrapPassword("GCET_BOOTSTRAP_PRINCIPAL_PASS"), PortalCategory.ADMIN);
        login(auth, "tpo.gcet", cfg.getBootstrapPassword("GCET_BOOTSTRAP_OFFICER_PASS"), PortalCategory.FACULTY);
        login(auth, "faculty_lavanya", cfg.getBootstrapPassword("GCET_BOOTSTRAP_FACULTY_PASS"), PortalCategory.FACULTY);
        login(auth, "22R11A0501", cfg.getBootstrapPassword("GCET_BOOTSTRAP_STUDENT_SEED_PASS"), PortalCategory.STUDENTS);
        login(auth, "recruiter_amazon", cfg.getBootstrapPassword("GCET_BOOTSTRAP_RECRUITER_PASS"), PortalCategory.COMPANIES);
        try {
            auth.login("principal.sagar", "definitely-wrong", PortalCategory.ADMIN);
            check("wrong password rejected", false, null);
        } catch (AuthService.AuthException ex) {
            check("wrong password rejected", true, ex.getMessage());
        }

        // 6. Aggregation pipelines
        System.out.println("[6] Aggregation pipelines + explain(executionStats)");
        PlacementDrive demoDrive = drives.stream().filter(d -> "OPEN".equalsIgnoreCase(d.getStatus()) && !d.getRequiredSkills().isEmpty()).findFirst().orElse(drives.get(0));
        List<Document> eligible = PlacementEngine.executeEligibilityPipeline(db, demoDrive);
        check("eligibility pipeline ($match/$project/$sort)", !eligible.isEmpty(), demoDrive.getCompanyName() + ": " + eligible.size() + " eligible");
        List<Document> gaps = PlacementEngine.executeSkillGapPipeline(db, demoDrive);
        check("skill-gap pipeline ($setIntersection/$setDifference/$lookup)",
                !gaps.isEmpty() && gaps.get(0).containsKey("readinessPct") && gaps.get(0).containsKey("missingSkillNames"),
                gaps.isEmpty() ? null : gaps.get(0).get("fullName") + " readiness=" + gaps.get(0).get("readinessPct") + "% missing=" + gaps.get(0).get("missingSkillNames"));
        Map<String, DashboardService.BranchStat> branch = DashboardService.runBranchAggregationPipeline(db);
        check("branch analytics ($group/$sort)", branch.size() >= 5, String.valueOf(branch.keySet()));
        List<Document> ctc = DashboardService.runCtcDistributionPipeline(db);
        check("CTC distribution ($bucket)", !ctc.isEmpty(), ctc.stream().map(d -> d.get("_id") + ":" + d.get("count")).toList().toString());
        Document funnel = DashboardService.runApplicationFunnelPipeline(db);
        check("application funnel ($facet/$unwind)", funnel.containsKey("byStage") && funnel.containsKey("stageTransitions"),
                "applied=" + funnel.get("uniqueApplicants") + " placed=" + funnel.get("placed"));
        for (String name : List.of("branch", "eligibility", "skillgap", "ctc", "funnel")) {
            String explain = switch (name) {
                case "branch" -> DashboardService.explainBranchAggregation(db);
                case "eligibility" -> PlacementEngine.explainEligibilityPipeline(db, demoDrive);
                case "skillgap" -> PlacementEngine.explainSkillGapPipeline(db, demoDrive);
                case "ctc" -> DashboardService.explainCtcDistribution(db);
                default -> DashboardService.explainApplicationFunnel(db);
            };
            check("explain(executionStats) " + name, explain.contains("executionStats") && !explain.contains("\"error\""),
                    explain.length() + " chars");
        }
        check("pipeline JSON rendering (inspector)", BsonUtil.prettyJson(PlacementEngine.buildSkillGapPipeline(demoDrive)).contains("$setIntersection"), null);

        // 7. Services used by the Swing screens
        System.out.println("[7] Services behind the Swing screens");
        DashboardService.DashboardData dash = new DashboardService().loadDashboardData(principal, null);
        check("DashboardService.loadDashboardData", dash.errorMessage == null && dash.totalStudents > 0,
                "students=" + dash.totalStudents + " companies=" + dash.totalCompanies + " placed=" + dash.placedCount + " funnel=" + dash.funnelMetrics);
        ReportBundle report = new GovernanceService().generateReportBundle(principal, ReportFilter.all());
        check("GovernanceService.generateReportBundle", report.error() == null && !report.branchRows().isEmpty(), "branches=" + report.branchRows().size());
        check("GovernanceService.loadApprovalBundle", new GovernanceService().loadApprovalBundle(principal).error() == null, null);
        check("GovernanceService.loadAuditLogs", new GovernanceService().loadAuditLogs(principal, "", "ALL", "ALL", "", "").error() == null, null);
        check("GovernanceService.loadExtendedDbDiagnostics", new GovernanceService().loadExtendedDbDiagnostics().baseStatus().isConnected(), null);
        StudentManagementService sms = new StudentManagementService();
        check("StudentManagementService.loadDirectoryBundle", sms.loadDirectoryBundle(principal).error == null, null);
        PlacementBundle pb = new PlacementManagementService().loadPlacementBundle(principal);
        check("PlacementManagementService.loadPlacementBundle", pb.error == null && !pb.drives.isEmpty(), null);

        // 8. Write round-trip (atomic $push stageHistory) then clean up
        System.out.println("[8] Write round-trip: project + application stage trail");
        User tpo = userDao.findByLoginIdentifier(db, "tpo.gcet");
        List<Skill> skills = new SkillDao().findAllSkills(db);
        Student target = null;
        PlacementDrive targetDrive = null;
        ApplicationDao appDao = new ApplicationDao();
        outer:
        for (Student s : students) {
            if (!"APPROVED".equalsIgnoreCase(s.getApprovalStatus()) || "PLACED".equalsIgnoreCase(s.getPlacementStatus())) continue;
            for (PlacementDrive d : drives) {
                if (!PlacementEngine.isDriveOpenForApplications(d)) continue;
                if (PlacementEngine.checkEligibility(s, d, skills).eligible && appDao.findByStudentAndDrive(db, s.getId(), d.getId()) == null) {
                    target = s;
                    targetDrive = d;
                    break outer;
                }
            }
        }
        check("found eligible (student, drive) pair for write test", target != null, target != null ? target.getRollNumber() + " -> " + targetDrive.getCompanyName() : null);
        if (target != null) {
            PlacementManagementService pms = new PlacementManagementService();
            OperationResult<String> submit = pms.submitApplication(tpo, targetDrive.getId(), target.getId());
            check("submitApplication", submit.isSuccess(), submit.getMessage());
            if (submit.isSuccess()) {
                OperationResult<Void> adv = pms.advanceApplicationStage(tpo, submit.getData(), ApplicationStage.SHORTLISTED, null, null, "Smoke test shortlist");
                check("advanceApplicationStage -> SHORTLISTED", adv.isSuccess(), adv.getMessage());
                Application a = appDao.findById(db, submit.getData());
                check("stageHistory trail has 2 entries", a != null && a.getStageHistory().size() == 2 && a.getCurrentStage() == ApplicationStage.SHORTLISTED,
                        a != null ? "trail=" + a.getStageHistory().size() : null);
                OperationResult<String> dup = pms.submitApplication(tpo, targetDrive.getId(), target.getId());
                check("duplicate application blocked", !dup.isSuccess(), dup.getMessage());
                // cleanup
                db.getCollection("applications").deleteOne(BsonUtil.idFilter(submit.getData()));
                db.getCollection("students").updateOne(BsonUtil.idFilter(target.getId()), new Document("$set", new Document("placementStatus", "UNPLACED")));
                check("cleanup application", appDao.findById(db, submit.getData()) == null, null);
            }
            StudentProject project = new StudentProject();
            project.setTitle("Smoke Test Project");
            project.setDescription("Temporary");
            project.setTechnologies(List.of("Java", "MongoDB"));
            OperationResult<Void> add = sms.addProject(tpo, target.getId(), project);
            Student reloaded = studentDao.findByIdOrRoll(db, target.getId());
            boolean present = reloaded != null && reloaded.getProjects().stream().anyMatch(p -> "Smoke Test Project".equals(p.getTitle()));
            check("addProject ($push projects[])", add.isSuccess() && present, add.getMessage());
            if (present) {
                String pid = reloaded.getProjects().stream().filter(p -> "Smoke Test Project".equals(p.getTitle())).findFirst().get().getId();
                OperationResult<Void> del = sms.deleteProject(tpo, target.getId(), pid);
                Student again = studentDao.findByIdOrRoll(db, target.getId());
                check("deleteProject ($pull projects[])", del.isSuccess() && again.getProjects().stream().noneMatch(p -> "Smoke Test Project".equals(p.getTitle())), del.getMessage());
            }
        }

        long ms = System.currentTimeMillis() - t0;
        System.out.println();
        System.out.println("=== RESULT: " + passed + " passed, " + failed + " failed (" + ms + " ms) against " + cfg.getClusterType() + " ===");
        conn.close();
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void login(AuthService auth, String user, String pass, PortalCategory portal) {
        try {
            AuthService.AuthResult r = auth.login(user, pass, portal);
            check("login " + user + " (" + portal + ")", r.getUser() != null, r.getUser().getRole() + " - " + r.getUser().getFullName());
        } catch (AuthService.AuthException ex) {
            check("login " + user + " (" + portal + ")", false, ex.getMessage());
        }
    }
}
