package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.db.DatabaseConnection;
import edu.gcet.placementhub.model.AdminRequest;
import edu.gcet.placementhub.model.ApprovalRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GCET PLACEMENT HUB - Approvals & Audit Data Access Object
 * Handles administrative privilege requests (Principal exclusive),
 * approval history audit trails, and security action logging.
 */
public class ApprovalsDao {
    private static final Logger LOGGER = Logger.getLogger(ApprovalsDao.class.getName());

    // ========================================================
    // 1. Administrator Privilege Requests (Principal Exclusive)
    // ========================================================

    public List<AdminRequest> findAllAdminRequests(String statusFilter) {
        List<AdminRequest> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM admin_requests WHERE 1=1 ");
        if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
            sql.append("AND status = ? ");
        }
        sql.append("ORDER BY created_at DESC");

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            if (statusFilter != null && !statusFilter.isEmpty() && !"ALL".equalsIgnoreCase(statusFilter)) {
                ps.setString(1, statusFilter.toUpperCase());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AdminRequest ar = new AdminRequest();
                    ar.setId(rs.getString("id"));
                    ar.setUserId(rs.getString("user_id"));
                    ar.setApplicantName(rs.getString("applicant_name"));
                    ar.setApplicantEmail(rs.getString("applicant_email"));
                    ar.setCurrentRole(rs.getString("current_role"));
                    ar.setRequestedRole(rs.getString("requested_role"));
                    ar.setDepartment(rs.getString("department"));
                    ar.setReason(rs.getString("reason"));
                    ar.setStatus(rs.getString("status"));
                    ar.setDecidedByPrincipalId(rs.getString("decided_by_principal_id"));
                    ar.setDecidedByPrincipalName(rs.getString("decided_by_principal_name"));
                    ar.setDecisionDate(rs.getTimestamp("decision_date"));
                    ar.setDecisionNotes(rs.getString("decision_notes"));
                    ar.setCreatedAt(rs.getTimestamp("created_at"));
                    list.add(ar);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error querying admin requests", e);
        }
        return list;
    }

    public boolean submitAdminRequest(AdminRequest req) {
        String sql = "INSERT INTO admin_requests (id, user_id, applicant_name, applicant_email, current_role, requested_role, department, reason, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')";
        if (req.getId() == null) req.setId(UUID.randomUUID().toString());

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, req.getId());
            ps.setString(2, req.getUserId());
            ps.setString(3, req.getApplicantName());
            ps.setString(4, req.getApplicantEmail());
            ps.setString(5, req.getCurrentRole());
            ps.setString(6, req.getRequestedRole() != null ? req.getRequestedRole() : "ADMIN");
            ps.setString(7, req.getDepartment());
            ps.setString(8, req.getReason());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error submitting admin request", e);
            return false;
        }
    }

    public boolean decideAdminRequest(String requestId, String principalId, String principalName, boolean approve, String notes) {
        String querySql = "SELECT user_id, requested_role FROM admin_requests WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement psQuery = conn.prepareStatement(querySql)) {
            psQuery.setString(1, requestId);
            String userId = null;
            String requestedRole = "ADMIN";
            try (ResultSet rs = psQuery.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getString("user_id");
                    requestedRole = rs.getString("requested_role");
                }
            }

            String updateSql = "UPDATE admin_requests SET status = ?, decided_by_principal_id = ?, decided_by_principal_name = ?, " +
                               "decision_date = CURRENT_TIMESTAMP, decision_notes = ? WHERE id = ?";
            try (PreparedStatement psUpd = conn.prepareStatement(updateSql)) {
                psUpd.setString(1, approve ? "APPROVED" : "REJECTED");
                psUpd.setString(2, principalId);
                psUpd.setString(3, principalName);
                psUpd.setString(4, notes);
                psUpd.setString(5, requestId);
                int affected = psUpd.executeUpdate();

                // If approved by Principal, promote user account role
                if (approve && userId != null) {
                    try (PreparedStatement psUser = conn.prepareStatement("UPDATE users SET role = ?, is_active = TRUE WHERE id = ?")) {
                        psUser.setString(1, requestedRole);
                        psUser.setString(2, userId);
                        psUser.executeUpdate();
                    }
                }
                return affected > 0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error deciding admin request", e);
            return false;
        }
    }

    // ========================================================
    // 2. Approval Audit Records
    // ========================================================

    public void logApprovalRecord(ApprovalRecord record) {
        String sql = "INSERT INTO approval_records (id, request_type, target_id, target_name, approver_id, approver_name, approver_role, decision, comments) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        if (record.getId() == null) record.setId(UUID.randomUUID().toString());

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.getId());
            ps.setString(2, record.getRequestType().name());
            ps.setString(3, record.getTargetId());
            ps.setString(4, record.getTargetName());
            ps.setString(5, record.getApproverId());
            ps.setString(6, record.getApproverName());
            ps.setString(7, record.getApproverRole());
            ps.setString(8, record.getDecision());
            ps.setString(9, record.getComments());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error logging approval record", e);
        }
    }

    public List<ApprovalRecord> getApprovalHistory(String targetId) {
        List<ApprovalRecord> list = new ArrayList<>();
        String sql = "SELECT * FROM approval_records WHERE target_id = ? ORDER BY decided_at DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, targetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ApprovalRecord r = new ApprovalRecord();
                    r.setId(rs.getString("id"));
                    r.setRequestType(ApprovalRecord.RequestType.valueOf(rs.getString("request_type")));
                    r.setTargetId(rs.getString("target_id"));
                    r.setTargetName(rs.getString("target_name"));
                    r.setApproverId(rs.getString("approver_id"));
                    r.setApproverName(rs.getString("approver_name"));
                    r.setApproverRole(rs.getString("approver_role"));
                    r.setDecision(rs.getString("decision"));
                    r.setComments(rs.getString("comments"));
                    r.setDecidedAt(rs.getTimestamp("decided_at"));
                    list.add(r);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error loading approval history", e);
        }
        return list;
    }

    // ========================================================
    // 3. Security Audit Logging
    // ========================================================

    public void logAudit(String action, String actorId, String actorUsername, String actorRole, String details) {
        String sql = "INSERT INTO audit_logs (id, action, actor_id, actor_username, actor_role, details) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, action);
            ps.setString(3, actorId);
            ps.setString(4, actorUsername);
            ps.setString(5, actorRole);
            ps.setString(6, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Audit log note: " + e.getMessage());
        }
    }
}
