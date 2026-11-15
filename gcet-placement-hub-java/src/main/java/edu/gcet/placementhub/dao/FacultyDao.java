package edu.gcet.placementhub.dao;

import edu.gcet.placementhub.models.Faculty;
import edu.gcet.placementhub.utils.DeptNormalizer;

import java.sql.*;
import java.util.*;

/**
 * JDBC Data Access Object querying `users` + `faculty` tables for the GCET Faculty Directory.
 */
public class FacultyDao {

    public List<Faculty> findAll(Connection conn) throws SQLException {
        List<Faculty> result = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        String sql = """
                SELECT u.id AS user_id, u.username, u.email, u.full_name, u.department, u.status AS user_status,
                       u.is_active, u.authorized_departments AS user_auth_depts, u.approved_by, u.rejection_reason AS user_rej,
                       u.created_at,
                       f.id AS faculty_row_id, f.faculty_id, f.employee_id, f.designation, f.phone,
                       f.authorized_departments AS fac_auth_depts, f.faculty_approved, f.admin_approved,
                       f.approved_by_name, f.rejection_reason AS fac_rej, f.status AS fac_status
                FROM users u
                LEFT JOIN faculty f ON f.user_id = u.id OR LOWER(f.email) = LOWER(u.email)
                WHERE u.role = 'FACULTY'
                ORDER BY u.created_at DESC
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Faculty f = new Faculty();
                String uid = rs.getString("user_id");
                String username = rs.getString("username");
                String rowId = rs.getString("faculty_row_id");
                f.setId(rowId != null && !rowId.isBlank() ? rowId : uid);
                f.setUserId(uid);
                f.setUsername(username);

                String empId = rs.getString("employee_id");
                if (empId == null || empId.isBlank()) {
                    empId = rs.getString("faculty_id");
                }
                if (empId == null || empId.isBlank()) {
                    empId = username != null ? username.toUpperCase(Locale.ROOT) : "-";
                }
                f.setEmployeeId(empId);

                f.setFullName(rs.getString("full_name"));
                f.setDepartment(rs.getString("department"));
                String desig = rs.getString("designation");
                f.setDesignation(desig != null && !desig.isBlank() ? desig : "Faculty Coordinator");
                f.setEmail(rs.getString("email"));
                f.setPhone(rs.getString("phone"));

                String rawAuth = rs.getString("user_auth_depts");
                if (rawAuth == null || rawAuth.isBlank()) {
                    rawAuth = rs.getString("fac_auth_depts");
                }
                List<String> authDepts = parseDepartments(rawAuth);
                if (authDepts.isEmpty()) {
                    authDepts = new ArrayList<>(DeptNormalizer.getFacultyAuthorizedDepts(username));
                }
                if (authDepts.isEmpty() && f.getDepartment() != null && !f.getDepartment().isBlank()) {
                    authDepts = List.of(f.getDepartment());
                }
                f.setAuthorizedDepartments(authDepts);

                String uStatus = rs.getString("user_status");
                String fStatus = rs.getString("fac_status");
                String effStatus = uStatus != null ? uStatus : (fStatus != null ? fStatus : "APPROVED");
                if ("PENDING_APPROVAL".equalsIgnoreCase(effStatus)) {
                    effStatus = "PENDING";
                }
                f.setStatus(effStatus);
                f.setActive(rs.getBoolean("is_active"));
                f.setFacultyApproved(rs.getObject("faculty_approved") == null || rs.getBoolean("faculty_approved"));
                f.setAdminApproved(rs.getObject("admin_approved") == null ? !"PENDING".equalsIgnoreCase(effStatus) : rs.getBoolean("admin_approved"));

                String appBy = rs.getString("approved_by_name");
                if (appBy == null || appBy.isBlank()) appBy = rs.getString("approved_by");
                f.setApprovedByName(appBy);

                String rej = rs.getString("fac_rej");
                if (rej == null || rej.isBlank()) rej = rs.getString("user_rej");
                f.setRejectionReason(rej);

                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) f.setCreatedAt(ts.toInstant().toString());

                result.add(f);
                if (f.getEmail() != null) seenKeys.add(f.getEmail().toLowerCase(Locale.ROOT));
                if (f.getUserId() != null) seenKeys.add(f.getUserId());
            }
        }

        // Also include standalone rows from `faculty` table if any exist without a `users` entry
        String standaloneSql = "SELECT * FROM faculty ORDER BY created_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(standaloneSql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String email = rs.getString("email");
                String uid = rs.getString("user_id");
                if ((email != null && seenKeys.contains(email.toLowerCase(Locale.ROOT)))
                        || (uid != null && seenKeys.contains(uid))) {
                    continue;
                }
                Faculty f = new Faculty();
                f.setId(rs.getString("id"));
                f.setUserId(uid);
                String facId = rs.getString("faculty_id");
                f.setUsername(facId);
                String empId = rs.getString("employee_id");
                f.setEmployeeId(empId != null && !empId.isBlank() ? empId : facId);
                f.setFullName(rs.getString("full_name"));
                f.setDepartment(rs.getString("department"));
                f.setDesignation(rs.getString("designation"));
                f.setEmail(email);
                f.setPhone(rs.getString("phone"));
                List<String> authDepts = parseDepartments(rs.getString("authorized_departments"));
                if (authDepts.isEmpty() && f.getDepartment() != null && !f.getDepartment().isBlank()) {
                    authDepts = List.of(f.getDepartment());
                }
                f.setAuthorizedDepartments(authDepts);
                f.setFacultyApproved(rs.getBoolean("faculty_approved"));
                f.setAdminApproved(rs.getBoolean("admin_approved"));
                f.setApprovedByName(rs.getString("approved_by_name"));
                f.setRejectionReason(rs.getString("rejection_reason"));
                String status = rs.getString("status");
                f.setStatus(status != null ? status : "ACTIVE");
                f.setActive("ACTIVE".equalsIgnoreCase(f.getStatus()) || "APPROVED".equalsIgnoreCase(f.getStatus()));
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) f.setCreatedAt(ts.toInstant().toString());
                result.add(f);
            }
        } catch (SQLException ignored) {
            // Gracefully handle if faculty table has no additional records
        }

        return result;
    }

    private List<String> parseDepartments(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        for (String part : trimmed.split(",")) {
            String clean = part.replace("\"", "").trim();
            if (!clean.isEmpty()) {
                out.add(clean);
            }
        }
        return out;
    }
}
