package edu.gcet.placementhub.services;

import edu.gcet.placementhub.dao.*;
import edu.gcet.placementhub.database.DatabaseConnection;
import edu.gcet.placementhub.models.*;
import edu.gcet.placementhub.models.GovernanceModels.*;
import edu.gcet.placementhub.services.StudentManagementService.OperationResult;
import edu.gcet.placementhub.utils.DeptNormalizer;
import edu.gcet.placementhub.utils.PasswordHasher;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * Pure JDBC Phase 5 Governance, Approvals, Analytics, Settings, Audit & Diagnostics Service.
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            List<Student> allStudents = studentDao.findAll(conn);
            List<Student> visibleStudents = new ArrayList<>();
            if (role == UserRole.FACULTY) {
                for (Student s : allStudents) {
                    if (DeptNormalizer.isFacultyAuthorizedForStudent(
                            actor.getAuthorizedDepartments(), actor.getUsername(), s.getBranch())) {
                        visibleStudents.add(s);
                    }
                }
            } else {
                // Placement Officer and Admins can inspect list (Admins remain read-only on student tab)
                visibleStudents.addAll(allStudents);
            }

            List<Faculty> facultyList = isAdmin ? facultyDao.findAll(conn) : List.of();
            List<Company> companyList = isAdmin ? companyDao.findAll(conn) : List.of();
            List<AdminRequest> adminRequests = isAdmin ? approvalDao.findAllAdminRequests(conn) : List.of();
            List<ApprovalRecord> history = approvalDao.findApprovalHistory(conn, 250);

            if (role == UserRole.FACULTY) {
                List<ApprovalRecord> scopedHistory = new ArrayList<>();
                for (ApprovalRecord r : history) {
                    if ("STUDENT_REGISTRATION".equalsIgnoreCase(r.getRequestType())
                            && (DeptNormalizer.isFacultyAuthorizedForStudent(
                            actor.getAuthorizedDepartments(), actor.getUsername(), r.getDepartment())
                            || actor.getId().equals(r.getApproverId()))) {
                        scopedHistory.add(r);
                    }
                }
                history = scopedHistory;
            }

            return new ApprovalBundle(visibleStudents, facultyList, companyList, adminRequests, history, null);
        } catch (SQLException ex) {
            return new ApprovalBundle(List.of(), List.of(), List.of(), List.of(), List.of(),
                    "Failed to load Approval Center data from MySQL: " + ex.getMessage());
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            int processed = 0;
            try {
                for (String sid : studentIds) {
                    Student stu = studentDao.findByIdOrRoll(conn, sid);
                    if (stu == null) continue;

                    if (role == UserRole.FACULTY && !DeptNormalizer.isFacultyAuthorizedForStudent(
                            actor.getAuthorizedDepartments(), actor.getUsername(), stu.getBranch())) {
                        conn.rollback();
                        return OperationResult.fail("Department Access Denied: You are not authorized to clear students in branch \""
                                + stu.getBranch() + "\" (" + stu.getRollNumber() + ").");
                    }

                    approvalDao.updateStudentApproval(
                            conn,
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
                    approvalDao.insertApprovalRecord(conn, rec);

                    auditLogDao.insertLog(
                            conn,
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
                    conn.rollback();
                    return OperationResult.fail("No matching student records found in MySQL.");
                }

                conn.commit();
                return OperationResult.ok(
                        (approve ? "Approved " : "Rejected ") + processed + " student registration(s) in MySQL.",
                        processed
                );
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to save student decision in MySQL: " + ex.getMessage());
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                approvalDao.updateFacultyApproval(
                        conn,
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
                approvalDao.insertApprovalRecord(conn, rec);

                auditLogDao.insertLog(
                        conn,
                        approve ? "FACULTY_APPROVED" : "FACULTY_REJECTED",
                        actor.getId(),
                        actor.getUsername(),
                        actor.getRole().name(),
                        (approve ? "Approved" : "Rejected") + " faculty coordinator " + target.getFullName()
                                + " (" + target.getEmployeeId() + ", " + target.getDepartment() + ")."
                                + (approve ? "" : " Reason: " + cleanReason)
                );
                conn.commit();
                return OperationResult.ok(
                        "Faculty account \"" + target.getFullName() + "\" marked " + (approve ? "ACTIVE" : "REJECTED") + " in MySQL.",
                        null
                );
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update faculty approval in MySQL: " + ex.getMessage());
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            Company existing = companyDao.findById(conn, target.getId());
            if (existing == null) {
                return OperationResult.fail("Company record no longer exists in MySQL.");
            }
            conn.setAutoCommit(false);
            try {
                approvalDao.updateCompanyApprovalAndLinkedUsers(
                        conn,
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
                approvalDao.insertApprovalRecord(conn, rec);

                auditLogDao.insertLog(
                        conn,
                        approve ? "COMPANY_APPROVED" : "COMPANY_REJECTED",
                        actor.getId(),
                        actor.getUsername(),
                        actor.getRole().name(),
                        (approve ? "Approved" : "Rejected") + " corporate partner \"" + existing.getName()
                                + "\" (" + existing.getIndustry() + ")."
                                + (approve ? "" : " Reason: " + cleanReason)
                );
                conn.commit();
                return OperationResult.ok(
                        "Company \"" + existing.getName() + "\" updated to " + (approve ? "APPROVED" : "REJECTED") + " in MySQL.",
                        null
                );
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update company approval in MySQL: " + ex.getMessage());
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            AdminRequest req = approvalDao.findAdminRequestById(conn, requestId);
            if (req == null) {
                return OperationResult.fail("Admin privilege request not found in MySQL.");
            }
            if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
                return OperationResult.fail("This privilege request has already been processed (" + req.getStatus() + ").");
            }

            conn.setAutoCommit(false);
            try {
                String decision = approve ? "APPROVED" : "REJECTED";
                String effectiveNotes = cleanNotes.isEmpty()
                        ? "Principal granted " + req.getRequestedRole() + " privilege."
                        : cleanNotes;

                approvalDao.updateAdminRequestDecision(
                        conn, req.getId(), decision, effectiveNotes, actor.getId(), actor.getFullName()
                );

                if (approve) {
                    approvalDao.elevateUserRole(conn, req.getUserId(), req.getRequestedRole());
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
                approvalDao.insertApprovalRecord(conn, rec);

                auditLogDao.insertLog(
                        conn,
                        approve ? "ADMIN_PRIVILEGE_GRANTED" : "ADMIN_PRIVILEGE_DECLINED",
                        actor.getId(),
                        actor.getUsername(),
                        actor.getRole().name(),
                        "Principal " + (approve ? "granted" : "declined") + " " + req.getRequestedRole()
                                + " privilege for " + req.getApplicantName() + ". Notes: " + effectiveNotes
                );
                conn.commit();
                return OperationResult.ok(
                        "Admin privilege petition for \"" + req.getApplicantName() + "\" marked " + decision + ".",
                        null
                );
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to record Principal decision in MySQL: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // 2. ANALYTICS & REPORTS (LIVE MYSQL QUERIES + STRICT RBAC SCOPING)
    // -------------------------------------------------------------------------

    public ReportBundle generateReportBundle(User actor, ReportFilter filter) {
        ReportFilter effectiveFilter = filter != null ? filter : ReportFilter.all();
        if (actor == null) {
            return emptyReportBundle(effectiveFilter, "Authentication required.", "Sign in to view placement reports.");
        }

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            List<Student> allStudents = studentDao.findAll(conn);
            List<Company> allCompanies = companyDao.findAll(conn);
            List<PlacementDrive> allDrives = driveDao.findAll(conn);
            List<Skill> allSkills = skillDao.findAllSkills(conn);
            List<Application> scopedAppsRaw = applicationDao.findAllForUser(conn, actor);

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
                scopeNotice = "Faculty Coordinator Scope: Department-scoped student placement records ("
                        + String.join(", ", actor.getAuthorizedDepartments().isEmpty()
                        ? List.of(nonBlank(actor.getDepartment(), "All Assigned Branches"))
                        : actor.getAuthorizedDepartments()) + ") & institutional drives.";
                for (Student s : allStudents) {
                    if (DeptNormalizer.isFacultyAuthorizedForStudent(
                            actor.getAuthorizedDepartments(), actor.getUsername(), s.getBranch())) {
                        roleStudents.add(s);
                    }
                }
                roleCompanies.addAll(allCompanies);
                roleDrives.addAll(allDrives);
            } else {
                scopeNotice = "Institutional Governance Scope (" + role.name() + "): Complete live MySQL records across all GCET departments, companies, drives & applications.";
                roleStudents.addAll(allStudents);
                roleCompanies.addAll(allCompanies);
                roleDrives.addAll(allDrives);
            }

            // Apply Filters (Academic Year, Graduation Year, Branch, Date Range)
            LocalDate fromDt = parseDateSafe(effectiveFilter.fromDate());
            LocalDate toDt = parseDateSafe(effectiveFilter.toDate());

            List<Student> filteredStudents = new ArrayList<>();
            for (Student s : roleStudents) {
                if (!matchesAcademicYear(s, effectiveFilter.academicYear())) continue;
                if (!matchesGraduationYear(s.getGraduationYear(), effectiveFilter.graduationYear())) continue;
                if (!matchesBranch(s.getBranch(), effectiveFilter.branch())) continue;
                if (!matchesDateRange(s.getCreatedAt(), fromDt, toDt)) continue;
                filteredStudents.add(s);
            }

            List<PlacementDrive> filteredDrives = new ArrayList<>();
            for (PlacementDrive d : roleDrives) {
                if (!matchesGraduationYear(d.getTargetGraduationYear(), effectiveFilter.graduationYear())) continue;
                if (!matchesDriveBranch(d, effectiveFilter.branch())) continue;
                if (!matchesDateRange(d.getDriveDate(), fromDt, toDt)) continue;
                filteredDrives.add(d);
            }

            Set<String> filteredDriveIds = new HashSet<>();
            for (PlacementDrive d : filteredDrives) filteredDriveIds.add(d.getId());
            Set<String> filteredStudentIds = new HashSet<>();
            for (Student s : filteredStudents) filteredStudentIds.add(s.getId());

            List<Application> filteredApps = new ArrayList<>();
            for (Application a : scopedAppsRaw) {
                if (!"ALL".equalsIgnoreCase(effectiveFilter.branch())
                        && !matchesBranch(a.getStudentBranch(), effectiveFilter.branch())) {
                    continue;
                }
                if ((fromDt != null || toDt != null) && !matchesDateRange(a.getAppliedAt(), fromDt, toDt)) {
                    continue;
                }
                if ((!"ALL".equalsIgnoreCase(effectiveFilter.academicYear())
                        || !"ALL".equalsIgnoreCase(effectiveFilter.graduationYear()))
                        && !filteredStudentIds.isEmpty() && !filteredStudentIds.contains(a.getStudentId())) {
                    continue;
                }
                filteredApps.add(a);
            }

            // Aggregate Branch Rows
            // Note: For STUDENT role, compute branch summary across department cohort safely without exposing peers' rows
            List<Student> branchSource = (role == UserRole.STUDENT)
                    ? filterStudentsByFilter(allStudents, effectiveFilter, fromDt, toDt)
                    : filteredStudents;

            Map<String, List<Student>> byBranch = new LinkedHashMap<>();
            for (Student s : branchSource) {
                String br = nonBlank(s.getBranch(), "Unassigned");
                byBranch.computeIfAbsent(br, k -> new ArrayList<>()).add(s);
            }

            List<BranchReportRow> branchRows = new ArrayList<>();
            for (Map.Entry<String, List<Student>> entry : byBranch.entrySet()) {
                List<Student> list = entry.getValue();
                int total = list.size();
                int eligible = 0;
                int placed = 0;
                double cgpaSum = 0;
                double pkgSum = 0;
                int pkgCount = 0;
                double maxPkg = 0;

                for (Student st : list) {
                    cgpaSum += st.getCgpa();
                    if (st.getCgpa() >= 6.0 && st.getBacklogs() == 0) {
                        eligible++;
                    }
                    if ("PLACED".equalsIgnoreCase(st.getPlacementStatus())) {
                        placed++;
                        if (st.getPlacedPackageLpa() != null && st.getPlacedPackageLpa() > 0) {
                            pkgSum += st.getPlacedPackageLpa();
                            pkgCount++;
                            if (st.getPlacedPackageLpa() > maxPkg) maxPkg = st.getPlacedPackageLpa();
                        }
                    }
                }
                double rate = total > 0 ? (placed * 100.0) / total : 0.0;
                double avgCgpa = total > 0 ? cgpaSum / total : 0.0;
                double avgPkg = pkgCount > 0 ? pkgSum / pkgCount : 0.0;
                branchRows.add(new BranchReportRow(
                        entry.getKey(), total, eligible, placed, Math.max(0, total - placed),
                        rate, avgCgpa, avgPkg, maxPkg
                ));
            }
            branchRows.sort((a, b) -> Integer.compare(b.totalStudents(), a.totalStudents()));

            // Aggregate Company Rows
            List<CompanyReportRow> companyRows = new ArrayList<>();
            for (Company c : roleCompanies) {
                int drivesCnt = 0;
                double maxPkg = 0;
                for (PlacementDrive d : filteredDrives) {
                    if (c.getId().equals(d.getCompanyId())
                            || (c.getName() != null && c.getName().equalsIgnoreCase(d.getCompanyName()))) {
                        drivesCnt++;
                        if (d.getSalaryPackageLpa() > maxPkg) maxPkg = d.getSalaryPackageLpa();
                    }
                }
                int appsCnt = 0;
                int offersCnt = 0;
                for (Application a : filteredApps) {
                    boolean match = (c.getId().equals(a.getCompanyId()))
                            || (c.getName() != null && c.getName().equalsIgnoreCase(a.getCompanyName()));
                    if (match) {
                        appsCnt++;
                        if (a.getCurrentStage() != null && a.getCurrentStage().isOfferOrSelected()) {
                            offersCnt++;
                            if (a.getFinalOfferLpa() != null && a.getFinalOfferLpa() > maxPkg) {
                                maxPkg = a.getFinalOfferLpa();
                            }
                        }
                    }
                }
                companyRows.add(new CompanyReportRow(
                        c.getId(), nonBlank(c.getName(), "-"), nonBlank(c.getIndustry(), "-"),
                        nonBlank(c.getTier(), "Standard IT"), nonBlank(c.getApprovalStatus(), nonBlank(c.getStatus(), "ACTIVE")),
                        drivesCnt, appsCnt, offersCnt, maxPkg
                ));
            }

            // Aggregate Drive Rows
            List<DriveReportRow> driveRows = new ArrayList<>();
            for (PlacementDrive d : filteredDrives) {
                int appsCnt = 0;
                int selCnt = 0;
                for (Application a : filteredApps) {
                    if (d.getId().equals(a.getDriveId())) {
                        appsCnt++;
                        if (a.getCurrentStage() != null && a.getCurrentStage().isOfferOrSelected()) {
                            selCnt++;
                        }
                    }
                }
                driveRows.add(new DriveReportRow(
                        d.getId(), nonBlank(d.getCompanyName(), "-"), nonBlank(d.getJobRole(), "-"),
                        nonBlank(d.getStatus(), "OPEN"), nonBlank(d.getDriveDate(), "-"),
                        nonBlank(d.getApplicationDeadline(), "-"), d.getMinCgpa(),
                        d.getMaxBacklogsAllowed(), d.getTargetGraduationYear(),
                        d.getSalaryPackageLpa(), d.getAvailablePositions(), appsCnt, selCnt
                ));
            }

            // Pipeline Stage Distribution
            Map<ApplicationStage, Integer> stageCounts = new LinkedHashMap<>();
            for (ApplicationStage stg : ApplicationStage.values()) {
                stageCounts.put(stg, 0);
            }
            for (Application a : filteredApps) {
                ApplicationStage stg = a.getCurrentStage() != null ? a.getCurrentStage() : ApplicationStage.APPLICATION_SUBMITTED;
                stageCounts.put(stg, stageCounts.getOrDefault(stg, 0) + 1);
            }
            int totalApps = filteredApps.size();
            List<PipelineReportRow> pipelineRows = new ArrayList<>();
            for (Map.Entry<ApplicationStage, Integer> entry : stageCounts.entrySet()) {
                int cnt = entry.getValue();
                double pct = totalApps > 0 ? (cnt * 100.0) / totalApps : 0.0;
                pipelineRows.add(new PipelineReportRow(entry.getKey().name(), entry.getKey().getLabel(), cnt, pct));
            }

            // Skill Distribution Report (supply across scoped students vs demand across filtered drives)
            Map<String, Integer> skillSupply = new HashMap<>();
            Map<String, Integer> skillVerified = new HashMap<>();
            for (Student st : filteredStudents) {
                for (StudentSkill ss : st.getSkills()) {
                    skillSupply.put(ss.getSkillId(), skillSupply.getOrDefault(ss.getSkillId(), 0) + 1);
                    if (ss.isVerifiedByFaculty()) {
                        skillVerified.put(ss.getSkillId(), skillVerified.getOrDefault(ss.getSkillId(), 0) + 1);
                    }
                }
            }
            Map<String, Integer> skillDemand = new HashMap<>();
            for (PlacementDrive d : filteredDrives) {
                for (DriveRequiredSkill dr : d.getRequiredSkills()) {
                    skillDemand.put(dr.getSkillId(), skillDemand.getOrDefault(dr.getSkillId(), 0) + 1);
                }
            }
            List<SkillReportRow> skillRows = new ArrayList<>();
            for (Skill sk : allSkills) {
                int sup = skillSupply.getOrDefault(sk.getId(), 0);
                int ver = skillVerified.getOrDefault(sk.getId(), 0);
                int dem = skillDemand.getOrDefault(sk.getId(), 0);
                if (sup > 0 || dem > 0) {
                    skillRows.add(new SkillReportRow(sk.getId(), sk.getName(), sk.getCategory(), sup, ver, dem));
                }
            }
            skillRows.sort((a, b) -> Integer.compare(
                    b.studentsWithSkill() + b.drivesRequiringSkill(),
                    a.studentsWithSkill() + a.drivesRequiringSkill()
            ));

            // KPI Totals
            int totalStu = filteredStudents.size();
            int eligibleStu = 0;
            int placedStu = 0;
            double pkgSum = 0;
            int pkgCount = 0;
            double highPkg = 0;

            for (Student s : filteredStudents) {
                if (s.getCgpa() >= 6.0 && s.getBacklogs() == 0) eligibleStu++;
                if ("PLACED".equalsIgnoreCase(s.getPlacementStatus())) {
                    placedStu++;
                    if (s.getPlacedPackageLpa() != null && s.getPlacedPackageLpa() > 0) {
                        pkgSum += s.getPlacedPackageLpa();
                        pkgCount++;
                        if (s.getPlacedPackageLpa() > highPkg) highPkg = s.getPlacedPackageLpa();
                    }
                }
            }
            for (PlacementDrive d : filteredDrives) {
                if (d.getSalaryPackageLpa() > highPkg) highPkg = d.getSalaryPackageLpa();
            }

            double ratePct = totalStu > 0 ? (placedStu * 100.0) / totalStu : 0.0;
            double avgPkg = pkgCount > 0 ? pkgSum / pkgCount : 0.0;

            return new ReportBundle(
                    effectiveFilter,
                    filteredStudents,
                    roleCompanies,
                    filteredDrives,
                    filteredApps,
                    branchRows,
                    companyRows,
                    driveRows,
                    pipelineRows,
                    skillRows,
                    totalStu,
                    eligibleStu,
                    placedStu,
                    ratePct,
                    avgPkg,
                    highPkg,
                    scopeNotice,
                    null
            );
        } catch (SQLException ex) {
            return emptyReportBundle(effectiveFilter, "MySQL query error: " + ex.getMessage(), "");
        }
    }

    // -------------------------------------------------------------------------
    // 3. SETTINGS, PROFILE, PASSWORD & USER MANAGEMENT
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (userDao.existsByEmailExcludingUser(conn, cleanEmail, actor.getId())) {
                return OperationResult.fail("Another account is already registered with email \"" + cleanEmail + "\".");
            }
            conn.setAutoCommit(false);
            try {
                userDao.updateUserProfile(conn, actor.getId(), cleanName, cleanEmail, phone, department, designation);
                auditLogDao.insertLog(conn, "PROFILE_UPDATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Updated account profile details (Name: " + cleanName + ", Dept: " + nonBlank(department, "-") + ").");
                User refreshed = userDao.findById(conn, actor.getId());
                if (refreshed != null) {
                    refreshed.setPasswordHash(null);
                    refreshed.setSalt(null);
                }
                conn.commit();
                return OperationResult.ok("Profile updated and saved in MySQL.", refreshed);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update profile in MySQL: " + ex.getMessage());
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            User dbUser = userDao.findById(conn, actor.getId());
            if (dbUser == null) {
                return OperationResult.fail("User account not found in MySQL.");
            }
            if (!PasswordHasher.verifyPassword(currentPassword, dbUser.getSalt(), dbUser.getPasswordHash())) {
                return OperationResult.fail("Current password verification failed. No changes saved.");
            }

            String[] hashed = PasswordHasher.hashWithBCrypt(newPassword);
            conn.setAutoCommit(false);
            try {
                userDao.updatePasswordHash(conn, dbUser.getId(), hashed[0], hashed[1]);
                auditLogDao.insertLog(conn, "PASSWORD_CHANGED", dbUser.getId(), dbUser.getUsername(), dbUser.getRole().name(),
                        "User verified current password and updated credentials via BCrypt.");
                conn.commit();
                return OperationResult.ok("Password updated securely in MySQL.", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to update password: " + ex.getMessage());
        }
    }

    public OperationResult<List<User>> loadUsersForAdmin(User actor) {
        if (actor == null || !actor.getRole().isAdminRole()) {
            return OperationResult.fail("Admin Authority Required: Only College Administrators may inspect user accounts.");
        }
        try {
            return OperationResult.ok("Loaded users from MySQL.", userDao.findAll());
        } catch (SQLException ex) {
            return OperationResult.fail("Failed to load user directory from MySQL: " + ex.getMessage());
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            if (userDao.existsByEmailOrUsername(conn, cleanEmail, cleanUser)) {
                return OperationResult.fail("An account with that username or email already exists in MySQL.");
            }
            User u = new User();
            u.setId("usr-" + UUID.randomUUID());
            u.setUsername(cleanUser);
            u.setEmail(cleanEmail);
            u.setFullName(cleanName);
            u.setRole(targetRole);
            u.setDepartment(department != null ? department.trim() : "");
            u.setDesignation(designation != null ? designation.trim() : "");

            String[] hashed = PasswordHasher.hashWithBCrypt(initialPassword);
            conn.setAutoCommit(false);
            try {
                userDao.createUserAccount(conn, u, hashed[0], hashed[1]);
                auditLogDao.insertLog(conn, "ADMIN_USER_CREATED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Created " + targetRole.name() + " user @" + cleanUser + " (" + cleanName + ").");
                conn.commit();
                return OperationResult.ok("Created user @" + cleanUser + " in MySQL.", u.getId());
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            User target = userDao.findById(conn, targetUserId);
            if (target == null) {
                return OperationResult.fail("User account not found in MySQL.");
            }
            if (target.getRole() == UserRole.ADMIN && userDao.countActiveAdmins(conn) <= 1) {
                return OperationResult.fail("Safety Guardrail: Cannot delete the last remaining active System Admin account.");
            }

            conn.setAutoCommit(false);
            try {
                userDao.deleteUserById(conn, target.getId());
                auditLogDao.insertLog(conn, "ADMIN_USER_DELETED", actor.getId(), actor.getUsername(), actor.getRole().name(),
                        "Deleted user @" + target.getUsername() + " (Role: " + target.getRole().name() + ").");
                conn.commit();
                return OperationResult.ok("Deleted account @" + target.getUsername() + " from MySQL.", null);
            } catch (SQLException txEx) {
                conn.rollback();
                throw txEx;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            return OperationResult.fail("Cannot delete user (may be referenced by placement records): " + ex.getMessage());
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

        try (Connection conn = DatabaseConnection.getInstance().getConnection()) {
            List<AuditLog> logs = auditLogDao.findFiltered(
                    conn, search, actionFilter, roleFilter, fromDate, toDate, restrictedActorId, 400
            );
            List<String> actions = fullInstitutional ? auditLogDao.findDistinctActions(conn) : List.of();
            return new AuditLogBundle(logs, actions, fullInstitutional, null);
        } catch (SQLException ex) {
            return new AuditLogBundle(List.of(), List.of(), fullInstitutional,
                    "Failed to query audit_logs from MySQL: " + ex.getMessage());
        }
    }

    public ExtendedDbStatus loadExtendedDbDiagnostics() {
        return DatabaseConnection.getInstance().inspectExtendedDiagnostics();
    }

    // -------------------------------------------------------------------------
    // FILTER HELPERS
    // -------------------------------------------------------------------------

    private List<Student> filterStudentsByFilter(List<Student> list, ReportFilter f, LocalDate fromDt, LocalDate toDt) {
        List<Student> out = new ArrayList<>();
        for (Student s : list) {
            if (!matchesAcademicYear(s, f.academicYear())) continue;
            if (!matchesGraduationYear(s.getGraduationYear(), f.graduationYear())) continue;
            if (!matchesBranch(s.getBranch(), f.branch())) continue;
            if (!matchesDateRange(s.getCreatedAt(), fromDt, toDt)) continue;
            out.add(s);
        }
        return out;
    }

    private boolean matchesAcademicYear(Student s, String filterVal) {
        if (filterVal == null || filterVal.isBlank() || "ALL".equalsIgnoreCase(filterVal)) return true;
        int yr = s.getAcademicYear() > 0 ? s.getAcademicYear() : s.getYearOfStudy();
        return filterVal.trim().equals(String.valueOf(yr));
    }

    private boolean matchesGraduationYear(int gradYear, String filterVal) {
        if (filterVal == null || filterVal.isBlank() || "ALL".equalsIgnoreCase(filterVal)) return true;
        return filterVal.trim().equals(String.valueOf(gradYear));
    }

    private boolean matchesBranch(String branch, String filterBranch) {
        if (filterBranch == null || filterBranch.isBlank() || "ALL".equalsIgnoreCase(filterBranch)) return true;
        return DeptNormalizer.normalizeDeptCode(branch)
                .equalsIgnoreCase(DeptNormalizer.normalizeDeptCode(filterBranch));
    }

    private boolean matchesDriveBranch(PlacementDrive d, String filterBranch) {
        if (filterBranch == null || filterBranch.isBlank() || "ALL".equalsIgnoreCase(filterBranch)) return true;
        if (d.getEligibleBranches() == null || d.getEligibleBranches().isEmpty()) return true;
        String target = DeptNormalizer.normalizeDeptCode(filterBranch);
        for (String b : d.getEligibleBranches()) {
            if (DeptNormalizer.normalizeDeptCode(b).equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private boolean matchesDateRange(String rawIsoOrDate, LocalDate fromDt, LocalDate toDt) {
        if (fromDt == null && toDt == null) return true;
        if (rawIsoOrDate == null || rawIsoOrDate.isBlank()) return true;
        LocalDate parsed = parseDateSafe(rawIsoOrDate);
        if (parsed == null) return true;
        if (fromDt != null && parsed.isBefore(fromDt)) return false;
        if (toDt != null && parsed.isAfter(toDt)) return false;
        return true;
    }

    private LocalDate parseDateSafe(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = raw.trim();
        if (clean.length() >= 10) {
            clean = clean.substring(0, 10);
        }
        try {
            return LocalDate.parse(clean);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ReportBundle emptyReportBundle(ReportFilter filter, String err, String scopeNotice) {
        return new ReportBundle(
                filter, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                0, 0, 0, 0.0, 0.0, 0.0, scopeNotice, err
        );
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nonBlank(String s, String def) {
        return (s != null && !s.isBlank()) ? s : def;
    }
}
