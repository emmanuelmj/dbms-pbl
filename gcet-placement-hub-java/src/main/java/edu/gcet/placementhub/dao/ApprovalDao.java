package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.models.GovernanceModels.AdminRequest;
import edu.gcet.placementhub.models.GovernanceModels.ApprovalRecord;
import edu.gcet.placementhub.utils.PasswordHasher;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * JDBC Data Access Object for Phase 5 Institutional Approvals:
 * - Student registration approvals (Faculty / TPO)
 * - Faculty registration approvals (Admins)
 * - Company registration approvals (Admins)
 * - Admin privilege petitions (`admin_requests`, Principal only)
 * - Immutable audit history (`approval_records`)
 */
public class ApprovalDao {

    public List<AdminRequest> findAllAdminRequests(Connection conn) throws SQLException {
        List<AdminRequest> list = new ArrayList<>();
        String sql = "SELECT * FROM admin_requests ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AdminRequest r = new AdminRequest();
                r.setId(rs.getString("id"));
                r.setUserId(rs.getString("user_id"));
                r.setApplicantName(rs.getString("applicant_name"));
                r.setApplicantEmail(rs.getString("applicant_email"));
                r.setCurrentRole(rs.getString("current_role"));
                r.setRequestedRole(rs.getString("requested_role"));
                r.setDepartment(rs.getString("department"));
                r.setReason(rs.getString("reason"));
                r.setStatus(rs.getString("status"));
                r.setDecidedBy(rs.getString("decided_by"));
                r.setDecidedByName(rs.getString("decided_by_name"));
                r.setDecisionNotes(rs.getString("decision_notes"));
                Timestamp c = rs.getTimestamp("created_at");
                if (c != null) r.setCreatedAt(c.toInstant().toString());
                Timestamp d = rs.getTimestamp("decided_at");
                if (d != null) r.setDecidedAt(d.toInstant().toString());
                list.add(r);
            }
        }
        return list;
    }

    public AdminRequest findAdminRequestById(Connection conn, String id) throws SQLException {
        String sql = "SELECT * FROM admin_requests WHERE id = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AdminRequest r = new AdminRequest();
                    r.setId(rs.getString("id"));
                    r.setUserId(rs.getString("user_id"));
                    r.setApplicantName(rs.getString("applicant_name"));
                    r.setApplicantEmail(rs.getString("applicant_email"));
                    r.setCurrentRole(rs.getString("current_role"));
                    r.setRequestedRole(rs.getString("requested_role"));
                    r.setDepartment(rs.getString("department"));
                    r.setReason(rs.getString("reason"));
                    r.setStatus(rs.getString("status"));
                    r.setDecidedBy(rs.getString("decided_by"));
                    r.setDecidedByName(rs.getString("decided_by_name"));
                    r.setDecisionNotes(rs.getString("decision_notes"));
                    Timestamp c = rs.getTimestamp("created_at");
                    if (c != null) r.setCreatedAt(c.toInstant().toString());
                    Timestamp d = rs.getTimestamp("decided_at");
                    if (d != null) r.setDecidedAt(d.toInstant().toString());
                    return r;
                }
            }
        }
        return null;
    }

    public void insertAdminRequest(Connection conn, AdminRequest req) throws SQLException {
        String sql = """
                INSERT INTO admin_requests (
                  id, user_id, applicant_name, applicant_email, current_role,
                  requested_role, department, reason, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', NOW())
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, req.getId() != null ? req.getId() : "admreq-" + UUID.randomUUID());
            ps.setString(2, req.getUserId());
            ps.setString(3, req.getApplicantName());
            ps.setString(4, req.getApplicantEmail());
            ps.setString(5, req.getCurrentRole());
            ps.setString(6, req.getRequestedRole());
            ps.setString(7, req.getDepartment());
            ps.setString(8, req.getReason());
            ps.executeUpdate();
        }
    }

    public void updateAdminRequestDecision(
            Connection conn,
            String requestId,
            String decision,
            String notes,
            String principalId,
            String principalName
    ) throws SQLException {
        String sql = """
                UPDATE admin_requests
                SET status = ?, decided_by = ?, decided_by_name = ?, decision_notes = ?, decided_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, decision);
            ps.setString(2, principalId);
            ps.setString(3, principalName);
            ps.setString(4, notes);
            ps.setString(5, requestId);
            ps.executeUpdate();
        }
    }

    public void elevateUserRole(Connection conn, String userId, String newRole) throws SQLException {
        if (userId == null || userId.isBlank() || newRole == null || newRole.isBlank()) return;
        String sql = "UPDATE users SET role = ?, is_active = TRUE, status = 'ACTIVE' WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setString(2, userId);
            ps.executeUpdate();
        }
    }

    public List<ApprovalRecord> findApprovalHistory(Connection conn, int limit) throws SQLException {
        List<ApprovalRecord> records = new ArrayList<>();
        String sql = "SELECT * FROM approval_records ORDER BY decided_at DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ApprovalRecord r = new ApprovalRecord();
                    r.setId(rs.getString("id"));
                    r.setRequestType(rs.getString("request_type"));
                    r.setTargetId(rs.getString("target_id"));
                    r.setTargetName(rs.getString("target_name"));
                    r.setStudentRollNumber(rs.getString("student_roll_number"));
                    r.setDepartment(rs.getString("department"));
                    r.setApproverId(rs.getString("approver_id"));
                    r.setApproverName(rs.getString("approver_name"));
                    r.setApproverRole(rs.getString("approver_role"));
                    r.setDecision(rs.getString("decision"));
                    r.setComments(rs.getString("comments"));
                    Timestamp ts = rs.getTimestamp("decided_at");
                    if (ts != null) r.setDecidedAt(ts.toInstant().toString());
                    records.add(r);
                }
            }
        }
        return records;
    }

    public void insertApprovalRecord(Connection conn, ApprovalRecord r) throws SQLException {
        String sql = """
                INSERT INTO approval_records (
                  id, request_type, target_id, target_name, student_roll_number,
                  department, approver_id, approver_name, approver_role, decision, comments, decided_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, r.getId() != null ? r.getId() : "apr-" + UUID.randomUUID());
            ps.setString(2, r.getRequestType());
            ps.setString(3, r.getTargetId());
            ps.setString(4, r.getTargetName());
            if (r.getStudentRollNumber() != null && !r.getStudentRollNumber().isBlank()) {
                ps.setString(5, r.getStudentRollNumber());
            } else {
                ps.setNull(5, Types.VARCHAR);
            }
            ps.setString(6, r.getDepartment());
            ps.setString(7, r.getApproverId());
            ps.setString(8, r.getApproverName());
            ps.setString(9, r.getApproverRole());
            ps.setString(10, r.getDecision());
            ps.setString(11, r.getComments());
            ps.executeUpdate();
        }
    }

    public void updateStudentApproval(
            Connection conn,
            String studentId,
            String userId,
            String rollNumber,
            boolean approve,
            String reason,
            String facultyId,
            String facultyName
    ) throws SQLException {
        String newApproval = approve ? "APPROVED" : "REJECTED";
        String stuSql = """
                UPDATE students
                SET approval_status = ?, approved_by_faculty_id = ?, approved_by_faculty_name = ?,
                    approved_at = NOW(), rejection_reason = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(stuSql)) {
            ps.setString(1, newApproval);
            ps.setString(2, facultyId);
            ps.setString(3, facultyName);
            if (approve) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, reason);
            }
            ps.setString(5, studentId);
            ps.executeUpdate();
        }

        String cleanUserId = userId != null ? userId.trim() : "";
        String cleanRoll = rollNumber != null ? rollNumber.trim() : "";
        String usrSql = """
                UPDATE users
                SET status = ?, is_active = ?, approved_by = ?, approved_by_name = ?,
                    approved_at = NOW(), rejection_reason = ?
                WHERE (? <> '' AND id = ?)
                   OR (? <> '' AND student_roll_number IS NOT NULL AND LOWER(student_roll_number) = LOWER(?))
                """;
        try (PreparedStatement ps = conn.prepareStatement(usrSql)) {
            ps.setString(1, newApproval);
            ps.setBoolean(2, approve);
            ps.setString(3, facultyId);
            ps.setString(4, facultyName);
            if (approve) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, reason);
            }
            ps.setString(6, cleanUserId);
            ps.setString(7, cleanUserId);
            ps.setString(8, cleanRoll);
            ps.setString(9, cleanRoll);
            ps.executeUpdate();
        }
    }

    public void updateFacultyApproval(
            Connection conn,
            String facultyRowId,
            String userId,
            String email,
            boolean approve,
            String reason,
            String adminId,
            String adminName
    ) throws SQLException {
        String userStatus = approve ? "ACTIVE" : "REJECTED";
        String cleanUserId = userId != null ? userId.trim() : "";
        String cleanEmail = email != null ? email.trim() : "";
        String cleanFacultyId = facultyRowId != null ? facultyRowId.trim() : "";

        String usrSql = """
                UPDATE users
                SET status = ?, is_active = ?, approved_by = ?, approved_by_name = ?,
                    approved_at = NOW(), rejection_reason = ?
                WHERE (? <> '' AND id = ?)
                   OR (? <> '' AND email IS NOT NULL AND LOWER(email) = LOWER(?))
                """;
        try (PreparedStatement ps = conn.prepareStatement(usrSql)) {
            ps.setString(1, userStatus);
            ps.setBoolean(2, approve);
            ps.setString(3, adminId);
            ps.setString(4, adminName);
            if (approve) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, reason);
            }
            ps.setString(6, cleanUserId);
            ps.setString(7, cleanUserId);
            ps.setString(8, cleanEmail);
            ps.setString(9, cleanEmail);
            ps.executeUpdate();
        }

        String facSql = """
                UPDATE faculty
                SET admin_approved = ?, status = ?, approved_by_name = ?, rejection_reason = ?
                WHERE (? <> '' AND id = ?)
                   OR (? <> '' AND user_id = ?)
                   OR (? <> '' AND email IS NOT NULL AND LOWER(email) = LOWER(?))
                """;
        try (PreparedStatement ps = conn.prepareStatement(facSql)) {
            ps.setBoolean(1, approve);
            ps.setString(2, userStatus);
            ps.setString(3, adminName);
            if (approve) {
                ps.setNull(4, Types.VARCHAR);
            } else {
                ps.setString(4, reason);
            }
            ps.setString(5, cleanFacultyId);
            ps.setString(6, cleanFacultyId);
            ps.setString(7, cleanUserId);
            ps.setString(8, cleanUserId);
            ps.setString(9, cleanEmail);
            ps.setString(10, cleanEmail);
            ps.executeUpdate();
        }
    }

    public void updateCompanyApprovalAndLinkedUsers(
            Connection conn,
            String companyId,
            String companyName,
            String hrName,
            String hrEmail,
            boolean approve,
            String reason,
            String adminId,
            String adminName
    ) throws SQLException {
        String status = approve ? "APPROVED" : "REJECTED";
        String compSql = """
                UPDATE companies
                SET status = ?, approval_status = ?, rejection_reason = ?,
                    approved_by_admin_id = ?, approved_by_admin_name = ?, approved_by_name = ?, approved_at = NOW()
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(compSql)) {
            ps.setString(1, approve ? "ACTIVE" : "REJECTED");
            ps.setString(2, status);
            if (approve) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, reason);
            }
            ps.setString(4, adminId);
            ps.setString(5, adminName);
            ps.setString(6, adminName);
            ps.setString(7, companyId);
            ps.executeUpdate();
        }

        String safeName = companyName != null ? companyName.trim() : "partner";
        String slug = safeName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (slug.isEmpty()) slug = "partner";
        if (slug.length() > 15) slug = slug.substring(0, 15);
        String baseRecruiterUsername = "recruiter_" + slug;
        String cleanEmail = hrEmail != null ? hrEmail.trim() : "";

        // Inspect existing linked user before updating so valid self-registered hashes are never overwritten
        String existingUserId = null;
        String existingHash = null;
        String findSql = """
                SELECT id, password_hash FROM users
                WHERE company_id = ?
                   OR (? <> '' AND LOWER(email) = LOWER(?))
                   OR LOWER(username) = LOWER(?)
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, companyId);
            ps.setString(2, cleanEmail);
            ps.setString(3, cleanEmail);
            ps.setString(4, baseRecruiterUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    existingUserId = rs.getString("id");
                    existingHash = rs.getString("password_hash");
                }
            }
        }

        if (existingUserId != null) {
            boolean hasExistingHash = existingHash != null && !existingHash.trim().isEmpty();
            if (!approve || hasExistingHash) {
                String usrSql = """
                        UPDATE users
                        SET status = ?, is_active = ?, company_id = COALESCE(NULLIF(company_id, ''), ?),
                            approved_by = ?, approved_by_name = ?, approved_at = NOW(), rejection_reason = ?
                        WHERE id = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(usrSql)) {
                    ps.setString(1, approve ? "ACTIVE" : "REJECTED");
                    ps.setBoolean(2, approve);
                    ps.setString(3, companyId);
                    ps.setString(4, adminId);
                    ps.setString(5, adminName);
                    if (approve) {
                        ps.setNull(6, Types.VARCHAR);
                    } else {
                        ps.setString(6, reason);
                    }
                    ps.setString(7, existingUserId);
                    ps.executeUpdate();
                }
            } else {
                // Existing row had no password hash: assign configured initial recruiter password requiring change on first login
                String initialPass = edu.gcet.placementhub.config.DatabaseConfig.getInstance().getEnvValue("GCET_BOOTSTRAP_RECRUITER_PASS");
                if (initialPass == null || initialPass.isEmpty()) {
                    throw new SQLException("GCET_BOOTSTRAP_RECRUITER_PASS is not configured in environment or .env.");
                }
                String[] hashed = PasswordHasher.hashWithBCrypt(initialPass);
                String usrSql = """
                        UPDATE users
                        SET password_hash = ?, salt = ?, must_change_password = TRUE,
                            status = 'APPROVED', is_active = TRUE, company_id = COALESCE(NULLIF(company_id, ''), ?),
                            approved_by = ?, approved_by_name = ?, approved_at = NOW(), rejection_reason = NULL
                        WHERE id = ?
                        """;
                try (PreparedStatement ps = conn.prepareStatement(usrSql)) {
                    ps.setString(1, hashed[0]);
                    ps.setString(2, hashed[1]);
                    ps.setString(3, companyId);
                    ps.setString(4, adminId);
                    ps.setString(5, adminName);
                    ps.setString(6, existingUserId);
                    ps.executeUpdate();
                }
            }
        } else if (approve) {
            // Ensure unique recruiter username if another account uses the base prefix
            String recruiterUsername = baseRecruiterUsername;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE LOWER(username) = LOWER(?) LIMIT 1")) {
                ps.setString(1, recruiterUsername);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String suffix = companyId != null ? companyId.replaceAll("[^a-zA-Z0-9]", "") : UUID.randomUUID().toString();
                        if (suffix.length() > 4) suffix = suffix.substring(suffix.length() - 4);
                        recruiterUsername = baseRecruiterUsername + "_" + suffix;
                    }
                }
            }

            // Create initial recruiter user account with configured temporary BCrypt password requiring first-login change
            String initialPass = edu.gcet.placementhub.config.DatabaseConfig.getInstance().getEnvValue("GCET_BOOTSTRAP_RECRUITER_PASS");
            if (initialPass == null || initialPass.isEmpty()) {
                throw new SQLException("GCET_BOOTSTRAP_RECRUITER_PASS is not configured in environment or .env.");
            }
            String[] hashed = PasswordHasher.hashWithBCrypt(initialPass);
            String effectiveEmail = !cleanEmail.isEmpty() ? cleanEmail : (recruiterUsername + "@company.com");
            String effectiveHrName = (hrName != null && !hrName.isBlank()) ? hrName.trim() : "HR Lead";
            String insertRecruiterSql = """
                    INSERT INTO users (
                      id, username, email, full_name, role, department, company_id,
                      password_hash, salt, must_change_password, status, is_active, approved_by, approved_by_name, approved_at, created_at
                    ) VALUES (?, ?, ?, ?, 'COMPANY', ?, ?, ?, ?, TRUE, 'APPROVED', TRUE, ?, ?, NOW(), NOW())
                    """;
            try (PreparedStatement ps = conn.prepareStatement(insertRecruiterSql)) {
                ps.setString(1, "usr-comp-" + UUID.randomUUID());
                ps.setString(2, recruiterUsername);
                ps.setString(3, effectiveEmail);
                ps.setString(4, effectiveHrName + " (" + safeName + ")");
                ps.setString(5, safeName);
                ps.setString(6, companyId);
                ps.setString(7, hashed[0]);
                ps.setString(8, hashed[1]);
                ps.setString(9, adminId);
                ps.setString(10, adminName);
                ps.executeUpdate();
            }
        }
    }
}
