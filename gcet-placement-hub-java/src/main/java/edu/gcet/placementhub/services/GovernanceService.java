package edu.gcet.placementhub.services;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import edu.gcet.placementhub.dao.*;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.database.DatabaseException;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.GovernanceModels.*;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.OperationResult;
import edu.gcet.placementhub.utils.PasswordHasher;

import java.time.LocalDate;
import java.util.*;

/**
 * MongoDB-backed Phase 5 Governance, Approvals, Analytics, Settings, Audit & Diagnostics Service.
 * Strictly enforces RBAC rules:
 *  - Student approvals: Faculty / TPO only (Department-scoped via DeptNormalizer); Admins/Executives blocked.
 *  - Faculty & Company approvals: Admin Roles only (PRINCIPAL, CHAIRMAN, VICE_CHAIRMAN, ADMIN).
 *  - Admin privilege requests: Viewable by Admin Roles; decided strictly by PRINCIPAL only.
 *  - Audit Logs: Privileged leadership/admins see full institutional logs; other roles see only own actor logs.
 *  - Reports: Strictly scoped by user role; never fabricates mock statistics.
 */
public class GovernanceService {

    private final StudentDao studentDao = new StudentDao();
    private final FacultyDao facultyDao = new FacultyDao();
    private final CompanyDao companyDao = new CompanyDao();
    private final DriveDao driveDao = new DriveDao();
    private final SkillDao skillDao = new SkillDao();
    private final ApplicationDao applicationDao = new ApplicationDao();
    private final UserDao userDao = new UserDao();
    private final ApprovalDao approvalDao = new ApprovalDao();
    private final AuditLogDao auditLogDao = new AuditLogDao();

    // -------------------------------------------------------------------------
    // 1. APPROVAL CENTER
    // -------------------------------------------------------------------------

    public ApprovalBundle loadApprovalBundle(User actor) {
        if (actor == null) {
            return new ApprovalBundle(List.of(), List.of(), List.of(), List.of(), List.of(),
                    "Authentication required to access the Approval Center.");
        }
        UserRole role = actor.getRole();
        boolean isFacultyOrTpo = role == UserRole.FACULTY || role == UserRole.PLACEMENT_OFFICER;
        boolean isAdmin = role.isAdminRole();

        if (!isFacultyOrTpo && !isAdmin) {
            return new ApprovalBundle(List.of(), List.of(), List.of(), List.of(), List.of(),
                    "Access Restricted: Only Faculty Coordinators, Placement Officers, or College Administrators may open the Approval Center.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            List<Student> allStudents = studentDao.findAll(db);
            List<Student> visibleStudents = new ArrayList<>();
            if (role == UserRole.FACULTY) {
                for (Student s : allStudents) {
                    if (DeptNormalizer.isFacultyAuthorizedForStudent(
                            actor.getAuthorizedDepartments(), actor.getUsername(), s.getBranch())) {
                        visibleStudents.add(s);
                    }
                }
            } else if (role == UserRole.PLACEMENT_OFFICER) {
                visibleStudents.addAll(allStudents);
            }

            List<Faculty> visibleFaculty = isAdmin ? facultyDao.findAll(db) : List.of();
            List<Company> visibleCompanies = isAdmin ? companyDao.findAll(db) : List.of();
            List<AdminRequest> adminReqs = isAdmin ? approvalDao.findAllAdminRequests(db) : List.of();
            List<ApprovalRecord> history = approvalDao.findApprovalHistory(db, 100);

            return new ApprovalBundle(
                    visibleStudents,
                    visibleFaculty,
                    visibleCompanies,
                    adminReqs,
                    history,
                    null
            );
        } catch (DatabaseException | MongoException ex) {
            return new ApprovalBundle(List.of(), List.of(), List.of(), List.of(), List.of(),
                    "Failed to load Approval Center data from MongoDB: " + ex.getMessage());
        }
    }

    public OperationResult<Integer> decideStudents(User actor, List<String> studentIds, boolean approve, String reason) {
        if (actor == null) {
            return OperationResult.fail("Session expired. Please sign in again.");
        }
        UserRole role = actor.getRole();
        if (role != UserRole.FACULTY && role != UserRole.PLACEMENT_OFFICER) {
            return OperationResult.fail(
                    "RBAC Governance Enforcement: Student registrations can only be approved or rejected by Department Faculty Coordinators or the Placement Officer. "
                            + "Administrators and Executives are blocked from student clearance actions.");
        }
        if (studentIds == null || studentIds.isEmpty()) {
            return OperationResult.fail("Select at least one student registration to process.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (!approve && cleanReason.isEmpty()) {
            return OperationResult.fail("A rejection reason is mandatory when rejecting student registrations.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            int processed = 0;
            for (String sid : studentIds) {
                Student stu = studentDao.findByIdOrRoll(db, sid);
                if (stu == null) continue;

                if (role == UserRole.FACULTY && !DeptNormalizer.isFacultyAuthorizedForStudent(
                        actor.getAuthorizedDepartments(), actor.getUsername(), stu.getBranch())) {
                    return OperationResult.fail("Department Access Denied: You are not authorized to clear students in branch \""
                            + stu.getBranch() + "\" (" + stu.getRollNumber() + ").");
                }

                approvalDao.updateStudentApproval(
                        db,
                        stu.getId(),
                        stu.getUserId(),
                        stu.getRollNumber(),
                        approve,
                        approve ? null : cleanReason,
                        actor.getId(),
                        actor.getFullName()
                );

                ApprovalRecord rec = new ApprovalRecord();
                rec.setRequestType("STUDENT_REGISTRATION");
                rec.setTargetId(stu.getId());
                rec.setTargetName(stu.getFullName());
                rec.setStudentRollNumber(stu.getRollNumber());
                rec.setDepartment(stu.getBranch());
                rec.setApproverId(actor.getId());
                rec.setApproverName(actor.getFullName());
                rec.setApproverRole(role.name());
                rec.setDecision(approve ? "APPROVED" : "REJECTED");
                rec.setComments(approve
                        ? "Verified departmental academic credentials & activated student portal login."
                        : cleanReason);
                approvalDao.insertApprovalRecord(db, rec);

                auditLogDao.insertLog(
                        db,
                        approve ? "STUDENT_REGISTRATION_APPROVED" : "STUDENT_REGISTRATION_REJECTED",
                        actor.getId(),
                        actor.getUsername(),
                        role.name(),
                        (approve ? "Approved" : "Rejected") + " student " + stu.getFullName()
                                + " (" + stu.getRollNumber() + ", " + stu.getBranch() + ")."
                                + (approve ? "" : " Reason: " + cleanReason)
                );
                processed++;
            }

            if (processed == 0) {
                return OperationResult.fail("No matching student records found in MongoDB.");
            }

            return OperationResult.ok(
                    (approve ? "Approved " : "Rejected ") + processed + " student registration(s) in MongoDB.",
                    processed
            );
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to save student decision in MongoDB: " + ex.getMessage());
        }
    }

    public OperationResult<Void> decideFaculty(User actor, Faculty target, boolean approve, String reason) {
        if (actor == null || !actor.getRole().isAdminRole()) {
            return OperationResult.fail("Admin Authority Required: Only Principal, Chairman, Vice Chairman, or System Admin may approve or reject Faculty registrations.");
        }
        if (target == null) {
            return OperationResult.fail("Select a valid faculty applicant record.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (!approve && cleanReason.isEmpty()) {
            return OperationResult.fail("A mandatory rejection reason is required when rejecting a Faculty registration.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            approvalDao.updateFacultyApproval(
                    db,
                    target.getId(),
                    target.getUserId(),
                    target.getEmail(),
                    approve,
                    approve ? null : cleanReason,
                    actor.getId(),
                    actor.getFullName()
            );

            ApprovalRecord rec = new ApprovalRecord();
            rec.setRequestType("FACULTY_REGISTRATION");
            rec.setTargetId(target.getId());
            rec.setTargetName(target.getFullName());
            rec.setDepartment(target.getDepartment());
            rec.setApproverId(actor.getId());
            rec.setApproverName(actor.getFullName());
            rec.setApproverRole(actor.getRole().name());
            rec.setDecision(approve ? "APPROVED" : "REJECTED");
            rec.setComments(approve
                    ? "Authorized faculty coordinator access & departmental clearance privileges."
                    : cleanReason);
            approvalDao.insertApprovalRecord(db, rec);

            auditLogDao.insertLog(
                    db,
                    approve ? "FACULTY_APPROVED" : "FACULTY_REJECTED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    (approve ? "Approved" : "Rejected") + " faculty coordinator " + target.getFullName()
                            + " (" + target.getEmployeeId() + ", " + target.getDepartment() + ")."
                            + (approve ? "" : " Reason: " + cleanReason)
            );
            return OperationResult.ok(
                    "Faculty account \"" + target.getFullName() + "\" marked " + (approve ? "ACTIVE" : "REJECTED") + " in MongoDB.",
                    null
            );
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update faculty approval in MongoDB: " + ex.getMessage());
        }
    }

    public OperationResult<Void> decideCompany(User actor, Company target, boolean approve, String reason) {
        if (actor == null || !actor.getRole().isAdminRole()) {
            return OperationResult.fail("Admin Authority Required: Only authorized College Administrators may approve or reject Corporate Partner registrations.");
        }
        if (target == null || isBlank(target.getId())) {
            return OperationResult.fail("Select a valid corporate partner record.");
        }
        String cleanReason = reason == null ? "" : reason.trim();
        if (!approve && cleanReason.isEmpty()) {
            return OperationResult.fail("A mandatory rejection reason is required when rejecting a Company registration.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            Company existing = companyDao.findById(db, target.getId());
            if (existing == null) {
                return OperationResult.fail("Company record no longer exists in MongoDB.");
            }

            approvalDao.updateCompanyApprovalAndLinkedUsers(
                    db,
                    existing.getId(),
                    existing.getName(),
                    existing.getHrName(),
                    existing.getHrEmail(),
                    approve,
                    approve ? null : cleanReason,
                    actor.getId(),
                    actor.getFullName()
            );

            ApprovalRecord rec = new ApprovalRecord();
            rec.setRequestType("COMPANY_REGISTRATION");
            rec.setTargetId(existing.getId());
            rec.setTargetName(existing.getName());
            rec.setDepartment(existing.getIndustry());
            rec.setApproverId(actor.getId());
            rec.setApproverName(actor.getFullName());
            rec.setApproverRole(actor.getRole().name());
            rec.setDecision(approve ? "APPROVED" : "REJECTED");
            rec.setComments(approve
                    ? "Corporate partner legitimacy verified; campus placement drives enabled."
                    : cleanReason);
            approvalDao.insertApprovalRecord(db, rec);

            auditLogDao.insertLog(
                    db,
                    approve ? "COMPANY_APPROVED" : "COMPANY_REJECTED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    (approve ? "Approved" : "Rejected") + " corporate partner \"" + existing.getName()
                            + "\" (" + existing.getIndustry() + ")."
                            + (approve ? "" : " Reason: " + cleanReason)
            );
            return OperationResult.ok(
                    "Company \"" + existing.getName() + "\" updated to " + (approve ? "APPROVED" : "REJECTED") + " in MongoDB.",
                    null
            );
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update company approval in MongoDB: " + ex.getMessage());
        }
    }

    public OperationResult<Void> decideAdminRequest(User actor, String requestId, boolean approve, String notes) {
        if (actor == null || actor.getRole() != UserRole.PRINCIPAL) {
            return OperationResult.fail("Principal Exclusive Authority: Only the College Principal may grant or decline Administrator privilege petitions.");
        }
        if (isBlank(requestId)) {
            return OperationResult.fail("Invalid privilege request ID.");
        }
        String cleanNotes = notes == null ? "" : notes.trim();
        if (!approve && cleanNotes.isEmpty()) {
            return OperationResult.fail("A mandatory rejection reason is required when declining an Admin privilege request.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            AdminRequest req = approvalDao.findAdminRequestById(db, requestId);
            if (req == null) {
                return OperationResult.fail("Admin privilege request not found in MongoDB.");
            }
            if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                return OperationResult.fail("This privilege request has already been processed (" + req.getStatus() + ").");
            }

            String decision = approve ? "APPROVED" : "REJECTED";
            String effectiveNotes = cleanNotes.isEmpty()
                    ? "Principal granted " + req.getRequestedRole() + " privilege."
                    : cleanNotes;

            approvalDao.updateAdminRequestDecision(
                    db, req.getId(), decision, effectiveNotes, actor.getId(), actor.getFullName()
            );

            if (approve) {
                approvalDao.elevateUserRole(db, req.getUserId(), req.getRequestedRole());
            }

            ApprovalRecord rec = new ApprovalRecord();
            rec.setRequestType("ADMIN_PRIVILEGE");
            rec.setTargetId(req.getId());
            rec.setTargetName(req.getApplicantName() + " (" + req.getRequestedRole() + ")");
            rec.setDepartment(req.getDepartment());
            rec.setApproverId(actor.getId());
            rec.setApproverName(actor.getFullName());
            rec.setApproverRole(actor.getRole().name());
            rec.setDecision(decision);
            rec.setComments(effectiveNotes);
            approvalDao.insertApprovalRecord(db, rec);

            auditLogDao.insertLog(
                    db,
                    approve ? "ADMIN_PRIVILEGE_GRANTED" : "ADMIN_PRIVILEGE_DECLINED",
                    actor.getId(),
                    actor.getUsername(),
                    actor.getRole().name(),
                    "Principal " + (approve ? "granted" : "declined") + " " + req.getRequestedRole()
                            + " privilege for " + req.getApplicantName() + ". Notes: " + effectiveNotes
            );
            return OperationResult.ok(
                    "Admin privilege petition for \"" + req.getApplicantName() + "\" marked " + decision + ".",
                    null
            );
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to record Principal decision in MongoDB: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 2. ANALYTICS & REPORTS (LIVE MONGODB QUERIES + STRICT RBAC SCOPING)
    // -------------------------------------------------------------------------

    public ReportBundle generateReportBundle(User actor, ReportFilter filter) {
        ReportFilter effectiveFilter = filter != null ? filter : ReportFilter.all();
        if (actor == null) {
            return emptyReportBundle(effectiveFilter, "Authentication required.", "Sign in to view placement reports.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            List<Student> allStudents = studentDao.findAll(db);
            List<Company> allCompanies = companyDao.findAll(db);
            List<PlacementDrive> allDrives = driveDao.findAll(db);
            List<Skill> allSkills = skillDao.findAllSkills(db);
            List<Application> scopedAppsRaw = applicationDao.findAllForUser(db, actor);

            UserRole role = actor.getRole();
            String scopeNotice;
            List<Student> roleStudents = new ArrayList<>();
            List<Company> roleCompanies = new ArrayList<>();
            List<PlacementDrive> roleDrives = new ArrayList<>();

            if (role == UserRole.STUDENT) {
                scopeNotice = "Student Role Scope: Individual placement/application records restricted to your own profile ("
                        + nonBlank(actor.getStudentRollNumber(), actor.getUsername()) + "); institutional branch aggregates shown read-only.";
                for (Student s : allStudents) {
                    if (actor.getId().equals(s.getUserId())
                            || (actor.getStudentRollNumber() != null && actor.getStudentRollNumber().equalsIgnoreCase(s.getRollNumber()))) {
                        roleStudents.add(s);
                    }
                }
                roleCompanies.addAll(allCompanies);
                roleDrives.addAll(allDrives);
            } else if (role == UserRole.COMPANY) {
                scopeNotice = "Recruiter Role Scope: Reports scoped strictly to your organization ("
                        + nonBlank(actor.getCompanyName(), actor.getDepartment()) + ") drives & applicants.";
                Set<String> applicantStudentIds = new HashSet<>();
                for (Application a : scopedAppsRaw) {
                    if (a.getStudentId() != null) applicantStudentIds.add(a.getStudentId());
                }
                for (Student s : allStudents) {
                    if (applicantStudentIds.contains(s.getId())) {
                        roleStudents.add(s);
                    }
                }
                for (Company c : allCompanies) {
                    if (actor.getCompanyId() != null && actor.getCompanyId().equals(c.getId())) {
                        roleCompanies.add(c);
                    }
                }
                for (PlacementDrive d : allDrives) {
                    if (actor.getCompanyId() != null && actor.getCompanyId().equals(d.getCompanyId())) {
                        roleDrives.add(d);
                    }
                }
            } else if (role == UserRole.FACULTY) {
                scopeNotice = "Faculty Coordinator Scope: Scoped to your authorized department(s) ("
                        + String.join(", ", DeptNormalizer.getFacultyAuthorizedDepts(actor.getUsername())) + ").";
                for (Student s : allStudents) {
                    if (DeptNormalizer.isFacultyAuthorizedForStudent(
                            actor.getAuthorizedDepartments(), actor.getUsername(), s.getBranch())) {
                        roleStudents.add(s);
                    }
                }
                roleCompanies.addAll(allCompanies);
                for (PlacementDrive d : allDrives) {
                    if (d.getEligibleBranches() != null && !d.getEligibleBranches().isEmpty()) {
                        boolean overlaps = false;
                        for (String eb : d.getEligibleBranches()) {
                            if (DeptNormalizer.isFacultyAuthorizedForStudent(
                                    actor.getAuthorizedDepartments(), actor.getUsername(), eb)) {
                                overlaps = true;
                                break;
                            }
                        }
                        if (overlaps) roleDrives.add(d);
                    } else {
                        roleDrives.add(d);
                    }
                }
            } else {
                scopeNotice = "Institutional Scope (" + role.name() + "): Full college-wide view across all departments, drives, and recruitment partners.";
                roleStudents.addAll(allStudents);
                roleCompanies.addAll(allCompanies);
                roleDrives.addAll(allDrives);
            }

            // Apply filters (Academic Year, Grad Year, Branch)
            List<Student> filteredStudents = new ArrayList<>();
            for (Student s : roleStudents) {
                if (!matchesFilter(s, effectiveFilter)) continue;
                filteredStudents.add(s);
            }

            // Compute branch report rows
            Map<String, List<Student>> byBranch = new LinkedHashMap<>();
            for (Student s : (role == UserRole.STUDENT ? allStudents : filteredStudents)) {
                String br = s.getBranch() != null && !s.getBranch().isBlank() ? s.getBranch() : "CSE";
                byBranch.computeIfAbsent(br, k -> new ArrayList<>()).add(s);
            }

            List<BranchReportRow> branchRows = new ArrayList<>();
            for (Map.Entry<String, List<Student>> entry : byBranch.entrySet()) {
                String br = entry.getKey();
                List<Student> bStudents = entry.getValue();
                int bTotal = bStudents.size();
                int bEligible = 0;
                int bPlaced = 0;
                double cgpaSum = 0;
                double pkgSum = 0;
                double highestPkg = 0;
                int pkgCount = 0;

                for (Student s : bStudents) {
                    cgpaSum += s.getCgpa();
                    if (s.getCgpa() >= 6.0 && s.getBacklogs() == 0) bEligible++;
                    if ("PLACED".equalsIgnoreCase(s.getPlacementStatus())) {
                        bPlaced++;
                        if (s.getPlacedPackageLpa() != null && s.getPlacedPackageLpa() > 0) {
                            pkgSum += s.getPlacedPackageLpa();
                            pkgCount++;
                            if (s.getPlacedPackageLpa() > highestPkg) highestPkg = s.getPlacedPackageLpa();
                        }
                    }
                }

                double bRate = bTotal > 0 ? (bPlaced * 100.0) / bTotal : 0.0;
                double bAvgCgpa = bTotal > 0 ? cgpaSum / bTotal : 0.0;
                double bAvgPkg = pkgCount > 0 ? pkgSum / pkgCount : 0.0;
                branchRows.add(new BranchReportRow(
                        br, bTotal, bEligible, bPlaced, Math.max(0, bTotal - bPlaced),
                        Math.round(bRate * 10.0) / 10.0,
                        Math.round(bAvgCgpa * 100.0) / 100.0,
                        Math.round(bAvgPkg * 100.0) / 100.0,
                        Math.round(highestPkg * 10.0) / 10.0
                ));
            }

            // Company report rows
            List<CompanyReportRow> companyRows = new ArrayList<>();
            for (Company c : roleCompanies) {
                int dCount = 0;
                double maxPkg = 0;
                for (PlacementDrive d : roleDrives) {
                    if (Objects.equals(d.getCompanyId(), c.getId())
                            || (d.getCompanyName() != null && c.getName() != null && d.getCompanyName().equalsIgnoreCase(c.getName()))) {
                        dCount++;
                        if (d.getSalaryPackageLpa() > maxPkg) maxPkg = d.getSalaryPackageLpa();
                    }
                }
                int appCount = 0;
                int offersCount = 0;
                for (Application a : scopedAppsRaw) {
                    if (Objects.equals(a.getCompanyId(), c.getId())
                            || (a.getCompanyName() != null && c.getName() != null && a.getCompanyName().equalsIgnoreCase(c.getName()))) {
                        appCount++;
                        if (a.getCurrentStage() != null && a.getCurrentStage().isOfferOrSelected()) {
                            offersCount++;
                        }
                    }
                }
                companyRows.add(new CompanyReportRow(
                        c.getId(), c.getName(), c.getIndustry(), c.getTier(), c.getApprovalStatus(),
                        dCount, appCount, offersCount, maxPkg
                ));
            }

            // Drive report rows
            List<DriveReportRow> driveRows = new ArrayList<>();
            for (PlacementDrive d : roleDrives) {
                int applicants = 0;
                int selected = 0;
                for (Application a : scopedAppsRaw) {
                    if (Objects.equals(a.getDriveId(), d.getId())) {
                        applicants++;
                        if (a.getCurrentStage() != null && a.getCurrentStage().isOfferOrSelected()) {
                            selected++;
                        }
                    }
                }
                driveRows.add(new DriveReportRow(
                        d.getId(), d.getCompanyName(), d.getJobRole(), d.getStatus(),
                        d.getDriveDate(), d.getApplicationDeadline(),
                        d.getMinCgpa(), d.getMaxBacklogs(), d.getTargetGradYear(),
                        d.getSalaryPackageLpa(), d.getPositions(), applicants, selected
                ));
            }

            // Pipeline rows
            Map<ApplicationStage, Integer> stageCounts = new EnumMap<>(ApplicationStage.class);
            for (Application a : scopedAppsRaw) {
                ApplicationStage st = a.getCurrentStage() != null ? a.getCurrentStage() : ApplicationStage.APPLICATION_SUBMITTED;
                stageCounts.merge(st, 1, Integer::sum);
            }
            int totalApps = scopedAppsRaw.size();
            List<PipelineReportRow> pipelineRows = new ArrayList<>();
            for (ApplicationStage st : ApplicationStage.values()) {
                int c = stageCounts.getOrDefault(st, 0);
                double pct = totalApps > 0 ? (c * 100.0) / totalApps : 0.0;
                pipelineRows.add(new PipelineReportRow(st.name(), st.getLabel(), c, Math.round(pct * 10.0) / 10.0));
            }

            // Skill report rows
            List<SkillReportRow> skillRows = new ArrayList<>();
            for (Skill sk : allSkills) {
                int countWithSkill = 0;
                int verifiedCount = 0;
                for (Student s : filteredStudents) {
                    if (s.getSkills() != null) {
                        for (StudentSkill ss : s.getSkills()) {
                            if (Objects.equals(ss.getSkillId(), sk.getId())
                                    || (ss.getSkillName() != null && ss.getSkillName().equalsIgnoreCase(sk.getName()))) {
                                countWithSkill++;
                                if (ss.isVerifiedByFaculty()) verifiedCount++;
                            }
                        }
                    }
                }
                int driveCount = 0;
                for (PlacementDrive d : roleDrives) {
                    if (d.getRequiredSkills() != null) {
                        for (DriveRequiredSkill drs : d.getRequiredSkills()) {
                            if (Objects.equals(drs.getSkillId(), sk.getId())
                                    || (drs.getSkillName() != null && drs.getSkillName().equalsIgnoreCase(sk.getName()))) {
                                driveCount++;
                                break;
                            }
                        }
                    }
                }
                skillRows.add(new SkillReportRow(sk.getId(), sk.getName(), sk.getCategory(), countWithSkill, verifiedCount, driveCount));
            }

            // Institutional summary KPIs
            int totalStud = filteredStudents.size();
            int placedStud = 0;
            int eligibleStud = 0;
            double totalPkg = 0;
            double highPkg = 0;
            int placedPkgCount = 0;

            for (Student s : filteredStudents) {
                if (s.getCgpa() >= 6.0 && s.getBacklogs() == 0) eligibleStud++;
                if ("PLACED".equalsIgnoreCase(s.getPlacementStatus())) {
                    placedStud++;
                    if (s.getPlacedPackageLpa() != null && s.getPlacedPackageLpa() > 0) {
                        totalPkg += s.getPlacedPackageLpa();
                        placedPkgCount++;
                        if (s.getPlacedPackageLpa() > highPkg) highPkg = s.getPlacedPackageLpa();
                    }
                }
            }

            double rate = totalStud > 0 ? (placedStud * 100.0) / totalStud : 0.0;
            double avgPkg = placedPkgCount > 0 ? totalPkg / placedPkgCount : 0.0;

            return new ReportBundle(
                    effectiveFilter,
                    filteredStudents,
                    roleCompanies,
                    roleDrives,
                    scopedAppsRaw,
                    branchRows,
                    companyRows,
                    driveRows,
                    pipelineRows,
                    skillRows,
                    totalStud,
                    eligibleStud,
                    placedStud,
                    Math.round(rate * 10.0) / 10.0,
                    Math.round(avgPkg * 100.0) / 100.0,
                    Math.round(highPkg * 10.0) / 10.0,
                    scopeNotice,
                    null
            );
        } catch (DatabaseException | MongoException ex) {
            return emptyReportBundle(effectiveFilter, "Failed to query MongoDB reports: " + ex.getMessage(), ex.getMessage());
        }
    }

    private static boolean matchesFilter(Student s, ReportFilter filter) {
        if (filter == null) return true;
        if (!"ALL".equalsIgnoreCase(filter.branch())) {
            String br = s.getBranch() != null ? s.getBranch().trim() : "";
            if (!br.equalsIgnoreCase(filter.branch())) return false;
        }
        if (!"ALL".equalsIgnoreCase(filter.academicYear())) {
            try {
                int yr = Integer.parseInt(filter.academicYear());
                if (s.getAcademicYear() != yr && s.getYearOfStudy() != yr) return false;
            } catch (NumberFormatException ignored) {
            }
        }
        if (!"ALL".equalsIgnoreCase(filter.graduationYear())) {
            try {
                int gy = Integer.parseInt(filter.graduationYear());
                if (s.getGraduationYear() != gy) return false;
            } catch (NumberFormatException ignored) {
            }
        }
        return true;
    }

    private static ReportBundle emptyReportBundle(ReportFilter f, String notice, String err) {
        return new ReportBundle(
                f, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                0, 0, 0, 0.0, 0.0, 0.0, notice, err
        );
    }

    // -------------------------------------------------------------------------
    // 3. SETTINGS & PROFILE MANAGEMENT
    // -------------------------------------------------------------------------

    public OperationResult<User> updateMyProfile(
            User actor,
            String fullName,
            String email,
            String phone,
            String department,
            String designation
    ) {
        if (actor == null || isBlank(actor.getId())) {
            return OperationResult.fail("Session expired. Please sign in again.");
        }
        String cleanName = fullName == null ? "" : fullName.trim();
        String cleanEmail = email == null ? "" : email.trim();
        if (cleanName.isEmpty() || cleanEmail.isEmpty()) {
            return OperationResult.fail("Full Name and Official Email cannot be empty.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            if (userDao.existsByEmailExcludingUser(db, cleanEmail, actor.getId())) {
                return OperationResult.fail("Another account is already registered with email \"" + cleanEmail + "\".");
            }
            userDao.updateUserProfile(db, actor.getId(), cleanName, cleanEmail, phone, department, designation);
            auditLogDao.insertLog(db, "PROFILE_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Updated account profile details (Name: " + cleanName + ", Dept: " + nonBlank(department, "-") + ").");
            User refreshed = userDao.findById(db, actor.getId());
            if (refreshed != null) {
                refreshed.setPasswordHash(null);
                refreshed.setSalt(null);
            }
            return OperationResult.ok("Profile updated and saved in MongoDB.", refreshed);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update profile in MongoDB: " + ex.getMessage());
        }
    }

    public OperationResult<Void> changeMyPassword(User actor, String currentPassword, String newPassword) {
        if (actor == null || isBlank(actor.getId())) {
            return OperationResult.fail("Session expired. Please sign in again.");
        }
        if (currentPassword == null || currentPassword.isEmpty() || newPassword == null || newPassword.isEmpty()) {
            return OperationResult.fail("Enter both your current password and a new password.");
        }
        if (newPassword.length() < 6) {
            return OperationResult.fail("New password must be at least 6 characters long.");
        }
        if (currentPassword.equals(newPassword)) {
            return OperationResult.fail("New password must differ from your current password.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            User dbUser = userDao.findById(db, actor.getId());
            if (dbUser == null) {
                return OperationResult.fail("User account not found in MongoDB.");
            }
            if (!PasswordHasher.verifyPassword(currentPassword, dbUser.getSalt(), dbUser.getPasswordHash())) {
                return OperationResult.fail("Current password verification failed. No changes saved.");
            }

            String[] hashed = PasswordHasher.hashWithBCrypt(newPassword);
            userDao.updatePasswordHash(db, dbUser.getId(), hashed[0], hashed[1]);
            auditLogDao.insertLog(db, "PASSWORD_CHANGED", dbUser.getId(), dbUser.getUsername(), dbUser.getRole().name(),
                    "User verified current password and updated credentials via BCrypt.");
            return OperationResult.ok("Password updated securely in MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to update password: " + ex.getMessage());
        }
    }

    public OperationResult<List<User>> loadUsersForAdmin(User actor) {
        if (actor == null || !actor.getRole().isAdminRole()) {
            return OperationResult.fail("Admin Authority Required: Only College Administrators may inspect user accounts.");
        }
        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            return OperationResult.ok("Loaded users from MongoDB.", userDao.findAll(db));
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to load user directory from MongoDB: " + ex.getMessage());
        }
    }

    public OperationResult<String> createUserByAdmin(
            User actor,
            String username,
            String email,
            String fullName,
            UserRole targetRole,
            String department,
            String designation,
            String initialPassword
    ) {
        if (actor == null || !actor.getRole().isAdminRole()) {
            return OperationResult.fail("Admin Authority Required: Only College Administrators may create accounts.");
        }
        String cleanUser = username == null ? "" : username.trim();
        String cleanEmail = email == null ? "" : email.trim();
        String cleanName = fullName == null ? "" : fullName.trim();
        if (cleanUser.isEmpty() || cleanEmail.isEmpty() || cleanName.isEmpty() || targetRole == null) {
            return OperationResult.fail("Username, Email, Full Name, and Role are required.");
        }
        if (initialPassword == null || initialPassword.length() < 8) {
            return OperationResult.fail("Initial password must be at least 8 characters long.");
        }
        if (targetRole.isAdminRole() && actor.getRole() != UserRole.PRINCIPAL && actor.getRole() != UserRole.ADMIN) {
            return OperationResult.fail("Only Principal or System Admin may provision Administrative accounts.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            if (userDao.existsByEmailOrUsername(db, cleanEmail, cleanUser)) {
                return OperationResult.fail("An account with that username or email already exists in MongoDB.");
            }
            User u = new User();
            u.setUsername(cleanUser);
            u.setEmail(cleanEmail);
            u.setFullName(cleanName);
            u.setRole(targetRole);
            u.setDepartment(department != null ? department.trim() : "");
            u.setDesignation(designation != null ? designation.trim() : "");

            String[] hashed = PasswordHasher.hashWithBCrypt(initialPassword);
            String id = userDao.createUserAccount(db, u, hashed[0], hashed[1]);
            auditLogDao.insertLog(db, "ADMIN_USER_CREATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Created " + targetRole.name() + " user @" + cleanUser + " (" + cleanName + ").");
            return OperationResult.ok("Created user @" + cleanUser + " in MongoDB.", id);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Failed to create user account: " + ex.getMessage());
        }
    }

    public OperationResult<Void> deleteUserByAdmin(User actor, String targetUserId) {
        if (actor == null || !actor.getRole().isAdminRole()) {
            return OperationResult.fail("Admin Authority Required: Only College Administrators may delete accounts.");
        }
        if (isBlank(targetUserId)) {
            return OperationResult.fail("Invalid target user ID.");
        }
        if (targetUserId.equals(actor.getId())) {
            return OperationResult.fail("Safety Guardrail: You cannot delete your own active administrative session account.");
        }

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            User target = userDao.findById(db, targetUserId);
            if (target == null) {
                return OperationResult.fail("User account not found in MongoDB.");
            }
            if (target.getRole() == UserRole.ADMIN && userDao.countActiveAdmins(db) <= 1) {
                return OperationResult.fail("Safety Guardrail: Cannot delete the last remaining active System Admin account.");
            }

            userDao.deleteUserById(db, target.getId());
            auditLogDao.insertLog(db, "ADMIN_USER_DELETED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                    "Deleted user @" + target.getUsername() + " (Role: " + target.getRole().name() + ").");
            return OperationResult.ok("Deleted account @" + target.getUsername() + " from MongoDB.", null);
        } catch (DatabaseException | MongoException ex) {
            return OperationResult.fail("Cannot delete user: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 4. AUDIT LOGS & DATABASE DIAGNOSTICS
    // -------------------------------------------------------------------------

    public record AuditLogBundle(List<AuditLog> logs, List<String> distinctActions, boolean fullInstitutionalView, String error) {}

    public AuditLogBundle loadAuditLogs(
            User actor,
            String search,
            String actionFilter,
            String roleFilter,
            String fromDate,
            String toDate
    ) {
        if (actor == null) {
            return new AuditLogBundle(List.of(), List.of(), false, "Authentication required.");
        }
        boolean fullInstitutional = actor.getRole().isPrivilegedRole();
        String restrictedActorId = fullInstitutional ? null : actor.getId();

        try {
            MongoDatabase db = DatabaseConnection.getInstance().getDatabase();
            List<AuditLog> logs = auditLogDao.findFiltered(
                    db, search, actionFilter, roleFilter, fromDate, toDate, restrictedActorId, 400
            );
            List<String> actions = fullInstitutional ? auditLogDao.findDistinctActions(db) : List.of();
            return new AuditLogBundle(logs, actions, fullInstitutional, null);
        } catch (DatabaseException | MongoException ex) {
            return new AuditLogBundle(List.of(), List.of(), fullInstitutional,
                    "Failed to query audit_logs from MongoDB: " + ex.getMessage());
        }
    }

    public ExtendedDbStatus loadExtendedDbDiagnostics() {
        return DatabaseConnection.getInstance().inspectExtendedDiagnostics();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nonBlank(String val, String fallback) {
        return (val != null && !val.trim().isEmpty()) ? val.trim() : fallback;
    }
}
