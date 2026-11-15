package edu.gcet.placementhub.dao;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import edu.gcet.placementhub.config.DatabaseConfig;
import edu.gcet.placementhub.models.GovernanceModels.AdminRequest;
import edu.gcet.placementhub.models.GovernanceModels.ApprovalRecord;
import edu.gcet.placementhub.utils.PasswordHasher;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static edu.gcet.placementhub.database.BsonUtil.*;

/**
 * Data Access Object for institutional approvals:
 * <ul>
 *   <li>{@code approvals}      - immutable decision records (student / faculty / company / admin privilege)</li>
 *   <li>{@code admin_requests} - Principal-only privilege petitions</li>
 * </ul>
 * plus the cross-collection status updates on {@code students}, {@code users}, {@code faculty}, {@code companies}.
 */
public class ApprovalDao {

    public static final String APPROVALS = "approvals";
    public static final String ADMIN_REQUESTS = "admin_requests";

    public static MongoCollection<Document> approvals(MongoDatabase db) {
        return db.getCollection(APPROVALS);
    }

    public static MongoCollection<Document> adminRequests(MongoDatabase db) {
        return db.getCollection(ADMIN_REQUESTS);
    }

    // --------------------------------------------------------- admin requests

    public List<AdminRequest> findAllAdminRequests(MongoDatabase db) {
        List<AdminRequest> list = new ArrayList<>();
        List<AdminRequest> pending = new ArrayList<>();
        List<AdminRequest> others = new ArrayList<>();
        for (Document d : adminRequests(db).find().sort(Sorts.descending("createdAt"))) {
            AdminRequest r = mapAdminRequest(d);
            if ("PENDING".equalsIgnoreCase(r.getStatus())) pending.add(r); else others.add(r);
        }
        list.addAll(pending);
        list.addAll(others);
        return list;
    }

    public AdminRequest findAdminRequestById(MongoDatabase db, String id) {
        Document d = adminRequests(db).find(idFilter(id)).first();
        return d == null ? null : mapAdminRequest(d);
    }

    public String insertAdminRequest(MongoDatabase db, AdminRequest req) {
        Document d = new Document();
        if (req.getId() != null && !req.getId().isBlank()) d.append("_id", toKey(req.getId()));
        d.append("userId", toKey(req.getUserId()))
                .append("applicantName", req.getApplicantName())
                .append("applicantEmail", req.getApplicantEmail())
                .append("currentRole", req.getCurrentRole())
                .append("requestedRole", req.getRequestedRole())
                .append("department", req.getDepartment())
                .append("reason", req.getReason())
                .append("status", "PENDING")
                .append("createdAt", now());
        adminRequests(db).insertOne(d);
        req.setId(id(d));
        return req.getId();
    }

    public void updateAdminRequestDecision(
            MongoDatabase db,
            String requestId,
            String decision,
            String notes,
            String principalId,
            String principalName
    ) {
        adminRequests(db).updateOne(idFilter(requestId), Updates.combine(
                Updates.set("status", decision),
                Updates.set("decidedBy", toKey(principalId)),
                Updates.set("decidedByName", principalName),
                Updates.set("decisionNotes", notes),
                Updates.set("decidedAt", now())));
    }

    public void elevateUserRole(MongoDatabase db, String userId, String newRole) {
        if (userId == null || userId.isBlank() || newRole == null || newRole.isBlank()) return;
        db.getCollection(UserDao.COLLECTION).updateOne(idFilter(userId), Updates.combine(
                Updates.set("role", newRole),
                Updates.set("isActive", true),
                Updates.set("status", "ACTIVE")));
    }

    // ------------------------------------------------------- approval records

    public List<ApprovalRecord> findApprovalHistory(MongoDatabase db, int limit) {
        List<ApprovalRecord> records = new ArrayList<>();
        for (Document d : approvals(db).find().sort(Sorts.descending("decidedAt")).limit(Math.max(1, limit))) {
            records.add(mapApprovalRecord(d));
        }
        return records;
    }

    public String insertApprovalRecord(MongoDatabase db, ApprovalRecord r) {
        Document d = new Document();
        if (r.getId() != null && !r.getId().isBlank()) d.append("_id", toKey(r.getId()));
        d.append("requestType", r.getRequestType())
                .append("targetId", toKey(r.getTargetId()))
                .append("targetName", r.getTargetName())
                .append("studentRollNumber", nvl(r.getStudentRollNumber(), null))
                .append("department", r.getDepartment())
                .append("approverId", toKey(r.getApproverId()))
                .append("approverName", r.getApproverName())
                .append("approverRole", r.getApproverRole())
                .append("decision", r.getDecision())
                .append("comments", r.getComments())
                .append("decidedAt", r.getDecidedAt() != null ? toDateOrToday(r.getDecidedAt()) : now());
        approvals(db).insertOne(d);
        r.setId(id(d));
        return r.getId();
    }

    // --------------------------------------------------- student approvals

    public void updateStudentApproval(
            MongoDatabase db,
            String studentId,
            String userId,
            String rollNumber,
            boolean approve,
            String reason,
            String facultyId,
            String facultyName
    ) {
        String newApproval = approve ? "APPROVED" : "REJECTED";
        db.getCollection(StudentDao.COLLECTION).updateOne(idFilter(studentId), Updates.combine(
                Updates.set("approvalStatus", newApproval),
                Updates.set("approvedByFacultyId", toKey(facultyId)),
                Updates.set("approvedByFacultyName", facultyName),
                Updates.set("approvedAt", now()),
                Updates.set("rejectionReason", approve ? null : reason),
                Updates.set("updatedAt", now())));

        List<Bson> userMatch = new ArrayList<>();
        if (userId != null && !userId.isBlank()) userMatch.add(idFilter(userId.trim()));
        if (rollNumber != null && !rollNumber.isBlank()) {
            userMatch.add(Filters.regex("studentRollNumber", "^" + java.util.regex.Pattern.quote(rollNumber.trim()) + "$", "i"));
        }
        if (userMatch.isEmpty()) return;
        db.getCollection(UserDao.COLLECTION).updateMany(Filters.or(userMatch), Updates.combine(
                Updates.set("status", newApproval),
                Updates.set("isActive", approve),
                Updates.set("approvedBy", toKey(facultyId)),
                Updates.set("approvedByName", facultyName),
                Updates.set("approvedAt", now()),
                Updates.set("rejectionReason", approve ? null : reason)));
    }

    // --------------------------------------------------- faculty approvals

    public void updateFacultyApproval(
            MongoDatabase db,
            String facultyRowId,
            String userId,
            String email,
            boolean approve,
            String reason,
            String adminId,
            String adminName
    ) {
        String userStatus = approve ? "ACTIVE" : "REJECTED";

        List<Bson> userMatch = new ArrayList<>();
        if (userId != null && !userId.isBlank()) userMatch.add(idFilter(userId.trim()));
        if (email != null && !email.isBlank()) {
            userMatch.add(Filters.regex("email", "^" + java.util.regex.Pattern.quote(email.trim()) + "$", "i"));
        }
        if (!userMatch.isEmpty()) {
            db.getCollection(UserDao.COLLECTION).updateMany(Filters.or(userMatch), Updates.combine(
                    Updates.set("status", userStatus),
                    Updates.set("isActive", approve),
                    Updates.set("approvedBy", toKey(adminId)),
                    Updates.set("approvedByName", adminName),
                    Updates.set("approvedAt", now()),
                    Updates.set("rejectionReason", approve ? null : reason)));
        }

        List<Bson> facMatch = new ArrayList<>();
        if (facultyRowId != null && !facultyRowId.isBlank()) facMatch.add(idFilter(facultyRowId.trim()));
        if (userId != null && !userId.isBlank()) facMatch.add(refFilter("userId", userId.trim()));
        if (email != null && !email.isBlank()) {
            facMatch.add(Filters.regex("email", "^" + java.util.regex.Pattern.quote(email.trim()) + "$", "i"));
        }
        if (!facMatch.isEmpty()) {
            db.getCollection(FacultyDao.COLLECTION).updateMany(Filters.or(facMatch), Updates.combine(
                    Updates.set("adminApproved", approve),
                    Updates.set("status", userStatus),
                    Updates.set("approvedByName", adminName),
                    Updates.set("rejectionReason", approve ? null : reason)));
        }
    }

    // --------------------------------------------------- company approvals

    public void updateCompanyApprovalAndLinkedUsers(
            MongoDatabase db,
            String companyId,
            String companyName,
            String hrName,
            String hrEmail,
            boolean approve,
            String reason,
            String adminId,
            String adminName
    ) throws com.mongodb.MongoException {
        String status = approve ? "APPROVED" : "REJECTED";
        db.getCollection(CompanyDao.COLLECTION).updateOne(idFilter(companyId), Updates.combine(
                Updates.set("status", approve ? "ACTIVE" : "REJECTED"),
                Updates.set("approvalStatus", status),
                Updates.set("rejectionReason", approve ? null : reason),
                Updates.set("approvedByAdminId", toKey(adminId)),
                Updates.set("approvedByName", adminName),
                Updates.set("approvedAt", now()),
                Updates.set("updatedAt", now())));

        String safeName = companyName != null ? companyName.trim() : "partner";
        String slug = safeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (slug.isEmpty()) slug = "partner";
        if (slug.length() > 15) slug = slug.substring(0, 15);
        String baseRecruiterUsername = "recruiter_" + slug;
        String cleanEmail = hrEmail != null ? hrEmail.trim() : "";

        MongoCollection<Document> users = db.getCollection(UserDao.COLLECTION);
        List<Bson> ors = new ArrayList<>();
        ors.add(refFilter("companyId", companyId));
        if (!cleanEmail.isEmpty()) ors.add(Filters.eq("email", cleanEmail));
        ors.add(Filters.eq("username", baseRecruiterUsername));
        Document existing = users.find(Filters.or(ors)).collation(CASE_INSENSITIVE).first();

        if (existing != null) {
            String existingHash = str(existing, "passwordHash");
            boolean hasExistingHash = existingHash != null && !existingHash.trim().isEmpty();
            if (!approve || hasExistingHash) {
                List<Bson> sets = new ArrayList<>();
                sets.add(Updates.set("status", approve ? "ACTIVE" : "REJECTED"));
                sets.add(Updates.set("isActive", approve));
                if (existing.get("companyId") == null) sets.add(Updates.set("companyId", toKey(companyId)));
                sets.add(Updates.set("approvedBy", toKey(adminId)));
                sets.add(Updates.set("approvedByName", adminName));
                sets.add(Updates.set("approvedAt", now()));
                sets.add(Updates.set("rejectionReason", approve ? null : reason));
                users.updateOne(Filters.eq("_id", existing.get("_id")), Updates.combine(sets));
            } else {
                String[] hashed = PasswordHasher.hashWithBCrypt(
                        DatabaseConfig.getInstance().getBootstrapPassword("GCET_BOOTSTRAP_RECRUITER_PASS"));
                users.updateOne(Filters.eq("_id", existing.get("_id")), Updates.combine(
                        Updates.set("passwordHash", hashed[0]),
                        Updates.set("salt", hashed[1]),
                        Updates.set("mustChangePassword", true),
                        Updates.set("status", "APPROVED"),
                        Updates.set("isActive", true),
                        Updates.set("companyId", existing.get("companyId") != null ? existing.get("companyId") : toKey(companyId)),
                        Updates.set("approvedBy", toKey(adminId)),
                        Updates.set("approvedByName", adminName),
                        Updates.set("approvedAt", now()),
                        Updates.set("rejectionReason", null)));
            }
        } else if (approve) {
            String recruiterUsername = baseRecruiterUsername;
            if (users.find(Filters.eq("username", recruiterUsername)).collation(CASE_INSENSITIVE).first() != null) {
                String suffix = companyId != null ? companyId.replaceAll("[^a-zA-Z0-9]", "") : UUID.randomUUID().toString();
                if (suffix.length() > 4) suffix = suffix.substring(suffix.length() - 4);
                recruiterUsername = baseRecruiterUsername + "_" + suffix;
            }
            String[] hashed = PasswordHasher.hashWithBCrypt(
                    DatabaseConfig.getInstance().getBootstrapPassword("GCET_BOOTSTRAP_RECRUITER_PASS"));
            String effectiveEmail = !cleanEmail.isEmpty() ? cleanEmail : (recruiterUsername + "@company.com");
            String effectiveHrName = (hrName != null && !hrName.isBlank()) ? hrName.trim() : "HR Lead";
            users.insertOne(new Document("username", recruiterUsername)
                    .append("email", effectiveEmail)
                    .append("fullName", effectiveHrName + " (" + safeName + ")")
                    .append("role", "COMPANY")
                    .append("department", safeName)
                    .append("designation", "Recruiter")
                    .append("phone", "")
                    .append("companyId", toKey(companyId))
                    .append("companyName", safeName)
                    .append("passwordHash", hashed[0])
                    .append("salt", hashed[1])
                    .append("mustChangePassword", true)
                    .append("status", "APPROVED")
                    .append("isActive", true)
                    .append("authorizedDepartments", new ArrayList<>())
                    .append("approvedBy", toKey(adminId))
                    .append("approvedByName", adminName)
                    .append("approvedAt", now())
                    .append("createdAt", now()));
        }
    }

    // ------------------------------------------------------------------ mapping

    public static AdminRequest mapAdminRequest(Document d) {
        AdminRequest r = new AdminRequest();
        r.setId(id(d));
        r.setUserId(ref(d, "userId"));
        r.setApplicantName(str(d, "applicantName"));
        r.setApplicantEmail(str(d, "applicantEmail"));
        r.setCurrentRole(str(d, "currentRole"));
        r.setRequestedRole(str(d, "requestedRole"));
        r.setDepartment(str(d, "department"));
        r.setReason(str(d, "reason"));
        r.setStatus(str(d, "status"));
        r.setDecidedBy(ref(d, "decidedBy"));
        r.setDecidedByName(str(d, "decidedByName"));
        r.setDecisionNotes(str(d, "decisionNotes"));
        r.setCreatedAt(iso(d, "createdAt"));
        r.setDecidedAt(iso(d, "decidedAt"));
        return r;
    }

    public static ApprovalRecord mapApprovalRecord(Document d) {
        ApprovalRecord r = new ApprovalRecord();
        r.setId(id(d));
        r.setRequestType(str(d, "requestType"));
        r.setTargetId(ref(d, "targetId"));
        r.setTargetName(str(d, "targetName"));
        r.setStudentRollNumber(str(d, "studentRollNumber"));
        r.setDepartment(str(d, "department"));
        r.setApproverId(ref(d, "approverId"));
        r.setApproverName(str(d, "approverName"));
        r.setApproverRole(str(d, "approverRole"));
        r.setDecision(str(d, "decision"));
        r.setComments(str(d, "comments"));
        r.setDecidedAt(iso(d, "decidedAt"));
        return r;
    }
}
